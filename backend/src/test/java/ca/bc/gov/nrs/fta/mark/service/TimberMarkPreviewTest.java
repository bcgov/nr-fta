package ca.bc.gov.nrs.fta.mark.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.mark.dto.MarkDetailDto;
import java.util.List;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The preview steps the counter exactly as legacy's ASSIGN_MARK, or it shows the wrong mark. */
@DisplayName("Unit Test | TimberMarkPreview")
class TimberMarkPreviewTest {

  @Test
  void stepsTheLastCharacter() {
    assertThat(TimberMarkPreview.next("B08", "CHYF")).isEqualTo("ECHYG");
    assertThat(TimberMarkPreview.next("B09", "CHYF")).isEqualTo("NCHYG");
  }

  @Test
  void aZRollsOverAndCarries() {
    assertThat(TimberMarkPreview.next("B08", "CHYZ")).isEqualTo("ECHZA");
    assertThat(TimberMarkPreview.next("B08", "CHZZ")).isEqualTo("ECIAA");
  }

  @Test
  void irMarksCountUp() {
    assertThat(TimberMarkPreview.next("B14", "41")).isEqualTo("IR0042");
    assertThat(TimberMarkPreview.next("B14", "999")).isEqualTo("IR1000");
  }

  @Test
  void otherTypesHaveNoPreview() {
    assertThat(TimberMarkPreview.next("B01", "CHYF")).isNull();
  }

  @Test
  void skippingTakesTheShownMarkAndPreviewsTheNext() {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    PrivateMarkPackage pkg = mock(PrivateMarkPackage.class);
    // The counter before the skip, after the skip took a number.
    when(jdbc.queryForList(anyString(), any(SqlParameterSource.class), eq(String.class)))
        .thenReturn(List.of("CHYF"), List.of("CHYG"));

    assertThat(new TimberMarkPreview(jdbc, pkg).skipFor("B08")).contains("ECHYH");
    verify(pkg).assignMark("B08");
  }

  @Test
  void aTypeWithoutMarksIsNotSkipped() {
    PrivateMarkPackage pkg = mock(PrivateMarkPackage.class);

    assertThat(new TimberMarkPreview(mock(NamedParameterJdbcTemplate.class), pkg).skipFor("B01"))
        .isEmpty();
    verify(pkg, never()).assignMark(anyString());
  }

  /** An application with the given type and client, as the Assign Mark gate reads it. */
  private static MarkDetailDto application(String status, String fileType, String client) {
    return new MarkDetailDto(
        null, "150557", null, fileType, status, null, null, null, null, null, 60, "1867",
        "DVA", client, client == null ? null : "00", "Holder", "S", "H", null, null, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, 0, null, null, null, null, 1L, null, true, List.of(), List.of(),
        List.of(), List.of(), null);
  }

  @Test
  void skippingNeedsAMarkThatAssignMarkIsOpenOn() {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    PrivateMarkPackage pkg = mock(PrivateMarkPackage.class);
    TimberMarkPreview preview = new TimberMarkPreview(jdbc, pkg);

    // No client; a type already; a district — legacy's Assign Mark was disabled for each.
    for (MarkDetailDto refused : List.of(
        application("PI", null, null), application("PI", "B08", "00001"))) {
      assertThatThrownBy(() -> preview.skipFor(refused, false, "B08"))
          .isInstanceOf(ResponseStatusException.class);
    }
    assertThatThrownBy(() -> preview.skipFor(application("PI", null, "00001"), true, "B08"))
        .isInstanceOf(ResponseStatusException.class);
    verify(pkg, never()).assignMark(anyString());
  }

  @Test
  void skippingOnAnAssignableMark() {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    PrivateMarkPackage pkg = mock(PrivateMarkPackage.class);
    when(jdbc.queryForList(anyString(), any(SqlParameterSource.class), eq(String.class)))
        .thenReturn(List.of("CHYF"), List.of("CHYG"));

    assertThat(new TimberMarkPreview(jdbc, pkg)
        .skipFor(application("PI", null, "00001"), false, "B08")).contains("ECHYH");
    verify(pkg).assignMark("B08");
  }
}
