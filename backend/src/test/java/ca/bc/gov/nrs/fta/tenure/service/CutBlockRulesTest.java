package ca.bc.gov.nrs.fta.tenure.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test | CutBlockRules")
class CutBlockRulesTest {

  private static CutBlockRules.Facts facts(String blockStatus) {
    return new CutBlockRules.Facts("A01", "HI", "HI", blockStatus, true, false, false, false, false);
  }

  private static CutBlockRules.Edit edit(String status, String net, String gross) {
    return new CutBlockRules.Edit(
        status, "SSS", null, new BigDecimal(gross), new BigDecimal(net), null, null, null, "N", null);
  }

  @Nested
  @DisplayName("FTA904's buttons and locks, without its organization checks")
  class Rules {

    @Test
    void aViewerCannotEdit() {
      assertThat(CutBlockRules.of(facts("HB"), false, false).canEdit()).isFalse();
    }

    @Test
    void districtStaffEditButGetNoSeniorAction() {
      CutBlockRules rules = CutBlockRules.of(facts("HB"), true, false);
      assertThat(rules.canEdit()).isTrue();
      assertThat(rules.canRelabel()).isFalse();
      assertThat(rules.canSuspend()).isFalse();
      assertThat(rules.canEditSuspension()).isFalse();
      assertThat(rules.seniorAuthority()).isFalse();
    }

    @Test
    void aPeBlockCannotBeEditedOrItsStatusChanged() {
      CutBlockRules rules = CutBlockRules.of(facts("PE"), true, true);
      assertThat(rules.canEdit()).isFalse();
      assertThat(rules.statusLocked()).isTrue();
    }

    @Test
    void oilAndGasFilesStayReadOnly() {
      CutBlockRules.Facts a11 =
          new CutBlockRules.Facts("A11", "HI", "HI", "HB", true, false, false, false, false);
      assertThat(CutBlockRules.of(a11, true, true).canEdit()).isFalse();
    }

    @Test
    void aBlockWithNoAdminRowCannotBeEdited() {
      CutBlockRules.Facts noAdmin =
          new CutBlockRules.Facts("A01", "HI", "HI", "HB", false, false, false, false, false);
      assertThat(CutBlockRules.of(noAdmin, true, true).canEdit()).isFalse();
    }

    @Test
    void aClosedFileLocksTheStatus() {
      CutBlockRules.Facts closed =
          new CutBlockRules.Facts("A01", "HC", "HI", "HB", true, false, false, false, false);
      assertThat(CutBlockRules.of(closed, true, true).statusLocked()).isTrue();
    }

    @Test
    void aMapFeatureLocksPlannedAndAnOpeningLocksDisturbance() {
      CutBlockRules.Facts spatial =
          new CutBlockRules.Facts("A01", "HI", "HI", "HB", true, true, false, false, false);
      CutBlockRules.Facts opened =
          new CutBlockRules.Facts("A01", "HI", "HI", "HB", true, false, true, false, false);
      assertThat(CutBlockRules.of(spatial, true, true).plannedLocked()).isTrue();
      assertThat(CutBlockRules.of(opened, true, true).disturbanceLocked()).isTrue();
      assertThat(CutBlockRules.of(opened, true, true).spExemptLocked()).isTrue();
    }

    @Test
    void aRetiredPermitOrPeFileBlocksRelabel() {
      CutBlockRules.Facts retired =
          new CutBlockRules.Facts("A01", "HI", "HI", "HB", true, false, false, false, true);
      CutBlockRules.Facts peFile =
          new CutBlockRules.Facts("A01", "PE", "HI", "HB", true, false, false, false, false);
      assertThat(CutBlockRules.of(facts("HB"), true, true).canRelabel()).isTrue();
      assertThat(CutBlockRules.of(retired, true, true).canRelabel()).isFalse();
      assertThat(CutBlockRules.of(peFile, true, true).canRelabel()).isFalse();
    }

    @Test
    void suspendIsOfferedOnSuspendableStatusesWithNoSuspensionYet() {
      CutBlockRules.Facts suspended =
          new CutBlockRules.Facts("A01", "HI", "HI", "HB", true, false, false, true, false);
      assertThat(CutBlockRules.of(facts("HB"), true, true).canSuspend()).isTrue();
      assertThat(CutBlockRules.of(facts("PP"), true, true).canSuspend()).isTrue();
      assertThat(CutBlockRules.of(facts("LC"), true, true).canSuspend()).isFalse();
      assertThat(CutBlockRules.of(suspended, true, true).canSuspend()).isFalse();
    }

    @Test
    void editSuspensionIsOfferedOnlyOnASuspendedBlock() {
      assertThat(CutBlockRules.of(facts("HS"), true, true).canEditSuspension()).isTrue();
      assertThat(CutBlockRules.of(facts("HB"), true, true).canEditSuspension()).isFalse();
    }
  }

  @Nested
  @DisplayName("FTA904's Save validators")
  class EditProblems {

    @Test
    void aGoodEditHasNoProblems() {
      assertThat(CutBlockRules.editProblems(edit("HB", "1", "2"), facts("HB"))).isEmpty();
    }

    @Test
    void netMustNotExceedGross() {
      assertThat(CutBlockRules.editProblems(edit("HB", "3", "2"), facts("HB")))
          .containsExactly("Planned net area must be less than or equal to planned gross area.");
    }

    @Test
    void areasMustBeInRange() {
      assertThat(CutBlockRules.editProblems(edit("HB", "-1", "10000000"), facts("HB")))
          .hasSize(2);
    }

    @Test
    void loggingCompleteNeedsLoggedAreaAndCompleteDate() {
      assertThat(CutBlockRules.editProblems(edit("LC", "1", "2"), facts("HB")))
          .containsExactly(
              "Actual Logged Area (ha) is required if status is LC.",
              "Complete Date is mandatory.");
    }

    @Test
    void anOpeningWaivesTheLoggingChecks() {
      CutBlockRules.Facts opened =
          new CutBlockRules.Facts("A01", "HI", "HI", "HB", true, false, true, false, false);
      assertThat(CutBlockRules.editProblems(edit("LC", "1", "2"), opened)).isEmpty();
    }

    @Test
    void startMustBeBeforeComplete() {
      LocalDate day = LocalDate.of(2026, 1, 1);
      CutBlockRules.Edit e = new CutBlockRules.Edit(
          "HB", "SSS", null, BigDecimal.TEN, BigDecimal.ONE, null, day, day, "N", null);
      assertThat(CutBlockRules.editProblems(e, facts("HB")))
          .containsExactly("Start Date must be less than Complete Date.");
    }

    @Test
    void cutRegulationMustBeBlankOnNonSalvagePermits() {
      CutBlockRules.Edit e = new CutBlockRules.Edit(
          "HB", null, "X", BigDecimal.TEN, BigDecimal.ONE, null, null, null, "N", null);
      assertThat(CutBlockRules.editProblems(e, facts("HB"))).hasSize(1);
    }

    @Test
    void descriptionIsLimited() {
      CutBlockRules.Edit e = new CutBlockRules.Edit(
          "HB", "SSS", null, BigDecimal.TEN, BigDecimal.ONE, null, null, null, "N", "x".repeat(121));
      assertThat(CutBlockRules.editProblems(e, facts("HB"))).hasSize(1);
    }
  }
}
