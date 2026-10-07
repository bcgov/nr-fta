package ca.bc.gov.nrs.fta.mark.service;

import java.sql.CallableStatement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The two places the backend calls the legacy {@code THE.FTA_510_PRIVATE_MARK} package
 * rather than writing SQL: taking a certificate number ({@code ADD_NEW_DEFAULTS}, legacy's
 * "Add New") and generating a timber mark ({@code ASSIGN_MARK}, legacy's "Assign Mark").
 *
 * <p>Both draw from {@code THE.PRT_MRK_NUM_CTL}, the shared counter legacy still updates. The
 * package runs with its owner's rights (no {@code AUTHID} clause), so the caller needs
 * {@code EXECUTE} on it rather than {@code UPDATE} on the counter, which the application
 * account is not granted (ORA-01031) — and the counter keeps a single writer.
 *
 * <p><b>The package commits.</b> Both actions {@code COMMIT} after bumping the counter, so call
 * them before any other write in a transaction — the commit then carries nothing but the
 * number. A number taken and then not used is spent, leaving a gap, as in legacy.
 */
@Component
public class PrivateMarkPackage {

  /**
   * {@code mainline}'s parameters, in the spec's order — all {@code IN OUT VARCHAR2}, so each
   * needs a variable to bind to (FTA_510_PRIVATE_MARK.PKS, lines 35-95).
   */
  private static final List<String> MAINLINE_PARAMS = List.of(
      "p_action", "p_accessed_from_fta500", "p_hdr_timber_mark", "p_hdr_certificate",
      "p_file_type_code", "p_mgmt_unit_type", "p_mgmt_unit_id", "p_mgmt_unit_desc",
      "p_pfu_revision_count", "p_client_number", "p_client_locn_code", "p_client_name",
      "p_client_plus_ind", "p_timber_mark", "p_prev_timber_mark", "p_certificate",
      "p_forest_district", "p_forest_region", "p_mark_status_st", "p_prev_mark_status_st",
      "p_mark_status_date", "p_mark_appl_date", "p_mark_issue_date", "p_mark_expiry_date",
      "p_mark_extend_date", "p_prev_mark_extend_date", "p_mark_extend_rsn_cd",
      "p_mark_extend_count", "p_mark_cancel_date", "p_granted_acqrd_date",
      "p_crown_granted_acq_desc", "p_tenure_term", "p_marking_method_cd", "p_markng_instrmnt_cd",
      "p_cascade_split_code", "p_bcaa_folio_number", "p_tm_revision_count",
      "p_permit_block_locn", "p_permit_block_area", "p_p_of_c_or_legal", "p_pb_revision_count",
      "p_map_reference_reg", "p_map_reference_comp", "p_map_reference_let",
      "p_fml_revision_count", "p_tt_revision_count", "p_amd_revision_count", "p_mark_amend_date",
      "p_amended_count", "p_prv_mrk_amd_sts_st", "p_prev_prv_mrk_amd_sts_st",
      "p_amendment_exists_ind", "p_disable_save_ind", "p_disable_assign_mark_ind",
      "p_disable_submit_ind", "p_userid", "p_user_org_no", "p_trace_ind", "p_error_message");

  private final JdbcTemplate jdbc;

  // The template every service injects; its JdbcTemplate shares the transaction-bound
  // connection, so the call joins the caller's transaction.
  public PrivateMarkPackage(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc.getJdbcTemplate();
  }

  /**
   * Takes the next certificate number — {@code ADD_NEW_DEFAULTS}. Commits (see the class note).
   *
   * @param forestDistrict ORG_UNIT_NO of the application's district — the package's
   *                       {@code p_user_org_no}, used only for defaults this caller ignores
   */
  public String nextCertificate(String forestDistrict) {
    return required(
        call("ADD_NEW_DEFAULTS", Map.of("p_user_org_no", forestDistrict), "p_certificate"),
        "ADD_NEW_DEFAULTS");
  }

  /**
   * Generates the next timber mark for a mark type — {@code ASSIGN_MARK}: E… for B08, N… for
   * B09, IR… for B14. Commits (see the class note). The package also refuses a generated mark
   * that already exists, asking for another try.
   *
   * @throws IllegalArgumentException with the package's message when it refuses
   */
  public String assignMark(String fileTypeCode) {
    return required(
        call("ASSIGN_MARK", Map.of("p_file_type_code", fileTypeCode), "p_timber_mark"),
        "ASSIGN_MARK");
  }

  private static String required(String value, String action) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("FTA_510_PRIVATE_MARK." + action + " returned no value.");
    }
    return value.trim();
  }

  /**
   * Runs {@code mainline} for {@code action} with {@code inputs} set and every other parameter
   * null, and returns {@code output}. The package reports failures in {@code p_error_message}
   * rather than raising, so a non-empty one is thrown here.
   */
  private String call(String action, Map<String, String> inputs, String output) {
    Map<String, String> in = new LinkedHashMap<>(inputs);
    StringBuilder sql = new StringBuilder("DECLARE\n");
    MAINLINE_PARAMS.forEach(p -> sql.append("  v_").append(p).append(" VARCHAR2(4000);\n"));
    sql.append("BEGIN\n  v_p_action := '").append(action).append("';\n");
    List<String> binds = new ArrayList<>(in.keySet());
    binds.forEach(p -> sql.append("  v_").append(p).append(" := ?;\n"));
    sql.append("  the.fta_510_private_mark.mainline(\n")
        .append(MAINLINE_PARAMS.stream()
            .map(p -> "    " + p + " => v_" + p)
            .collect(Collectors.joining(",\n")))
        .append(");\n")
        .append("  ? := v_").append(output).append(";\n")
        .append("  ? := v_p_error_message;\n")
        .append("END;");

    String[] out = jdbc.execute(
        (java.sql.Connection con) -> con.prepareCall(sql.toString()),
        (CallableStatement cs) -> {
          int i = 1;
          for (String p : binds) {
            cs.setString(i++, in.get(p));
          }
          cs.registerOutParameter(i, Types.VARCHAR);
          cs.registerOutParameter(i + 1, Types.VARCHAR);
          cs.execute();
          return new String[] {cs.getString(i), cs.getString(i + 1)};
        });
    String error = out == null ? null : out[1];
    if (error != null && !error.isBlank()) {
      throw new IllegalArgumentException(readable(error));
    }
    return out == null ? null : out[0];
  }

  /**
   * The package's messages are message-key strings ("key:arg,arg;"); the custom ones carry
   * their text after {@code fta.web.error.user.custom.msg:}. Plain text (ASSIGN_MARK's
   * "already exists" message) passes through.
   */
  private static String readable(String error) {
    return error.replace("fta.web.error.user.custom.msg:", "").replaceAll(";\\s*$", "").trim();
  }
}
