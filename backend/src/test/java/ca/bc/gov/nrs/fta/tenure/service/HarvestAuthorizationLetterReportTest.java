package ca.bc.gov.nrs.fta.tenure.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The FTA413 letter's JRXML compiles and fills: a failure would otherwise only show when someone
 * presses Letter on a Fort St. John permit.
 */
@DisplayName("Unit Test | FTA413 harvest authorization letter")
class HarvestAuthorizationLetterReportTest {

  private static String text(byte[] pdf) throws Exception {
    PdfReader reader = new PdfReader(pdf);
    StringBuilder sb = new StringBuilder();
    for (int i = 1; i <= reader.getNumberOfPages(); i++) {
      sb.append(new PdfTextExtractor(reader).getTextFromPage(i)).append('\n');
    }
    return sb.toString();
  }

  private static Map<String, ?> row(String hvaId) {
    Map<String, Object> row = new HashMap<>();
    row.put("forest_file_id", "A12345");
    row.put("harvesting_authority_id", hvaId);
    row.put("issue_date", "2026-03-01");
    row.put("expiry_date", "2028-03-01");
    row.put("client_name", "NORTHERN TIMBER LTD.");
    row.put("address_1", "100 MAIN ST");
    row.put("city", "FORT ST. JOHN");
    row.put("province", "BC");
    row.put("country", "CANADA");
    row.put("postal_code", "V1J 1A1");
    row.put("submission_date", "2026-02-10");
    row.put("serverid", "FTATEST");
    return row;
  }

  private static Map<String, ?> block(String id) {
    Map<String, Object> b = new HashMap<>();
    b.put("cut_block_id", id);
    return b;
  }

  @Test
  void rendersTheLetterWithItsBlocks() throws Exception {
    byte[] pdf = HarvestAuthorizationLetterReport.fill("42", "IDIR\\JSMITH",
        List.of(row("FSJ-001")), List.of(block("B1"), block("B2")));

    assertThat(new PdfReader(pdf).getNumberOfPages()).isEqualTo(1);
    assertThat(text(pdf))
        .contains("NORTHERN TIMBER LTD.").contains("100 MAIN ST")
        .contains("FORT ST. JOHN").contains("V1J 1A1")
        .contains("A12345").contains("FSJ-001")
        .contains("2026-02-10").contains("2026-03-01").contains("2028-03-01")
        .contains("B1").contains("B2");
  }

  @Test
  void eachRowIsALetterOfItsOwn() throws Exception {
    byte[] pdf = HarvestAuthorizationLetterReport.fill("42", "IDIR\\JSMITH",
        List.of(row("FSJ-001"), row("FSJ-001")), List.of(block("B1")));

    assertThat(new PdfReader(pdf).getNumberOfPages()).isEqualTo(2);
    assertThat(text(pdf).split("B1", -1)).hasSize(3);
  }

  @Test
  void rendersWithNoBlocksAndBlankFields() {
    Map<String, Object> sparse = new HashMap<>();
    sparse.put("forest_file_id", "A12345");
    assertThat(HarvestAuthorizationLetterReport.fill(
        "42", "IDIR\\JSMITH", List.of(sparse), List.of())).isNotEmpty();
  }
}
