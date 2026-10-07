package ca.bc.gov.nrs.fta.mark.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

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
}
