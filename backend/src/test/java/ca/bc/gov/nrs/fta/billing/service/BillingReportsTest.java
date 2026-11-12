package ca.bc.gov.nrs.fta.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The ported FTA408 and FTA409 layouts compile and render. Worth pinning because the JRXML
 * is legacy layout run on a different Jasper version, and a failure only shows when someone
 * submits the report.
 */
@DisplayName("Unit Test | Range billing reports")
class BillingReportsTest {

  private static BigDecimal d(String s) {
    return new BigDecimal(s);
  }

  private static String text(byte[] pdf) throws Exception {
    PdfReader reader = new PdfReader(pdf);
    StringBuilder sb = new StringBuilder();
    for (int i = 1; i <= reader.getNumberOfPages(); i++) {
      sb.append(new PdfTextExtractor(reader).getTextFromPage(i)).append('\n');
    }
    return sb.toString();
  }

  private static Map<String, Object> preBillingRow(String sortBy, String reverse, Object rent) {
    Map<String, Object> r = new HashMap<>();
    r.put("forest_file_id", "RAN076543");
    r.put("sort_by", sortBy);
    r.put("reverse_charge_ind", reverse);
    r.put("client_name", "WILLOWFIELD RANCH LTD.");
    r.put("file_type_code", "E01");
    r.put("auth_amount", d("120"));
    r.put("rent", rent);
    r.put("fee", d("150.00"));
    r.put("non_use_billable_amount", d("4.00"));
    r.put("non_use_nonbillable", d("3"));
    r.put("non_use_billable", d("10"));
    r.put("issue_fee", d("25.00"));
    r.put("total_incl_tax", "N".equals(reverse) ? d("5") : d("-9.55"));
    r.put("temp_increase", d("0"));
    r.put("agreement_no", d("0"));
    r.put("current_server", "FTATEST");
    return r;
  }

  private static Map<String, Object> preBillingParams(List<Map<String, ?>> errors) {
    Map<String, Object> params = new HashMap<>();
    params.put("p_year", "2025");
    params.put("p_district_code", "DCC");
    params.put("p_userid", "IDIR\\JSMITH");
    params.put("CURRENT_SERVER", "FTATEST");
    params.put("ERRORS", new JRMapCollectionDataSource(errors));
    params.put("ERRORS_SUBREPORT", JasperTemplates.get(PreBillingReport.ERRORS_TEMPLATE));
    return params;
  }

  @Test
  void preBillingRendersChargesReversalsAndErrors() throws Exception {
    Map<String, Object> error = new HashMap<>();
    error.put("calendar_year", "2025");
    error.put("forest_file_id", "RAN099999");
    error.put("billing_instruc_code", "Grazing Usage Fee not found for billing year.");
    error.put("current_server", "FTATEST");

    byte[] pdf = JasperTemplates.pdf(PreBillingReport.TEMPLATE,
        preBillingParams(List.of(error)),
        List.of(preBillingRow("1", "Y", d("-60.00")), preBillingRow("2", "N", d("60.00"))));

    Files.write(Path.of("target", "FTA408-sample.pdf"), pdf);
    String text = text(pdf);
    assertThat(text).contains("Pre-Billing Report For 2025", "RAN076543",
        "WILLOWFIELD RANCH LTD.", "RAN099999", "Grazing Usage Fee not found for billing year.");
  }

  @Test
  void preBillingRendersWithNoRows() throws Exception {
    byte[] pdf = JasperTemplates.pdf(
        PreBillingReport.TEMPLATE, preBillingParams(List.of()), List.of());
    assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
  }

  private static Map<String, Object> invoiceRow(String invoice, String client) {
    Map<String, Object> r = new HashMap<>();
    r.put("invoice_number", invoice);
    r.put("payable_by_client", "00120890");
    r.put("payable_by_cli_loc", "00");
    r.put("invoice_amount", d("277.20"));
    r.put("invoice_date", Date.valueOf("2025-06-01"));
    r.put("invc_sub_type_code", "RBS");
    r.put("invoice_rln_typ_cd", "ORG");
    r.put("posted_date", Date.valueOf("2025-06-02"));
    r.put("forest_file_id", "RAN076543");
    r.put("admin_org_unit_no", 1010);
    r.put("admin_org_unit_code", "DCC");
    r.put("orign_org_unit_no", 1010);
    r.put("admin_org_unit_name", "Cariboo-Chilcotin Natural Resource District");
    r.put("orign_org_unit_name", "Cariboo-Chilcotin Natural Resource District");
    r.put("file_type_code", "E01");
    r.put("legal_first_name", null);
    r.put("client_name", client);
    r.put("tax", d("13.20"));
    r.put("issue_fee", d("25.00"));
    r.put("fee", d("150.00"));
    r.put("nonuse", d("4.00"));
    r.put("rent", d("60.00"));
    r.put("auth_amt", d("120"));
    r.put("hdbs_company_code", null);
    r.put("address_1", "PO BOX 1");
    r.put("city", "WILLIAMS LAKE");
    r.put("province", "BC");
    r.put("postal_code", "V2G 1A1");
    r.put("current_server", "FTATEST");
    return r;
  }

  private static Map<String, Object> postBillingParams(String sort, String level) {
    Map<String, Object> params = new HashMap<>();
    params.put("p_year", "2025");
    params.put("p_org_unit_no", "1010");
    params.put("p_org_unit_code", "DCC");
    params.put("p_org_level", level);
    params.put("p_sort_ind", sort);
    params.put("p_userid", "IDIR\\JSMITH");
    return params;
  }

  @Test
  void postBillingByClientRenders() throws Exception {
    byte[] pdf = JasperTemplates.pdf(PostBillingReport.BY_CLIENT_TEMPLATE,
        postBillingParams("1", "D"),
        List.of(invoiceRow("9001", "WILLOWFIELD RANCH LTD."), invoiceRow("9002", "ACME")));
    Files.write(Path.of("target", "FTA409-sample.pdf"), pdf);
    assertThat(text(pdf)).contains("Post - Billing Report For 2025", "9001", "RAN076543");
  }

  @Test
  void postBillingOtherSortsRenderForAllDistricts() throws Exception {
    byte[] pdf = JasperTemplates.pdf(PostBillingReport.OTHER_TEMPLATE,
        postBillingParams("4", ""), List.of(invoiceRow("9001", "WILLOWFIELD RANCH LTD.")));
    Files.write(Path.of("target", "FTA409-nonclient-sample.pdf"), pdf);
    assertThat(text(pdf)).contains("by Province", "9001");
  }

  @Test
  void postBillingRendersWithNoRows() throws Exception {
    byte[] pdf = JasperTemplates.pdf(
        PostBillingReport.BY_CLIENT_TEMPLATE, postBillingParams("1", "D"), List.of());
    assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
  }
}
