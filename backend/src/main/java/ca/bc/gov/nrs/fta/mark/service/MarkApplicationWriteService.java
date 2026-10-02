package ca.bc.gov.nrs.fta.mark.service;

import static ca.bc.gov.nrs.fta.mark.service.MarkFieldChecks.trim;

import ca.bc.gov.nrs.fta.mark.dto.MarkApplicationRequest;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Creates a private mark application — the FTA510 "Add New".
 *
 * <p>Takes the certificate number from the legacy package itself
 * ({@link PrivateMarkPackage}, {@code ADD_NEW_DEFAULTS}) and ports {@code ADD_NEW}
 * ({@code create_certificate}, then {@code CREATE_PRIVATE_MARK_CLIENT} when a holder is
 * given). Validation is the legacy form's "Save" chain for a new record, through
 * {@link MarkFieldChecks} — the same checks a later edit runs.
 *
 * <p>Legacy sets a new application's status by the user's organization level: PI at
 * Headquarters, PA at a district, which then submits it to Headquarters (Submit to HQ,
 * {@link MarkSubmitService}). Here the district level is the
 * {@code FTA_TIMBER_MARK_DISTRICT_ADMIN} role; everyone else creates at PI.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class MarkApplicationWriteService {

  /** Initial Term choices on the legacy screen, in months. */
  private static final Set<Integer> TERMS = Set.of(6, 12, 24, 36, 48, 60);

  /** A new application's status (ADD_NEW): Headquarters' and a district's. */
  private static final String NEW_STATUS = "PI";

  private static final String NEW_DISTRICT_STATUS = "PA";

  private final NamedParameterJdbcTemplate jdbc;
  private final MarkFieldChecks checks;
  private final PrivateMarkPackage privateMarkPackage;

  public MarkApplicationWriteService(
      NamedParameterJdbcTemplate jdbc,
      MarkFieldChecks checks,
      PrivateMarkPackage privateMarkPackage) {
    this.jdbc = jdbc;
    this.checks = checks;
    this.privateMarkPackage = privateMarkPackage;
  }

  private static final String INSERT_CERTIFICATE_SQL =
      """
      INSERT INTO the.private_mark_certificate (
        certificate, forest_district, private_mark_application_date,
        private_mark_status_code, private_mark_tenure_term, private_mark_status_date,
        cascade_split_code, quota_type_code, mgmt_unit_type_code, mgmt_unit_id,
        bcaa_folio_number, map_type_code, map_database_id, map_reference_id,
        permit_block_locn, permit_block_area, p_of_c_or_legal,
        entry_userid, entry_timestamp, update_userid, update_timestamp, revision_count
      ) VALUES (
        :certificate, TO_NUMBER(:forestDistrict), :applicationDate,
        :status, :term, TRUNC(SYSDATE),
        :cascade, :quotaType, :mgmtUnitType, :mgmtUnitId,
        :ltoPid, 'I', 'I', :mapReferenceId,
        :permitBlockLocn, :area, :legal,
        :userId, SYSDATE, :userId, SYSDATE, 0
      )
      """;

  private static final String INSERT_CLIENT_SQL =
      """
      INSERT INTO the.private_mark_client (
        certificate, private_mark_client_skey, private_mark_client_type_code,
        client_number, client_locn_code, licensee_start_date, licensee_end_date,
        entry_userid, entry_timestamp, update_userid, update_timestamp, revision_count
      ) VALUES (
        :certificate, the.for_client_link_seq.NEXTVAL, 'A',
        :clientNumber, :clientLocnCode, TRUNC(SYSDATE), NULL,
        :userId, SYSDATE, :userId, SYSDATE, 0
      )
      """;

  /**
   * Creates the application and returns its certificate number.
   *
   * @param request the application
   * @param userId  the authenticated user id (audit columns)
   * @param districtUser whether the user is at district level, whose applications start PA
   * @throws ResponseStatusException 400 if a field is invalid
   */
  @Transactional
  public String create(MarkApplicationRequest request, String userId, boolean districtUser) {
    String clientNumber = trim(request.clientNumber());
    String clientLocnCode = trim(request.clientLocnCode());
    MarkFieldChecks.Application app = new MarkFieldChecks.Application(
        trim(request.forestDistrict()),
        trim(request.permitBlockLocn()),
        trim(request.proofOfCrownOrLegal()),
        trim(request.bcaaFolioNumber()),
        request.permitBlockArea(),
        upper(trim(request.mgmtUnitTypeCode())),
        trim(request.mgmtUnitId()),
        trim(request.cascadeSplitCode()),
        trim(request.mapReferenceReg()),
        trim(request.mapReferenceComp()));

    List<String> e = new ArrayList<>();
    if (request.applicationDate() == null) {
      e.add("Application Date is required.");
    } else if (request.applicationDate().isAfter(LocalDate.now())) {
      e.add("Application Date cannot be later than today.");
    }
    if (request.tenureTerm() == null || !TERMS.contains(request.tenureTerm())) {
      e.add("Initial Term must be 6, 12, 24, 36, 48 or 60 months.");
    }
    checks.validateApplication(e, null, app);
    if (clientNumber != null) {
      if (clientLocnCode == null) {
        // locnCodeAndID: a client number needs its location.
        e.add("Client location is required with a client number.");
      } else if (!clientLocationExists(clientNumber, clientLocnCode)) {
        e.add("Client " + clientNumber + " has no location " + clientLocnCode + ".");
      }
    }
    if (!e.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", e));
    }

    // First write in the transaction: the package commits the counter as it takes it.
    String certificate = privateMarkPackage.nextCertificate(app.forestDistrict());

    jdbc.update(INSERT_CERTIFICATE_SQL, new MapSqlParameterSource()
        .addValue("certificate", certificate)
        .addValue("forestDistrict", app.forestDistrict())
        .addValue("applicationDate", request.applicationDate(), Types.DATE)
        .addValue("status", districtUser ? NEW_DISTRICT_STATUS : NEW_STATUS)
        .addValue("term", request.tenureTerm())
        .addValue("cascade", app.cascade())
        .addValue("quotaType", MarkFieldChecks.quotaType(app.mgmtUnitType()))
        .addValue("mgmtUnitType", app.mgmtUnitType())
        .addValue("mgmtUnitId", app.mgmtUnitId())
        .addValue("ltoPid", app.ltoPid())
        .addValue("mapReferenceId", MarkFieldChecks.mapReferenceId(app.reg(), app.comp()))
        .addValue("permitBlockLocn", app.permitBlockLocn())
        .addValue("area", app.area())
        // ADD_NEW stores the legal description upper-cased.
        .addValue("legal", app.legal().toUpperCase())
        .addValue("userId", userId));

    if (clientNumber != null) {
      jdbc.update(INSERT_CLIENT_SQL, new MapSqlParameterSource()
          .addValue("certificate", certificate)
          .addValue("clientNumber", clientNumber)
          .addValue("clientLocnCode", clientLocnCode)
          .addValue("userId", userId));
    }
    return certificate;
  }

  private static String upper(String s) {
    return s == null ? null : s.toUpperCase();
  }

  /** The Sil30 client field's check: the client number and location exist. */
  private boolean clientLocationExists(String clientNumber, String clientLocnCode) {
    Long n = jdbc.queryForObject(
        """
        SELECT COUNT(*) FROM the.client_location
         WHERE client_number = :clientNumber AND client_locn_code = :clientLocnCode
        """,
        new MapSqlParameterSource()
            .addValue("clientNumber", clientNumber)
            .addValue("clientLocnCode", clientLocnCode),
        Long.class);
    return n != null && n > 0;
  }
}
