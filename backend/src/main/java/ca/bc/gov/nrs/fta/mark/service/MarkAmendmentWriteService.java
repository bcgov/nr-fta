package ca.bc.gov.nrs.fta.mark.service;

import static ca.bc.gov.nrs.fta.mark.service.MarkFieldChecks.trim;

import ca.bc.gov.nrs.fta.mark.dto.MarkAmendmentRequest;
import ca.bc.gov.nrs.fta.mark.dto.MarkDetailDto;
import ca.bc.gov.nrs.fta.mark.dto.MarkEditRules;
import java.math.BigDecimal;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Requests an amendment to a private mark — legacy FTA512.
 *
 * <p>Ports {@code FTA_512_MARK_AMEND}: the gate from its {@code GET} (an HI mark with no
 * amendment outstanding, not B15/B16 — {@link MarkEditRules#amendments()}), the form's checks
 * ({@code Fta512MarkAmendForm}: Requested Amendment Changes required and at most 2000
 * characters, upper-cased; Requested Area 0 to 9999.9 with one decimal, 0.0 when blank), and
 * {@code SAVE}'s insert: a PI amendment in the mark's district, dated today, carrying the
 * mark's current status. Approving it (PI to HN) and the print that issues it (HN to HI) are
 * the Mark application tab's amendment status and Print.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class MarkAmendmentWriteService {

  /** {@code Fta512MarkAmendForm}'s StringLengthValidator. */
  static final int MAX_CHANGES = 2000;

  private static final BigDecimal MAX_AREA = new BigDecimal("9999.9");

  private final NamedParameterJdbcTemplate jdbc;
  private final MarkDetailService markDetailService;

  public MarkAmendmentWriteService(
      NamedParameterJdbcTemplate jdbc, MarkDetailService markDetailService) {
    this.jdbc = jdbc;
    this.markDetailService = markDetailService;
  }

  /** Another amendment got in first — the detail read may be stale by now. */
  private static final String OUTSTANDING_SQL =
      """
      SELECT COUNT(*) FROM the.tmbr_mark_amend
       WHERE timber_mark = :timberMark AND prv_mrk_amd_sts_st IN ('PI', 'HN')
      """;

  /**
   * TMBR_MARK_AMEND's parent (TMA_TM_FK) is the old TIMBER_MARK table, which legacy never
   * writes directly: trigger FTA_SYNC_PMC_TM creates the row when the certificate first gets
   * its forest file. A mark issued some other way can lack it, and then cannot be amended.
   */
  private static final String TIMBER_MARK_SQL =
      """
      SELECT COUNT(*) FROM the.timber_mark WHERE timber_mark = :timberMark
      """;

  private static final String INSERT_SQL =
      """
      INSERT INTO the.tmbr_mark_amend (
        timber_mark, forest_district, amend_request_date, prv_mrk_amd_sts_st,
        private_mrk_sts_st, permit_block_area, p_of_c_or_legal, mark_cancel_date,
        requesting_userid, authorizing_userid, revision_count,
        entry_userid, entry_timestamp, update_userid, update_timestamp
      ) VALUES (
        :timberMark, :district, SYSDATE, 'PI',
        :markStatus, :area, :changes, NULL,
        :userId, NULL, 0,
        :userId, SYSDATE, :userId, SYSDATE
      )
      """;

  /**
   * Adds the amendment request.
   *
   * @throws ResponseStatusException 404 if the mark does not exist; 409 if it cannot be
   *     amended now; 400 if a field is invalid
   */
  @Transactional
  public void add(
      String id, boolean byCertificate, MarkAmendmentRequest request, String userId) {
    MarkDetailDto mark = (byCertificate
            ? markDetailService.findByCertificate(id)
            : markDetailService.findByMarkNumber(id))
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Private mark not found."));
    MarkEditRules rules = MarkEditRules.of(mark, true);
    if (!rules.amendments()) {
      if (rules.amendmentsReason() == null) {
        throw alreadyOutstanding();
      }
      throw new ResponseStatusException(HttpStatus.CONFLICT, rules.amendmentsReason());
    }

    String changes = trim(request.requestedChanges());
    BigDecimal area = request.permitBlockArea() == null
        ? new BigDecimal("0.0")
        : request.permitBlockArea();

    List<String> e = new ArrayList<>();
    if (changes == null) {
      e.add("Requested amendment changes are required.");
    } else if (changes.length() > MAX_CHANGES) {
      e.add("Requested amendment changes can be at most " + MAX_CHANGES + " characters.");
    }
    if (area.signum() < 0 || area.compareTo(MAX_AREA) > 0) {
      e.add("Requested area must be between 0 and 9999.9 hectares.");
    } else if (area.stripTrailingZeros().scale() > 1) {
      e.add("Requested area can have at most one decimal place.");
    }
    if (!e.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", e));
    }

    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("timberMark", mark.timberMark())
        .addValue("district", mark.forestDistrict(), Types.INTEGER)
        .addValue("markStatus", mark.markStatusCode())
        .addValue("area", area, Types.NUMERIC)
        // Legacy upper-cased the text before saving.
        .addValue("changes", changes.toUpperCase(Locale.ROOT))
        .addValue("userId", userId);

    Long parent = jdbc.queryForObject(TIMBER_MARK_SQL, p, Long.class);
    if (parent == null || parent == 0) {
      throw noTimberMarkRecord(mark.timberMark());
    }
    Long outstanding = jdbc.queryForObject(OUTSTANDING_SQL, p, Long.class);
    if (outstanding != null && outstanding > 0) {
      throw alreadyOutstanding();
    }
    try {
      jdbc.update(INSERT_SQL, p);
    } catch (DuplicateKeyException ex) {
      // FTA_512.SAVE's DUP_VAL_ON_INDEX: one already today.
      throw alreadyOutstanding();
    } catch (DataIntegrityViolationException ex) {
      // TMA_TM_FK, should the row go between the check above and the insert.
      throw noTimberMarkRecord(mark.timberMark());
    }
  }

  private static ResponseStatusException noTimberMarkRecord(String timberMark) {
    return new ResponseStatusException(
        HttpStatus.CONFLICT,
        "Timber mark " + timberMark + " has no TIMBER_MARK record, which an amendment needs."
            + " Ask for a data fix.");
  }

  private static ResponseStatusException alreadyOutstanding() {
    return new ResponseStatusException(
        HttpStatus.CONFLICT,
        "An amendment already exists for this private mark. Reload and try again.");
  }
}
