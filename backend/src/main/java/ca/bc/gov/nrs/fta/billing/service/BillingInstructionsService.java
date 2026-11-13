package ca.bc.gov.nrs.fta.billing.service;

import ca.bc.gov.nrs.fta.billing.dto.BillingInstructionRowDto;
import ca.bc.gov.nrs.fta.billing.dto.BillingInstructionsCriteria;
import ca.bc.gov.nrs.fta.billing.dto.PrepareInstructionsRequest;
import ca.bc.gov.nrs.fta.billing.dto.PrepareInstructionsResult;
import ca.bc.gov.nrs.fta.billing.service.BillingInstructionRules.EditFacts;
import ca.bc.gov.nrs.fta.billing.service.BillingInstructionRules.Outcome;
import ca.bc.gov.nrs.fta.billing.service.BillingInstructionRules.RowFacts;
import ca.bc.gov.nrs.fta.billing.service.BillingInstructionRules.RowState;
import ca.bc.gov.nrs.fta.shared.sql.ClientNameSql;
import java.sql.Types;
import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * FTA680 Tenure Billing Instructions, ported from {@code THE.FTA_680_TENURE_BILLING}.
 *
 * <p>{@link #search} is GET: every active, assigned or suspended range tenure a
 * district administers whose term covers the year, with the instruction it
 * would be billed on. {@link #prepare} is Prepare: EDIT for every row, in one
 * transaction, recording a new RANGE_BILL_INST row where the instruction
 * changed. Any row's error rolls back all of them, as legacy did.
 *
 * <p>Legacy wrote GET's rows to a scratch table (FTA680_BILL_INSTR_RESULTS)
 * before returning them; they are returned directly here. Prepare re-runs the
 * search rather than trusting the invoice and lock state the browser sends.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local
 * database, so it is exercised only in a deployed environment.
 */
@Service
public class BillingInstructionsService {

  private static final ZoneId ZONE = ZoneId.of("America/Vancouver");

  static final String ORG_UNIT_REQUIRED = "Org Unit is required.";
  static final String YEAR_INVALID = "Year must be a four-digit year.";
  static final String YEAR_IN_FUTURE = "Year must be less than or equal to Current Year.";
  static final String LIST_CHANGED = "List has changed. Press Go to re-select data.";

  /**
   * GET's cursor, plus the per-row lookups it then made one at a time: the
   * latest instruction (by certified time), the client's account status, any
   * General Billing System transaction, and the highest-numbered RBS invoice.
   */
  private static final String SEARCH_SQL =
      """
      SELECT x.*,
             CASE WHEN x.rbs_invoice IS NOT NULL AND EXISTS (
                    SELECT 1 FROM the.gen_invoice_dtl d
                     WHERE d.invoice_number = x.rbs_invoice
                       AND d.cancellation_ind = x.rbs_cancellation_ind
                       AND d.revenue_classn_cd = 'RLF')
                  THEN 'Y' ELSE 'N' END AS rbs_has_rlf
        FROM (
          SELECT pfu.forest_file_id,
                 pfu.file_type_code,
                 %s AS client_name,
                 TO_CHAR(tt.legal_effective_dt, 'YYYY') AS effective_year,
                 (SELECT MAX(rbi.billing_instruction_code)
                           KEEP (DENSE_RANK FIRST ORDER BY rbi.certified_ts DESC)
                    FROM the.range_bill_inst rbi
                   WHERE rbi.forest_file_id = pfu.forest_file_id
                     AND rbi.calendar_year = TO_NUMBER(:year)) AS latest_instr,
                 (SELECT TO_CHAR(MAX(rbi.certified_ts), 'YYYY-MM-DD HH24:MI')
                    FROM the.range_bill_inst rbi
                   WHERE rbi.forest_file_id = pfu.forest_file_id
                     AND rbi.calendar_year = TO_NUMBER(:year)) AS certified_date,
                 (SELECT MAX(rbi.certified_userid)
                           KEEP (DENSE_RANK FIRST ORDER BY rbi.certified_ts DESC)
                    FROM the.range_bill_inst rbi
                   WHERE rbi.forest_file_id = pfu.forest_file_id
                     AND rbi.calendar_year = TO_NUMBER(:year)) AS certified_by,
                 (SELECT MAX(cra.account_status_cd)
                    FROM the.clnt_rcvbl_acct cra
                   WHERE cra.client_number = fcl.client_number
                     AND cra.client_locn_code = fcl.client_locn_code) AS account_status,
                 (SELECT COUNT(*)
                    FROM the.gnrl_invc_txn g
                   WHERE g.forest_file_id = pfu.forest_file_id
                     AND g.billing_year = TO_NUMBER(:year)) AS gbs_count,
                 (SELECT MAX(fi.invoice_number)
                    FROM the.general_invoice gi
                    JOIN the.forest_invoice fi
                      ON fi.invoice_number = gi.invoice_number
                     AND fi.cancellation_ind = gi.cancellation_ind
                   WHERE gi.forest_file_id = pfu.forest_file_id
                     AND fi.invc_sub_type_code = 'RBS'
                     AND gi.billing_year = TO_NUMBER(:year)) AS rbs_invoice,
                 (SELECT MAX(fi.cancellation_ind)
                           KEEP (DENSE_RANK FIRST ORDER BY gi.invoice_number DESC)
                    FROM the.general_invoice gi
                    JOIN the.forest_invoice fi
                      ON fi.invoice_number = gi.invoice_number
                     AND fi.cancellation_ind = gi.cancellation_ind
                   WHERE gi.forest_file_id = pfu.forest_file_id
                     AND fi.invc_sub_type_code = 'RBS'
                     AND gi.billing_year = TO_NUMBER(:year)) AS rbs_cancellation_ind,
                 (SELECT MAX(fi.invoice_rln_typ_cd)
                           KEEP (DENSE_RANK FIRST ORDER BY gi.invoice_number DESC)
                    FROM the.general_invoice gi
                    JOIN the.forest_invoice fi
                      ON fi.invoice_number = gi.invoice_number
                     AND fi.cancellation_ind = gi.cancellation_ind
                   WHERE gi.forest_file_id = pfu.forest_file_id
                     AND fi.invc_sub_type_code = 'RBS'
                     AND gi.billing_year = TO_NUMBER(:year)) AS rbs_relation_type
            FROM the.prov_forest_use pfu
            JOIN the.range_tenure rt          ON rt.forest_file_id = pfu.forest_file_id
            JOIN the.range_file_type_code rftc ON rftc.range_file_type_code = pfu.file_type_code
            JOIN the.tenure_term tt           ON tt.forest_file_id = pfu.forest_file_id
            LEFT JOIN the.forest_file_client fcl
                   ON fcl.forest_file_id = pfu.forest_file_id
                  AND fcl.forest_file_client_type_code = 'A'
            LEFT JOIN the.v_client_public vcp ON vcp.client_number = fcl.client_number
           WHERE pfu.file_status_st IN ('A', 'AS', 'SU')
             AND rt.admin_forest_district_no = :orgUnitNo
             AND :year BETWEEN TO_CHAR(tt.legal_effective_dt, 'YYYY')
                           AND TO_CHAR(NVL(NVL(tt.current_expiry_dt, tt.initial_expiry_dt),
                                           SYSDATE), 'YYYY')
             AND (:forestFileId IS NULL OR pfu.forest_file_id = :forestFileId)
             AND (:fileTypeCode IS NULL OR pfu.file_type_code = :fileTypeCode)
             AND (:clientName IS NULL OR UPPER(vcp.client_name) LIKE UPPER(:clientName) || '%%')
        ) x
       ORDER BY x.forest_file_id
      """.formatted(ClientNameSql.displayName("vcp"));

  /**
   * EDIT's reads for one tenure. The RBS invoice here is the earliest entered,
   * where GET took the highest-numbered: legacy's two cursors differ, and EDIT's
   * checks were written against its own.
   */
  private static final String EDIT_FACTS_SQL =
      """
      SELECT x.*,
             CASE WHEN x.rbs_invoice IS NOT NULL AND EXISTS (
                    SELECT 1 FROM the.gen_invoice_dtl d
                     WHERE d.invoice_number = x.rbs_invoice
                       AND d.cancellation_ind = x.rbs_cancellation_ind
                       AND d.revenue_classn_cd = 'RLF')
                  THEN 'Y' ELSE 'N' END AS rbs_has_rlf
        FROM (
          SELECT (SELECT COUNT(*)
                    FROM the.range_provision rp
                    JOIN the.range_tenure rt ON rt.forest_file_id = rp.forest_file_id
                   WHERE rp.forest_file_id = :forestFileId
                     AND rp.calendar_year = TO_NUMBER(:year)) AS provision_count,
                 (SELECT MAX(cra.account_status_cd)
                    FROM the.clnt_rcvbl_acct cra
                    JOIN the.forest_file_client fcl
                      ON cra.client_number = fcl.client_number
                     AND cra.client_locn_code = fcl.client_locn_code
                   WHERE fcl.forest_file_id = :forestFileId
                     AND fcl.forest_file_client_type_code = 'A') AS account_status,
                 (SELECT MAX(fi.invoice_number)
                           KEEP (DENSE_RANK FIRST ORDER BY fi.entry_timestamp)
                    FROM the.general_invoice gi
                    JOIN the.forest_invoice fi
                      ON fi.invoice_number = gi.invoice_number
                     AND fi.cancellation_ind = gi.cancellation_ind
                   WHERE gi.forest_file_id = :forestFileId
                     AND fi.invc_sub_type_code = 'RBS'
                     AND gi.billing_year = TO_NUMBER(:year)) AS rbs_invoice,
                 (SELECT MAX(fi.cancellation_ind)
                           KEEP (DENSE_RANK FIRST ORDER BY fi.entry_timestamp)
                    FROM the.general_invoice gi
                    JOIN the.forest_invoice fi
                      ON fi.invoice_number = gi.invoice_number
                     AND fi.cancellation_ind = gi.cancellation_ind
                   WHERE gi.forest_file_id = :forestFileId
                     AND fi.invc_sub_type_code = 'RBS'
                     AND gi.billing_year = TO_NUMBER(:year)) AS rbs_cancellation_ind,
                 (SELECT MAX(fi.invoice_rln_typ_cd)
                           KEEP (DENSE_RANK FIRST ORDER BY fi.entry_timestamp)
                    FROM the.general_invoice gi
                    JOIN the.forest_invoice fi
                      ON fi.invoice_number = gi.invoice_number
                     AND fi.cancellation_ind = gi.cancellation_ind
                   WHERE gi.forest_file_id = :forestFileId
                     AND fi.invc_sub_type_code = 'RBS'
                     AND gi.billing_year = TO_NUMBER(:year)) AS rbs_relation_type,
                 (SELECT MAX(rbi.billing_instruction_code)
                           KEEP (DENSE_RANK FIRST ORDER BY rbi.update_timestamp DESC)
                    FROM the.range_bill_inst rbi
                   WHERE rbi.forest_file_id = :forestFileId
                     AND rbi.calendar_year = TO_NUMBER(:year)) AS previous_instr
            FROM dual
        ) x
      """;

  private static final String INSERT_SQL =
      """
      INSERT INTO the.range_bill_inst (
        range_bill_inst_id, forest_file_id, calendar_year, certified_ts,
        billing_instruction_code, certified_userid, entry_userid, entry_timestamp,
        update_userid, update_timestamp, revision_count
      ) VALUES (
        the.range_bill_inst_seq.NEXTVAL, :forestFileId, TO_NUMBER(:year), SYSDATE,
        :instr, :userId, :userId, SYSDATE, :userId, SYSDATE, 1
      )
      """;

  /** Undo the instructions certified since the last "B" (none if there is no "B"). */
  private static final String DELETE_AFTER_LAST_B_SQL =
      """
      DELETE FROM the.range_bill_inst
       WHERE forest_file_id = :forestFileId
         AND calendar_year = TO_NUMBER(:year)
         AND certified_ts > (SELECT MAX(certified_ts)
                               FROM the.range_bill_inst
                              WHERE forest_file_id = :forestFileId
                                AND calendar_year = TO_NUMBER(:year)
                                AND billing_instruction_code = 'B')
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public BillingInstructionsService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** The Go button. */
  public List<BillingInstructionRowDto> search(BillingInstructionsCriteria c) {
    return rows(c).stream().map(Row::dto).toList();
  }

  /**
   * The Prepare button: EDIT every row. All rows are checked before anything is
   * kept; if any is refused, nothing is saved and every refusal is reported.
   *
   * @throws ResponseStatusException 400 with each row's error, or 409 if the
   *     rows no longer match the search
   */
  @Transactional
  public PrepareInstructionsResult prepare(PrepareInstructionsRequest req, String userId) {
    if (req == null || req.criteria() == null || req.instructions() == null) {
      throw bad(ORG_UNIT_REQUIRED + " " + YEAR_INVALID);
    }
    BillingInstructionsCriteria c = req.criteria();
    List<Row> rows = rows(c);

    List<String> sent = req.instructions().stream()
        .map(PrepareInstructionsRequest.Instruction::forestFileId).toList();
    List<String> found = rows.stream().map(r -> r.forestFileId).toList();
    if (!sent.equals(found)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, LIST_CHANGED);
    }

    Map<String, String> chosen = new HashMap<>();
    req.instructions().forEach(i -> chosen.put(i.forestFileId(), i.instructionCode()));

    List<String> errors = new ArrayList<>();
    List<Runnable> writes = new ArrayList<>();
    boolean created = false;
    for (int i = 0; i < rows.size(); i++) {
      Row row = rows.get(i);
      // A locked row's instruction cannot be changed: legacy's disabled select
      // kept the value GET gave it.
      String instr = row.state.locked() ? row.state.instruction() : chosen.get(row.forestFileId);
      MapSqlParameterSource p = new MapSqlParameterSource()
          .addValue("forestFileId", row.forestFileId)
          .addValue("year", c.calendarYear())
          .addValue("userId", userId);
      Outcome o = BillingInstructionRules.edit(
          row.state, row.fileTypeCode, instr, editFacts(p));
      if (o.error() != null) {
        errors.add("Row %d (Tenure %s): %s".formatted(i + 1, row.forestFileId, o.error()));
      } else if (o.insert() != null) {
        created = true;
        writes.add(() -> jdbc.update(INSERT_SQL, new MapSqlParameterSource(p.getValues())
            .addValue("instr", o.insert())));
      } else if (o.deleteAfterLastB()) {
        writes.add(() -> jdbc.update(DELETE_AFTER_LAST_B_SQL, p));
      }
    }
    if (!errors.isEmpty()) {
      throw bad(String.join("\n", errors));
    }
    writes.forEach(Runnable::run);
    return new PrepareInstructionsResult(created);
  }

  private List<Row> rows(BillingInstructionsCriteria c) {
    validate(c);
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("orgUnitNo", c.orgUnitNo())
        .addValue("year", c.calendarYear())
        // Typed, so Oracle can bind a null for ":x IS NULL".
        .addValue("forestFileId", trimToNull(c.forestFileId()), Types.VARCHAR)
        .addValue("fileTypeCode", trimToNull(c.fileTypeCode()), Types.VARCHAR)
        .addValue("clientName", trimToNull(c.clientName()), Types.VARCHAR);
    // A file with two 'A' clients comes back twice; it is shown once, so
    // Prepare can match rows to files.
    Map<String, Row> byFile = new LinkedHashMap<>();
    jdbc.query(SEARCH_SQL, p, (rs, n) -> {
      String clientName = rs.getString("client_name");
      boolean clientFound = clientName != null;
      RowFacts facts = new RowFacts(
          clientFound,
          rs.getString("latest_instr"),
          rs.getString("account_status"),
          rs.getLong("gbs_count") > 0,
          rs.getString("rbs_invoice"),
          rs.getString("rbs_relation_type"),
          "Y".equals(rs.getString("rbs_has_rlf")),
          rs.getString("effective_year"));
      Row row = new Row(
          rs.getString("forest_file_id"),
          rs.getString("file_type_code"),
          clientFound ? clientName : BillingInstructionRules.NOT_FOUND,
          rs.getString("certified_date"),
          rs.getString("certified_by"),
          BillingInstructionRules.derive(facts, c.calendarYear()));
      byFile.putIfAbsent(row.forestFileId, row);
      return row;
    });
    return new ArrayList<>(byFile.values());
  }

  private EditFacts editFacts(MapSqlParameterSource p) {
    return jdbc.queryForObject(EDIT_FACTS_SQL, p, (rs, n) -> new EditFacts(
        rs.getLong("provision_count") > 0,
        rs.getString("account_status"),
        rs.getString("rbs_invoice"),
        rs.getString("rbs_relation_type"),
        "Y".equals(rs.getString("rbs_has_rlf")),
        rs.getString("previous_instr")));
  }

  static void validate(BillingInstructionsCriteria c) {
    List<String> e = new ArrayList<>();
    if (c == null || c.orgUnitNo() == null) {
      e.add(ORG_UNIT_REQUIRED);
    }
    String year = c == null ? null : c.calendarYear();
    if (year == null || !year.matches("\\d{4}")) {
      e.add(YEAR_INVALID);
    } else if (Integer.parseInt(year) > Year.now(ZONE).getValue()) {
      e.add(YEAR_IN_FUTURE);
    }
    if (!e.isEmpty()) {
      throw bad(String.join(" ", e));
    }
  }

  private static String trimToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  private static ResponseStatusException bad(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private record Row(
      String forestFileId,
      String fileTypeCode,
      String clientName,
      String certifiedDate,
      String certifiedBy,
      RowState state) {

    BillingInstructionRowDto dto() {
      return new BillingInstructionRowDto(
          forestFileId,
          fileTypeCode,
          clientName,
          state.instruction(),
          state.invoiceNumber(),
          certifiedDate,
          certifiedBy,
          state.locked());
    }
  }
}
