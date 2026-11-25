package ca.bc.gov.nrs.fta.apt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.apt.dto.AptManagementUnitDto;
import ca.bc.gov.nrs.fta.apt.dto.AptOrgUnitDto;
import ca.bc.gov.nrs.fta.apt.dto.AptReportParams;
import ca.bc.gov.nrs.fta.apt.service.AptReportBatchService.Item;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A report batch: the units and regions its form offers, named as single downloads, zipped, with
 * any PDF that fails listed instead of losing the rest.
 */
@DisplayName("Unit Test | AptReportBatchService")
class AptReportBatchServiceTest {

  private static final LocalDate DAY = LocalDate.of(2026, 3, 31);

  private static AptManagementUnitDto unit(long id, String name) {
    return new AptManagementUnitDto(id, name, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null);
  }

  @Test
  void onlyThePerUnitAndPerRegionReportsBatch() {
    assertThat(AptReportBatchService.batchable(AptReportDefinition.APTR011)).isTrue();
    assertThat(AptReportBatchService.batchable(AptReportDefinition.APTR012)).isTrue();
    assertThat(AptReportBatchService.batchable(AptReportDefinition.APTR013)).isTrue();
    assertThat(AptReportBatchService.batchable(AptReportDefinition.APTR017)).isFalse();
    assertThat(AptReportBatchService.batchable(AptReportDefinition.APTR021)).isFalse();
  }

  @Test
  void aptr011RunsForEachTsaOnceByNameInNameOrder() {
    List<Item> items = AptReportBatchService.tsaItems(List.of(
        unit(3, "Kamloops TSA"), unit(1, "Arrow TSA"), unit(9, "TFL 48"),
        unit(4, "Arrow TSA"), unit(5, null)), DAY);

    assertThat(items).extracting(Item::name).containsExactly("Arrow TSA", "Kamloops TSA");
    assertThat(items.get(0).params()).isEqualTo(new AptReportParams(DAY, 1L, null));
  }

  @Test
  void regionReportsRunForEachRegionLabelledAsTheFormLabelsIt() {
    List<Item> items = AptReportBatchService.regionItems(List.of(
        new AptOrgUnitDto(10, "RSI", "South Island", "RSI - South Island"),
        new AptOrgUnitDto(11, "RCO", "Coast", null),
        new AptOrgUnitDto(null, "RXX", "No key", null)), DAY);

    assertThat(items).extracting(Item::name).containsExactly("RSI - South Island", "Coast");
    assertThat(items.get(1).params()).isEqualTo(new AptReportParams(DAY, null, 11));
  }

  @Test
  void eachPdfIsNamedAsASingleDownload() {
    assertThat(AptReportBatchService.pdfName("APTR011", "Arrow TSA"))
        .isEqualTo("APTR011_Arrow_TSA.pdf");
    assertThat(AptReportBatchService.pdfName("APTR013", "RSI - South/Island"))
        .isEqualTo("APTR013_RSI_-_South-Island.pdf");
    assertThat(AptReportBatchService.zipName(AptReportDefinition.APTR012, DAY))
        .isEqualTo("APTR012_20260331.zip");
  }

  @Test
  void theZipHoldsOnePdfPerUnitAndListsAnyThatFailed() throws IOException {
    AptReportService reports = mock(AptReportService.class);
    AptReportBatchService service = new AptReportBatchService(reports,
        mock(AptManagementUnitService.class), mock(AptLookupService.class), 4);
    when(reports.pdf(eq(AptReportDefinition.APTR011), argThat(p -> p.managementUnitId() == 1L),
        any())).thenReturn("one".getBytes(StandardCharsets.UTF_8));
    when(reports.pdf(eq(AptReportDefinition.APTR011), argThat(p -> p.managementUnitId() == 2L),
        any())).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "No data."));
    when(reports.pdf(eq(AptReportDefinition.APTR011), argThat(p -> p.managementUnitId() == 3L),
        any())).thenReturn("three".getBytes(StandardCharsets.UTF_8));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    service.writeZip(AptReportDefinition.APTR011, List.of(
        new Item("Arrow TSA", new AptReportParams(DAY, 1L, null)),
        new Item("Boundary TSA", new AptReportParams(DAY, 2L, null)),
        new Item("Cassiar TSA", new AptReportParams(DAY, 3L, null))), "IDIR\\JSAMPLE", out);

    Map<String, String> entries = unzip(out.toByteArray());
    assertThat(entries.keySet()).containsExactly(
        "APTR011_Arrow_TSA.pdf", "APTR011_Cassiar_TSA.pdf", AptReportBatchService.FAILURES_FILE);
    assertThat(entries.get("APTR011_Cassiar_TSA.pdf")).isEqualTo("three");
    assertThat(entries.get(AptReportBatchService.FAILURES_FILE)).contains("Boundary TSA: No data.");
  }

  @Test
  void twoUnitsWithOneNameGetDistinctFiles() throws IOException {
    AptReportService reports = mock(AptReportService.class);
    when(reports.pdf(any(), any(), any())).thenReturn(new byte[] {1});
    AptReportBatchService service = new AptReportBatchService(reports,
        mock(AptManagementUnitService.class), mock(AptLookupService.class), 4);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    service.writeZip(AptReportDefinition.APTR012, List.of(
        new Item("Coast", new AptReportParams(DAY, null, 1)),
        new Item("Coast", new AptReportParams(DAY, null, 2))), "IDIR\\JSAMPLE", out);

    assertThat(unzip(out.toByteArray()).keySet())
        .containsExactly("APTR012_Coast.pdf", "APTR012_Coast_2.pdf");
  }

  @Test
  void pdfsRenderAtOnceButTheZipKeepsTheListsOrder() throws IOException {
    AptReportService reports = mock(AptReportService.class);
    CountDownLatch bothStarted = new CountDownLatch(2);
    when(reports.pdf(any(), any(), any())).thenAnswer(call -> {
      bothStarted.countDown();
      // Each waits for the other, so this passes only when the two render at once.
      assertThat(bothStarted.await(5, TimeUnit.SECONDS)).isTrue();
      AptReportParams p = call.getArgument(1);
      if (p.orgUnitNo() == 1) {
        Thread.sleep(100);
      }
      return String.valueOf(p.orgUnitNo()).getBytes(StandardCharsets.UTF_8);
    });
    AptReportBatchService service = new AptReportBatchService(reports,
        mock(AptManagementUnitService.class), mock(AptLookupService.class), 2);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    service.writeZip(AptReportDefinition.APTR013, List.of(
        new Item("Coast", new AptReportParams(DAY, null, 1)),
        new Item("North", new AptReportParams(DAY, null, 2))), "IDIR\\JSAMPLE", out);

    assertThat(unzip(out.toByteArray())).containsExactly(
        Map.entry("APTR013_Coast.pdf", "1"), Map.entry("APTR013_North.pdf", "2"));
  }

  private static Map<String, String> unzip(byte[] zip) throws IOException {
    Map<String, String> entries = new LinkedHashMap<>();
    try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
      for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
        entries.put(e.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
      }
    }
    return entries;
  }
}
