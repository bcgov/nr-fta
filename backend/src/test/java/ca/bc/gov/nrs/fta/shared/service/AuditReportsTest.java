package ca.bc.gov.nrs.fta.shared.service;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.billing.service.JasperTemplates;
import ca.bc.gov.nrs.fta.shared.service.AuditReportRules.Report;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The ported FTA403 and FTA405 layouts compile and render: they are legacy Jasper Server JRXML
 * run on a different Jasper version, and a failure would only show when someone submits.
 */
@DisplayName("Unit Test | FTA675 audit reports")
class AuditReportsTest {

  private static String text(byte[] pdf) throws Exception {
    PdfReader reader = new PdfReader(pdf);
    StringBuilder sb = new StringBuilder();
    for (int i = 1; i <= reader.getNumberOfPages(); i++) {
      sb.append(new PdfTextExtractor(reader).getTextFromPage(i)).append('\n');
    }
    return sb.toString();
  }

  @Test
  void districtOverrideAuditRenders() throws Exception {
    Map<String, Object> row = new HashMap<>();
    row.put("org_unit_no", "1835");
    row.put("org_unit_code", "DCC");
    row.put("org_unit_name", "Cariboo-Chilcotin Natural Resource District");
    row.put("forest_file_id", "A12345");
    row.put("timber_mark", "12345A");
    row.put("b_file_status_st", "HI");
    row.put("a_file_status_st", "HX");
    row.put("b_file_tenure_term_yr", "2");
    row.put("b_file_tenure_term_mn", "6");
    row.put("b_file_award_dt", "2024-01-15");
    row.put("change_comments", "Corrected the expiry date.");
    row.put("entry_userid", "IDIR\\JSMITH");
    row.put("entry_timestamp", "2026-10-01 09:30:00");
    row.put("file_type_code", "A01");
    row.put("db_name", "FTATEST");

    Map<String, Object> params = new HashMap<>();
    params.put("p_user_id", "IDIR\\JSMITH");
    params.put("p_region_code", "RCB");
    params.put("p_district_code", "DCC");
    params.put("p_from_date", "2026-08-25");
    params.put("p_to_date", "2026-11-25");
    params.put("p_file_type_code_1", "A01");

    String t = text(JasperTemplates.pdf(Report.FTA403.template, params, List.of(row)));
    assertThat(t).contains("District Override Facility Audit Report")
        .contains("A12345").contains("Corrected the expiry date.").contains("End of Report");
  }

  @Test
  void districtOverrideAuditRendersWithNoRows() {
    Map<String, Object> params = new HashMap<>();
    params.put("p_user_id", "IDIR\\JSMITH");
    params.put("p_region_code", "*");
    params.put("p_district_code", "*");
    assertThat(JasperTemplates.pdf(Report.FTA403.template, params, List.of())).isNotEmpty();
  }

  @Test
  void deleteEeAuditRenders() throws Exception {
    Map<String, Object> row = new HashMap<>();
    row.put("org_unit_no", "1835");
    row.put("org_unit_code", "DCC");
    row.put("forest_file_id", "A12345");
    row.put("timber_mark", "12345A");
    row.put("cutting_permit_id", "01");
    row.put("cut_block_id", "B1");
    row.put("file_type_code", "A01");
    row.put("delete_option_cd", "CB");
    row.put("entry_userid", "IDIR\\JSMITH");
    row.put("entry_timestamp", Timestamp.valueOf("2026-10-01 09:30:00"));
    row.put("db_name", "FTATEST");
    row.put("out_message", "Cariboo-Chilcotin Only ");

    Map<String, Object> params = new HashMap<>();
    params.put("p_user_id", "IDIR\\JSMITH");
    params.put("p_region_code", "*");
    params.put("p_district_code", "DCC");
    params.put("p_from_date", "");
    params.put("p_to_date", "2026-11-25");
    for (int i = 1; i <= 5; i++) {
      params.put("p_file_type_code_" + i, i == 1 ? "A01" : "");
    }

    String t = text(JasperTemplates.pdf(Report.FTA405.template, params, List.of(row)));
    assertThat(t).contains("FTA405R").contains("A12345").contains("2026-10-01")
        .contains("From: *").contains("Total : 1");
  }
}
