package ca.bc.gov.nrs.fta.range.service;

import ca.bc.gov.nrs.fta.range.dto.RangeUsageDtos.CopyRequest;
import ca.bc.gov.nrs.fta.range.dto.RangeUsageDtos.Options;
import ca.bc.gov.nrs.fta.range.dto.RangeUsageDtos.Result;
import ca.bc.gov.nrs.fta.range.dto.RangeUsageDtos.SaveRequest;
import ca.bc.gov.nrs.fta.range.service.RangeUsageLegacy.Outcome;
import ca.bc.gov.nrs.fta.range.service.RangeUsageLegacy.Values;
import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * FTA613R Range Usage: Save (add or update a year) and Copy (a year's usage to other years).
 *
 * <p>Before writing, in legacy's order:
 *
 * <ol>
 *   <li>the user may edit the file ({@link RangeFileAccess#requireEditable}). This stands in for
 *       legacy's GET-time authority checks in {@code FTA_613R_RANGE_USAGE.MAINLINE}: the FILE
 *       authority ({@code FTA_FILE_CP_AUTHORITY}), the valid range file type, and the user's org
 *       against the file's admin org;
 *   <li>no application for the file is in the Inbox ({@code FTA_APPLICATION_PENDING}, ported) —
 *       legacy then warned and disabled Save and Copy;
 *   <li>{@code Fta613RangeUsageForm}'s checks ({@link RangeUsageRules}).
 * </ol>
 *
 * <p>The write itself is legacy's package ({@link RangeUsageLegacy}), with the term dates
 * {@code FTA_GET_FILE_HEADER} gave legacy's GET: the legal effective date and the current, else
 * initial, expiry. Anything the package reports rolls the change back.
 */
@Service
public class RangeUsageWriteService {

  /** FTA_GET_FILE_HEADER's type, status and term, and FTA_APPLICATION_PENDING. */
  static final String FILE_SQL =
      """
      SELECT pfu.file_type_code AS file_type_code,
             pfu.file_status_st AS file_status_st,
             TO_CHAR(tt.legal_effective_dt, 'YYYY-MM-DD') AS start_date,
             NVL(TO_CHAR(tt.current_expiry_dt, 'YYYY-MM-DD'),
                 TO_CHAR(tt.initial_expiry_dt, 'YYYY-MM-DD')) AS expiry_date,
             (SELECT COUNT(*)
                FROM the.tenure_application ta
               WHERE ta.forest_file_id = pfu.forest_file_id
                 AND ta.tenure_application_type_code IN ('FIL', 'RNG', 'RP')
                 AND ta.tenure_application_state_code = 'INB'
                 AND ta.tenure_app_purp_code = 'N') AS pending
        FROM the.prov_forest_use pfu
        LEFT JOIN the.tenure_term tt ON tt.forest_file_id = pfu.forest_file_id
       WHERE pfu.forest_file_id = :forestFileId
      """;

  static final String ROW_SQL =
      """
      SELECT authorized_use, non_use_agreement_no, non_use_nonbillable, non_use_billable,
             range_non_use_reason_code, temp_increase, range_increase_reason_code, revision_count
        FROM the.range_provision
       WHERE forest_file_id = :forestFileId
         AND calendar_year = :calendarYear
      """;

  /** FTA_CODE_LISTS.GET_RANGE_INCREASE_REASON_CODE, current codes only. */
  static final String INCREASE_REASONS_SQL =
      """
      SELECT range_increase_reason_code AS code,
             range_increase_reason_code || ' - ' || description AS description
        FROM the.range_increase_reason_code
       WHERE effective_date <= SYSDATE AND expiry_date >= SYSDATE
       ORDER BY range_increase_reason_code
      """;

  /** FTA_CODE_LISTS.GET_RANGE_NONUSE_REASON_CODE, current codes only. */
  static final String NON_USE_REASONS_SQL =
      """
      SELECT range_non_use_reason_code AS code,
             range_non_use_reason_code || ' - ' || description AS description
        FROM the.range_non_use_reason_code
       WHERE effective_date <= SYSDATE AND expiry_date >= SYSDATE
       ORDER BY range_non_use_reason_code
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final RangeFileAccess access;
  private final RangeUsageLegacy legacy;

  public RangeUsageWriteService(
      NamedParameterJdbcTemplate jdbc, RangeFileAccess access, RangeUsageLegacy legacy) {
    this.jdbc = jdbc;
    this.access = access;
    this.legacy = legacy;
  }

  /** The editors' lists, and whether the file has an application in the Inbox. */
  public Options options(String forestFileId) {
    FileFacts f = file(forestFileId);
    List<Integer> years = List.of();
    if (f.startDate() != null && f.expiryDate() != null) {
      int from = Integer.parseInt(f.startDate().substring(0, 4));
      int to = Integer.parseInt(f.expiryDate().substring(0, 4));
      years = IntStream.rangeClosed(from, to).boxed().toList();
    }
    return new Options(years, codes(INCREASE_REASONS_SQL), codes(NON_USE_REASONS_SQL),
        f.pending());
  }

  /** Save: add the year, or update it under the revision read. */
  @Transactional
  public Result save(String forestFileId, int calendarYear, SaveRequest r, String userId) {
    access.requireEditable(forestFileId);
    FileFacts f = file(forestFileId);
    if (f.pending()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, RangeUsageRules.PENDING);
    }
    StoredRow stored = row(forestFileId, calendarYear);
    List<String> errors = RangeUsageRules.validateSave(calendarYear, r, context(f, stored));
    if (!errors.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", errors));
    }
    // Legacy enabled Save on a year that already has usage only once it was picked with Select.
    if (r.revisionCount() == null && stored != null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, RangeUsageLegacy.MSG_MODIFIED);
    }
    fail(legacy.save(values(forestFileId, f, userId, calendarYear, r), r.revisionCount()));
    return new Result(RangeUsageRules.SAVE_SUCCESS);
  }

  /** Copy a saved year's usage to every remaining year of the term, or to From to To. */
  @Transactional
  public Result copy(String forestFileId, int calendarYear, CopyRequest c, String userId) {
    access.requireEditable(forestFileId);
    FileFacts f = file(forestFileId);
    if (f.pending()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, RangeUsageRules.PENDING);
    }
    StoredRow stored = row(forestFileId, calendarYear);
    if (stored == null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, RangeUsageLegacy.MSG_MODIFIED);
    }
    SaveRequest source = stored.asRequest();
    List<String> errors =
        RangeUsageRules.validateCopy(calendarYear, c, source, context(f, stored));
    if (!errors.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", errors));
    }
    boolean all = c.allRemainingYears();
    fail(legacy.copy(values(forestFileId, f, userId, calendarYear, source), all,
        all ? null : RangeUsageRules.intOrNull(c.copyFromYear()),
        all ? null : RangeUsageRules.intOrNull(c.copyToYear())));
    return new Result(RangeUsageRules.COPY_SUCCESS);
  }

  private static void fail(Outcome o) {
    if (!o.errors().isEmpty()) {
      throw new ResponseStatusException(
          o.modified() ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST,
          String.join(" ", o.errors()));
    }
  }

  private static Values values(String forestFileId, FileFacts f, String userId, int year,
      SaveRequest r) {
    return new Values(
        forestFileId,
        f.startDate(),
        f.expiryDate(),
        userId,
        year,
        RangeUsageRules.trimToNull(r.authorizedUse()),
        RangeUsageRules.trimToNull(r.nonUseAgreementNo()),
        RangeUsageRules.trimToNull(r.nonUseNonbillable()),
        RangeUsageRules.trimToNull(r.nonUseBillable()),
        RangeUsageRules.trimToNull(r.rangeNonUseReasonCode()),
        RangeUsageRules.trimToNull(r.tempIncrease()),
        RangeUsageRules.trimToNull(r.rangeIncreaseReasonCode()),
        RangeUsageRules.totalAnnualUse(r));
  }

  private RangeUsageRules.Context context(FileFacts f, StoredRow stored) {
    return new RangeUsageRules.Context(
        f.fileTypeCode(),
        f.fileStatus(),
        codeSet(INCREASE_REASONS_SQL),
        codeSet(NON_USE_REASONS_SQL),
        stored == null ? null : stored.increaseReason(),
        stored == null ? null : stored.nonUseReason());
  }

  private FileFacts file(String forestFileId) {
    List<FileFacts> rows = jdbc.query(FILE_SQL,
        new MapSqlParameterSource("forestFileId", forestFileId),
        (rs, n) -> new FileFacts(
            rs.getString("file_type_code"),
            rs.getString("file_status_st"),
            rs.getString("start_date"),
            rs.getString("expiry_date"),
            rs.getInt("pending") > 0));
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    return rows.get(0);
  }

  private StoredRow row(String forestFileId, int calendarYear) {
    List<StoredRow> rows = jdbc.query(ROW_SQL,
        new MapSqlParameterSource()
            .addValue("forestFileId", forestFileId)
            .addValue("calendarYear", calendarYear),
        (rs, n) -> new StoredRow(
            text(rs.getObject("authorized_use")),
            text(rs.getObject("temp_increase")),
            rs.getString("range_increase_reason_code"),
            text(rs.getObject("non_use_agreement_no")),
            text(rs.getObject("non_use_billable")),
            text(rs.getObject("non_use_nonbillable")),
            rs.getString("range_non_use_reason_code"),
            rs.getInt("revision_count")));
    return rows.isEmpty() ? null : rows.get(0);
  }

  private List<CodeOptionDto> codes(String sql) {
    return jdbc.query(sql, Map.of(),
        (rs, n) -> new CodeOptionDto(rs.getString("code"), rs.getString("description")));
  }

  private Set<String> codeSet(String sql) {
    Set<String> set = new HashSet<>();
    for (CodeOptionDto c : codes(sql)) {
      set.add(c.code());
    }
    return set;
  }

  /** A whole number as legacy's form held it; null stays null. */
  private static String text(Object n) {
    if (n == null) {
      return null;
    }
    return n instanceof Number num ? Long.toString(num.longValue()) : n.toString();
  }

  record FileFacts(
      String fileTypeCode, String fileStatus, String startDate, String expiryDate,
      boolean pending) {}

  /** A year's stored usage, as the form would hold it after Select. */
  record StoredRow(
      String authorizedUse,
      String tempIncrease,
      String increaseReason,
      String nonUseAgreementNo,
      String nonUseBillable,
      String nonUseNonbillable,
      String nonUseReason,
      int revisionCount) {

    SaveRequest asRequest() {
      return new SaveRequest(authorizedUse, tempIncrease, increaseReason, nonUseAgreementNo,
          nonUseBillable, nonUseNonbillable, nonUseReason, revisionCount);
    }
  }
}
