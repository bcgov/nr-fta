package ca.bc.gov.nrs.fta.tenure.dto;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitRules.Facts;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test | CuttingPermitRules")
class CuttingPermitRulesTest {

  private static final LocalDate ISSUED = LocalDate.of(2020, 1, 1);
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);

  private static Facts facts(String type, String status, boolean editor, boolean senior) {
    return new Facts(type, status, false, ISSUED, null, false, false, false, editor, senior, false);
  }

  private static CuttingPermitRules district(String status) {
    return CuttingPermitRules.of(facts("A01", status, true, false));
  }

  private static CuttingPermitRules senior(String status) {
    return CuttingPermitRules.of(facts("A01", status, true, true));
  }

  @Test
  void aReaderGetsNothing() {
    CuttingPermitRules r = CuttingPermitRules.of(facts("A01", "HI", false, false));
    assertThat(r.canSave()).isFalse();
    assertThat(r.canRetire()).isFalse();
    assertThat(r.canPostpone()).isFalse();
    assertThat(r.canSuspendBlocks()).isFalse();
  }

  @Test
  void oilAndGasStaysReadOnly() {
    CuttingPermitRules r = CuttingPermitRules.of(facts("A11", "HI", true, true));
    assertThat(r.canSave()).isFalse();
    assertThat(r.canDelete()).isFalse();
    assertThat(r.canSuspendBlocks()).isFalse();
  }

  @Test
  void savingStopsAtEeAndPe() {
    assertThat(district("EE").canSave()).isFalse();
    assertThat(district("PE").canSave()).isFalse();
    assertThat(district("HI").canSave()).isTrue();
  }

  @Test
  void onlyASeniorUserDeletesAndOnlyAnEePermit() {
    assertThat(senior("EE").canDelete()).isTrue();
    assertThat(senior("HI").canDelete()).isFalse();
    assertThat(district("EE").canDelete()).isFalse();
  }

  @Test
  void issuedTermsAreLockedForDistrictStaff() {
    assertThat(district("HI").termsLocked()).isTrue();
    assertThat(district("HN").termsLocked()).isFalse();
    assertThat(district("PP").termsLocked()).isFalse();
    assertThat(senior("HI").termsLocked()).isFalse();
  }

  @Test
  void hpLocksTermsForEveryone() {
    assertThat(senior("HP").termsLocked()).isTrue();
  }

  @Test
  void extensionNeedsAnIssuedStatusOtherThanHp() {
    assertThat(district("HI").canEditExtend()).isTrue();
    assertThat(district("HP").canEditExtend()).isFalse();
    assertThat(district("PP").canEditExtend()).isFalse();
  }

  @Test
  void retireFollowsSetRetiredInd() {
    assertThat(district("HC").canRetire()).isTrue();
    assertThat(district("HRS").canRetire()).isTrue();
    assertThat(district("HI").canRetire()).isFalse();
    assertThat(CuttingPermitRules.of(new Facts("A01", "HC", false, ISSUED, TODAY, false, false,
        false, true, false, false)).canRetire()).isFalse();
    assertThat(CuttingPermitRules.of(new Facts("A01", "HC", false, ISSUED, null, true, false,
        false, true, false, false)).canRetire()).isFalse();
  }

  @Test
  void areaIsEditableOnlyWithoutSpatialBeforeIssue() {
    assertThat(district("PP").canEditArea()).isTrue();
    assertThat(district("HI").canEditArea()).isFalse();
    assertThat(CuttingPermitRules.of(new Facts("A01", "PP", false, ISSUED, null, false, true,
        false, true, false, false)).canEditArea()).isFalse();
  }

  @Test
  void assignMarksIsAnFsjPermitsOnly() {
    Facts fsj = new Facts("A01", "HI", true, ISSUED, null, false, false, false, true, false, false);
    assertThat(CuttingPermitRules.of(fsj).canAssignMarks()).isTrue();
    assertThat(CuttingPermitRules.of(fsj).canEditMarking()).isFalse();
    assertThat(district("HI").canAssignMarks()).isFalse();
  }

  @Test
  void postponementsAreSeniorAndNeedHiOrHp() {
    assertThat(senior("HI").canPostpone()).isTrue();
    assertThat(senior("HP").canPostpone()).isTrue();
    assertThat(senior("PP").canPostpone()).isFalse();
    assertThat(district("HI").canPostpone()).isFalse();
    assertThat(CuttingPermitRules.postponeProblem("HI", LocalDate.of(2003, 11, 3), false))
        .isEqualTo(CuttingPermitRules.MSG_HP_ISSUE);
    assertThat(CuttingPermitRules.postponeProblem("HI", ISSUED, true))
        .isEqualTo(CuttingPermitRules.MSG_HP_RESCINDED);
  }

  @Test
  void hpStatusChangesFollowHpStatusCheck() {
    assertThat(CuttingPermitRules.statusChangeProblem("HI", "HC", false, null, TODAY)).isNull();
    assertThat(CuttingPermitRules.statusChangeProblem("HP", "HP", false, null, TODAY)).isNull();
    assertThat(CuttingPermitRules.statusChangeProblem("HI", "HP", false, null, TODAY))
        .isEqualTo("Status cannot be changed from HI to HP without Forest Senior Admin Role or"
            + " BCTS Role.");
    assertThat(CuttingPermitRules.statusChangeProblem("HI", "HP", true, null, TODAY))
        .isEqualTo(CuttingPermitRules.MSG_HP_NOT_CREATED);
    assertThat(CuttingPermitRules.statusChangeProblem("HP", "HC", true, null, TODAY))
        .isEqualTo(CuttingPermitRules.MSG_HP_TO);
    assertThat(CuttingPermitRules.statusChangeProblem("HP", "HI", true, TODAY, TODAY))
        .isEqualTo(CuttingPermitRules.MSG_HP_NOT_EXPIRED);
    assertThat(CuttingPermitRules.statusChangeProblem("HP", "HI", true, TODAY.minusDays(1), TODAY))
        .isNull();
  }

  @Test
  void rescindingIsWithinTheTermUnlessAlwaysAllowed() {
    LocalDate start = TODAY.minusDays(1);
    LocalDate expiry = TODAY.plusDays(1);
    assertThat(CuttingPermitRules.rescindProblem(false, start, expiry, TODAY)).isNull();
    assertThat(CuttingPermitRules.rescindProblem(false, start, TODAY, TODAY))
        .isEqualTo(CuttingPermitRules.MSG_RESCIND_TERM);
    assertThat(CuttingPermitRules.rescindProblem(true, start, TODAY, TODAY)).isNull();
  }
}
