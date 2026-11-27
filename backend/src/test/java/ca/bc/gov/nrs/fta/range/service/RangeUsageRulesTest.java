package ca.bc.gov.nrs.fta.range.service;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.range.dto.RangeUsageDtos.CopyRequest;
import ca.bc.gov.nrs.fta.range.dto.RangeUsageDtos.SaveRequest;
import ca.bc.gov.nrs.fta.range.service.RangeUsageRules.Context;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test | RangeUsageRules (Fta613RangeUsageForm)")
class RangeUsageRulesTest {

  static Context grazing() {
    return new Context("E01", "HI", Set.of("INC"), Set.of("NU1"), null, null);
  }

  static Context hay() {
    return new Context("H01", "HI", Set.of("INC"), Set.of("NU1"), null, null);
  }

  static SaveRequest row(String auth, String inc, String incReason, String agreement,
      String billable, String nonBillable, String reason) {
    return new SaveRequest(auth, inc, incReason, agreement, billable, nonBillable, reason, null);
  }

  static SaveRequest valid() {
    return row("100", "", "", "", "", "", "");
  }

  @Nested
  class Save {

    @Test
    void aPlainAuthorizedUsePasses() {
      assertThat(RangeUsageRules.validateSave(2025, valid(), grazing())).isEmpty();
    }

    @Test
    void peStatusRefusesSave() {
      Context pe = new Context("E01", "PE", Set.of(), Set.of(), null, null);
      assertThat(RangeUsageRules.validateSave(2025, valid(), pe))
          .containsExactly(RangeUsageRules.NO_SAVE_WHEN_PE);
    }

    @Test
    void authorizedUseIsMandatoryAndAnIntegerInRange() {
      assertThat(RangeUsageRules.validateSave(2025, row("", "", "", "", "", "", ""), grazing()))
          .containsExactly("Authorized Use is mandatory.");
      assertThat(RangeUsageRules.validateSave(2025, row("1.5", "", "", "", "", "", ""),
          grazing())).containsExactly("Authorized Use must be an integer.");
      assertThat(RangeUsageRules.validateSave(2025, row("100000", "", "", "", "", "", ""),
          grazing())).containsExactly("Authorized Use field must be between 0 and 99999.");
    }

    @Test
    void theOtherAmountsAreIntegersFrom0To99999() {
      assertThat(RangeUsageRules.validateSave(2025,
          row("100", "x", "INC", "-1", "", "", ""), grazing()))
          .contains("Temporary Increase Amount must be an integer.",
              "Non-Use Agreement No field must be between 0 and 99999.");
    }

    @Test
    void theNonBillableLabelFollowsTheFileType() {
      assertThat(RangeUsageRules.validateSave(2025,
          row("100", "", "", "1", "", "100000", "NU1"), grazing()))
          .contains("Non-Use Non-Billable field must be between 0 and 99999.");
      assertThat(RangeUsageRules.validateSave(2025,
          row("100", "", "", "1", "", "100000", "NU1"), hay()))
          .contains("Temporary Reduction field must be between 0 and 99999.");
    }

    @Test
    void increaseAndItsReasonGoTogether() {
      String msg = "If either Temporary Increase amount or Increase Reason are entered they both"
          + " must contain values.";
      assertThat(RangeUsageRules.validateSave(2025, row("100", "5", "", "", "", "", ""),
          grazing())).containsExactly(msg);
      assertThat(RangeUsageRules.validateSave(2025, row("100", "0", "INC", "", "", "", ""),
          grazing())).containsExactly(msg);
      assertThat(RangeUsageRules.validateSave(2025, row("100", "5", "INC", "", "", "", ""),
          grazing())).isEmpty();
    }

    @Test
    void grazingNonUseNeedsAnAgreementNumber() {
      assertThat(RangeUsageRules.validateSave(2025, row("100", "", "", "", "10", "", ""),
          grazing()))
          .containsExactly("If Billable or Non-Billable non-use is provided, Agreement No. must"
              + " be provided.");
      assertThat(RangeUsageRules.validateSave(2025, row("100", "", "", "7", "10", "", ""),
          grazing())).isEmpty();
    }

    @Test
    void grazingAgreementAndReasonMustBeBlankWithNoNonUse() {
      assertThat(RangeUsageRules.validateSave(2025, row("100", "", "", "7", "0", "", ""),
          grazing()))
          .containsExactly("Agreement No. and Non-Billable Reason must be blank when Billable or"
              + " Non-Billable non-use is 0.");
    }

    @Test
    void hayReductionNeedsAnAgreementNumber() {
      assertThat(RangeUsageRules.validateSave(2025, row("100", "", "", "", "", "10", "NU1"),
          hay()))
          .containsExactly("If Temporary Reduction amount is provided, Agreement No. must be"
              + " provided.");
      // The grazing-only checks do not apply to hay.
      assertThat(RangeUsageRules.validateSave(2025, row("100", "", "", "7", "", "", ""), hay()))
          .isEmpty();
    }

    @Test
    void nonBillableAndItsReasonGoTogether() {
      assertThat(RangeUsageRules.validateSave(2025, row("100", "", "", "7", "", "10", ""),
          grazing()))
          .containsExactly("If either Non-Use Non-Billable amount or Non-Use Non-Billable Reason"
              + " are entered they both must contain values.");
      assertThat(RangeUsageRules.validateSave(2025, row("100", "", "", "7", "", "10", ""),
          hay()))
          .containsExactly("If either Temporary Reduction amount or Temporary Reduction Reason"
              + " are entered they both must contain values.");
    }

    @Test
    void increaseAndNonUseAreNotAllowedTogether() {
      assertThat(RangeUsageRules.validateSave(2025, row("100", "5", "INC", "7", "10", "", ""),
          grazing()))
          .containsExactly("You may only have a Temporary Increase or a Non-Use. Not both."
              + " Please update and re-submit.");
    }

    @Test
    void nonUseCannotExceedTheAuthorizedUse() {
      assertThat(RangeUsageRules.validateSave(2025, row("10", "", "", "7", "6", "5", "NU1"),
          grazing()))
          .containsExactly("The Non-Use amount cannot be greater than the authorized_use for the"
              + " year");
      assertThat(RangeUsageRules.validateSave(2025, row("11", "", "", "7", "6", "5", "NU1"),
          grazing())).isEmpty();
    }

    @Test
    void anExpiredCodeIsRefusedUnlessTheRowHoldsIt() {
      SaveRequest r = row("100", "5", "OLD", "", "", "", "");
      assertThat(RangeUsageRules.validateSave(2025, r, grazing()))
          .containsExactly("Increase Reason (OLD) is an expired code.");
      Context holds = new Context("E01", "HI", Set.of("INC"), Set.of(), "OLD", null);
      assertThat(RangeUsageRules.validateSave(2025, r, holds)).isEmpty();
    }
  }

  @Nested
  class Copy {

    @Test
    void allRemainingYearsOrFromAndToButNotBoth() {
      assertThat(RangeUsageRules.validateCopy(2025, new CopyRequest(true, null, null), valid(),
          grazing())).isEmpty();
      assertThat(RangeUsageRules.validateCopy(2025, new CopyRequest(false, "2026", "2028"),
          valid(), grazing())).isEmpty();
      assertThat(RangeUsageRules.validateCopy(2025, new CopyRequest(true, "2026", ""), valid(),
          grazing())).containsExactly(RangeUsageRules.COPY_ALL_AND_YEARS);
      assertThat(RangeUsageRules.validateCopy(2025, new CopyRequest(false, "", ""), valid(),
          grazing())).containsExactly(RangeUsageRules.COPY_NEITHER);
      assertThat(RangeUsageRules.validateCopy(2025, new CopyRequest(false, "2026", ""), valid(),
          grazing())).containsExactly(RangeUsageRules.COPY_BOTH_YEARS);
    }

    @Test
    void fromMustNotBeAfterTo() {
      assertThat(RangeUsageRules.validateCopy(2025, new CopyRequest(false, "2028", "2026"),
          valid(), grazing())).containsExactly(RangeUsageRules.COPY_FROM_AFTER_TO);
      assertThat(RangeUsageRules.validateCopy(2025, new CopyRequest(false, "2026", "2026"),
          valid(), grazing())).isEmpty();
    }

    @Test
    void yearsLieBetween1000And99999() {
      assertThat(RangeUsageRules.validateCopy(2025, new CopyRequest(false, "999", "2026"),
          valid(), grazing()))
          .containsExactly("Year Source field must be between 1000 and 99999.");
    }

    @Test
    void copyRunsTheFieldChecksButNotThePeCheck() {
      Context pe = new Context("E01", "PE", Set.of(), Set.of(), null, null);
      assertThat(RangeUsageRules.validateCopy(2025, new CopyRequest(true, null, null), valid(),
          pe)).isEmpty();
      assertThat(RangeUsageRules.validateCopy(2025, new CopyRequest(true, null, null),
          row("", "", "", "", "", "", ""), grazing()))
          .containsExactly("Authorized Use is mandatory.");
    }
  }

  @Test
  void totalAnnualUseIsAuthorizedPlusIncreaseLessNonUse() {
    assertThat(RangeUsageRules.totalAnnualUse(row("100", "20", "", "", "5", "3", "")))
        .isEqualTo(112);
    assertThat(RangeUsageRules.totalAnnualUse(row("100", "", "", "", "", "", ""))).isEqualTo(100);
    assertThat(RangeUsageRules.totalAnnualUse(row("x", "", "", "", "", "", ""))).isZero();
  }
}
