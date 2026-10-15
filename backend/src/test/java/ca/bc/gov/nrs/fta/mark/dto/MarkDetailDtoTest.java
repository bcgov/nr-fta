package ca.bc.gov.nrs.fta.mark.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins which amendment a mark shows as its "Amendment Status". Only an outstanding (PI or HN)
 * amendment used to be shown, so once one was issued (HI) the field read "—" while the
 * Amendments tab listed it as HI - Issued.
 */
@DisplayName("Unit Test | MarkDetailDto")
class MarkDetailDtoTest {

  private static MarkDetailDto.Amendment amendment(LocalDate requested, String status) {
    return new MarkDetailDto.Amendment(
        requested, status, status + " - Description", 1, "IDIR\\JSMITH", null, "Changes");
  }

  /** A mark with just its amendments, newest first as MarkDetailService reads them. */
  private static MarkDetailDto mark(List<MarkDetailDto.Amendment> amendments) {
    return new MarkDetailDto(
        "12A345", "150557", "12A345", "B01", "HI", null, null, null, null, null, 60, "1867",
        "DVA", "00001", "00", "Holder", "S", "H", null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null, 0, null,
        null, null, 1L, null, true, List.of(), List.of(), amendments, List.of(), null);
  }

  @Test
  void anIssuedAmendmentIsShown() {
    MarkDetailDto mark = mark(List.of(amendment(LocalDate.of(2025, 3, 1), "HI")));

    assertThat(mark.shownAmendment().prvMrkAmdStsSt()).isEqualTo("HI");
  }

  @Test
  void theNewestIsShownWhenNoneIsOutstanding() {
    MarkDetailDto mark = mark(List.of(
        amendment(LocalDate.of(2025, 3, 1), "DV"),
        amendment(LocalDate.of(2024, 3, 1), "HI")));

    assertThat(mark.shownAmendment().prvMrkAmdStsSt()).isEqualTo("DV");
  }

  @Test
  void anOutstandingAmendmentWins() {
    MarkDetailDto mark = mark(List.of(
        amendment(LocalDate.of(2025, 3, 1), "HI"),
        amendment(LocalDate.of(2024, 3, 1), "PI")));

    assertThat(mark.shownAmendment().prvMrkAmdStsSt()).isEqualTo("PI");
  }

  @Test
  void noAmendmentsShowNothing() {
    assertThat(mark(List.of()).shownAmendment()).isNull();
  }
}
