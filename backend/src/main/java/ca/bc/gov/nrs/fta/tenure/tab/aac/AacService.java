package ca.bc.gov.nrs.fta.tenure.tab.aac;

import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import ca.bc.gov.nrs.fta.tenure.tab.aac.AacDtos.AacAreasRequest;
import ca.bc.gov.nrs.fta.tenure.tab.aac.AacDtos.AacResponse;
import ca.bc.gov.nrs.fta.tenure.tab.aac.AacDtos.AacRow;
import ca.bc.gov.nrs.fta.tenure.tab.aac.AacDtos.AacSaveRequest;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * A tenure's Allowable Annual Cut — legacy FTA930 ({@code FTA_930_AAC}).
 *
 * <p>The AAC history is {@code AAC_ALLOCATION_PERIOD} (an effective date and a unit of measure)
 * with its {@code AAC_ALLOCATION_AMOUNT}s (area type, cut type, amount, reason…); legacy shows
 * each amount as a row, the most recent period first. The Schedule A / B areas live on
 * {@code TIMBER_TENURE}.
 *
 * <p>Writes port the package's actions: SAVE_AREA (update the areas), SAVE_AAC (add or change
 * a row: a new row joins the period already starting on its date, or starts a new period) and
 * DELETE (remove a row, and its period once empty) — each guarded by the revision counts
 * legacy carried, with legacy's field checks ({@link AacFieldChecks}) and gates
 * ({@link AacRules}).
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class AacService {

  static final String MODIFIED =
      "The AAC record has been modified by another user. Reload the tab and try again.";

  private static final String HEADER_SQL =
      """
      SELECT pfu.file_type_code,
             tt.legal_effective_dt                                AS award_date,
             NVL(tt.current_expiry_dt, tt.initial_expiry_dt)      AS expiry_date,
             ttn.schedule_a_area,
             ttn.schedule_b_area,
             ttn.revision_count                                   AS tt_revision_count,
             CASE WHEN ttn.forest_file_id IS NULL THEN 'N' ELSE 'Y' END AS has_tt
        FROM the.prov_forest_use pfu
        LEFT JOIN the.tenure_term tt     ON tt.forest_file_id = pfu.forest_file_id
        LEFT JOIN the.timber_tenure ttn  ON ttn.forest_file_id = pfu.forest_file_id
       WHERE pfu.forest_file_id = :forestFileId
      """;

  // FTA_930_AAC.get_aac_history_cur, with the codes' descriptions.
  private static final String ROWS_SQL =
      """
      SELECT aap.aac_allocation_period_id,
             aap.effective_date,
             aap.harvest_unit_of_measure_code,
             aap.revision_count                 AS ap_revision_count,
             aaa.aac_allocation_amount_id,
             aaa.allowable_area_type_code,
             aat.description                    AS area_type_desc,
             aaa.allowable_cut_type_code,
             act.description                    AS cut_type_desc,
             aaa.allocation_amount,
             aaa.aac_adjustment_reason_code,
             arc.description                    AS reason_desc,
             aaa.fra2003_fn_share_vol_aac,
             aaa.is_revenue_shareable,
             aaa.adjustment_comment,
             aaa.entry_userid,
             aaa.update_userid,
             aaa.revision_count                 AS aa_revision_count
        FROM the.aac_allocation_period aap
        JOIN the.aac_allocation_amount aaa
          ON aaa.aac_allocation_period_id = aap.aac_allocation_period_id
        LEFT JOIN the.allowable_area_type_code aat
               ON aat.allowable_area_type_code = aaa.allowable_area_type_code
        LEFT JOIN the.allowable_cut_type_code act
               ON act.allowable_cut_type_code = aaa.allowable_cut_type_code
        LEFT JOIN the.aac_adjustment_reason_code arc
               ON arc.aac_adjustment_reason_code = aaa.aac_adjustment_reason_code
       WHERE aap.forest_file_id = :forestFileId
       ORDER BY aap.effective_date DESC,
                aap.harvest_unit_of_measure_code,
                aaa.allowable_area_type_code,
                aaa.allowable_cut_type_code
      """;

  private static final String SAVE_AREA_SQL =
      """
      UPDATE the.timber_tenure
         SET schedule_a_area = :scheduleA,
             schedule_b_area = :scheduleB,
             revision_count = revision_count + 1,
             update_userid = :userId,
             update_timestamp = SYSDATE
       WHERE forest_file_id = :forestFileId
         AND revision_count = :rev
      """;

  private static final String NEXT_PERIOD_SQL =
      "SELECT the.aac_alloc_period_id_seq.NEXTVAL FROM dual";

  private static final String NEXT_AMOUNT_SQL =
      "SELECT the.aac_alloc_amount_id_seq.NEXTVAL FROM dual";

  private static final String INSERT_PERIOD_SQL =
      """
      INSERT INTO the.aac_allocation_period (
        aac_allocation_period_id, forest_file_id, effective_date, harvest_unit_of_measure_code,
        revision_count, entry_userid, entry_timestamp, update_userid, update_timestamp
      ) VALUES (
        :periodId, :forestFileId, :effectiveDate, :unit,
        1, :userId, SYSDATE, :userId, SYSDATE
      )
      """;

  private static final String UPDATE_PERIOD_SQL =
      """
      UPDATE the.aac_allocation_period
         SET effective_date = :effectiveDate,
             harvest_unit_of_measure_code = :unit,
             revision_count = revision_count + 1,
             update_userid = :userId,
             update_timestamp = SYSDATE
       WHERE aac_allocation_period_id = :periodId
         AND revision_count = :periodRev
      """;

  private static final String INSERT_AMOUNT_SQL =
      """
      INSERT INTO the.aac_allocation_amount (
        aac_allocation_amount_id, aac_allocation_period_id, allowable_area_type_code,
        allowable_cut_type_code, allocation_amount, aac_adjustment_reason_code,
        adjustment_comment, revision_count, entry_userid, entry_timestamp, update_userid,
        update_timestamp, fra2003_fn_share_vol_aac, is_revenue_shareable
      ) VALUES (
        :amountId, :periodId, :areaType,
        :cutType, :amount, :reason,
        :comment, 1, :userId, SYSDATE, :userId,
        SYSDATE, :fra, :shareable
      )
      """;

  private static final String UPDATE_AMOUNT_SQL =
      """
      UPDATE the.aac_allocation_amount
         SET allowable_area_type_code = :areaType,
             allowable_cut_type_code = :cutType,
             allocation_amount = :amount,
             aac_adjustment_reason_code = :reason,
             adjustment_comment = :comment,
             revision_count = revision_count + 1,
             update_userid = :userId,
             update_timestamp = SYSDATE,
             fra2003_fn_share_vol_aac = :fra,
             is_revenue_shareable = :shareable
       WHERE aac_allocation_amount_id = :amountId
         AND revision_count = :amountRev
      """;

  private static final String DELETE_AMOUNT_SQL =
      """
      DELETE FROM the.aac_allocation_amount
       WHERE aac_allocation_amount_id = :amountId
         AND aac_allocation_period_id = :periodId
         AND revision_count = :amountRev
      """;

  private static final String PERIOD_AMOUNTS_SQL =
      "SELECT COUNT(*) FROM the.aac_allocation_amount WHERE aac_allocation_period_id = :periodId";

  private static final String DELETE_PERIOD_SQL =
      """
      DELETE FROM the.aac_allocation_period
       WHERE aac_allocation_period_id = :periodId
         AND revision_count = :periodRev
      """;

  /** The code tables behind the dialog's lists: column, table. */
  private static final Map<String, String[]> LOOKUPS = Map.of(
      "aac-area-types", new String[] {"allowable_area_type_code", "the.allowable_area_type_code"},
      "aac-cut-types", new String[] {"allowable_cut_type_code", "the.allowable_cut_type_code"},
      "aac-adjustment-reasons",
      new String[] {"aac_adjustment_reason_code", "the.aac_adjustment_reason_code"},
      "aac-harvest-units",
      new String[] {"harvest_unit_of_measure_code", "the.harvest_unit_of_measure_code"});

  private final NamedParameterJdbcTemplate jdbc;

  public AacService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private record Header(
      String fileTypeCode,
      LocalDate awardDate,
      LocalDate expiryDate,
      BigDecimal scheduleA,
      BigDecimal scheduleB,
      Long ttRevision,
      boolean hasTimberTenure) {}

  /** The tab's data; 404 when the tenure does not exist. */
  public AacResponse get(String forestFileId) {
    Header h = header(forestFileId);
    return new AacResponse(
        h.fileTypeCode(),
        h.awardDate(),
        h.expiryDate(),
        h.scheduleA(),
        h.scheduleB(),
        h.ttRevision(),
        rows(forestFileId),
        AacRules.of(h.fileTypeCode(), h.hasTimberTenure()));
  }

  /** SAVE_AREA: the Schedule A / B hectares. */
  @Transactional
  public void saveAreas(String forestFileId, AacAreasRequest q, String userId) {
    Header h = header(forestFileId);
    AacRules rules = AacRules.of(h.fileTypeCode(), h.hasTimberTenure());
    if (!rules.areas()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, rules.areasReason());
    }
    List<String> e = AacFieldChecks.areas(q.scheduleAArea(), q.scheduleBArea());
    if (!e.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", e));
    }
    if (q.revisionCount() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revision count is required.");
    }
    int n = jdbc.update(SAVE_AREA_SQL, new MapSqlParameterSource()
        .addValue("scheduleA", q.scheduleAArea(), Types.NUMERIC)
        .addValue("scheduleB", q.scheduleBArea(), Types.NUMERIC)
        .addValue("userId", userId)
        .addValue("forestFileId", forestFileId.toUpperCase(Locale.ROOT))
        .addValue("rev", q.revisionCount(), Types.NUMERIC));
    if (n == 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, MODIFIED);
    }
  }

  /** SAVE_AAC for a new row. */
  @Transactional
  public void add(String forestFileId, AacSaveRequest raw, String userId) {
    save(forestFileId, null, raw, userId);
  }

  /** SAVE_AAC for an existing row. */
  @Transactional
  public void update(String forestFileId, long amountId, AacSaveRequest raw, String userId) {
    save(forestFileId, amountId, raw, userId);
  }

  private void save(String forestFileId, Long amountId, AacSaveRequest raw, String userId) {
    Header h = header(forestFileId);
    AacRules rules = AacRules.of(h.fileTypeCode(), h.hasTimberTenure());
    if (!rules.edit()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, rules.editReason());
    }
    AacSaveRequest q = normalize(raw);
    List<AacRow> rows = rows(forestFileId);
    AacRow editing = null;
    if (amountId != null) {
      editing = rows.stream().filter(r -> r.amountId() == amountId).findFirst()
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, MODIFIED));
      if (q.periodRevisionCount() == null || q.amountRevisionCount() == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revision counts are required.");
      }
    }
    List<String> e = new ArrayList<>(AacFieldChecks.save(
        q, rows, editing, h.awardDate(), h.expiryDate(), h.fileTypeCode()));
    codeCheck(e, "aac-area-types", q.areaTypeCode(), "Area Type");
    codeCheck(e, "aac-cut-types", q.cutTypeCode(), "Cut type");
    codeCheck(e, "aac-harvest-units", q.unitOfMeasureCode(), "Unit of Measure");
    codeCheck(e, "aac-adjustment-reasons", q.reasonCode(), "Reason");
    if (!e.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", e));
    }

    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("forestFileId", forestFileId.toUpperCase(Locale.ROOT))
        .addValue("effectiveDate", q.effectiveDate(), Types.DATE)
        .addValue("unit", q.unitOfMeasureCode())
        .addValue("areaType", q.areaTypeCode())
        .addValue("cutType", q.cutTypeCode())
        .addValue("amount", q.amount(), Types.NUMERIC)
        .addValue("reason", q.reasonCode())
        .addValue("comment", q.comment())
        .addValue("fra", q.fra2003Volume(), Types.NUMERIC)
        .addValue("shareable", AacFieldChecks.shareable(q.revenueShareable()))
        .addValue("userId", userId);

    if (editing == null) {
      // A new row joins the period already starting on its date, else starts one.
      AacRow period = AacFieldChecks.rowByDate(rows, q.effectiveDate());
      long periodId;
      if (period == null) {
        periodId = jdbc.queryForObject(NEXT_PERIOD_SQL, Map.of(), Long.class);
        p.addValue("periodId", periodId);
        jdbc.update(INSERT_PERIOD_SQL, p);
      } else {
        // Legacy's update of the found period: same date and unit, a new revision.
        periodId = period.periodId();
        p.addValue("periodId", periodId)
            .addValue("periodRev", period.periodRevisionCount(), Types.NUMERIC);
        if (jdbc.update(UPDATE_PERIOD_SQL, p) == 0) {
          throw new ResponseStatusException(HttpStatus.CONFLICT, MODIFIED);
        }
      }
      long newAmountId = jdbc.queryForObject(NEXT_AMOUNT_SQL, Map.of(), Long.class);
      p.addValue("amountId", newAmountId);
      jdbc.update(INSERT_AMOUNT_SQL, p);
    } else {
      p.addValue("periodId", editing.periodId())
          .addValue("periodRev", q.periodRevisionCount(), Types.NUMERIC)
          .addValue("amountId", editing.amountId())
          .addValue("amountRev", q.amountRevisionCount(), Types.NUMERIC);
      if (jdbc.update(UPDATE_PERIOD_SQL, p) == 0 || jdbc.update(UPDATE_AMOUNT_SQL, p) == 0) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, MODIFIED);
      }
    }
  }

  /** DELETE: the row, and its period when no other row is left in it. */
  @Transactional
  public void delete(
      String forestFileId, long amountId, Long periodRevisionCount, Long amountRevisionCount) {
    Header h = header(forestFileId);
    AacRules rules = AacRules.of(h.fileTypeCode(), h.hasTimberTenure());
    if (!rules.edit()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, rules.editReason());
    }
    if (periodRevisionCount == null || amountRevisionCount == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revision counts are required.");
    }
    AacRow row = rows(forestFileId).stream().filter(r -> r.amountId() == amountId).findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, MODIFIED));
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("amountId", amountId)
        .addValue("periodId", row.periodId())
        .addValue("amountRev", amountRevisionCount, Types.NUMERIC)
        .addValue("periodRev", periodRevisionCount, Types.NUMERIC);
    if (jdbc.update(DELETE_AMOUNT_SQL, p) == 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, MODIFIED);
    }
    Long left = jdbc.queryForObject(PERIOD_AMOUNTS_SQL, p, Long.class);
    if ((left == null || left == 0) && jdbc.update(DELETE_PERIOD_SQL, p) == 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, MODIFIED);
    }
  }

  /** A code list for the dialog: current codes, "CODE - description". */
  public List<CodeOptionDto> lookup(String name) {
    String[] t = LOOKUPS.get(name);
    if (t == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such list.");
    }
    // Column and table are constants from LOOKUPS, never input.
    String sql = "SELECT " + t[0] + " AS code, " + t[0] + " || ' - ' || description AS description"
        + " FROM " + t[1] + " WHERE SYSDATE BETWEEN effective_date AND expiry_date"
        + " ORDER BY " + t[0];
    return jdbc.query(sql, Map.of(),
        (rs, i) -> new CodeOptionDto(rs.getString("code"), rs.getString("description")));
  }

  private void codeCheck(List<String> e, String list, String code, String label) {
    if (code == null) {
      return;
    }
    String[] t = LOOKUPS.get(list);
    Long n = jdbc.queryForObject(
        "SELECT COUNT(*) FROM " + t[1] + " WHERE " + t[0] + " = :code",
        Map.of("code", code), Long.class);
    if (n == null || n == 0) {
      e.add(label + " " + code + " is not a valid code.");
    }
  }

  private Header header(String forestFileId) {
    List<Header> found = jdbc.query(
        HEADER_SQL,
        Map.of("forestFileId", forestFileId),
        (rs, i) -> new Header(
            rs.getString("file_type_code"),
            rs.getObject("award_date", LocalDate.class),
            rs.getObject("expiry_date", LocalDate.class),
            rs.getBigDecimal("schedule_a_area"),
            rs.getBigDecimal("schedule_b_area"),
            rs.getObject("tt_revision_count") == null ? null : rs.getLong("tt_revision_count"),
            "Y".equals(rs.getString("has_tt"))));
    if (found.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenure not found.");
    }
    return found.get(0);
  }

  private List<AacRow> rows(String forestFileId) {
    return jdbc.query(
        ROWS_SQL,
        Map.of("forestFileId", forestFileId),
        (rs, i) -> new AacRow(
            rs.getLong("aac_allocation_amount_id"),
            rs.getLong("aac_allocation_period_id"),
            rs.getObject("effective_date", LocalDate.class),
            rs.getString("harvest_unit_of_measure_code"),
            rs.getString("allowable_area_type_code"),
            rs.getString("area_type_desc"),
            rs.getString("allowable_cut_type_code"),
            rs.getString("cut_type_desc"),
            rs.getBigDecimal("allocation_amount"),
            rs.getString("aac_adjustment_reason_code"),
            rs.getString("reason_desc"),
            rs.getString("is_revenue_shareable"),
            rs.getBigDecimal("fra2003_fn_share_vol_aac"),
            rs.getString("adjustment_comment"),
            rs.getString("entry_userid"),
            rs.getString("update_userid"),
            rs.getLong("ap_revision_count"),
            rs.getLong("aa_revision_count")));
  }

  /** Codes trimmed and upper-cased, blanks to null; the comment trimmed. */
  static AacSaveRequest normalize(AacSaveRequest q) {
    return new AacSaveRequest(
        q.effectiveDate(),
        code(q.unitOfMeasureCode()),
        code(q.areaTypeCode()),
        code(q.cutTypeCode()),
        q.amount(),
        code(q.reasonCode()),
        q.comment() == null || q.comment().isBlank() ? null : q.comment().trim(),
        code(q.revenueShareable()),
        q.fra2003Volume(),
        q.periodRevisionCount(),
        q.amountRevisionCount());
  }

  private static String code(String s) {
    return s == null || s.isBlank() ? null : s.trim().toUpperCase(Locale.ROOT);
  }
}
