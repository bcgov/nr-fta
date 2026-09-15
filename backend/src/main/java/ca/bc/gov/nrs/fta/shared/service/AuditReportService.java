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

  // SQL derived from the FTA_402_PKG.PKS spec (rec_FTA402) — no .PKB body
  // exists, so the joins are reconstructed from the base tables.
  //
  // The driving table is PRIVATE_MARK_CERTIFICATE, not TIMBER_MARK: FTA402 is
  // the Private Mark Certificate report, and every column the record declares
  // that TIMBER_MARK lacks — P_OF_C_OR_LEGAL, MAP_REFERENCE_ID — is on the
  // certificate, along with CROWN_GRANTED_ACQ_DESC and GRANTED_ACQRD_DATE.
  // Clients hang off the certificate too (PRIVATE_MARK_CLIENT keyed by
  // CERTIFICATE), not off the timber mark.
  //
  // CROWN_GRANTED_ACQ_DESC is a description held directly on the certificate,
  // so there is no code table to join — the record's matching field is filled
  // from that column.
  //
  // The district/region rollup uses ORG_UNIT.ROLLUP_REGION_NO, which is how
  // FTA_001_TENR_SRCH resolves a region; ORG_UNIT has no parent-org column.
  private static final String REPORT_SQL =
      """
      SELECT pmc.timber_mark                      AS timber_mark,
             pmc.private_mark_issue_date          AS mark_issue_date,
             pmc.private_mark_expiry_date         AS mark_expiry_date,
             ftc.description                      AS file_type_desc,
             TO_CHAR(pmc.granted_acqrd_date, 'YYYY-MM-DD') AS granted_acqrd_date,
             pmc.crown_granted_acq_desc           AS crown_granted_acq_desc,
             pmc.private_mark_amend_date          AS mark_amend_date,
             pmc.private_mark_amended_userid      AS amended_userid,
             pmc.private_mark_activated_userid    AS activated_userid,
             dou.org_unit_name                    AS district,
             rou.org_unit_name                    AS region,
             cli.client_name                      AS main_licensee,
             addr.address_1                       AS address_1,
             addr.address_2                       AS address_2,
             addr.address_3                       AS address_3,
             addr.city                            AS city,
             addr.province                        AS province,
             addr.country                         AS country,
             addr.postal_code                     AS postal_code,
             pmc.p_of_c_or_legal                  AS p_of_c_or_legal,
             pmc.map_reference_id                 AS map_reference_id,
             (SELECT COUNT(*)
                FROM the.private_mark_client pmc2
               WHERE pmc2.certificate = pmc.certificate
                 AND pmc2.private_mark_client_type_code <> 'A') AS secondary_client_count
        FROM the.private_mark_certificate pmc
        LEFT JOIN the.private_mark_client pmcl
               ON pmcl.certificate = pmc.certificate
              AND pmcl.private_mark_client_type_code = 'A'
        LEFT JOIN the.forest_client cli   ON cli.client_number = pmcl.client_number
        LEFT JOIN the.client_location addr
               ON addr.client_number = pmcl.client_number
              AND addr.client_locn_code = pmcl.client_locn_code
        LEFT JOIN the.prov_forest_use pfu ON pfu.forest_file_id = pmc.forest_file_id
        LEFT JOIN the.file_type_code ftc  ON ftc.file_type_code = pfu.file_type_code
        LEFT JOIN the.org_unit dou        ON dou.org_unit_no = pmc.forest_district
        LEFT JOIN the.org_unit rou        ON rou.org_unit_no = dou.rollup_region_no
       WHERE (:timberMark   IS NULL OR pmc.timber_mark LIKE :timberMark || '%')
         AND (:mainLicensee IS NULL OR UPPER(cli.client_name) LIKE UPPER(:mainLicensee) || '%')
       ORDER BY pmc.timber_mark
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
