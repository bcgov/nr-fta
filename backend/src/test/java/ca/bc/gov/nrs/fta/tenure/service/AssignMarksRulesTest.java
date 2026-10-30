package ca.bc.gov.nrs.fta.tenure.service;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.tenure.dto.AssignMarksRequest;
import ca.bc.gov.nrs.fta.tenure.dto.AssignMarksRequest.BlockAssignment;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test | AssignMarksRules")
class AssignMarksRulesTest {

  private static AssignMarksRequest one(String mark, String created) {
    return new AssignMarksRequest("F1", 1L, null,
        List.of(new BlockAssignment(9L, mark, created, 3L)));
  }

  @Test
  void anExistingMarkIsOpenToBothRoles() {
    assertThat(AssignMarksRules.problem(one("AB1", null), false)).isNull();
    assertThat(AssignMarksRules.problem(one("AB1", null), true)).isNull();
  }

  @Test
  void aNewMarkIsSeniorOnly() {
    assertThat(AssignMarksRules.problem(one(null, "AB2"), true)).isNull();
    assertThat(AssignMarksRules.problem(one(null, "AB2"), false)).startsWith("Specify New");
  }

  @Test
  void exactlyOneOfTheMarksIsGiven() {
    assertThat(AssignMarksRules.problem(one("AB1", "AB2"), true)).contains("not both");
    assertThat(AssignMarksRules.problem(one(" ", null), true)).contains("not both");
  }

  @Test
  void aNewMarkFitsLegacysField() {
    assertThat(AssignMarksRules.problem(one(null, "ABCDEFG"), true)).contains("at most");
  }

  @Test
  void assignToAllStandsAlone() {
    AssignMarksRequest all = new AssignMarksRequest("F1", 1L, "AB1", List.of());
    AssignMarksRequest both = new AssignMarksRequest("F1", 1L, "AB1",
        List.of(new BlockAssignment(9L, "AB1", null, 3L)));
    assertThat(AssignMarksRules.problem(all, false)).isNull();
    assertThat(AssignMarksRules.problem(both, false)).contains("not both");
  }

  @Test
  void nothingToDoIsRefused() {
    assertThat(AssignMarksRules.problem(new AssignMarksRequest("F1", 1L, null, null), true))
        .isEqualTo("No changes made.");
  }

  @Test
  void anAssignmentNeedsItsBlockAndRevision() {
    AssignMarksRequest q = new AssignMarksRequest("F1", 1L, null,
        List.of(new BlockAssignment(9L, "AB1", null, null)));
    assertThat(AssignMarksRules.problem(q, true)).contains("revision");
  }
}
