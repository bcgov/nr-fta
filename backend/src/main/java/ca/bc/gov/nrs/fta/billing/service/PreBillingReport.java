package ca.bc.gov.nrs.fta.billing.service;

import ca.bc.gov.nrs.fta.billing.service.PreBillingRules.Amounts;
import ca.bc.gov.nrs.fta.billing.service.PreBillingRules.InvoiceLine;
import ca.bc.gov.nrs.fta.billing.service.PreBillingRules.Provision;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * FTA685 Pre Billing Report Submission: the FTA408 Pre-Billing Report, as a PDF.
 *
 * <p>Ports {@code FTAR408P} (what the next billing run will reverse and charge, per tenure)
 * and {@code FTAR408P_ERRORS} with {@code FTAR408_PRE_BILLING_PROBLEM} (the tenures it will
 * refuse, and why). The FTAR408_* functions' arithmetic is {@link PreBillingRules}; their reads
 * are here. Legacy queued the report on Jasper Server; it is produced on request here.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Component
public class PreBillingReport {

  private static final ZoneId ZONE = ZoneId.of("America/Vancouver");
  static final String TEMPLATE = "reports/FTA408_PreBilling.jrxml";
  static final String ERRORS_TEMPLATE = "reports/FTA408_PreBilling_Errors.jrxml";

  static final String DISTRICT_REQUIRED = "District is required.";
  static final String YEAR_INVALID = "Billing Year must be a four-digit year.";
  static final String YEAR_IN_FUTURE = "Billing Year must be less than or equal to Current Year.";
  static final String NOT_A_DISTRICT = "District is not a valid district.";

  private static final String DISTRICT_CODE_SQL =
      "SELECT org_unit_code FROM the.org_unit WHERE org_unit_no = :orgUnitNo"
          + " AND org_level_code = 'D'";

  private static final String SERVER_SQL = "SELECT SYS_CONTEXT('USERENV', 'CON_NAME') FROM dual";

  /**
   * FTAR408P's rows before its function calls: each tenure's latest instruction for the year
   * (not N or B), in the district, not billed through GBS, in a year that has range rates.
   */
  private static final String CANDIDATES_SQL =
      """
      SELECT DISTINCT a.forest_file_id, a.billing_instruction_code, a.certified_ts,
             pfu.file_type_code,
             rp.authorized_use, rp.temp_increase, rp.non_use_nonbillable, rp.non_use_billable,
             NVL(rp.non_use_agreement_no, 0) AS agreement_no,
             NVL2(rp.range_non_use_reason_code, rnuc.description, '') AS nonuse_code,
             NVL2(rp.range_increase_reason_code, ric.description, '') AS increase_code,
             GREATEST(rp.update_timestamp, b.update_timestamp) AS changed_ts
        FROM the.range_bill_inst a
        JOIN the.prov_forest_use pfu ON pfu.forest_file_id = a.forest_file_id
        JOIN the.range_tenure b      ON b.forest_file_id = a.forest_file_id
        JOIN the.range_provision rp  ON rp.forest_file_id = a.forest_file_id
                                    AND rp.calendar_year = a.calendar_year
        JOIN the.org_unit c          ON c.org_unit_no = b.admin_forest_district_no
        LEFT JOIN the.range_non_use_reason_code rnuc
               ON rnuc.range_non_use_reason_code = rp.range_non_use_reason_code
        LEFT JOIN the.range_increase_reason_code ric
               ON ric.range_increase_reason_code = rp.range_increase_reason_code
       WHERE a.calendar_year = TO_NUMBER(:year)
         AND EXISTS (SELECT 1 FROM the.range_bill_rate
                      WHERE calendar_year = TO_NUMBER(:year)
                        AND range_rate_type_code IN ('GGR', 'GUF', 'GNF', 'HGR', 'HUF', 'HNF'))
         AND a.billing_instruction_code NOT IN ('N', 'B')
         AND c.org_unit_code = :districtCode
         AND a.certified_ts = (SELECT MAX(certified_ts) FROM the.range_bill_inst
                                WHERE calendar_year = a.calendar_year
                                  AND forest_file_id = a.forest_file_id)
         AND a.forest_file_id NOT IN (SELECT forest_file_id FROM the.gnrl_invc_txn
                                       WHERE forest_file_id LIKE 'RAN%')
      """;

  /** FTAR408P_ERRORS: every latest instruction (not N or B) in the district. */
  private static final String ERROR_CANDIDATES_SQL =
      """
      SELECT DISTINCT a.forest_file_id, a.billing_instruction_code, a.certified_ts
        FROM the.range_bill_inst a
        JOIN the.range_tenure b ON b.forest_file_id = a.forest_file_id
        JOIN the.org_unit c     ON c.org_unit_no = b.admin_forest_district_no
       WHERE a.calendar_year = TO_NUMBER(:year)
         AND a.billing_instruction_code NOT IN ('N', 'B')
         AND c.rollup_dist_code = :districtCode
         AND a.certified_ts = (SELECT MAX(certified_ts) FROM the.range_bill_inst
                                WHERE calendar_year = a.calendar_year
                                  AND forest_file_id = a.forest_file_id)
       ORDER BY a.forest_file_id
      """;

  /**
   * The lines of the tenure's latest invoice, as the FTAR408_GET_* functions joined them:
   * the general invoice(s) last updated among its uncancelled ones, through a forest invoice
   * that is neither cancelled nor a cancellation.
   */
  private static final String LATEST_LINES_SQL =
      """
      SELECT a.revenue_classn_cd, a.extended_amount, a.number_of_units, v.client_name
        FROM the.gen_invoice_dtl a
        JOIN the.general_invoice b ON b.invoice_number = a.invoice_number
        JOIN the.forest_invoice c  ON c.invoice_number = b.invoice_number
        LEFT JOIN the.v_client_public v ON v.client_number = c.payable_by_client
       WHERE b.forest_file_id = :forestFileId
         AND b.update_timestamp = (SELECT MAX(update_timestamp) FROM the.general_invoice
                                    WHERE forest_file_id = b.forest_file_id
                                      AND cancellation_ind = 'N')
         AND c.invoice_rln_typ_cd != 'CAN'
         AND c.cancellation_ind = 'N'
      """;

  /** FTAR408_GET_TAX's tax code: a GST or HST line on any of the tenure's invoices. */
  private static final String TAX_CLASSES_SQL =
      """
      SELECT DISTINCT gid.revenue_classn_cd
        FROM the.gen_invoice_dtl gid
        JOIN the.general_invoice gi ON gi.invoice_number = gid.invoice_number
       WHERE gi.forest_file_id = :forestFileId
         AND gid.revenue_classn_cd IN ('HST', 'GST')
      """;

  /** The latest uncancelled general invoice (FTAR408_INVOICE_CANCELLED, step 1). */
  private static final String LATEST_INVOICE_SQL =
      """
      SELECT a.invoice_number
        FROM the.general_invoice a
       WHERE a.forest_file_id = :forestFileId
         AND a.update_timestamp = (SELECT MAX(update_timestamp) FROM the.general_invoice
                                    WHERE forest_file_id = a.forest_file_id
                                      AND cancellation_ind = 'N')
      """;

  private static final String FOREST_INVOICE_LIVE_SQL =
      """
      SELECT COUNT(*)
        FROM the.forest_invoice a
        JOIN the.general_invoice b ON b.invoice_number = a.invoice_number
       WHERE a.invoice_number = :invoiceNumber
         AND a.cancellation_ind = 'N'
         AND a.update_timestamp = (SELECT MAX(update_timestamp) FROM the.forest_invoice
                                    WHERE invoice_number = a.invoice_number)
      """;

  private static final String DETAIL_LIVE_SQL =
      """
      SELECT COUNT(*)
        FROM the.gen_invoice_dtl a
        JOIN the.general_invoice b ON b.invoice_number = a.invoice_number
       WHERE b.invoice_number = :invoiceNumber
         AND a.cancellation_ind = 'N'
      """;

  /** The 'A' and 'B' clients: their names, and the first one's type (tax exemption). */
  private static final String CLIENTS_SQL =
      """
      SELECT v.client_name, v.client_type_code
        FROM the.forest_file_client a
        JOIN the.v_client_public v ON v.client_number = a.client_number
       WHERE a.forest_file_id = :forestFileId
         AND a.forest_file_client_type_code IN ('A', 'B')
       ORDER BY a.forest_file_client_type_code
      """;

  /** get_range_rate for every file and rate type in the year: the latest by update. */
  private static final String RATES_SQL =
      """
      SELECT range_file_type_code, range_rate_type_code,
             MAX(range_rate) KEEP (DENSE_RANK FIRST ORDER BY update_timestamp DESC) AS range_rate
        FROM the.range_bill_rate
       WHERE calendar_year = TO_NUMBER(:year)
       GROUP BY range_file_type_code, range_rate_type_code
      """;

  /** FTAR408_GET_TAX: the GST rate in force today. */
  private static final String GST_TODAY_SQL =
      """
      SELECT MAX(tax_rate_pct) KEEP (DENSE_RANK FIRST ORDER BY effective_date DESC)
        FROM the.corp_tax_rate_table
       WHERE effective_date <= SYSDATE AND expiry_date >= SYSDATE AND tax_rate_cd = 'GST'
      """;

  // FTAR408_PRE_BILLING_PROBLEM's reads.
  private static final String PROBLEM_FILE_SQL =
      "SELECT file_type_code FROM the.prov_forest_use WHERE forest_file_id = :forestFileId";

  private static final String PROBLEM_PROVISION_SQL =
      """
      SELECT GREATEST(b.update_timestamp, a.update_timestamp) AS changed_ts,
             NVL(a.nonuse_forage_tonnes, 0) AS non_use
        FROM the.range_provision a
        JOIN the.range_tenure b ON b.forest_file_id = a.forest_file_id
       WHERE a.forest_file_id = :forestFileId
         AND a.calendar_year = TO_NUMBER(:year)
      """;

  private static final String PROBLEM_RATE_TYPES_SQL =
      """
      SELECT DISTINCT range_rate_type_code FROM the.range_bill_rate
       WHERE calendar_year = TO_NUMBER(:year)
      """;

  private static final String PROBLEM_GBS_SQL =
      """
      SELECT COUNT(*)
        FROM the.gnrl_invc_txn git
        JOIN the.forest_invc_txn fit ON fit.invoice_number = git.invoice_number
       WHERE git.forest_file_id = :forestFileId
         AND fit.billing_status_st <> 'DSC'
      """;

  private static final String PROBLEM_TAX_SQL =
      """
      SELECT COUNT(*) FROM the.corp_tax_rate_table
       WHERE tax_rate_cd IN ('GST', 'HST')
         AND effective_date <= SYSDATE AND expiry_date >= SYSDATE
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public PreBillingReport(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * The report PDF for a district and billing year.
   *
   * @throws ResponseStatusException 400 for a missing or invalid district or year
   */
  public byte[] render(Long orgUnitNo, String year, String userId) {
    validate(orgUnitNo, year);
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("orgUnitNo", orgUnitNo)
        .addValue("year", year);
    List<String> codes = jdbc.queryForList(DISTRICT_CODE_SQL, p, String.class);
    if (codes.isEmpty()) {
      throw bad(NOT_A_DISTRICT);
    }
    String districtCode = codes.get(0);
    p.addValue("districtCode", districtCode);
    String server = jdbc.queryForObject(SERVER_SQL, p, String.class);

    List<Map<String, ?>> rows = rows(p, year, server);
    List<Map<String, ?>> errors = errors(p, year, server);

    Map<String, Object> params = new HashMap<>();
    params.put("p_year", year);
    params.put("p_district_code", districtCode);
    params.put("p_userid", userId);
    params.put("CURRENT_SERVER", server);
    params.put("ERRORS", new JRMapCollectionDataSource(errors));
    params.put("ERRORS_SUBREPORT", JasperTemplates.get(ERRORS_TEMPLATE));
    return JasperTemplates.pdf(TEMPLATE, params, rows);
  }

  private List<Map<String, ?>> rows(MapSqlParameterSource p, String year, String server) {
    Map<String, Map<String, BigDecimal>> ratesByType = new HashMap<>();
    jdbc.query(RATES_SQL, p, rs -> {
      ratesByType
          .computeIfAbsent(rs.getString("range_file_type_code"), k -> new HashMap<>())
          .put(rs.getString("range_rate_type_code"), rs.getBigDecimal("range_rate"));
    });
    BigDecimal gstToday = jdbc.queryForObject(GST_TODAY_SQL, p, BigDecimal.class);

    List<Map<String, ?>> out = new ArrayList<>();
    for (Map<String, Object> c : jdbc.queryForList(CANDIDATES_SQL, p)) {
      String file = (String) c.get("FOREST_FILE_ID");
      String instr = (String) c.get("BILLING_INSTRUCTION_CODE");
      String fileType = (String) c.get("FILE_TYPE_CODE");
      MapSqlParameterSource fp = new MapSqlParameterSource("forestFileId", file);
      boolean reversed = PreBillingRules.REVERSED.contains(instr);
      boolean charged = PreBillingRules.CHARGED.contains(instr);
      // FTAR408_INVOICE_CANCELLED: only a reversal has an invoice to look for.
      if (reversed && latestInvoiceProblem(fp) != null) {
        continue;
      }
      List<Map<String, Object>> latest = reversed ? jdbc.queryForList(LATEST_LINES_SQL, fp)
          : List.of();
      List<Map<String, Object>> clients = jdbc.queryForList(CLIENTS_SQL, fp);

      // FTAR408_GET_CLIENT_NAME: who was invoiced for a reversal, else the A and B clients.
      String clientName = reversed
          ? latest.stream().map(r -> (String) r.get("CLIENT_NAME"))
              .filter(Objects::nonNull).findFirst().orElse(null)
          : String.join(", ", clients.stream()
              .map(r -> String.valueOf(r.get("CLIENT_NAME"))).toList());

      if (reversed) {
        List<InvoiceLine> lines = latest.stream().map(r -> new InvoiceLine(
            (String) r.get("REVENUE_CLASSN_CD"),
            (BigDecimal) r.get("EXTENDED_AMOUNT"),
            (BigDecimal) r.get("NUMBER_OF_UNITS"))).toList();
        out.add(row(c, "1", "Y", clientName, server,
            PreBillingRules.reversal(lines, taxClass(fp, lines))));
      }
      Timestamp changed = (Timestamp) c.get("CHANGED_TS");
      Timestamp certified = (Timestamp) c.get("CERTIFIED_TS");
      // FTAR408P: a charge only if neither the tenure nor its provision changed since.
      if (charged && changed != null && certified != null && !changed.after(certified)) {
        boolean exempt = !clients.isEmpty() && "B".equals(clients.get(0).get("CLIENT_TYPE_CODE"));
        Provision prov = new Provision(
            (BigDecimal) c.get("AUTHORIZED_USE"),
            (BigDecimal) c.get("TEMP_INCREASE"),
            (BigDecimal) c.get("NON_USE_NONBILLABLE"),
            (BigDecimal) c.get("NON_USE_BILLABLE"));
        out.add(row(c, "2", "N", clientName, server, PreBillingRules.charge(
            fileType, instr, prov, ratesByType.getOrDefault(fileType, Map.of()),
            exempt ? BigDecimal.ZERO : gstToday)));
      }
    }
    // FTAR408P: ORDER BY forest_file_id, sort_by.
    out.sort(Comparator.comparing((Map<String, ?> r) -> (String) r.get("forest_file_id"))
        .thenComparing(r -> (String) r.get("sort_by")));
    return out;
  }

  private static Map<String, ?> row(
      Map<String, Object> c, String sortBy, String reverse, String clientName, String server,
      Amounts a) {
    Map<String, Object> r = new HashMap<>();
    r.put("forest_file_id", c.get("FOREST_FILE_ID"));
    r.put("sort_by", sortBy);
    r.put("reverse_charge_ind", reverse);
    r.put("client_name", clientName);
    r.put("file_type_code", c.get("FILE_TYPE_CODE"));
    r.put("auth_amount", a.authAmount());
    r.put("rent", a.rent());
    r.put("fee", a.fee());
    r.put("non_use_billable_amount", a.nonUseBillableAmount());
    r.put("non_use_nonbillable", PreBillingRules.nz((BigDecimal) c.get("NON_USE_NONBILLABLE")));
    r.put("non_use_billable", PreBillingRules.nz((BigDecimal) c.get("NON_USE_BILLABLE")));
    r.put("issue_fee", a.issueFee());
    r.put("total_incl_tax", a.totalInclTax());
    r.put("temp_increase", PreBillingRules.nz((BigDecimal) c.get("TEMP_INCREASE")));
    r.put("agreement_no", c.get("AGREEMENT_NO"));
    r.put("nonuse_code", c.get("NONUSE_CODE"));
    r.put("increase_code", c.get("INCREASE_CODE"));
    r.put("current_server", server);
    return r;
  }

  /**
   * FTAR408_GET_TAX's tax line: the GST/HST code found on the tenure's invoices. Legacy took
   * whichever its cursor returned first; this prefers the one on the latest invoice.
   */
  private String taxClass(MapSqlParameterSource fp, List<InvoiceLine> latest) {
    List<String> classes = jdbc.queryForList(TAX_CLASSES_SQL, fp, String.class);
    return classes.stream()
        .filter(t -> latest.stream().anyMatch(l -> t.equals(l.revenueClass())))
        .findFirst()
        .orElse(classes.isEmpty() ? null : classes.get(0));
  }

  /**
   * FTAR408_INVOICE_CANCELLED, and the same checks in FTAR408_PRE_BILLING_PROBLEM: the
   * tenure's latest uncancelled invoice must be one invoice, live, with live lines. Returns
   * why not, or null when it is.
   */
  private String latestInvoiceProblem(MapSqlParameterSource fp) {
    List<String> latest = jdbc.queryForList(LATEST_INVOICE_SQL, fp, String.class);
    if (latest.size() != 1) {
      return "GENERAL_INVOICE lookup returned unexpected code.";
    }
    MapSqlParameterSource ip = new MapSqlParameterSource("invoiceNumber", latest.get(0));
    if (count(FOREST_INVOICE_LIVE_SQL, ip) == 0) {
      return "FOREST_INVOICE lookup returned unexpected code.";
    }
    if (count(DETAIL_LIVE_SQL, ip) == 0) {
      return "GEN_INVOICE_DTL lookup returned unexpected code.";
    }
    return null;
  }

  /** FTAR408P_ERRORS: the tenures the billing run will refuse, with the first reason. */
  private List<Map<String, ?>> errors(MapSqlParameterSource p, String year, String server) {
    List<String> rateTypes = jdbc.queryForList(PROBLEM_RATE_TYPES_SQL, p, String.class);
    boolean taxInForce = count(PROBLEM_TAX_SQL, p) > 0;
    List<Map<String, ?>> out = new ArrayList<>();
    for (Map<String, Object> c : jdbc.queryForList(ERROR_CANDIDATES_SQL, p)) {
      String file = (String) c.get("FOREST_FILE_ID");
      String problem = problem(file, (String) c.get("BILLING_INSTRUCTION_CODE"),
          (Timestamp) c.get("CERTIFIED_TS"), year, rateTypes, taxInForce);
      // A tenure with no problem is not listed (the subreport counted only these).
      if (problem != null) {
        Map<String, Object> r = new HashMap<>();
        r.put("calendar_year", year);
        r.put("forest_file_id", file);
        r.put("billing_instruc_code", problem);
        r.put("certified_ts", c.get("CERTIFIED_TS"));
        r.put("current_server", server);
        out.add(r);
      }
    }
    return out;
  }

  /**
   * FTAR408_PRE_BILLING_PROBLEM: the first reason the tenure cannot be billed, or null.
   *
   * <p>Legacy also noted a missing client or district account, but without stopping, and
   * then returned nothing unless a later check failed, so those never showed. They are left
   * out here for the same result.
   */
  private String problem(String file, String instr, Timestamp certified, String year,
      List<String> rateTypes, boolean taxInForce) {
    MapSqlParameterSource fp = new MapSqlParameterSource()
        .addValue("forestFileId", file)
        .addValue("year", year);
    List<String> types = jdbc.queryForList(PROBLEM_FILE_SQL, fp, String.class);
    if (types.isEmpty()) {
      return "FTA Forest File ID not found.";
    }
    boolean hay = List.of("H01", "H02", "H03").contains(types.get(0));
    List<Map<String, Object>> prov = jdbc.queryForList(PROBLEM_PROVISION_SQL, fp);
    if (prov.size() != 1) {
      return hay
          ? (prov.isEmpty() ? "Hay Cutting Tenure information not found for billing year."
              : "Hay Cutting Tenure information lookup returned unexpected error.")
          : (prov.isEmpty() ? "Grazing AUM information not found for billing year."
              : "Grazing AUM information lookup returned unexpected error.");
    }
    Timestamp changed = (Timestamp) prov.get(0).get("CHANGED_TS");
    if (changed != null && certified != null && changed.after(certified)) {
      return "Tenure updated after instruction issued, cannot process.";
    }
    boolean nonUse = PreBillingRules.nz((BigDecimal) prov.get(0).get("NON_USE")).signum() > 0;
    String rateProblem = hay
        ? missingRate(rateTypes, nonUse, "HGR", "Hay Cutting Ground Rent rate",
            "HUF", "Hay Cutting Rate Usage Fee", "HNF", "Hay Cutting Rate Non-Usage Fee")
        : missingRate(rateTypes, nonUse, "GGR", "Grazing Ground Rent rate",
            "GUF", "Grazing Usage Fee", "GNF", "Grazing Rate Non-Usage Fee");
    if (rateProblem != null) {
      return rateProblem;
    }
    if (count(PROBLEM_GBS_SQL, fp) > 0) {
      return "The tenure is being processed by GBS.";
    }
    if (PreBillingRules.REVERSED.contains(instr)) {
      String invoiceProblem = latestInvoiceProblem(fp);
      if (invoiceProblem != null) {
        return invoiceProblem;
      }
    }
    if (!taxInForce) {
      return "Calculate tax routine returned unexpected code.";
    }
    return null;
  }

  static String missingRate(List<String> rateTypes, boolean nonUse,
      String ground, String groundName, String usage, String usageName,
      String nonUseType, String nonUseName) {
    if (!rateTypes.contains(ground)) {
      return groundName + " not found for billing year.";
    }
    if (!rateTypes.contains(usage)) {
      return usageName + " not found for billing year.";
    }
    if (nonUse && !rateTypes.contains(nonUseType)) {
      return nonUseName + " not found for billing year.";
    }
    return null;
  }

  static void validate(Long orgUnitNo, String year) {
    List<String> e = new ArrayList<>();
    if (orgUnitNo == null) {
      e.add(DISTRICT_REQUIRED);
    }
    if (year == null || !year.matches("\\d{4}")) {
      e.add(YEAR_INVALID);
    } else if (Integer.parseInt(year) > Year.now(ZONE).getValue()) {
      e.add(YEAR_IN_FUTURE);
    }
    if (!e.isEmpty()) {
      throw bad(String.join(" ", e));
    }
  }

  private long count(String sql, MapSqlParameterSource p) {
    Long n = jdbc.queryForObject(sql, p, Long.class);
    return n == null ? 0 : n;
  }

  private static ResponseStatusException bad(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
