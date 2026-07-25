package ca.bc.gov.nrs.fta.mark.service;

import ca.bc.gov.nrs.fta.mark.dto.MarkApplicationRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write operations for private mark applications.
 *
 * <p>Ports the create path of the legacy {@code fta_510_private_mark} package
 * ({@code add_new} → {@code create_certificate}) to an INSERT against {@code
 * THE.PRIVATE_MARK_CERTIFICATE} via {@link NamedParameterJdbcTemplate}. The
 * legacy PL/SQL also generates a certificate number, defaults the status by org
 * level, and inserts a companion {@code PRIVATE_MARK_CLIENT} row; that business
 * logic should be reproduced here as the module is hardened. Runs against the
 * shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class MarkApplicationWriteService {

  private final NamedParameterJdbcTemplate jdbc;

  public MarkApplicationWriteService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // NOTE: derived from the FTA_510_PRIVATE_MARK spec + create_certificate body;
  // maps the mark-application form fields onto THE.PRIVATE_MARK_CERTIFICATE. The
  // full legacy proc also writes PRIVATE_MARK_CLIENT and defaults status/dates.
  private static final String INSERT_SQL =
      """
      INSERT INTO the.private_mark_certificate (
        certificate, forest_district, p_of_c_or_legal,
        private_mark_status_code, private_mark_application_date,
        entry_userid, entry_timestamp, update_userid, update_timestamp,
        revision_count
      ) VALUES (
        :markNumber,
        (SELECT org_unit_no FROM the.org_unit WHERE org_unit_code = :orgUnit),
        :timberOrigin, 'PA', SYSDATE,
        :userId, SYSDATE, :userId, SYSDATE, 0
      )
      """;

  /**
   * Submit a new private mark application. Returns the mark (certificate) number.
   *
   * @param request the mark application to create
   * @param userId  the authenticated user id (audit columns)
   */
  @Transactional
  public String create(MarkApplicationRequest request, String userId) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("markNumber", request.markNumber())
        .addValue("orgUnit", request.orgUnit())
        .addValue("timberOrigin", request.timberOrigin())
        .addValue("userId", userId);
    jdbc.update(INSERT_SQL, params);
    return request.markNumber();
  }
}
