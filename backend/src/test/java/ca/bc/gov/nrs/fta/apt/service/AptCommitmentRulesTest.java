package ca.bc.gov.nrs.fta.apt.service;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.apt.dto.AptCommitmentDto;
import ca.bc.gov.nrs.fta.apt.dto.AptCommitmentRequest;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("Unit Test | AptCommitmentRules")
class AptCommitmentRulesTest {

  private static final LocalDate D = LocalDate.of(2026, 1, 1);

  private static AptCommitmentRequest req(String ffid, String part, String type, String aac,
      String incl, LocalDate det, LocalDate eff, String reason) {
    return new AptCommitmentRequest(5L, ffid, part, type, aac == null ? null : new BigDecimal(aac),
        incl, det, eff, null, reason);
  }

  private static AptCommitmentRequest ok(String reason) {
    return req("A12345", "C", "FL", "1000", "Y", D, D, reason);
  }

  @Test
  void aCompleteNewCommitmentPassesWithoutAReason() {
    assertThat(AptCommitmentRules.problem(ok(null), false)).isNull();
  }

  @Test
  void aChangeNeedsAReason() {
    assertThat(AptCommitmentRules.problem(ok(" "), true)).isEqualTo("Reason is required.");
    assertThat(AptCommitmentRules.problem(ok("fix"), true)).isNull();
  }

  @Test
  void requiredFieldsAreNamed() {
    assertThat(AptCommitmentRules.problem(null, false)).isNotNull();
    assertThat(AptCommitmentRules.problem(req(" ", "C", "FL", "1", "Y", D, D, null), false))
        .contains("Forest File ID");
    assertThat(AptCommitmentRules.problem(req("A1", null, "FL", "1", "Y", D, D, null), false))
        .contains("Partition");
    assertThat(AptCommitmentRules.problem(req("A1", "C", "", "1", "Y", D, D, null), false))
        .contains("Type");
    assertThat(AptCommitmentRules.problem(req("A1", "C", "FL", null, "Y", D, D, null), false))
        .isEqualTo("AAC is required.");
    assertThat(AptCommitmentRules.problem(req("A1", "C", "FL", "1", "X", D, D, null), false))
        .contains("Y or N");
    assertThat(AptCommitmentRules.problem(req("A1", "C", "FL", "1", "N", null, D, null), false))
        .contains("Determination");
    assertThat(AptCommitmentRules.problem(req("A1", "C", "FL", "1", "N", D, null, null), false))
        .contains("Effective");
  }

  @Test
  void aacIsAWholeNumberWithinTheColumn() {
    assertThat(AptCommitmentRules.problem(req("A1", "C", "FL", "-1", "Y", D, D, null), false))
        .contains("between");
    assertThat(AptCommitmentRules.problem(
        req("A1", "C", "FL", "1000000000", "Y", D, D, null), false)).contains("between");
    assertThat(AptCommitmentRules.problem(req("A1", "C", "FL", "999999999", "Y", D, D, null),
        false)).isNull();
    assertThat(AptCommitmentRules.problem(req("A1", "C", "FL", "10.5", "Y", D, D, null), false))
        .contains("whole");
    assertThat(AptCommitmentRules.problem(req("A1", "C", "FL", "10.00", "Y", D, D, null), false))
        .isNull();
  }

  @Test
  void theReasonFitsTheJournalNote() {
    String max = "x".repeat(AptCommitmentRules.MAX_REASON);
    assertThat(AptCommitmentRules.problem(ok(max), true)).isNull();
    assertThat(AptCommitmentRules.problem(ok(max + "x"), true)).contains("240");
    assertThat(AptCommitmentRules.reasonProblem(null)).isNull();
  }

  @Test
  void onlyARealRowIdReachesTheDynamicSql() {
    assertThat(AptCommitmentRules.isRowId("AAAR3sAAEAAAACXAAA")).isTrue();
    assertThat(AptCommitmentRules.isRowId("AAAR3sAAE+AA/CXAAA")).isTrue();
    assertThat(AptCommitmentRules.isRowId("' OR 1=1 --")).isFalse();
    assertThat(AptCommitmentRules.isRowId("AAAR3sAAEAAAACXAA'")).isFalse();
    assertThat(AptCommitmentRules.isRowId("AAAR3sAAEAAAACXAAAA")).isFalse();
    assertThat(AptCommitmentRules.isRowId(null)).isFalse();
  }

  @Test
  void aCommitmentRepeatedPerClientIsListedOnce() {
    AptCommitmentDto a = row(1L, "Client A");
    AptCommitmentDto b = row(1L, "Client B");
    AptCommitmentDto c = row(2L, "Client A");
    assertThat(AptCommitmentRules.distinctById(List.of(a, b, c))).containsExactly(a, c);
  }

  @Test
  void aDatabaseRefusalIsShownAsOracleWordedIt() {
    ResponseStatusException e = AptCommitmentRules.refusal(new DataIntegrityViolationException(
        "x", new SQLException("ORA-12899: value too large for column\n\nhttps://help", "72000",
            12899)));
    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(e.getReason()).isEqualTo("ORA-12899: value too large for column");
  }

  @Test
  void duplicatesAndChildRecordBlocksAre409() {
    assertThat(AptCommitmentRules.refusal(new SQLException("ORA-00001: x", "23000", 1))
        .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(AptCommitmentRules.refusal(new DataIntegrityViolationException("x",
        new SQLException("ORA-02292: x", "23000", 2292))).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(AptCommitmentRules.refusal(new SQLException("ORA-02291: x", "23000", 2291))
        .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  private static AptCommitmentDto row(Long id, String client) {
    return new AptCommitmentDto(id, "A1", client, "C", "Conventional", "FL", BigDecimal.TEN, "Y",
        D, D, null, null, null, null);
  }
}
