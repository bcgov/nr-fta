package ca.bc.gov.nrs.fta.apt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.fta.apt.dto.AptReportParams;
import ca.bc.gov.nrs.fta.apt.service.AptReportExtractSql.Aptr013Row;
import ca.bc.gov.nrs.fta.apt.service.AptReportExtractSql.Aptr041Row;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * APT report parameters (APT2's names, kept exactly), and the CSV extracts: each line built as
 * APTR013_CSV_EXTRACT / APTR041_CSV_EXTRACT concatenated it, ordered as their UNION returned
 * them, then split into fields.
 */
@DisplayName("Unit Test | AptReportService")
class AptReportServiceTest {

  private static final LocalDate DAY = LocalDate.of(2026, 3, 31);

  @Test
  void commonParamsComeFirstThenTheReportsOwn() {
    assertThat(AptReportService.reportParams(AptReportDefinition.APTR011,
        new AptReportParams(DAY, 42L, 7), "IDIR\\JSMITH"))
        .containsExactly(
            java.util.Map.entry("user", "IDIR\\JSMITH"),
            java.util.Map.entry("in_rpt_date", "2026-03-31"),
            java.util.Map.entry("IN_MGMT_UNIT_ID", "42"));
  }

  @Test
  void eachReportSendsApt2sParameterNames() {
    AptReportParams all = new AptReportParams(DAY, 5L, 9);
    assertThat(AptReportDefinition.APTR012.reportParams(all))
        .containsOnlyKeys("in_report_date", "IN_ORG_UNIT_NO")
        .containsEntry("IN_ORG_UNIT_NO", "9");
    assertThat(AptReportDefinition.APTR032.reportParams(all)).containsOnlyKeys("in_rpt_date");
    assertThat(AptReportDefinition.APTR011.reportParams(all))
        .containsEntry("IN_MGMT_UNIT_ID", "5");
    assertThat(AptReportDefinition.APTR043.reportParams(all)).isEmpty();
    assertThat(AptReportDefinition.APTR041.template()).isEqualTo("APTR041_master");
  }

  @Test
  void missingRequiredInputIs400() {
    assertThatThrownBy(() -> AptReportDefinition.APTR013.reportParams(
        new AptReportParams(DAY, null, null)))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(e.getReason()).contains("a region");
        });
    assertThatThrownBy(() -> AptReportDefinition.APTR021.reportParams(null))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void unitAndRegionIdsMustBePositive() {
    assertThatThrownBy(() -> AptReportDefinition.APTR011.reportParams(
        new AptReportParams(DAY, 0L, null)))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(e.getReason()).isEqualTo("Management Unit ID must be a positive integer.");
        });
    assertThatThrownBy(() -> AptReportDefinition.APTR012.reportParams(
        new AptReportParams(DAY, null, -3)))
        .isInstanceOfSatisfying(ResponseStatusException.class, e ->
          assertThat(e.getReason()).isEqualTo("Organization Unit ID must be a positive integer."));
  }

  @Test
  void reportCodesAreCaseInsensitive() {
    assertThat(AptReportDefinition.of("aptr043")).contains(AptReportDefinition.APTR043);
    assertThat(AptReportDefinition.of("APTR999")).isEmpty();
    assertThat(AptReportExtract.of("aptr041")).contains(AptReportExtract.APTR041);
    assertThat(AptReportExtract.of("APTR011")).isEmpty();
  }

  @Test
  void apt2sDroppedReportsAreGone() {
    for (String code : new String[] {"APTR018", "APTR055", "APTR060", "APTR065"}) {
      assertThat(AptReportDefinition.of(code)).as(code).isEmpty();
    }
    assertThat(AptReportExtract.of("APTR065")).isEmpty();
    assertThat(AptReportExtract.APTR013.fileName()).isEqualTo("APTR013");
  }

  @Test
  void splitsTheExtractLines() {
    assertThat(AptReportService.splitLine(" Region,Management Unit,Variance"))
        .containsExactly("Region", "Management Unit", "Variance");
    // APTR013 style: quoted names, bare numbers.
    assertThat(AptReportService.splitLine("\"RSI\",\"Arrow TSA, North\",1200,,2026/01/01"))
        .containsExactly("RSI", "Arrow TSA, North", "1200", "", "2026/01/01");
    // APTR041 rows end with ", " (the unapportioned-volume indicator).
    assertThat(AptReportService.splitLine("\"PARENT LTD\",A12345,\"CHILD \"\"X\"\" INC\",FL, "))
        .containsExactly("PARENT LTD", "A12345", "CHILD \"X\" INC", "FL", "");
  }

  private static final LocalDate EXPIRY = LocalDate.of(2031, 12, 31);

  private static Aptr041Row row041(String holder, String path, String size, boolean uav) {
    return new Aptr041Row(holder, path, size, "PARENT LTD", "A12345", "CHILD INC", "A",
        EXPIRY, "RSI", "Arrow TSA", BigDecimal.valueOf(1200), uav, null);
  }

  @Test
  void aptr013LineIsTheFunctionsConcatenation() {
    Aptr013Row row = new Aptr013Row("RSI", "Arrow TSA", new BigDecimal("500000"),
        null, LocalDate.of(2026, 1, 1), null, "Unpartitioned", BigDecimal.valueOf(400000),
        BigDecimal.ZERO, "Forest Licence", BigDecimal.valueOf(1200), null);
    assertThat(row.line()).isEqualTo(
        "\"RSI\",\"Arrow TSA\",500000,,2026/01/01,,\"Unpartitioned\",400000,0,"
            + "\"Forest Licence\",1200,");
    // A null name is still quoted; a quote in a name is not escaped, as in the function.
    assertThat(new Aptr013Row(null, "O\"Hare", null, null, null, null, null, null, null,
        null, null, null).line())
        .isEqualTo("\"\",\"O\"Hare\",,,,,\"\",,,\"\",,");
  }

  @Test
  void aptr041LinesByHolder() {
    assertThat(row041("CORP", "D", null, false).line())
        .isEqualTo("\"PARENT LTD\",A12345,\"CHILD INC\",A,2031-12-31,RSI,Arrow TSA,1200, ");
    assertThat(row041("CORP", "Z", null, true).line())
        .isEqualTo("\"PARENT LTD\",A12345,\"CHILD INC\",A,2031-12-31,RSI,Arrow TSA,1200,#");
    assertThat(row041("IND", "D", "MED", false).line())
        .startsWith("INDEPENDENT MEDIUM,A12345,\"CHILD INC\",");
    // The function's Z-type medium branch had a stray leading comma; kept.
    assertThat(row041("IND", "Z", "MED", false).line())
        .startsWith(",INDEPENDENT MEDIUM,A12345,");
    assertThat(row041("IND", "Z", "SMALL", true).line())
        .isEqualTo("INDEPENDENT SMALL,A12345,\"CHILD INC\",A,2031-12-31,RSI,Arrow TSA,1200,#");
  }

  @Test
  void aptr041JointlyHeldLinesUseTheHoldersNames() {
    Aptr041Row joint = new Aptr041Row("JOINT", "D", null, null, "A12345", null, "A", null,
        "RSI", "Arrow TSA", null, false, "ONE LTD; TWO LTD");
    assertThat(joint.line())
        .isEqualTo("JOINTLY HELD,A12345,\"ONE LTD; TWO LTD\",A,,RSI,Arrow TSA,, ");
    Aptr041Row jointZ = new Aptr041Row("JOINT", "Z", null, null, "A12345", null, "A", EXPIRY,
        "RSI", "Arrow TSA", BigDecimal.TEN, true, null);
    assertThat(jointZ.line()).isEqualTo("Jointly Held,A12345,\"\",A,2031-12-31,RSI,Arrow TSA,10,#");
  }

  @Test
  void jointlyHeldNamesAreCutTo98Characters() {
    String names = "N".repeat(120);
    assertThat(AptReportExtractSql.jointlyHeldNames(names))
        .isEqualTo("\"" + "N".repeat(98) + "\"");
    assertThat(AptReportExtractSql.jointlyHeldNames(null)).isEqualTo("\"\"");
  }

  @Test
  void numbersAreWrittenAsToCharWritesThem() {
    assertThat(AptReportExtractSql.toChar(null)).isEmpty();
    assertThat(AptReportExtractSql.toChar(new BigDecimal("0.00"))).isEqualTo("0");
    assertThat(AptReportExtractSql.toChar(new BigDecimal("25000"))).isEqualTo("25000");
    assertThat(AptReportExtractSql.toChar(new BigDecimal("1.2E+3"))).isEqualTo("1200");
    assertThat(AptReportExtractSql.toChar(new BigDecimal("12.50"))).isEqualTo("12.5");
    assertThat(AptReportExtractSql.toChar(new BigDecimal("0.5"))).isEqualTo(".5");
    assertThat(AptReportExtractSql.toChar(new BigDecimal("-0.5"))).isEqualTo("-.5");
  }

  @Test
  void linesAreDeduplicatedAndSortedHeaderFirst() {
    List<String> lines = AptReportExtractSql.fileLines(AptReportExtractSql.APTR041_HEADER,
        List.of("JOINTLY HELD,B,\"X\",A,,R,M,1, ", "\"ZED\",A,\"Y\",A,,R,M,1, ",
            "INDEPENDENT SMALL,C,\"Z\",A,,R,M,1, ", ",INDEPENDENT MEDIUM,D,\"W\",A,,R,M,1, ",
            "Jointly Held,E,\"V\",A,,R,M,1, ", "\"ZED\",A,\"Y\",A,,R,M,1, "));
    assertThat(lines).containsExactly(
        AptReportExtractSql.APTR041_HEADER,
        "\"ZED\",A,\"Y\",A,,R,M,1, ",
        ",INDEPENDENT MEDIUM,D,\"W\",A,,R,M,1, ",
        "INDEPENDENT SMALL,C,\"Z\",A,,R,M,1, ",
        "JOINTLY HELD,B,\"X\",A,,R,M,1, ",
        "Jointly Held,E,\"V\",A,,R,M,1, ");
    // Binary order: upper case before lower, accented letters after ASCII.
    assertThat(AptReportExtractSql.compareBinary("Z", "a")).isNegative();
    assertThat(AptReportExtractSql.compareBinary("\u00c9", "z")).isPositive();
  }

  @Test
  void extractRowsAreTheSplitLines() {
    assertThat(AptReportService.toRows(List.of(AptReportExtractSql.APTR013_HEADER,
        "\"RSI\",\"Arrow TSA\",500000,,2026/01/01,,\"Unpartitioned\",400000,0,"
            + "\"Forest Licence\",1200,", " ")))
        .containsExactly(
            List.of("Region", "Management Unit Name", "MU AAC(m3)", "MU AAC(ha)",
                "AAC Effect Date", "Apport Effect Date", "Partition", "Partition AAC(m3)",
                "Partition AAC(ha)", "FOA", "FOA AAC(m3)", "FOA AAC(ha)"),
            List.of("RSI", "Arrow TSA", "500000", "", "2026/01/01", "", "Unpartitioned",
                "400000", "0", "Forest Licence", "1200", ""));
  }
}
