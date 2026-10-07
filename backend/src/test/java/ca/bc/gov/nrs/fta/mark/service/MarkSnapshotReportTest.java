package ca.bc.gov.nrs.fta.mark.service;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.mark.dto.MarkDetailDto;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The snapshot renders the mark as the detail page shows it, stamped with when and by whom.
 * Pinned because a layout exception only shows when someone presses Snapshot.
 */
@DisplayName("Unit Test | MarkSnapshotReport")
class MarkSnapshotReportTest {

  private static final ZonedDateTime AT =
      ZonedDateTime.of(2026, 10, 7, 14, 32, 0, 0, ZoneId.of("America/Vancouver"));

  private static MarkDetailDto mark(String timberMark, List<MarkDetailDto.LandIndex> landIndex) {
    return new MarkDetailDto(
        timberMark, "150557", timberMark, "B01", "HI", null, LocalDate.of(2023, 4, 2),
        LocalDate.of(2023, 5, 1), LocalDate.of(2028, 4, 30), null, 60, "1867", "DVA",
        "00001234", "00", "MEADOW RANCH LTD", "S", "H", null, null, "PINCHI MINE RD",
        new BigDecimal("64.7"), "District Lot 1234, Range 5 Coast District", "HI - Issued",
        "B01 - Private", "DVA - Stuart Nechako Natural Resource District",
        "RNO - Omineca Natural Resource Region", "S - Stamp", "H - Hammer", "012-982-971", "U",
        "24", "Prince George TSA", "I", "I - Interior", "68", "071", null, 0, null, null, null,
        1L, null, true,
        landIndex,
        List.of(new MarkDetailDto.AssociatedClient(
            "00001234", "00", "MEADOW RANCH LTD", "Vanderhoof", 1L, "A", "A - Main Licensee",
            LocalDate.of(2023, 5, 1), null, 1)),
        List.of(new MarkDetailDto.Amendment(
            LocalDate.of(2024, 1, 15), "HN", "HN - Approved", 1, "IDIR\\JSMITH",
            new BigDecimal("70"), "Area change")),
        List.of(new MarkDetailDto.Note(
            "IDIR\\JSMITH", LocalDateTime.of(2024, 2, 1, 9, 30), "Called the holder.")),
        null);
  }

  private static final MarkDetailDto.LandIndex LOT_4 =
      new MarkDetailDto.LandIndex("CA", null, "CA - Cariboo", null, "Lot 4", null, 1L, 1);

  private static String text(byte[] pdf) throws Exception {
    PdfReader reader = new PdfReader(pdf);
    PdfTextExtractor extractor = new PdfTextExtractor(reader);
    StringBuilder all = new StringBuilder();
    for (int p = 1; p <= reader.getNumberOfPages(); p++) {
      all.append(extractor.getTextFromPage(p)).append('\n');
    }
    return all.toString();
  }

  @Test
  void rendersTheMarkSummaryAndAdministrationWithTheStamp() throws Exception {
    byte[] pdf = new MarkSnapshotReport().render(
        mark("12A345", List.of(LOT_4)),
        new MarkSnapshotReport.Stamp(AT, "Jane Smith (IDIR\\JSMITH)"));

    assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    assertThat(text(pdf))
        .contains("Private Mark Snapshot")
        .contains("Oct 7, 2026 2:32 PM")
        .contains("Jane Smith (IDIR\\JSMITH)")
        .contains("Mark summary", "Administration", "S - Stamp", "B01 - Private",
            "Timber Mark Status", "60 months")
        .contains("Page 1 of 1")
        // Only those two sections: not the rest of the page's cards and tabs.
        .doesNotContain("Land index", "CA - Cariboo", "Associated clients", "Notes",
            "Called the holder.");
  }

  @Test
  void anUnissuedMarkShowsTheMarkingPlaceholdersAsThePageDoes() throws Exception {
    byte[] pdf = new MarkSnapshotReport().render(
        mark(null, List.of()), new MarkSnapshotReport.Stamp(AT, "IDIR\\JSMITH"));

    assertThat(text(pdf)).contains("S - Stamp", "H - Hammer");
  }
}
