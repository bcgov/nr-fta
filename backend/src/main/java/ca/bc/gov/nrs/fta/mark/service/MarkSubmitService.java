package ca.bc.gov.nrs.fta.mark.service;

import ca.bc.gov.nrs.fta.mark.dto.MarkDetailDto;
import ca.bc.gov.nrs.fta.mark.dto.MarkEditRules;
import java.sql.Types;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * FTA510 "Submit to HQ": a district sends its PA application to Headquarters, which makes
 * it PI — pending issuance, Headquarters' to approve or disallow.
 *
 * <p>Ports {@code FTA_510_PRIVATE_MARK}'s SUBMIT branch: only from PA, only with a client
 * ("Client is required. Please go to Associated Clients screen to add"), and only the status
 * changes. Legacy's submit also saved the form's fields; here the form is saved with its own
 * Save, so a submit carries just the revision it was made against. Who may submit is
 * {@link MarkEditRules#submit()}: the {@code FTA_TIMBER_MARK_DISTRICT_ADMIN} role.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class MarkSubmitService {

  private static final String SUBMIT_SQL =
      """
      UPDATE the.private_mark_certificate
         SET private_mark_status_code = 'PI',
             private_mark_status_date = SYSDATE,
             update_timestamp         = SYSDATE,
             update_userid            = :userId,
             revision_count           = revision_count + 1
       WHERE certificate = :certificate
         AND private_mark_status_code = 'PA'
         AND revision_count = :revision
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final MarkDetailService markDetailService;

  public MarkSubmitService(NamedParameterJdbcTemplate jdbc, MarkDetailService markDetailService) {
    this.jdbc = jdbc;
    this.markDetailService = markDetailService;
  }

  /**
   * Submits the application.
   *
   * @param revisionCount the PRIVATE_MARK_CERTIFICATE revision the user saw
   * @throws ResponseStatusException 404 if the mark does not exist; 409 if it cannot be
   *     submitted, or changed since it was read
   */
  @Transactional
  public void submit(
      String id, boolean byCertificate, Long revisionCount, boolean districtUser,
      String userId) {
    MarkDetailDto mark = (byCertificate
            ? markDetailService.findByCertificate(id)
            : markDetailService.findByMarkNumber(id))
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Private mark not found."));
    MarkEditRules rules = MarkEditRules.of(mark, true, districtUser);
    if (!rules.submit()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, rules.submitReason());
    }
    if (revisionCount == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "revisionCount is required.");
    }
    int n = jdbc.update(SUBMIT_SQL, new MapSqlParameterSource()
        .addValue("certificate", mark.certificate())
        .addValue("revision", revisionCount, Types.INTEGER)
        .addValue("userId", userId));
    if (n == 0) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "This application was changed by someone else since you opened it."
              + " Reload and try again.");
    }
  }
}
