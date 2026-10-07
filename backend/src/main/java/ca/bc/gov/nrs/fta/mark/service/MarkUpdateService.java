package ca.bc.gov.nrs.fta.mark.service;

import ca.bc.gov.nrs.fta.mark.dto.MarkDetailDto;
import ca.bc.gov.nrs.fta.mark.dto.MarkEditRules;
import ca.bc.gov.nrs.fta.mark.dto.MarkUpdateRequest;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Saves a private mark — the FTA510 "Save" button.
 *
 * <p>Ports the Headquarters path of {@code FTA_510_PRIVATE_MARK.mainline} with
 * {@code p_action = 'SAVE'}: a plain data change goes through {@code UPDATE_REC}, a status or
 * amendment-status change through {@code SAVE}, and a mark that ends up issued (HI) has its
 * tenure term refreshed ({@code UPDATE_TERM}). Field validation is the legacy form's
 * ({@code Fta510PrivateMarkForm}, the "Save" validator chain); what may change at all is
 * {@link MarkEditRules}, the same rules the detail page opens fields by.
 *
 * <p>Two deliberate departures, both where legacy silently lost input:
 * <ul>
 *   <li>Legacy's status-only branches (PI to DV, PI to EE) wrote the status and dropped any
 *       field edits made in the same save; here the fields are saved with the status.
 *   <li>Legacy stamped {@code PRIVATE_MARK_STATUS_DATE} on every data save; here it moves
 *       only when the status does.
 * </ul>
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class MarkUpdateService {

  /** Initial Term choices on the legacy screen, in months. */
  private static final Set<Integer> TERMS = Set.of(6, 12, 24, 36, 48, 60);

  /** Crown grant cut-over used by the B08/B09 mark types. */
  private static final LocalDate CROWN_GRANT_CUTOVER = LocalDate.of(1906, 3, 12);

  private final NamedParameterJdbcTemplate jdbc;
  private final MarkDetailService markDetailService;
  private final MarkFieldChecks checks;
  private final PrivateMarkPackage privateMarkPackage;

  public MarkUpdateService(
      NamedParameterJdbcTemplate jdbc,
      MarkDetailService markDetailService,
      MarkFieldChecks checks,
      PrivateMarkPackage privateMarkPackage) {
    this.jdbc = jdbc;
    this.markDetailService = markDetailService;
    this.checks = checks;
    this.privateMarkPackage = privateMarkPackage;
  }

  /** The values to write: the request's where the rules open a field, the stored ones elsewhere. */
  private record Values(
      LocalDate applicationDate,
      Integer tenureTerm,
      String forestDistrict,
      String markingMethodCode,
      String markingInstrumentCode,
      String permitBlockLocn,
      String legal,
      String ltoPid,
      BigDecimal area,
      String mgmtUnitType,
      String mgmtUnitId,
      String cascade,
      String reg,
      String comp,
      LocalDate issueDate,
      LocalDate expiryDate,
      LocalDate extendDate,
      LocalDate cancelDate,
      LocalDate grantedDate,
      String grantedDesc,
      String status,
      String amendStatus) {}

  /**
   * Saves the mark.
   *
   * @param id            the timber mark, or the certificate when {@code byCertificate}
   * @param byCertificate whether {@code id} is a certificate
   * @param request       the edited values
   * @param districtUser  whether the user holds the district role, which edits less
   * @param userId        the authenticated user id (audit columns)
   * @throws ResponseStatusException 404 if the mark does not exist; 409 if it cannot be edited
   *     or was changed by someone else since it was read; 400 if a value is invalid
   */
  @Transactional
  public void update(
      String id,
      boolean byCertificate,
      MarkUpdateRequest request,
      boolean districtUser,
      String userId) {
    MarkDetailDto current = (byCertificate
            ? markDetailService.findByCertificate(id)
            : markDetailService.findByMarkNumber(id))
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Private mark not found."));

    MarkEditRules rules = MarkEditRules.of(current, true, districtUser);
    if (!rules.editable()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          rules.reason() != null ? rules.reason() : "This mark cannot be edited.");
    }
    if (request.revisionCount() == null
        || !request.revisionCount().equals(current.revisionCount())) {
      throw changedElsewhere();
    }

    // A Mark Type in the save means "issue the mark": legacy's Assign Mark followed by its
    // Save (SAVE's PI-to-HN branch), done as one step.
    boolean issuing = !blank(request.fileTypeCode());
    if (issuing && !rules.markType()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "This mark already has a mark type, or cannot be issued yet.");
    }

    Values v = merge(current, rules, request, issuing);
    List<String> errors = validate(current, rules, v, issuing, request);
    if (!errors.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", errors));
    }
    // ASSIGN_MARK, through the legacy package: it commits the counter as it generates the
    // mark, so it runs after every check and before any write — a save that fails
    // validation spends no number.
    String newTimberMark = null;
    if (issuing) {
      try {
        newTimberMark = privateMarkPackage.assignMark(trim(request.fileTypeCode()));
      } catch (IllegalArgumentException e) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
      }
    }
    write(current, rules, request, v, userId, newTimberMark);
  }

  private static ResponseStatusException changedElsewhere() {
    return new ResponseStatusException(
        HttpStatus.CONFLICT,
        "This mark was changed by someone else since you opened it. Reload and try again.");
  }

  private static Values merge(
      MarkDetailDto c, MarkEditRules r, MarkUpdateRequest q, boolean issuing) {
    // Issuing creates the hauling authority, which takes the marking codes.
    boolean marking = r.marking() || issuing;
    return new Values(
        r.applicationDate() ? q.applicationDate() : c.markApplicationDate(),
        r.term() ? q.tenureTerm() : c.tenureTerm(),
        r.location() ? trim(q.forestDistrict()) : c.forestDistrict(),
        marking ? trim(q.markingMethodCode()) : c.markingMethodCode(),
        marking ? trim(q.markingInstrumentCode()) : c.markingInstrumentCode(),
        r.location() ? trim(q.permitBlockLocn()) : c.permitBlockLocn(),
        r.location() ? trim(q.proofOfCrownOrLegal()) : c.proofOfCrownOrLegal(),
        r.location() ? trim(q.bcaaFolioNumber()) : c.bcaaFolioNumber(),
        r.location() ? q.permitBlockArea() : c.permitBlockArea(),
        r.location() ? upper(trim(q.mgmtUnitTypeCode())) : c.mgmtUnitTypeCode(),
        r.location() ? trim(q.mgmtUnitId()) : c.mgmtUnitId(),
        r.location() ? trim(q.cascadeSplitCode()) : c.cascadeSplitCode(),
        r.location() ? trim(q.mapReferenceReg()) : c.mapReferenceReg(),
        r.location() ? trim(q.mapReferenceComp()) : c.mapReferenceComp(),
        r.branch() ? q.markIssueDate() : c.markIssueDate(),
        r.branch() ? q.markExpiryDate() : c.markExpiryDate(),
        r.branch() ? q.markExtendDate() : c.markExtendDate(),
        r.branch() ? q.markCancelDate() : c.markCancelDate(),
        r.branch() ? q.grantedAcqrdDate() : c.grantedAcqrdDate(),
        r.branch() ? trim(q.crownGrantedAcqDesc()) : c.crownGrantedAcqDesc(),
        r.status() ? trim(q.markStatusCode()) : c.markStatusCode(),
        r.amendmentStatus() ? trim(q.amendStatusCode()) : c.outstandingAmendStatus());
  }

  // ─── Validation — the legacy form's "Save" chain ────────────────────────────

  private List<String> validate(
      MarkDetailDto c, MarkEditRules r, Values v, boolean issuing, MarkUpdateRequest q) {
    List<String> e = new ArrayList<>();

    if (issuing) {
      validateIssue(e, q);
    }

    if (r.applicationDate()) {
      if (v.applicationDate() == null) {
        e.add("Application Date is required.");
      } else if (v.applicationDate().isAfter(LocalDate.now())) {
        e.add("Application Date cannot be later than today.");
      }
    }
    if (r.term() && (v.tenureTerm() == null || !TERMS.contains(v.tenureTerm()))) {
      e.add("Initial Term must be 6, 12, 24, 36, 48 or 60 months.");
    }

    if (r.location()) {
      checks.validateApplication(e, application(c), application(v));
    }

    if (r.marking() || issuing) {
      if (blank(v.markingMethodCode())) {
        e.add("Marking Requirements is required.");
      } else if (!checks.unchangedOrActive(
          c.markingMethodCode(), v.markingMethodCode(), MarkFieldChecks.METHOD_SQL)) {
        e.add("Marking Requirements is not a current code.");
      }
      if (blank(v.markingInstrumentCode())) {
        e.add("Marking Instrument is required.");
      } else if (!checks.unchangedOrActive(
          c.markingInstrumentCode(), v.markingInstrumentCode(), MarkFieldChecks.INSTRUMENT_SQL)) {
        e.add("Marking Instrument is not a current code.");
      }
    }

    if (r.branch()) {
      String prev = c.markStatusCode();
      if (prev != null && prev.startsWith("H") && v.issueDate() == null) {
        e.add("Issued date is required when the status is " + prev + ".");
      }
      if (v.issueDate() != null && v.expiryDate() != null
          && !v.expiryDate().isAfter(v.issueDate())) {
        e.add("Expired date must be later than the Issued date.");
      }
      if ("PI".equals(prev) && v.extendDate() != null) {
        e.add("Extended date must be blank while the status is PI.");
      }
      if (v.extendDate() != null && v.expiryDate() != null
          && !v.extendDate().isAfter(v.expiryDate())) {
        e.add("Extended date must be later than the Expired date.");
      }
      if (v.grantedDesc() != null && v.grantedDesc().length() > 10) {
        e.add("Crown Granted Description can be at most 10 characters.");
      }
      // The type being assigned counts: legacy checked these on the Save that issued the mark.
      validateCrownGrant(e, issuing ? trim(q.fileTypeCode()) : c.fileTypeCode(), v);
    }

    if (r.status()) {
      if (blank(v.status()) || !r.statusOptions().contains(v.status())) {
        e.add("Status can be " + String.join(", ", r.statusOptions()) + ".");
      } else if ("HX".equals(v.status()) && !"HX".equals(c.markStatusCode())
          && v.cancelDate() == null) {
        e.add("Cancelled date is required to set the status to HX.");
      }
    }
    if (r.amendmentStatus()
        && (blank(v.amendStatus()) || !r.amendmentStatusOptions().contains(v.amendStatus()))) {
      e.add("Amendment Status must be PI, HN or DV.");
    }
    return e;
  }

  /** B08 marks are crown granted before the cut-over, B09 after; exactly one of date or year. */
  private static void validateCrownGrant(List<String> e, String fileType, Values v) {
    if (!"B08".equals(fileType) && !"B09".equals(fileType)) {
      return;
    }
    if ((v.grantedDate() == null) == blank(v.grantedDesc())) {
      // Legacy: "One and only one of Crown Granted Date or Crown Granted Description must be
      // entered."
      e.add("Enter exactly one of Crown Granted Date or Crown Granted Description.");
      return;
    }
    boolean b08 = "B08".equals(fileType);
    if (v.grantedDate() != null) {
      if (b08 && !v.grantedDate().isBefore(CROWN_GRANT_CUTOVER)) {
        e.add("Crown Granted Date must be before 1906-03-12 for mark type B08.");
      } else if (!b08 && v.grantedDate().isBefore(CROWN_GRANT_CUTOVER)) {
        e.add("Crown Granted Date must be on or after 1906-03-12 for mark type B09.");
      }
    } else if (v.grantedDesc().matches("\\d{4}")) {
      // Legacy reads a four-digit description as the grant year.
      int year = Integer.parseInt(v.grantedDesc());
      if (b08 && year > 1906) {
        e.add("Crown Granted year must be 1906 or earlier for mark type B08.");
      } else if (!b08 && year < 1906) {
        e.add("Crown Granted year must be 1906 or later for mark type B09.");
      }
    }
  }

  private static MarkFieldChecks.Application application(MarkDetailDto c) {
    return new MarkFieldChecks.Application(
        c.forestDistrict(), c.permitBlockLocn(), c.proofOfCrownOrLegal(), c.bcaaFolioNumber(),
        c.permitBlockArea(), c.mgmtUnitTypeCode(), c.mgmtUnitId(), c.cascadeSplitCode(),
        c.mapReferenceReg(), c.mapReferenceComp());
  }

  private static MarkFieldChecks.Application application(Values v) {
    return new MarkFieldChecks.Application(
        v.forestDistrict(), v.permitBlockLocn(), v.legal(), v.ltoPid(), v.area(),
        v.mgmtUnitType(), v.mgmtUnitId(), v.cascade(), v.reg(), v.comp());
  }

  /**
   * Issuing: a current private mark type (ASSIGN_MARK's check). The mark it generates is
   * checked for clashes by the package itself.
   */
  private void validateIssue(List<String> e, MarkUpdateRequest q) {
    String type = trim(q.fileTypeCode());
    if (!checks.unchangedOrActive(null, type, MarkFieldChecks.MARK_TYPE_SQL)) {
      e.add("Mark Type " + type + " is not a current private mark type.");
    }
  }

  // ─── Writes ──────────────────────────────────────────────────────────────────

  private static final String UPDATE_CERTIFICATE_SQL =
      """
      UPDATE the.private_mark_certificate
         SET timber_mark                   = NVL(:newTimberMark, timber_mark),
             forest_file_id                = NVL(:newTimberMark, forest_file_id),
             private_mark_activated_userid = CASE WHEN :newTimberMark IS NOT NULL
                                                  THEN UPPER(:userId)
                                                  ELSE private_mark_activated_userid END,
             forest_district               = TO_NUMBER(:forestDistrict),
             private_mark_status_code      = :status,
             private_mark_status_date      = CASE WHEN :statusChanged = 'Y' THEN SYSDATE
                                                  ELSE private_mark_status_date END,
             private_mark_application_date = :applicationDate,
             private_mark_issue_date       = :issueDate,
             -- An issued mark with no expiry gets issue date + term, less a day
             -- (FTA_CALC_EXPIRY_DATE).
             private_mark_expiry_date      = CASE
                 -- SAVE, PI to HN: issue date + term, less a day, recomputed on issue.
                 WHEN :newTimberMark IS NOT NULL
                 THEN ADD_MONTHS(:issueDate - 1, NVL(:term, 0))
                 WHEN :expiryDate IS NULL AND :status IN ('HI', 'HN') AND :issueDate IS NOT NULL
                 THEN ADD_MONTHS(:issueDate, NVL(:term, 0)) - 1
                 ELSE :expiryDate END,
             private_mark_tenure_term      = NVL(:term, 0),
             private_mark_extend_date      = :extendDate,
             private_mark_extend_count     = :extendCount,
             private_mark_extend_reas_code = :extendReason,
             private_mark_cancel_date      = :cancelDate,
             mgmt_unit_type_code           = :mgmtUnitType,
             mgmt_unit_id                  = :mgmtUnitId,
             quota_type_code               = :quotaType,
             cascade_split_code            = :cascade,
             bcaa_folio_number             = :ltoPid,
             permit_block_locn             = :permitBlockLocn,
             permit_block_area             = :area,
             p_of_c_or_legal               = :legal,
             granted_acqrd_date            = :grantedDate,
             crown_granted_acq_desc        = :grantedDesc,
             crown_granted_ind             = :crownGrantedInd,
             map_reference_id              = :mapReferenceId,
             private_mark_amend_date       = CASE WHEN :amendChanged = 'Y' THEN SYSDATE
                                                  ELSE private_mark_amend_date END,
             private_mark_amended_userid   = CASE WHEN :amendChanged = 'Y' THEN UPPER(:userId)
                                                  ELSE private_mark_amended_userid END,
             update_timestamp              = SYSDATE,
             update_userid                 = :userId,
             revision_count                = revision_count + 1
       WHERE certificate = :certificate
         AND revision_count = :revisionCount
      """;

  private void write(
      MarkDetailDto c,
      MarkEditRules r,
      MarkUpdateRequest q,
      Values v,
      String userId,
      String newTimberMark) {
    boolean issuing = newTimberMark != null;
    String prevStatus = c.markStatusCode();
    boolean amendChanged = r.amendmentStatus()
        && !Objects.equals(v.amendStatus(), c.outstandingAmendStatus());
    // SAVE: issuing makes the mark HN; approving the outstanding amendment (HN) re-issues it.
    String status = issuing ? "HN"
        : amendChanged && "HN".equals(v.amendStatus()) ? "HI" : v.status();
    // SAVE, PI to HN: an issue date defaults to today.
    LocalDate issueDate = issuing && v.issueDate() == null ? LocalDate.now() : v.issueDate();
    boolean statusChanged = !Objects.equals(status, prevStatus);

    // A changed extension on an issued mark is counted, with reason P (both procedures).
    Integer extendCount = c.markExtendCount();
    String extendReason = null;
    if (v.extendDate() == null) {
      extendCount = 0;
    } else if ("HI".equals(status) && !Objects.equals(v.extendDate(), c.markExtendDate())) {
      extendCount = (extendCount == null ? 0 : extendCount) + 1;
      extendReason = "P";
    }

    // Dates (and the nullable numbers) are bound with their type: Spring binds a null of
    // unknown type as text, and Oracle then rejects a CASE mixing it with a DATE branch
    // (ORA-00932) — any save with a blank date failed.
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("certificate", c.certificate())
        .addValue("revisionCount", q.revisionCount())
        .addValue("userId", userId)
        .addValue("forestDistrict", v.forestDistrict())
        .addValue("status", status)
        .addValue("statusChanged", statusChanged ? "Y" : "N")
        .addValue("applicationDate", v.applicationDate(), Types.DATE)
        .addValue("issueDate", issueDate, Types.DATE)
        .addValue("newTimberMark", newTimberMark)
        .addValue("expiryDate", v.expiryDate(), Types.DATE)
        .addValue("term", v.tenureTerm(), Types.INTEGER)
        .addValue("extendDate", v.extendDate(), Types.DATE)
        .addValue("extendCount", extendCount, Types.INTEGER)
        .addValue("extendReason", extendReason)
        .addValue("cancelDate", v.cancelDate(), Types.DATE)
        .addValue("mgmtUnitType", v.mgmtUnitType())
        .addValue("mgmtUnitId", v.mgmtUnitId())
        .addValue("quotaType", MarkFieldChecks.quotaType(v.mgmtUnitType()))
        .addValue("cascade", v.cascade())
        .addValue("ltoPid", v.ltoPid())
        .addValue("permitBlockLocn", v.permitBlockLocn())
        .addValue("area", v.area())
        .addValue("legal", v.legal())
        .addValue("grantedDate", v.grantedDate(), Types.DATE)
        .addValue("grantedDesc", v.grantedDesc())
        .addValue("crownGrantedInd",
            v.grantedDate() != null || !blank(v.grantedDesc()) ? "Y" : "N")
        .addValue("mapReferenceId", MarkFieldChecks.mapReferenceId(v.reg(), v.comp()))
        .addValue("amendChanged", amendChanged ? "Y" : "N");

    if (issuing) {
      // Before the certificate: it takes the new forest file as its own.
      issue(c, v, trim(q.fileTypeCode()), newTimberMark, userId);
    }

    if (jdbc.update(UPDATE_CERTIFICATE_SQL, p) == 0) {
      throw changedElsewhere();
    }

    if (issuing) {
      moveClientsToForestFile(c.certificate(), newTimberMark);
    }

    String timberMark = c.timberMark();
    if (timberMark != null) {
      // UPDATE_REC keeps the forest file's management unit in step.
      jdbc.update(
          """
          UPDATE the.prov_forest_use
             SET mgmt_unit_type = :mgmtUnitType, mgmt_unit_id = :mgmtUnitId
           WHERE forest_file_id = :timberMark
          """,
          new MapSqlParameterSource()
              .addValue("mgmtUnitType", v.mgmtUnitType())
              .addValue("mgmtUnitId", v.mgmtUnitId())
              .addValue("timberMark", timberMark));
    }

    // SAVE: cancelling an issued mark, or acting on a cancelled one, moves the forest
    // file's status with it; cancelling also drops the pending amendments.
    if (statusChanged && timberMark != null
        && (("HI".equals(prevStatus) && "HX".equals(status)) || "HX".equals(prevStatus))) {
      jdbc.update(
          """
          UPDATE the.prov_forest_use
             SET file_status_st   = :status,
                 file_status_date = SYSDATE,
                 update_timestamp = SYSDATE,
                 update_userid    = :userId,
                 revision_count   = revision_count + 1
           WHERE forest_file_id = :timberMark
          """,
          new MapSqlParameterSource()
              .addValue("status", status)
              .addValue("userId", userId)
              .addValue("timberMark", timberMark));
      if ("HI".equals(prevStatus) && "HX".equals(status)) {
        jdbc.update(
            """
            DELETE FROM the.tmbr_mark_amend
             WHERE timber_mark = :timberMark AND prv_mrk_amd_sts_st = 'PI'
            """,
            new MapSqlParameterSource("timberMark", timberMark));
      }
    }

    if (amendChanged) {
      int n = jdbc.update(
          """
          UPDATE the.tmbr_mark_amend
             SET prv_mrk_amd_sts_st = :amendStatus,
                 forest_district    = TO_NUMBER(:forestDistrict),
                 authorizing_userid = CASE WHEN :amendStatus = 'HN' THEN :userId END,
                 update_userid      = :userId,
                 update_timestamp   = SYSDATE,
                 revision_count     = revision_count + 1
           WHERE timber_mark = :timberMark
             AND prv_mrk_amd_sts_st IN ('PI', 'HN')
             AND revision_count = :amendRevisionCount
          """,
          new MapSqlParameterSource()
              .addValue("amendStatus", v.amendStatus())
              .addValue("forestDistrict", v.forestDistrict())
              .addValue("userId", userId)
              .addValue("timberMark", timberMark)
              .addValue("amendRevisionCount", q.amendRevisionCount()));
      if (n == 0) {
        throw changedElsewhere();
      }
    }

    if ("HI".equals(status) && timberMark != null) {
      if (r.marking()) {
        jdbc.update(
            """
            UPDATE the.hauling_authority
               SET marking_method_code     = :method,
                   marking_instrument_code = :instrument,
                   update_userid           = :userId,
                   revision_count          = revision_count + 1
             WHERE timber_mark = :timberMark
            """,
            new MapSqlParameterSource()
                .addValue("method", v.markingMethodCode())
                .addValue("instrument", v.markingInstrumentCode())
                .addValue("userId", userId)
                .addValue("timberMark", timberMark));
      }
      updateTerm(c, userId);
    }
  }

  /**
   * SAVE, PI to HN: the mark becomes a forest file of its own — a PROV_FOREST_USE at HN
   * (Fta_Create_Prov_Forest_Use), a TIMBER_TENURE when the type is a timber tenure type
   * (fta_create_timber_tenure, which ignores one that exists) and its HAULING_AUTHORITY with
   * the marking codes.
   */
  private void issue(
      MarkDetailDto c, Values v, String fileTypeCode, String timberMark, String userId) {
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("mark", timberMark)
        .addValue("fileType", fileTypeCode)
        .addValue("district", v.forestDistrict())
        .addValue("mgmtUnitType", v.mgmtUnitType())
        .addValue("mgmtUnitId", v.mgmtUnitId())
        .addValue("method", v.markingMethodCode())
        .addValue("instrument", v.markingInstrumentCode())
        .addValue("userId", userId);
    jdbc.update(
        """
        INSERT INTO the.prov_forest_use (
          forest_file_id, file_status_st, file_status_date, file_type_code, forest_region,
          mgmt_unit_type, mgmt_unit_id, bcts_org_unit, sb_funded_ind, district_admin_zone,
          entry_userid, entry_timestamp, update_userid, update_timestamp, revision_count)
        SELECT :mark, 'HN', SYSDATE, :fileType, ou.rollup_region_no,
               :mgmtUnitType, :mgmtUnitId, NULL, 'N', NULL,
               :userId, SYSDATE, :userId, SYSDATE, 0
          FROM the.org_unit ou
         WHERE ou.org_unit_no = TO_NUMBER(:district)
        """,
        p);
    jdbc.update(
        """
        INSERT INTO the.timber_tenure (
          forest_file_id, mark_designate, licence_replaceable_ind, supplemental_fl_ind,
          revision_count, entry_userid, entry_timestamp, update_userid, update_timestamp)
        SELECT :mark, NULL, 'N', 'N', 0, :userId, SYSDATE, :userId, SYSDATE
          FROM dual
         WHERE EXISTS (SELECT 1 FROM the.timber_file_type_code
                        WHERE timber_file_type_code = :fileType)
           AND NOT EXISTS (SELECT 1 FROM the.timber_tenure WHERE forest_file_id = :mark)
        """,
        p);
    jdbc.update(
        """
        INSERT INTO the.hauling_authority (
          timber_mark, marking_method_code, marking_instrument_code, entry_timestamp,
          entry_userid, update_userid, update_timestamp, revision_count, forest_file_id)
        VALUES (:mark, :method, :instrument, SYSDATE, :userId, :userId, SYSDATE, 1, :mark)
        """,
        p);
  }

  /**
   * SAVE, PI to HN: the application's clients become the forest file's — copied from
   * PRIVATE_MARK_CLIENT unless the file already has some — and leave PRIVATE_MARK_CLIENT.
   */
  private void moveClientsToForestFile(String certificate, String timberMark) {
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("certificate", certificate)
        .addValue("mark", timberMark);
    jdbc.update(
        """
        INSERT INTO the.forest_file_client (
          forest_file_client_skey, forest_file_id, forest_file_client_type_code, client_number,
          client_locn_code, licensee_start_date, licensee_end_date, entry_userid,
          entry_timestamp, update_userid, update_timestamp, revision_count)
        SELECT pmc.private_mark_client_skey, :mark, pmc.private_mark_client_type_code,
               pmc.client_number, pmc.client_locn_code, pmc.licensee_start_date,
               pmc.licensee_end_date, pmc.entry_userid, pmc.entry_timestamp,
               pmc.update_userid, pmc.update_timestamp, pmc.revision_count
          FROM the.private_mark_client pmc
         WHERE pmc.certificate = :certificate
           AND NOT EXISTS (SELECT 1 FROM the.forest_file_client ffc
                            WHERE ffc.forest_file_id IN (:certificate, :mark))
        """,
        p);
    jdbc.update(
        "DELETE FROM the.private_mark_client WHERE certificate = :certificate", p);
  }

  /**
   * UPDATE_TERM: the issued mark's tenure term follows the certificate's term, issue,
   * expiry and extension — created when the forest file has none yet.
   */
  void updateTerm(MarkDetailDto c, String userId) {
    String forestFileId = c.forestFileId() != null ? c.forestFileId() : c.timberMark();
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("forestFileId", forestFileId)
        .addValue("certificate", c.certificate())
        .addValue("userId", userId);
    int n = jdbc.update(
        """
        UPDATE the.tenure_term tt
           SET (tenure_term, legal_effective_dt, initial_expiry_dt, current_expiry_dt,
                tenure_extend_cnt, tenr_extend_rsn_st) =
               (SELECT pmc.private_mark_tenure_term, pmc.private_mark_issue_date,
                       pmc.private_mark_expiry_date, pmc.private_mark_extend_date,
                       pmc.private_mark_extend_count, pmc.private_mark_extend_reas_code
                  FROM the.private_mark_certificate pmc
                 WHERE pmc.certificate = :certificate),
               update_userid    = :userId,
               update_timestamp = SYSDATE,
               revision_count   = revision_count + 1
         WHERE tt.forest_file_id = :forestFileId
        """,
        p);
    if (n == 0) {
      jdbc.update(
          """
          INSERT INTO the.tenure_term (
            forest_file_id, tenure_term, legal_effective_dt, initial_expiry_dt,
            entry_userid, entry_timestamp, update_userid, update_timestamp, revision_count)
          SELECT pfu.forest_file_id, pmc.private_mark_tenure_term,
                 pmc.private_mark_issue_date, pmc.private_mark_expiry_date,
                 :userId, SYSDATE, :userId, SYSDATE, 1
            FROM the.prov_forest_use pfu
            JOIN the.private_mark_certificate pmc ON pmc.forest_file_id = pfu.forest_file_id
           WHERE pmc.certificate = :certificate
          """,
          p);
    }
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────────

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  private static String trim(String s) {
    return blank(s) ? null : s.trim();
  }

  private static String upper(String s) {
    return s == null ? null : s.toUpperCase();
  }
}
