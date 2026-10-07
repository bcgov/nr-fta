package ca.bc.gov.nrs.fta.mark.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The ported FTA402 certificate compiles and renders. Worth pinning because the JRXML is
 * legacy layout run on a different Jasper version, and a compile failure only shows when
 * someone presses Print.
 */
@DisplayName("Unit Test | MarkCertificateReport")
class MarkCertificateReportTest {

  @Test
  void rendersAPdfFromSampleRows() throws Exception {
    Map<String, Object> row = new HashMap<>();
    row.put("timber_mark", "E12345");
    row.put("mark_issue_date", MarkCertificateReport.longDate(LocalDate.of(2026, 4, 10)));
    row.put("mark_expiry_date", MarkCertificateReport.longDate(LocalDate.of(2030, 4, 9)));
    row.put("mark_amend_date", null);
    row.put("description", "Private Land - Crown Granted Before March 12, 1906");
    row.put("granted_acqrd_date", "1895");
    row.put("district", "Stuart Nechako Natural Resource District");
    row.put("region", "Omineca Natural Resource Region");
    row.put("main_licensee", "Canadian Forest Products Ltd.");
    row.put("address_1", "1234 Main Street");
    row.put("city", "Vanderhoof,  BC,  Canada  V0J 3A0");
    row.put("p_of_c_or_legal", "PARCEL A, PLAN 8941, DL325, RANGE 5, COAST LAND DISTRICT");
    row.put("map_reference_id", "68071");
    row.put("secondary_client_count", "1");

    byte[] pdf = new MarkCertificateReport(null).fill(
        "152409",
        List.of(row),
        List.of(Map.of("TIMBER_MARK", "E12345", "SECONDARY_LICENSEE", "Jane Smith")));

    assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    Path out = Path.of("target", "FTA402-sample.pdf");
    Files.write(out, pdf);
  }

  @Test
  void formatsDatesAsTheProcedureDid() {
    assertThat(MarkCertificateReport.longDate(LocalDate.of(2021, 4, 10)))
        .isEqualTo("10th day of April, 2021.");
    assertThat(MarkCertificateReport.longDate(LocalDate.of(2021, 4, 1)))
        .isEqualTo("1st day of April, 2021.");
    assertThat(MarkCertificateReport.longDate(LocalDate.of(2021, 4, 12)))
        .isEqualTo("12th day of April, 2021.");
    assertThat(MarkCertificateReport.longDate(LocalDate.of(2021, 4, 23)))
        .isEqualTo("23rd day of April, 2021.");
  }
}
