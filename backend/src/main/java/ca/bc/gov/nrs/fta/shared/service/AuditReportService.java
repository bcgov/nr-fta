package ca.bc.gov.nrs.fta.shared.service;

import ca.bc.gov.nrs.fta.shared.dto.AuditReportDto;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * FTA402 Private Mark Certificate report logic.
 *
 * <p>Ports the legacy Oracle package {@code THE.FTA_402_PKG} to a native query
 * against the shared {@code THE} schema. Column selection matches the package's
 * {@code rec_FTA402} record. Each filter is applied only when its bind value is
 * supplied (NVL-style), matching the legacy report behaviour.
 *
 * <p>NOTE: only the package spec ({@code FTA_402_PKG.PKS}) is present in the
 * archive — there is no {@code .PKB} body — so the SQL below is derived from the
 * record columns and the base {@code THE.*} tables the certificate is built
 * from; it is a faithful reconstruction, not a copy of the package body.
 *
 * <p>The SQL runs against the BC Gov shared Oracle ({@code THE}) via the
 * configured {@code DataSource}; there is no local database, so it is exercised
 * only in a deployed environment.
 */
@Service
public class AuditReportService {

  private final NamedParameterJdbcTemplate jdbc;

  public AuditReportService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // NOTE: SQL derived from the FTA_402_PKG.PKS spec (rec_FTA402) only — no .PKB
  // body exists — reconstructed against the THE.* timber-mark base tables.
  private static final String REPORT_SQL =
      """
      SELECT tm.timber_mark                       AS timber_mark,
             tm.mark_issue_date                   AS mark_issue_date,
             tm.mark_expiry_date                  AS mark_expiry_date,
             ftc.description                      AS file_type_desc,
             TO_CHAR(tm.granted_acquired_date, 'YYYY-MM-DD') AS granted_acqrd_date,
             cga.description                      AS crown_granted_acq_desc,
             tm.mark_amend_date                   AS mark_amend_date,
             tm.amended_userid                    AS amended_userid,
             tm.activated_userid                  AS activated_userid,
             dou.org_unit_name                    AS district,
             rou.org_unit_name                    AS region,
             cli.client_name                      AS main_licensee,
             addr.address_1                       AS address_1,
             addr.address_2                       AS address_2,
             addr.address_3                       AS address_3,
             addr.city                            AS city,
             addr.province_state_code             AS province,
             addr.country_code                    AS country,
             addr.postal_code                     AS postal_code,
             tm.place_of_carriage_or_legal        AS p_of_c_or_legal,
             tm.map_reference_id                  AS map_reference_id,
             (SELECT COUNT(*)
                FROM the.timber_mark_client tmc2
               WHERE tmc2.timber_mark = tm.timber_mark
                 AND tmc2.timber_mark_client_type_code <> 'A') AS secondary_client_count
        FROM the.timber_mark tm
        LEFT JOIN the.timber_mark_client tmc
               ON tmc.timber_mark = tm.timber_mark
              AND tmc.timber_mark_client_type_code = 'A'
        LEFT JOIN the.client cli          ON cli.client_number = tmc.client_number
        LEFT JOIN the.client_location addr ON addr.client_number = tmc.client_number
              AND addr.client_locn_code = tmc.client_locn_code
        LEFT JOIN the.file_type_code ftc  ON ftc.file_type_code = tm.file_type_code
        LEFT JOIN the.crown_granted_acquired_code cga
               ON cga.crown_granted_acquired_code = tm.crown_granted_acquired_code
        LEFT JOIN the.org_unit dou        ON dou.org_unit_no = tm.district_admin_zone
        LEFT JOIN the.org_unit rou        ON rou.org_unit_no = dou.parent_org_unit_no
       WHERE (:timberMark   IS NULL OR tm.timber_mark LIKE :timberMark || '%')
         AND (:mainLicensee IS NULL OR UPPER(cli.client_name) LIKE UPPER(:mainLicensee) || '%')
       ORDER BY tm.timber_mark
       FETCH FIRST 200 ROWS ONLY
      """;

  /**
   * FTA402 Private Mark Certificate report — mirrors {@code THE.FTA_402_PKG}.
   *
   * @param timberMark   partial timber mark (prefix match), or null
   * @param mainLicensee main licensee / client name (prefix match), or null
   */
  public List<AuditReportDto> report(String timberMark, String mainLicensee) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("timberMark", blankToNull(timberMark))
        .addValue("mainLicensee", blankToNull(mainLicensee));

    return jdbc.query(REPORT_SQL, params, (rs, rowNum) -> new AuditReportDto(
        rs.getString("timber_mark"),
        rs.getObject("mark_issue_date", java.time.LocalDate.class),
        rs.getObject("mark_expiry_date", java.time.LocalDate.class),
        rs.getString("file_type_desc"),
        rs.getString("granted_acqrd_date"),
        rs.getString("crown_granted_acq_desc"),
        rs.getObject("mark_amend_date", java.time.LocalDate.class),
        rs.getString("amended_userid"),
        rs.getString("activated_userid"),
        rs.getString("district"),
        rs.getString("region"),
        rs.getString("main_licensee"),
        rs.getString("address_1"),
        rs.getString("address_2"),
        rs.getString("address_3"),
        rs.getString("city"),
        rs.getString("province"),
        rs.getString("country"),
        rs.getString("postal_code"),
        rs.getString("p_of_c_or_legal"),
        rs.getString("map_reference_id"),
        rs.getObject("secondary_client_count", Integer.class)));
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
