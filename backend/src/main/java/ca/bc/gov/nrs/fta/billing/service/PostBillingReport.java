package ca.bc.gov.nrs.fta.billing.service;

import java.sql.Types;
import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * FTA686 Post Billing Report Submission: the FTA409 Post-Billing Report, as a PDF — every
 * range invoice (RBS, and manual RNG) for a billing year, for one district or all of them.
 *
 * <p>Ports {@code FTAR409P}; its FTA_GET_INVOICE_DTL / FTA_GET_RANGE_FEE /
 * FTA_GET_RANGE_NONUSE / SIL_GET_ORG_DESC calls are scalar subqueries and joins here. Sorted
 * by client name it uses the FTA409-PostBilling layout (client subtotals), otherwise
 * FTA409-PostBillingNonClient, as legacy chose.
 *
 * <p>Legacy also filtered by region, but its Org Unit list offered only districts and "*",
 * so that branch could not be reached; it is left out. An invoice with no rent line, or a
 * client location with no city or province, prints as 0 / blank here; the legacy layouts'
 * expressions would have failed on the null.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it
 * is exercised only in a deployed environment.
 */
@Component
public class PostBillingReport {

  private static final ZoneId ZONE = ZoneId.of("America/Vancouver");
  static final String BY_CLIENT_TEMPLATE = "reports/FTA409_PostBilling.jrxml";
  static final String OTHER_TEMPLATE = "reports/FTA409_PostBillingNonClient.jrxml";

  /** "*" in the Org Unit list: every district. */
  public static final String ALL = "*";

  static final String ORG_REQUIRED = "Org Unit is required.";
  static final String YEAR_INVALID = "Calendar Year must be a four-digit year.";
  static final String YEAR_IN_FUTURE = "Calendar Year must be less than or equal to Current Year.";
  static final String SORT_INVALID = "Sort By must be 1, 2, 3 or 4.";
  static final String NOT_A_DISTRICT = "Org Unit is not a valid district.";

  /** FTAR409P's ORDER BY per Sort By (1 client, 2 file, 3 file type, 4 invoice). */
  private static final Map<String, String> ORDER = Map.of(
      "1", "orign_org_unit_no, admin_org_unit_no, client_name, payable_by_client, forest_file_id",
      "2", "orign_org_unit_no, admin_org_unit_no, forest_file_id, file_type_code, invoice_number",
      "3", "orign_org_unit_no, admin_org_unit_no, file_type_code, forest_file_id, invoice_number",
      "4", "orign_org_unit_no, admin_org_unit_no, invoice_number, forest_file_id, file_type_code");

  /** FTA_GET_INVOICE_DTL: the one line of a revenue class, else 0. */
  private static String detail(String revenueClass) {
    return """
        (SELECT CASE WHEN COUNT(*) = 1 THEN NVL(MAX(d.extended_amount), 0) ELSE 0 END
           FROM the.gen_invoice_dtl d
          WHERE d.invoice_number = b.invoice_number AND d.revenue_classn_cd = '%s')"""
        .formatted(revenueClass);
  }

  private static final String SQL =
      """
      SELECT * FROM (
        SELECT DISTINCT
               a.invoice_number,
               a.payable_by_client,
               a.payable_by_cli_loc,
               a.invoice_amount,
               a.invoice_date,
               a.invc_sub_type_code,
               DECODE(a.invc_sub_type_code, 'RNG', 'M', a.invoice_rln_typ_cd) AS invoice_rln_typ_cd,
               a.posted_date,
               b.forest_file_id,
               b.admin_org_unit_no,
               o.org_unit_code AS admin_org_unit_code,
               b.orign_org_unit_no,
               o.org_unit_name AS admin_org_unit_name,
               orig.org_unit_name AS orign_org_unit_name,
               c.file_type_code,
               e.legal_first_name,
               e.client_name,
               %s + %s AS tax,
               %s AS issue_fee,
               (SELECT NVL(SUM(d.extended_amount), 0) FROM the.gen_invoice_dtl d
                 WHERE d.invoice_number = b.invoice_number
                   AND d.revenue_classn_cd IN ('RLU', 'RPU', 'RNU', 'RHU', 'RHF', 'RNF')) AS fee,
               (SELECT NVL(SUM(d.extended_amount), 0) FROM the.gen_invoice_dtl d
                 WHERE d.invoice_number = b.invoice_number
                   AND d.revenue_classn_cd IN ('RGN', 'RHN')) AS nonuse,
               NVL(rent.extended_amount, 0) AS rent,
               DECODE(a.invoice_rln_typ_cd, 'C', rent.number_of_units * -1,
                      rent.number_of_units) AS auth_amt,
               f.hdbs_company_code,
               f.address_1,
               f.address_2,
               f.address_3,
               NVL(f.city, ' ') AS city,
               NVL(f.province, ' ') AS province,
               f.postal_code
          FROM the.forest_invoice a
          JOIN the.general_invoice b    ON b.invoice_number = a.invoice_number
          JOIN the.prov_forest_use c    ON c.forest_file_id = b.forest_file_id
          JOIN the.forest_file_client d ON d.client_number = a.payable_by_client
                                       AND d.client_locn_code = a.payable_by_cli_loc
          JOIN the.org_unit o           ON o.org_unit_no = b.admin_org_unit_no
          LEFT JOIN the.org_unit orig   ON orig.org_unit_no = b.orign_org_unit_no
          JOIN the.v_client_public e    ON e.client_number = a.payable_by_client
          JOIN the.client_location f    ON f.client_number = d.client_number
                                       AND f.client_locn_code = d.client_locn_code
          LEFT JOIN (SELECT invoice_number, extended_amount, number_of_units
                       FROM the.gen_invoice_dtl
                      WHERE revenue_classn_cd IN ('RGL', 'RGP', 'RNG', 'RHL', 'RHP', 'RNH')) rent
                 ON rent.invoice_number = b.invoice_number
         WHERE b.billing_year = TO_NUMBER(:year)
           AND (:orgUnitNo IS NULL OR b.admin_org_unit_no = :orgUnitNo)
           AND a.invc_sub_type_code IN ('RBS', 'RNG')
      )
      """.formatted(detail("GST"), detail("HST"), detail("RLF"));

  private static final String DISTRICT_SQL =
      "SELECT org_unit_code FROM the.org_unit WHERE org_unit_no = :orgUnitNo"
          + " AND org_level_code = 'D'";

  private static final String SERVER_SQL = "SELECT SYS_CONTEXT('USERENV', 'CON_NAME') FROM dual";

  private final NamedParameterJdbcTemplate jdbc;

  public PostBillingReport(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * The report PDF.
   *
   * @param orgUnit a district's ORG_UNIT_NO, or {@link #ALL}
   * @param sortBy  1 client name, 2 tenure, 3 file type, 4 invoice number
   * @throws ResponseStatusException 400 for missing or invalid criteria
   */
  public byte[] render(String orgUnit, String year, String sortBy, String userId) {
    validate(orgUnit, year, sortBy);
    boolean all = ALL.equals(orgUnit);
    Long orgUnitNo = all ? null : Long.valueOf(orgUnit);
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("orgUnitNo", orgUnitNo, Types.NUMERIC)
        .addValue("year", year);
    String orgCode = ALL;
    if (!all) {
      List<String> codes = jdbc.queryForList(DISTRICT_SQL, p, String.class);
      if (codes.isEmpty()) {
        throw bad(NOT_A_DISTRICT);
      }
      orgCode = codes.get(0);
    }
    String server = jdbc.queryForObject(SERVER_SQL, p, String.class);

    List<Map<String, ?>> rows = new ArrayList<>();
    jdbc.query(SQL + " ORDER BY " + ORDER.get(sortBy), p, rs -> {
      Map<String, Object> r = new HashMap<>();
      for (String f : List.of("invoice_number", "payable_by_client", "payable_by_cli_loc",
          "invc_sub_type_code", "invoice_rln_typ_cd", "forest_file_id", "admin_org_unit_code",
          "admin_org_unit_name", "orign_org_unit_name", "file_type_code", "legal_first_name",
          "client_name", "hdbs_company_code", "address_1", "address_2", "address_3", "city",
          "province", "postal_code")) {
        r.put(f, rs.getString(f));
      }
      for (String f : List.of("invoice_amount", "tax", "issue_fee", "fee", "nonuse", "rent",
          "auth_amt")) {
        r.put(f, rs.getBigDecimal(f));
      }
      r.put("invoice_date", rs.getDate("invoice_date"));
      r.put("posted_date", rs.getDate("posted_date"));
      r.put("admin_org_unit_no", rs.getObject("admin_org_unit_no", Integer.class));
      r.put("orign_org_unit_no", rs.getObject("orign_org_unit_no", Integer.class));
      r.put("current_server", server);
      rows.add(r);
    });

    Map<String, Object> params = new HashMap<>();
    params.put("p_year", year);
    params.put("p_org_unit_no", all ? "" : orgUnit);
    params.put("p_org_unit_code", orgCode);
    // "D" for a district; the layouts read anything else as "by Province".
    params.put("p_org_level", all ? "" : "D");
    params.put("p_sort_ind", sortBy);
    params.put("p_userid", userId);
    return JasperTemplates.pdf(
        "1".equals(sortBy) ? BY_CLIENT_TEMPLATE : OTHER_TEMPLATE, params, rows);
  }

  static void validate(String orgUnit, String year, String sortBy) {
    List<String> e = new ArrayList<>();
    if (orgUnit == null || orgUnit.isBlank()) {
      e.add(ORG_REQUIRED);
    } else if (!ALL.equals(orgUnit) && !orgUnit.matches("\\d+")) {
      e.add(NOT_A_DISTRICT);
    }
    if (year == null || !year.matches("\\d{4}")) {
      e.add(YEAR_INVALID);
    } else if (Integer.parseInt(year) > Year.now(ZONE).getValue()) {
      e.add(YEAR_IN_FUTURE);
    }
    if (sortBy == null || !ORDER.containsKey(sortBy)) {
      e.add(SORT_INVALID);
    }
    if (!e.isEmpty()) {
      throw bad(String.join(" ", e));
    }
  }

  private static ResponseStatusException bad(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
