package ca.bc.gov.nrs.fta.tenure.tab.harvesthistory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.fta.billing.service.JasperTemplates;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * The ported FTA404 Mark Report compiles and renders — it is legacy Jasper Server JRXML run on a
 * different Jasper version, and a failure would only show when someone prints — and its two
 * inputs.
 */
@DisplayName("Unit Test | FTA404 Mark Report")
class MarkReportTest {

  private static String text(byte[] pdf) throws Exception {
    PdfReader reader = new PdfReader(pdf);
    StringBuilder sb = new StringBuilder();
    for (int i = 1; i <= reader.getNumberOfPages(); i++) {
      sb.append(new PdfTextExtractor(reader).getTextFromPage(i)).append('\n');
    }
    return sb.toString();
  }

  private static Map<String, ?> row(String type, String species, String grade, String volume,
      String amount) {
    Map<String, Object> row = new HashMap<>();
    row.put("timber_mark", "12345A");
    row.put("billing_year", new BigDecimal("2025"));
    row.put("cutting_permit_id", "01");
    row.put("billing_type_code", type);
    row.put("hdbs_tree_species", species);
    row.put("log_grade", grade);
    row.put("volume_billed", new BigDecimal(volume));
    row.put("total_amount", new BigDecimal(amount));
    row.put("catastrophic_ind", "N");
    row.put("db_name", "FTATEST");
    return row;
  }

  @Test
  void compiles() {
    assertThat(JasperTemplates.get(MarkReportService.TEMPLATE)).isNotNull();
  }

  @Test
  void rendersLegacysHeadingsGroupsAndTotals() throws Exception {
    List<Map<String, ?>> rows = List.of(
        row("Stumpage", "Douglas Fir", "Grade 1", "100.50", "2500.25"),
        row("Stumpage", "Douglas Fir", "Grade 2", "50.00", "1000.00"),
        row("Stumpage", "Lodgepole Pine", "Grade 1", "10.00", "99.75"));

    String t = text(JasperTemplates.pdf(MarkReportService.TEMPLATE,
        MarkReportRules.parameters("12345A", "IDIR\\JSMITH"), rows));

    assertThat(t)
        .contains("Timber Tenure Reporting")
        .contains("Harvest History")
        .contains("Timber Mark: 12345A")
        .contains("FTA404R")
        .contains("IDIR\\JSMITH")
        .contains("FTATEST")
        .contains("Catastrophic").contains("Species").contains("Grade").contains("Volume Billed")
        .contains("Douglas Fir Species Total")
        .contains("150.50")
        .contains("3,500.25")
        .contains("Lodgepole Pine Species Total")
        .contains("Stumpage Billing Type Total")
        .contains("12345A Timber Mark Total")
        .contains("160.50")
        .contains("3,600.00")
        .contains("File Id:  Total")
        .contains("End of Report");
  }

  @Test
  void rendersWithNoRows() throws Exception {
    String t = text(JasperTemplates.pdf(MarkReportService.TEMPLATE,
        MarkReportRules.parameters("12345A", "IDIR\\JSMITH"), List.of()));
    assertThat(t).contains("Timber Mark: 12345A").contains("End of Report");
  }

  @Test
  void theMarkIsTheOneOnScreen() {
    assertThat(MarkReportRules.timberMark(" 12345a ")).isEqualTo("12345A");
    assertThat(MarkReportRules.filename("12345A")).isEqualTo("FTA404-MarkReport-12345A.pdf");
    assertThatThrownBy(() -> MarkReportRules.timberMark(" "))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
    assertThatThrownBy(() -> MarkReportRules.timberMark("1234567"))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
  }
}
