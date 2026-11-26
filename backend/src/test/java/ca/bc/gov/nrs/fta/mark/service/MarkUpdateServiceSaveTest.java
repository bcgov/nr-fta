package ca.bc.gov.nrs.fta.mark.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.mark.dto.MarkDetailDto;
import ca.bc.gov.nrs.fta.mark.dto.MarkUpdateRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

/**
 * FTA510's Save, against the legacy form and FTA_510_PRIVATE_MARK: the expiry default, the
 * extension reason, the Crown grant year, the HX change, and the forest file's status.
 */
@DisplayName("Unit Test | MarkUpdateService save")
class MarkUpdateServiceSaveTest {

  private static final LocalDate ISSUED = LocalDate.of(2024, 1, 31);

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final MarkDetailService detail = mock(MarkDetailService.class);
  private final MarkFieldChecks checks = mock(MarkFieldChecks.class);
  private final MarkUpdateService service = new MarkUpdateService(
      jdbc, detail, checks, mock(PrivateMarkPackage.class), mock(MarkNoteWriteService.class));

  /** An issued B01 mark with a client, as Headquarters sees it. */
  private static MarkDetailDto mark(
      String status, String fileType, LocalDate expiry, LocalDate extend, String extendReason) {
    return new MarkDetailDto(
        "12A345", "150557", "12A345", fileType, status, null, LocalDate.of(2023, 12, 1),
        ISSUED, expiry, null, 60, "1867", "DVA", "00001", "00", "Holder", "S", "H", null,
        null, "PINCHI MINE RD", new BigDecimal("10"), "Legal", null, null, null, null, null,
        null, "012-982-971", "U", "24", null, "E", null, "68", "071", extend,
        extend == null ? 0 : 1, extendReason, null, null, null, 5L, null, true, List.of(),
        List.of(), List.of(), List.of(), null);
  }

  /** The mark's own values sent back, with the given branch fields and status. */
  private static MarkUpdateRequest save(
      MarkDetailDto m, LocalDate expiry, LocalDate extend, LocalDate cancel, String desc,
      String status) {
    return new MarkUpdateRequest(
        m.revisionCount(), null, m.markApplicationDate(), m.tenureTerm(), m.forestDistrict(),
        m.markingMethodCode(), m.markingInstrumentCode(), m.permitBlockLocn(),
        m.proofOfCrownOrLegal(), m.bcaaFolioNumber(), m.permitBlockArea(),
        m.mgmtUnitTypeCode(), m.mgmtUnitId(), m.cascadeSplitCode(), m.mapReferenceReg(),
        m.mapReferenceComp(), m.markIssueDate(), expiry, extend, cancel, null, desc, status,
        null, null, null);
  }

  private MapSqlParameterSource saved(MarkDetailDto m, MarkUpdateRequest q) {
    when(detail.findByMarkNumber("12A345")).thenReturn(Optional.of(m));
    when(checks.unchangedOrActive(any(), any(), anyString())).thenReturn(true);
    when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
    service.update("12A345", false, q, false, "IDIR\\TESTER");
    ArgumentCaptor<MapSqlParameterSource> p = ArgumentCaptor.forClass(MapSqlParameterSource.class);
    verify(jdbc).update(contains("UPDATE the.private_mark_certificate"), p.capture());
    return p.getValue();
  }

  @Nested
  @DisplayName("expiry default (setDates)")
  class ExpiryDefault {

    @Test
    void issuedPlusTermWithNoDayOff() {
      assertThat(MarkUpdateService.defaultExpiry(LocalDate.of(2024, 3, 15), null, 60))
          .isEqualTo(LocalDate.of(2029, 3, 15));
    }

    @Test
    void aMonthEndClampsAsGregorianCalendarDid() {
      assertThat(MarkUpdateService.defaultExpiry(ISSUED, null, 1))
          .isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @Test
    void anEnteredExpiryOrNoIssueDateIsLeftAlone() {
      LocalDate entered = LocalDate.of(2030, 1, 1);
      assertThat(MarkUpdateService.defaultExpiry(ISSUED, entered, 60)).isEqualTo(entered);
      assertThat(MarkUpdateService.defaultExpiry(null, null, 60)).isNull();
      assertThat(MarkUpdateService.defaultExpiry(ISSUED, null, null)).isNull();
    }

    @Test
    void appliesAtAnyStatus() {
      MarkDetailDto cancelled = mark("HX", "B01", null, null, null);

      MapSqlParameterSource p =
          saved(cancelled, save(cancelled, null, null, null, null, "HX"));

      assertThat(p.getValue("expiryDate")).isEqualTo(LocalDate.of(2029, 1, 31));
    }
  }

  @Nested
  @DisplayName("extension reason")
  class ExtensionReason {

    @Test
    void anUnchangedExtensionKeepsTheStoredReason() {
      LocalDate extend = LocalDate.of(2030, 6, 1);
      MarkDetailDto m = mark("HI", "B01", LocalDate.of(2029, 1, 31), extend, "R");

      MapSqlParameterSource p =
          saved(m, save(m, m.markExpiryDate(), extend, null, null, "HI"));

      assertThat(p.getValue("extendReason")).isEqualTo("R");
      assertThat(p.getValue("extendCount")).isEqualTo(1);
    }

    @Test
    void aChangedExtensionOnAnIssuedMarkIsCountedWithReasonP() {
      assertThat(MarkUpdateService.extendReason(
          "R", LocalDate.of(2030, 6, 1), LocalDate.of(2031, 6, 1), "HI")).isEqualTo("P");
      assertThat(MarkUpdateService.extendCount(
          1, LocalDate.of(2030, 6, 1), LocalDate.of(2031, 6, 1), "HI")).isEqualTo(2);
    }

    @Test
    void aChangeOffAnIssuedMarkKeepsTheReason() {
      assertThat(MarkUpdateService.extendReason(
          "R", LocalDate.of(2030, 6, 1), LocalDate.of(2031, 6, 1), "HX")).isEqualTo("R");
    }

    @Test
    void noExtensionZeroesTheCountButKeepsTheReason() {
      assertThat(MarkUpdateService.extendCount(3, LocalDate.of(2030, 6, 1), null, "HI"))
          .isZero();
      assertThat(MarkUpdateService.extendReason("R", LocalDate.of(2030, 6, 1), null, "HI"))
          .isEqualTo("R");
    }
  }

  @Nested
  @DisplayName("Crown grant year")
  class CrownGrantYear {

    @Test
    void anyNumberIsAYear() {
      assertThat(MarkUpdateService.crownGrantYear("1905")).isEqualTo(1905L);
      assertThat(MarkUpdateService.crownGrantYear(" 950 ")).isEqualTo(950L);
      assertThat(MarkUpdateService.crownGrantYear("1906.6")).isEqualTo(1907L);
      assertThat(MarkUpdateService.crownGrantYear("+12345")).isEqualTo(12345L);
    }

    @Test
    void textIsNot() {
      assertThat(MarkUpdateService.crownGrantYear("C.G. 1890")).isNull();
      assertThat(MarkUpdateService.crownGrantYear("")).isNull();
      assertThat(MarkUpdateService.crownGrantYear(null)).isNull();
    }

    @Test
    void aFiveDigitYearIsCheckedForB08() {
      MarkDetailDto m = mark("HI", "B08", LocalDate.of(2029, 1, 31), null, null);
      when(detail.findByMarkNumber("12A345")).thenReturn(Optional.of(m));
      when(checks.unchangedOrActive(any(), any(), anyString())).thenReturn(true);

      assertThatThrownBy(() -> service.update("12A345", false,
          save(m, m.markExpiryDate(), null, null, "19060", "HI"), false, "IDIR\\TESTER"))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("1906 or earlier");
      verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
    }
  }

  @Nested
  @DisplayName("cancelling")
  class Cancelling {

    @Test
    void hxNeedsACancelledDate() {
      MarkDetailDto m = mark("HI", "B01", LocalDate.of(2029, 1, 31), null, null);
      when(detail.findByMarkNumber("12A345")).thenReturn(Optional.of(m));
      when(checks.unchangedOrActive(any(), any(), anyString())).thenReturn(true);

      assertThatThrownBy(() -> service.update("12A345", false,
          save(m, m.markExpiryDate(), null, null, null, "HX"), false, "IDIR\\TESTER"))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Cancelled date is required to set the status to HX.");
      verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
    }

    @Test
    void hxWithACancelledDateSaves() {
      MarkDetailDto m = mark("HI", "B01", LocalDate.of(2029, 1, 31), null, null);

      MapSqlParameterSource p = saved(m,
          save(m, m.markExpiryDate(), null, LocalDate.of(2025, 1, 1), null, "HX"));

      assertThat(p.getValue("status")).isEqualTo("HX");
    }

    @Test
    void theForestFileTakesTheStatusButNotAStatusDate() {
      MarkDetailDto m = mark("HI", "B01", LocalDate.of(2029, 1, 31), null, null);
      saved(m, save(m, m.markExpiryDate(), null, LocalDate.of(2025, 1, 1), null, "HX"));

      ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
      verify(jdbc, org.mockito.Mockito.atLeastOnce())
          .update(sql.capture(), any(SqlParameterSource.class));
      String pfu = sql.getAllValues().stream()
          .filter(s -> s.contains("SET file_status_st")).findFirst().orElseThrow();
      assertThat(pfu).doesNotContain("file_status_date");
    }
  }
}
