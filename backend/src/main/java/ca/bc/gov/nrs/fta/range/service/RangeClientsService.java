package ca.bc.gov.nrs.fta.range.service;

import ca.bc.gov.nrs.fta.range.dto.RangeClientsDtos.RangeClientAssignRequest;
import ca.bc.gov.nrs.fta.range.dto.RangeClientsDtos.RangeClientRowDto;
import ca.bc.gov.nrs.fta.range.dto.RangeClientsDtos.RangeClientsListDto;
import ca.bc.gov.nrs.fta.range.dto.RangeClientsDtos.RelatedClientDto;
import ca.bc.gov.nrs.fta.shared.sql.ClientNameSql;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The range tenure's clients — legacy FTA920R (Range Associated Clients,
 * {@code Fta920RAssoClientsAction} over {@code THE.FTA_920R_ASSO_CLIENTS_DETAILS}).
 *
 * <ul>
 *   <li><b>List</b> ("Licensee (Current &amp; Previous)"): mainline GET's query and its
 *       {@code p_save_ok}. The query is inlined, since mainline also wants the legacy user's org
 *       unit for its authority checks, which {@link RangeFileAccess} replaces here. An
 *       application in the Inbox ({@code FTA_APPLICATION_PENDING}) makes the file view only.
 *   <li><b>Get</b> ("Selected Clients from the Client System"): the package's
 *       {@code related_client_mainline('GET')}.
 *   <li><b>Save</b> (Assign/Transfer to Client): the form's checks, the file authority, the Inbox
 *       check, Get again (legacy offered Save only after a Get without errors), then
 *       {@code related_client_mainline('SAVE')}.
 * </ul>
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class RangeClientsService {

  /** Mainline GET's {@code get}, in its order (NULLS FIRST on DESC: current licensees first). */
  static final String LIST_SQL =
      """
      SELECT fcl.forest_file_client_skey,
             fcl.client_number,
             fcl.client_locn_code,
             %s AS client_name,
             cl.address_1 || ' ' || NVL(cl.address_2, '') || cl.city || ', ' || cl.province
               || ' ' || cl.postal_code || ' ' || NVL(cl.country, '')          AS client_address,
             'Bus: ' || NVL(cl.business_phone, 'n/a') || ' Res: ' || NVL(cl.home_phone, 'n/a')
               || ' Cell:' || NVL(cl.cell_phone, 'n/a') || ' Fax:' || NVL(cl.fax_number, 'n/a')
                                                                                AS client_phone_fax,
             cl.email_address                                                   AS client_email,
             fcl.forest_file_client_type_code                                   AS file_client_type,
             fctc.description                                                   AS file_client_type_desc,
             fcl.licensee_start_date,
             fcl.licensee_end_date
        FROM the.forest_file_client fcl
        LEFT JOIN the.file_client_type_code fctc
               ON fctc.file_client_type_code = fcl.forest_file_client_type_code
        LEFT JOIN the.client_location cl
               ON cl.client_number = fcl.client_number AND cl.client_locn_code = fcl.client_locn_code
        LEFT JOIN the.forest_client fc ON fc.client_number = fcl.client_number
       WHERE fcl.forest_file_id = :forestFileId
       ORDER BY fcl.licensee_end_date DESC, fcl.update_timestamp DESC,
                fcl.forest_file_client_type_code
      """.formatted(ClientNameSql.displayName("fc"));

  /** {@code FTA_APPLICATION_PENDING}: a new FIL/RNG/RP application in the Inbox. */
  static final String APPLICATION_PENDING_SQL =
      """
      SELECT COUNT(*) FROM the.tenure_application
       WHERE forest_file_id = :forestFileId
         AND tenure_application_type_code IN ('FIL', 'RNG', 'RP')
         AND tenure_application_state_code = 'INB'
         AND tenure_app_purp_code = 'N'
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final RangeClientsPackage pkg;
  private final RangeFileAccess access;

  public RangeClientsService(NamedParameterJdbcTemplate jdbc, RangeClientsPackage pkg,
      RangeFileAccess access) {
    this.jdbc = jdbc;
    this.pkg = pkg;
    this.access = access;
  }

  /** The tab: the file's clients, and whether Save is enabled. */
  public RangeClientsListDto list(String forestFileId) {
    List<RangeClientRowDto> rows = jdbc.query(LIST_SQL, Map.of("forestFileId", forestFileId),
        (rs, n) -> {
          long skey = rs.getLong("forest_file_client_skey");
          return new RangeClientRowDto(
              rs.wasNull() ? null : skey,
              rs.getString("client_number"),
              rs.getString("client_locn_code"),
              rs.getString("client_name"),
              rs.getString("client_address"),
              rs.getString("client_phone_fax"),
              rs.getString("client_email"),
              rs.getString("file_client_type"),
              rs.getString("file_client_type_desc"),
              rs.getObject("licensee_start_date", LocalDate.class),
              rs.getObject("licensee_end_date", LocalDate.class));
        });
    boolean mayEdit = access.mayEdit(forestFileId);
    boolean pending = applicationPending(forestFileId);
    return new RangeClientsListDto(rows, RangeClientsRules.canEdit(mayEdit, pending),
        RangeClientsRules.warnings(mayEdit, pending));
  }

  /**
   * Get: the client (as A) and its partners (as B) that Save would make the licensees.
   *
   * @throws ResponseStatusException 400 with the form's or the package's messages
   */
  public List<RelatedClientDto> related(String forestFileId, String clientNumber,
      String clientLocnCode) {
    List<String> errors = RangeClientsRules.getErrors(clientNumber, clientLocnCode);
    if (!errors.isEmpty()) {
      throw badRequest(errors);
    }
    RangeClientsPackage.Related related =
        pkg.getRelated(forestFileId, clientNumber.trim(), clientLocnCode.trim());
    errors = RangeClientsRules.errors(related.errorMessage());
    if (!errors.isEmpty()) {
      throw badRequest(errors);
    }
    return related.rows();
  }

  /**
   * Save: makes the client and its partners the file's licensees.
   *
   * @return legacy's confirmation
   * @throws ResponseStatusException 400 with the form's or the package's messages, or when the
   *     file is not a range file; 403 when the user may not edit the file; 409 while an
   *     application is in the Inbox
   */
  @Transactional
  public String assign(String forestFileId, RangeClientAssignRequest request, String userId) {
    List<String> errors = RangeClientsRules.saveErrors(request.clientNumber(),
        request.clientLocnCode(), request.licenseeStartDate(),
        request.previousLicenseeEndDate());
    if (!errors.isEmpty()) {
      throw badRequest(errors);
    }
    try {
      access.requireEditable(forestFileId);
    } catch (ResponseStatusException e) {
      if (e.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, RangeClientsRules.NOT_AUTHORIZED);
      }
      throw e;
    }
    if (applicationPending(forestFileId)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          RangeClientsRules.APPLICATION_PENDING);
    }
    String number = request.clientNumber().trim();
    String locn = request.clientLocnCode().trim();
    related(forestFileId, number, locn);
    String message = pkg.save(forestFileId, number, locn,
        RangeClientsRules.packageDate(request.licenseeStartDate()),
        RangeClientsRules.packageDate(request.previousLicenseeEndDate()), userId);
    errors = RangeClientsRules.errors(message);
    if (!errors.isEmpty()) {
      // Throwing rolls back whatever the package wrote, as legacy's store.rollback().
      throw badRequest(errors);
    }
    return RangeClientsRules.SAVED;
  }

  private boolean applicationPending(String forestFileId) {
    Integer count = jdbc.queryForObject(APPLICATION_PENDING_SQL,
        Map.of("forestFileId", forestFileId), Integer.class);
    return count != null && count > 0;
  }

  private static ResponseStatusException badRequest(List<String> errors) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", errors));
  }
}
