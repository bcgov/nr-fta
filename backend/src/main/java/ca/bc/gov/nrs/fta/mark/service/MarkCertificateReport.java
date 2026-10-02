package ca.bc.gov.nrs.fta.mark.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * FTA402 — the Registered Timber Mark Certificate, as a PDF: legacy FTA510's "Print".
 *
 * <p>The layout is the legacy Jasper report ({@code reports/FTA402_PrivateMark.jrxml}; see its
 * header for what changed). Its rows came from the {@code FTAR402P} and
 * {@code FTAR402P_Client} procedures; their SELECTs are ported here, so the report needs only
 * read access, and the dates are formatted as the procedure did ("10th day of April, 2021.").
 *
 * <p>A certificate exists only once the mark is issued: it reads the mark's forest file and
 * its main ('A') client, as FTAR402P's inner joins do.
 */
@Component
public class MarkCertificateReport {

  /** FTAR402P's cursor, by certificate. */
  private static final String CERTIFICATE_SQL =
      """
      SELECT pmc.timber_mark                                AS timber_mark,
             pmc.private_mark_issue_date                    AS issue_date,
             NVL(pmc.private_mark_extend_date, pmc.private_mark_expiry_date) AS expiry_date,
             pmc.private_mark_amend_date                    AS amend_date,
             ft.description                                 AS description,
             NVL(TO_CHAR(pmc.granted_acqrd_date, 'YYYY-MM-DD'), pmc.crown_granted_acq_desc)
                                                            AS granted_acqrd_date,
             pmc.crown_granted_acq_desc                     AS crown_granted_acq_desc,
             org.org_unit_name                              AS district,
             reg.org_unit_name                              AS region,
             -- Sil_Get_Client_Name_Unformat: the name as written, given names first.
             TRIM(REGEXP_REPLACE(cl.legal_first_name || ' ' || cl.legal_middle_name || ' '
                                 || cl.client_name, ' +', ' '))  AS main_licensee,
             loc.address_1                                  AS address_1,
             loc.address_2                                  AS address_2,
             loc.address_3                                  AS address_3,
             DECODE(TRIM(loc.city), NULL, '', loc.city || ',  ')
               || DECODE(loc.province, NULL, '', loc.province || ',  ')
               || DECODE(loc.country, NULL, '', loc.country || '  ')
               || loc.postal_code                           AS city,
             loc.province                                   AS province,
             loc.country                                    AS country,
             loc.postal_code                                AS postal_code,
             SUBSTR(pmc.p_of_c_or_legal, 1, 4000)           AS p_of_c_or_legal,
             pmc.map_reference_id                           AS map_reference_id,
             (SELECT COUNT(*) FROM the.forest_file_client b
               WHERE b.forest_file_id = pmc.forest_file_id
                 AND b.forest_file_client_type_code = 'B')  AS secondary_client_count
        FROM the.private_mark_certificate pmc
        JOIN the.prov_forest_use pfu   ON pfu.forest_file_id = pmc.forest_file_id
        JOIN the.file_type_code ft     ON ft.file_type_code = pfu.file_type_code
        JOIN the.forest_file_client fcl
             ON fcl.forest_file_id = pmc.forest_file_id
            AND fcl.forest_file_client_type_code = 'A'
        LEFT JOIN the.forest_client cl ON cl.client_number = fcl.client_number
        LEFT JOIN the.client_location loc
             ON loc.client_number = fcl.client_number
            AND loc.client_locn_code = fcl.client_locn_code
        JOIN the.org_unit org          ON org.org_unit_no = pmc.forest_district
        JOIN the.org_unit reg          ON reg.org_unit_no = org.rollup_region_no
       WHERE pmc.certificate = :certificate
       FETCH FIRST 1 ROW ONLY
      """;

  /** FTAR402P_Client's cursor: the secondary ('B') licensees. */
  private static final String CLIENTS_SQL =
      """
      SELECT pmc.timber_mark AS timber_mark,
             TRIM(REGEXP_REPLACE(cl.legal_first_name || ' ' || cl.legal_middle_name || ' '
                                 || cl.client_name, ' +', ' ')) AS secondary_licensee
        FROM the.private_mark_certificate pmc
        JOIN the.forest_file_client fcl
             ON fcl.forest_file_id = pmc.forest_file_id
            AND fcl.forest_file_client_type_code = 'B'
        LEFT JOIN the.forest_client cl ON cl.client_number = fcl.client_number
       WHERE pmc.certificate = :certificate
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private volatile JasperReport main;
  private volatile JasperReport clients;

  public MarkCertificateReport(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * The certificate PDF, or null when the mark has no certificate to print yet (not issued,
   * or no main client).
   */
  public byte[] render(String certificate) {
    MapSqlParameterSource p = new MapSqlParameterSource("certificate", certificate);
    List<Map<String, ?>> rows = jdbc.query(CERTIFICATE_SQL, p, (rs, n) -> {
      Map<String, Object> row = new HashMap<>();
      row.put("timber_mark", rs.getString("timber_mark"));
      row.put("mark_issue_date", longDate(rs.getObject("issue_date", LocalDate.class)));
      row.put("mark_expiry_date", longDate(rs.getObject("expiry_date", LocalDate.class)));
      row.put("mark_amend_date", longDate(rs.getObject("amend_date", LocalDate.class)));
      for (String f : List.of("description", "granted_acqrd_date", "crown_granted_acq_desc",
          "district", "region", "main_licensee", "address_1", "address_2", "address_3", "city",
          "province", "country", "postal_code", "p_of_c_or_legal", "map_reference_id",
          "secondary_client_count")) {
        row.put(f, rs.getString(f));
      }
      return row;
    });
    if (rows.isEmpty()) {
      return null;
    }
    List<Map<String, ?>> secondary = jdbc.query(CLIENTS_SQL, p, (rs, n) -> Map.of(
        "TIMBER_MARK", String.valueOf(rs.getString("timber_mark")),
        "SECONDARY_LICENSEE", String.valueOf(rs.getString("secondary_licensee"))));
    return fill(certificate, rows, secondary);
  }

  /** Fills the compiled report with the rows and exports it. Package-visible for tests. */
  byte[] fill(String certificate, List<Map<String, ?>> rows, List<Map<String, ?>> secondary) {
    try {
      Map<String, Object> params = new HashMap<>();
      params.put("p_certificate", certificate);
      params.put("CLIENTS", new JRMapCollectionDataSource(secondary));
      params.put("CLIENT_SUBREPORT", clientsReport());
      JasperPrint print =
          JasperFillManager.fillReport(mainReport(), params, new JRMapCollectionDataSource(rows));
      return JasperExportManager.exportReportToPdf(print);
    } catch (JRException e) {
      throw new IllegalStateException("Could not produce the FTA402 certificate.", e);
    }
  }

  private JasperReport mainReport() {
    if (main == null) {
      main = compile("reports/FTA402_PrivateMark.jrxml");
    }
    return main;
  }

  private JasperReport clientsReport() {
    if (clients == null) {
      clients = compile("reports/FTA402_PrivateMark_Client.jrxml");
    }
    return clients;
  }

  private static JasperReport compile(String path) {
    try (InputStream in = new ClassPathResource(path).getInputStream()) {
      return JasperCompileManager.compileReport(in);
    } catch (IOException | JRException e) {
      throw new IllegalStateException("Could not compile " + path, e);
    }
  }

  /** Oracle's {@code 'fm ddth "day of" Month, yyyy.'}: "10th day of April, 2021." */
  static String longDate(LocalDate d) {
    if (d == null) {
      return null;
    }
    int day = d.getDayOfMonth();
    String suffix = (day % 100 >= 11 && day % 100 <= 13) ? "th"
        : switch (day % 10) {
          case 1 -> "st";
          case 2 -> "nd";
          case 3 -> "rd";
          default -> "th";
        };
    return day + suffix + " day of "
        + d.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + ", " + d.getYear() + ".";
  }
}
