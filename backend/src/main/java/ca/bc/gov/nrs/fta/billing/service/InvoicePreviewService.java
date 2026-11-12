package ca.bc.gov.nrs.fta.billing.service;

import ca.bc.gov.nrs.fta.billing.dto.InvoicePreviewDto;
import ca.bc.gov.nrs.fta.billing.service.InvoicePreviewRules.Invoice;
import ca.bc.gov.nrs.fta.billing.service.InvoicePreviewRules.Provision;
import java.math.BigDecimal;
import java.time.Year;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * FTA695 Invoice Preview, ported from {@code FTA_695_INVOICEPREVIEW.GET}: the
 * range invoice a grazing or hay tenure would get for a year, from its range
 * provision and that year's rates. Read-only.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local
 * database, so it is exercised only in a deployed environment.
 */
@Service
public class InvoicePreviewService {

  private static final ZoneId ZONE = ZoneId.of("America/Vancouver");

  static final String FILE_REQUIRED = "Tenure Number is required.";
  static final String YEAR_INVALID = "Year must be a four-digit year.";
  static final String FILE_INVALID = "Invalid Tenure Number.";
  static final String NOT_RANGE = "Tenure must be a Range tenure.";
  static final String BAD_STATUS = "File status must be A or S or AS.";
  static final String NO_CLIENT = "Client name not found.";
  static final String NO_PROVISION = "Range provision record not found.";

  private static final String FILE_EXISTS_SQL =
      "SELECT COUNT(*) FROM the.prov_forest_use WHERE forest_file_id = :forestFileId";

  private static final String TENURE_SQL =
      """
      SELECT f.file_type_code,
             f.file_status_st,
             rftc.description AS file_type_description,
             reg.org_unit_code AS region_code,
             d.org_unit_code   AS district_code
        FROM the.prov_forest_use f
        JOIN the.range_tenure t           ON t.forest_file_id = f.forest_file_id
        JOIN the.tenure_term tt           ON tt.forest_file_id = f.forest_file_id
        JOIN the.range_file_type_code rftc ON rftc.range_file_type_code = f.file_type_code
        JOIN the.org_unit r               ON r.org_unit_no = f.forest_region
        JOIN the.org_unit d               ON d.org_unit_no = t.admin_forest_district_no
        LEFT JOIN the.org_unit reg        ON reg.org_unit_no = d.rollup_region_no
       WHERE f.forest_file_id = :forestFileId
      """;

  /** The 'A' (main) then 'B' clients, with the main client's address. */
  private static final String CLIENTS_SQL =
      """
      SELECT l.forest_file_client_type_code AS client_type,
             c.client_name, c.client_type_code,
             cl.address_1, cl.address_2, cl.address_3,
             cl.city, cl.province, cl.postal_code
        FROM the.forest_file_client l
        JOIN the.v_client_public c ON c.client_number = l.client_number
        LEFT JOIN the.client_location cl
               ON cl.client_number = l.client_number
              AND cl.client_locn_code = l.client_locn_code
       WHERE l.forest_file_id = :forestFileId
         AND l.forest_file_client_type_code IN ('A', 'B')
       ORDER BY l.forest_file_client_type_code
      """;

  private static final String PROVISION_SQL =
      """
      SELECT NVL(authorized_use, 0)      AS authorized_use,
             NVL(temp_increase, 0)       AS temp_increase,
             NVL(non_use_nonbillable, 0) AS non_use_nonbillable,
             NVL(non_use_billable, 0)    AS non_use_billable,
             NVL(total_annual_use, 0)    AS total_annual_use
        FROM the.range_provision
       WHERE calendar_year = TO_NUMBER(:year)
         AND forest_file_id = :forestFileId
      """;

  /** get_range_rate: the most recently updated rate per rate type. */
  private static final String RATES_SQL =
      """
      SELECT range_rate_type_code,
             MAX(range_rate) KEEP (DENSE_RANK FIRST ORDER BY update_timestamp DESC) AS range_rate
        FROM the.range_bill_rate
       WHERE calendar_year = TO_NUMBER(:year)
         AND range_file_type_code = :fileTypeCode
       GROUP BY range_rate_type_code
      """;

  /** The GST/HST rate in force in the year (by year, as legacy compared them). */
  private static final String TAX_SQL =
      """
      SELECT MAX(tax_rate_pct) KEEP (DENSE_RANK FIRST ORDER BY TO_CHAR(expiry_date, 'YYYY') DESC)
        FROM the.corp_tax_rate_table
       WHERE tax_rate_cd IN ('GST', 'HST')
         AND TO_CHAR(effective_date, 'YYYY') <= :year
         AND TO_CHAR(expiry_date, 'YYYY') >= :year
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public InvoicePreviewService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * The Go button. A blank year means the current one, as legacy defaulted it.
   *
   * @throws ResponseStatusException 400 for a missing or invalid tenure, or one
   *     that cannot be invoiced (not range, wrong status, no client, no provision)
   */
  public InvoicePreviewDto preview(String forestFileId, String calendarYear) {
    String file = forestFileId == null ? "" : forestFileId.trim().toUpperCase();
    if (file.isEmpty()) {
      throw bad(FILE_REQUIRED);
    }
    String year = calendarYear == null || calendarYear.isBlank()
        ? String.valueOf(Year.now(ZONE).getValue())
        : calendarYear.trim();
    if (!year.matches("\\d{4}")) {
      throw bad(YEAR_INVALID);
    }
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("forestFileId", file)
        .addValue("year", year);

    Long exists = jdbc.queryForObject(FILE_EXISTS_SQL, p, Long.class);
    if (exists == null || exists == 0) {
      throw bad(FILE_INVALID);
    }
    List<Map<String, Object>> tenures = jdbc.queryForList(TENURE_SQL, p);
    if (tenures.isEmpty()) {
      throw bad(NOT_RANGE);
    }
    Map<String, Object> t = tenures.get(0);
    String fileTypeCode = (String) t.get("FILE_TYPE_CODE");
    if (!List.of("A", "S", "AS").contains(String.valueOf(t.get("FILE_STATUS_ST")))) {
      throw bad(BAD_STATUS);
    }

    List<Map<String, Object>> clients = jdbc.queryForList(CLIENTS_SQL, p);
    if (clients.isEmpty()) {
      throw bad(NO_CLIENT);
    }
    String names = String.join(";", clients.stream()
        .map(c -> String.valueOf(c.get("CLIENT_NAME"))).toList());
    Map<String, Object> main = clients.stream()
        .filter(c -> "A".equals(c.get("CLIENT_TYPE")))
        .findFirst().orElse(Map.of());
    boolean taxExempt = "B".equals(main.get("CLIENT_TYPE_CODE"));

    List<Provision> provisions = jdbc.query(PROVISION_SQL, p, (rs, n) -> new Provision(
        rs.getBigDecimal("authorized_use"),
        rs.getBigDecimal("temp_increase"),
        rs.getBigDecimal("non_use_nonbillable"),
        rs.getBigDecimal("non_use_billable"),
        rs.getBigDecimal("total_annual_use")));
    if (provisions.isEmpty()) {
      throw bad(NO_PROVISION);
    }

    Map<String, BigDecimal> rates = new HashMap<>();
    jdbc.query(RATES_SQL, p.addValue("fileTypeCode", fileTypeCode), rs -> {
      rates.put(rs.getString("range_rate_type_code"), rs.getBigDecimal("range_rate"));
    });
    BigDecimal taxRate = taxExempt
        ? BigDecimal.ZERO
        : jdbc.queryForObject(TAX_SQL, p, BigDecimal.class);

    String description = (String) t.get("FILE_TYPE_DESCRIPTION");
    Invoice inv = InvoicePreviewRules.compute(
        fileTypeCode, description, rates, provisions.get(0), taxRate);
    return new InvoicePreviewDto(
        file,
        year,
        description,
        (String) t.get("REGION_CODE"),
        (String) t.get("DISTRICT_CODE"),
        names,
        (String) main.get("ADDRESS_1"),
        (String) main.get("ADDRESS_2"),
        (String) main.get("ADDRESS_3"),
        (String) main.get("CITY"),
        (String) main.get("PROVINCE"),
        (String) main.get("POSTAL_CODE"),
        inv.lines(),
        inv.subtotal(),
        inv.taxRate(),
        inv.taxAmount(),
        inv.total());
  }

  private static ResponseStatusException bad(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
