package ca.bc.gov.nrs.fta.tenure.service;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Saves and relabels a cut block with legacy's own packages, called rather than ported so their
 * rules and table fan-out stay exactly legacy's. Only {@code MAINLINE} is public on either.
 *
 * <ul>
 *   <li>{@code FTA_904_CUTBLKDETAIL} — Save runs {@code Fta_Edit_Status_Change}, the salvage area
 *       limits and the district override audit. Legacy's form holds the block's values between
 *       its GET and its SAVE, so both run here in one block: GET loads them, the request's edits
 *       replace those the user may change, SAVE writes them.
 *   <li>{@code FTA_231_CUTBLK_RELABEL} — updates the label on {@code CUT_BLOCK},
 *       {@code HARVEST_AMEND}, {@code CUT_BLOCK_OPEN_ADMIN}, {@code WASTE_ASSESSMENT_AREA} and
 *       {@code FDC_AUDIT_LOG} and writes the {@code CUT_BLOCK_KEY_EVENT} 'R' record.
 * </ul>
 *
 * <p>The org unit both take is left null: nothing on either save path reads it to refuse a
 * user. Both report failure as message keys having possibly written part of the change, so the
 * caller must roll back when {@link Outcome#errors} is not empty.
 */
@Component
public class CutBlockLegacy {

  /** {@code FTA_904_CUTBLKDETAIL.MAINLINE}'s parameters, each bound to a local named v_*. */
  private static final List<String> FTA_904_PARAMS = List.of(
      "action", "forest_file_id", "pfu_revision_count", "cutting_permit_id", "cut_block_id",
      "file_type_cd", "file_type_desc", "admin_org", "licencee", "status", "status_date",
      "award_date", "expiry_date", "timber_mark", "forest_district", "mark_status_st",
      "mark_issue_date", "mark_expiry_date", "mark_term", "sched_a_area_label", "sched_a_area",
      "object_area_label", "show_sched_a_ind", "cb_skey", "hva_skey", "block_status_st",
      "block_status_date", "cut_block_description", "sp_exempt_ind", "reference_name",
      "object_area", "planned_gross_block_area", "planned_net_block_area", "opening",
      "update_userid", "cb_revision_count", "cboa_revision_count", "cboa_opening_id",
      "pab_revision_count", "error_message", "org_unit_no", "total_logged_area", "file_label",
      "mb_ff", "mb_cp", "prev_block_status_st", "prev_block_status_date",
      "prev_disturb_gross_area", "prev_sp_exempt_ind", "dist_override_comment",
      "senior_admin_staff_ind", "oil_gas_commission_ind", "disturbance_gross_area",
      "disturbance_start_date", "disturbance_end_date", "planned_start_date", "min_x", "min_y",
      "max_x", "max_y", "salvage_type_code", "cut_regulation_code",
      "reforest_declare_type_code", "harvest_type_code", "disable_save_ind",
      "cut_perm_retired_ind", "disable_area_ind", "disable_mb_ind", "audit_comment",
      "waste_description", "waste_assess_reqd_ind", "trace_ind", "decision_date", "issuance_date",
      "fire_harv_reasn_cd", "under_part_order", "cb_susp_id", "susp_part_code", "susp_order_no",
      "susp_start_date", "susp_end_date", "reported_fire_date");

  private static final String FTA_904_DECLARE = FTA_904_PARAMS.stream()
      .map(p -> "v_" + p + " VARCHAR2(4000);")
      .collect(Collectors.joining(" "));

  private static final String FTA_904_CALL = "the.fta_904_cutblkdetail.mainline("
      + FTA_904_PARAMS.stream()
          .map(p -> "p_" + p + " => v_" + p)
          .collect(Collectors.joining(", "))
      + ");";

  /** Binds, in order, are listed at {@link #save}. */
  private static final String SAVE_BLOCK =
      "DECLARE " + FTA_904_DECLARE + " BEGIN"
          + " v_action := 'GET'; v_forest_file_id := ?; v_cutting_permit_id := ?;"
          + " v_cb_skey := ?; v_cut_block_id := ?; v_trace_ind := 'N';"
          + " v_senior_admin_staff_ind := ?; v_oil_gas_commission_ind := 'N'; "
          + FTA_904_CALL
          + " IF v_error_message IS NULL THEN"
          + "   IF v_cb_revision_count <> ? OR v_cboa_revision_count <> ? THEN"
          + "     v_error_message := 'fta.web.usr.database.record.modified:FTA_904,SAVE,CUT_BLOCK;';"
          + "   ELSE"
          + "     v_action := 'SAVE'; v_update_userid := ?;"
          + "     v_block_status_st := ?; v_block_status_date := ?; v_cut_block_description := ?;"
          + "     v_cut_regulation_code := ?; v_reforest_declare_type_code := ?;"
          + "     v_fire_harv_reasn_cd := ?; v_under_part_order := ?; v_reported_fire_date := ?;"
          + "     v_waste_assess_reqd_ind := ?; v_planned_net_block_area := ?;"
          + "     v_dist_override_comment := ?;"
          + "     IF ? = 'N' THEN v_planned_gross_block_area := ?; v_planned_start_date := ?;"
          + "     END IF;"
          + "     IF ? = 'N' THEN v_disturbance_gross_area := ?; v_disturbance_start_date := ?;"
          + "       v_disturbance_end_date := ?; v_sp_exempt_ind := ?;"
          + "     END IF; "
          + FTA_904_CALL
          + "   END IF;"
          + " END IF;"
          + " ? := v_error_message; END;";

  private static final String RELABEL_BLOCK =
      "DECLARE v_action VARCHAR2(10) := 'SAVE'; v_org VARCHAR2(10);"
          + " v_file VARCHAR2(100) := ?; v_cp VARCHAR2(100) := ?; v_mark VARCHAR2(100) := ?;"
          + " v_block VARCHAR2(100) := ?; v_new VARCHAR2(100) := ?; v_comment VARCHAR2(4000) := ?;"
          + " v_user VARCHAR2(100) := ?; v_skey VARCHAR2(40) := ?; v_rev VARCHAR2(40) := ?;"
          + " v_err VARCHAR2(4000); BEGIN"
          + " the.fta_231_cutblk_relabel.mainline(v_action, v_org, v_file, v_cp, v_mark, v_block,"
          + " v_new, v_comment, v_user, v_skey, v_rev, v_err);"
          + " ? := v_err; END;";

  /** ApplicationResources.properties text for the keys these packages return. */
  private static final Map<String, String> MESSAGES = Map.ofEntries(
      Map.entry("fta.status.change.invalid", "Status change from {0} to {1} is invalid."),
      Map.entry("fta.status.change.noauthority",
          "You do not have authority to change status from {0} to {1}."),
      Map.entry("fta.status.change.invalidwithrespect",
          "Status change is invalid with respect to related statuses for this tenure."),
      Map.entry("fta.status.change.invalidwithrespect.block",
          "Cannot change {0} status to {1} -- all blocks must be in {2} status."),
      Map.entry("fta.status.change.invalidwithrespect.mark",
          "Cannot change {0} status to {1} -- all marks must be in {2} status."),
      Map.entry("fta.status.change.blockstatus",
          "All block(s) must be in {0} status before you can change {1} status to {2}."),
      Map.entry("fta.status.change.adminareacashsale",
          "Insufficient Sale Info. Enter Admin Area Cash Sale Volume & Dollars."),
      Map.entry("fta.status.change.majortsl",
          "SB Category code and Planned Sale Date must be entered on Sale Info."),
      Map.entry("fta.status.change.aac", "Insufficient AAC information to proceed."));

  private static final String CUSTOM = "fta.web.error.user.custom.msg";
  private static final String MODIFIED = "fta.web.usr.database.record.modified";
  private static final String WARNING_PREFIX = "~W,";
  private static final String MSG_MODIFIED =
      "The cut block was changed by someone else. Reload it and try again.";

  /** What a package reported: errors mean nothing may be kept, warnings are for the user. */
  public record Outcome(List<String> errors, List<String> warnings, boolean modified) {}

  /** The edits {@link #save} applies; a locked group is left at what GET loaded. */
  public record SaveParams(
      long cbSkey,
      String forestFileId,
      String cuttingPermitId,
      String cutBlockId,
      boolean senior,
      long cbRevisionCount,
      long cboaRevisionCount,
      String userId,
      String blockStatus,
      LocalDate blockStatusDate,
      String cutBlockDescription,
      String cutRegulationCode,
      String reforestDeclareTypeCode,
      String fireHarvestingReasonCode,
      String underPartitionOrder,
      LocalDate reportedFireDate,
      String wasteAssessmentRequired,
      BigDecimal plannedNetBlockArea,
      String overrideReason,
      boolean plannedLocked,
      BigDecimal plannedGrossBlockArea,
      LocalDate plannedHarvestDate,
      boolean disturbanceLocked,
      BigDecimal disturbanceGrossArea,
      LocalDate disturbanceStartDate,
      LocalDate disturbanceEndDate,
      String spExemptInd) {}

  private final JdbcTemplate jdbc;

  public CutBlockLegacy(NamedParameterJdbcTemplate named) {
    this.jdbc = named.getJdbcTemplate();
  }

  /** FTA904's GET then SAVE, under the revisions the user read. */
  public Outcome save(SaveParams p) {
    List<String> binds = Stream.of(
            p.forestFileId(), p.cuttingPermitId(), Long.toString(p.cbSkey()), p.cutBlockId(),
            yn(p.senior()), Long.toString(p.cbRevisionCount()), Long.toString(p.cboaRevisionCount()),
            p.userId(), p.blockStatus(), text(p.blockStatusDate()), p.cutBlockDescription(),
            p.cutRegulationCode(), p.reforestDeclareTypeCode(), p.fireHarvestingReasonCode(),
            p.underPartitionOrder(), text(p.reportedFireDate()), p.wasteAssessmentRequired(),
            text(p.plannedNetBlockArea()), p.overrideReason(), yn(p.plannedLocked()),
            text(p.plannedGrossBlockArea()), text(p.plannedHarvestDate()),
            yn(p.disturbanceLocked()), text(p.disturbanceGrossArea()),
            text(p.disturbanceStartDate()), text(p.disturbanceEndDate()), p.spExemptInd())
        .map(v -> v == null ? "" : v)
        .toList();
    return parse(call(SAVE_BLOCK, binds));
  }

  /** FTA231's Save. */
  public Outcome relabel(
      String forestFileId,
      String cuttingPermitId,
      String timberMark,
      String cutBlockId,
      String newCutBlockId,
      String reason,
      String userId,
      long cbSkey,
      long revisionCount) {
    return parse(call(RELABEL_BLOCK, Stream.of(
            forestFileId, cuttingPermitId, timberMark, cutBlockId, newCutBlockId, reason, userId,
            Long.toString(cbSkey), Long.toString(revisionCount))
        .map(v -> v == null ? "" : v)
        .toList()));
  }

  private String call(String block, List<String> binds) {
    return jdbc.execute(
        (java.sql.Connection con) -> con.prepareCall(block),
        (CallableStatement cs) -> {
          int i = 1;
          for (String bind : binds) {
            // Oracle reads an empty string as null, which is what a blank field means to these.
            cs.setString(i++, bind);
          }
          cs.registerOutParameter(i, Types.VARCHAR);
          cs.execute();
          return cs.getString(i);
        });
  }

  /**
   * Legacy error strings are {@code key;} or {@code key:args;} pairs; a custom message starting
   * {@code ~W,} is a warning, which legacy commits and shows.
   */
  static Outcome parse(String raw) {
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    boolean modified = false;
    if (raw == null) {
      return new Outcome(errors, warnings, false);
    }
    for (String part : raw.split(";")) {
      String p = part.trim();
      if (p.isEmpty()) {
        continue;
      }
      int colon = p.indexOf(':');
      String key = colon < 0 ? p : p.substring(0, colon);
      String args = colon < 0 ? "" : p.substring(colon + 1).trim();
      if (CUSTOM.equals(key) && args.startsWith(WARNING_PREFIX)) {
        warnings.add(sentence(args.substring(WARNING_PREFIX.length())));
      } else if (CUSTOM.equals(key)) {
        errors.add(sentence(args));
        modified |= args.contains("has been modified by another user");
      } else if (MODIFIED.equals(key)) {
        errors.add(MSG_MODIFIED);
        modified = true;
      } else {
        errors.add(MESSAGES.containsKey(key) ? format(MESSAGES.get(key), args) : p);
      }
    }
    return new Outcome(errors, warnings, modified);
  }

  private static String format(String template, String args) {
    String text = template;
    String[] values = args.split(",");
    for (int i = 0; i < values.length; i++) {
      text = text.replace("{" + i + "}", values[i].trim());
    }
    return text;
  }

  private static String sentence(String s) {
    String t = s.trim();
    return t.endsWith(".") ? t : t + ".";
  }

  private static String yn(boolean b) {
    return b ? "Y" : "N";
  }

  private static String text(LocalDate d) {
    return d == null ? null : d.toString();
  }

  private static String text(BigDecimal n) {
    return n == null ? null : n.toPlainString();
  }
}
