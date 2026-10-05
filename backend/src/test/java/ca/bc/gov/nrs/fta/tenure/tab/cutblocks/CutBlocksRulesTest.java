package ca.bc.gov.nrs.fta.tenure.tab.cutblocks;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.tenure.tab.cutblocks.CutBlocksDtos.CutBlockCreateRequest;
import ca.bc.gov.nrs.fta.tenure.tab.cutblocks.CutBlocksDtos.CutBlockPermitOption;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CutBlocksRulesTest {

  private static CutBlockPermitOption permit(String problem) {
    return new CutBlockPermitOption(1L, "AB", "TM1", "HI", "SSS", problem == null, problem);
  }

  @Nested
  @DisplayName("FTA903_ADD_NEW per permit, without the district check")
  class PermitProblem {

    @Test
    void salvagePermitOfATimberTenureIsEligible() {
      assertThat(CutBlocksRules.permitProblem("A01", null, "HI", "SSS", true)).isNull();
    }

    @Test
    void nonSalvagePermitIsRefused() {
      assertThat(CutBlocksRules.permitProblem("A01", null, "HI", null, true))
          .isEqualTo(CutBlocksRules.MSG_SALVAGE);
    }

    @Test
    void pePermitIsInTheInbox() {
      assertThat(CutBlocksRules.permitProblem("A01", null, "PE", "SSS", true))
          .isEqualTo(CutBlocksRules.MSG_INBOX);
    }

    @Test
    void singleMarkOfPurposeSsNeedsNoSalvageType() {
      assertThat(CutBlocksRules.permitProblem("B07", "SS", "HI", null, true)).isNull();
      assertThat(CutBlocksRules.permitProblem("B04", "DT", "HI", null, true))
          .isEqualTo(CutBlocksRules.MSG_SALVAGE);
    }

    @Test
    void peWinsOverPurposeSs() {
      assertThat(CutBlocksRules.permitProblem("B07", "SS", "PE", null, true))
          .isEqualTo(CutBlocksRules.MSG_INBOX);
    }

    @Test
    void a11AndOtherTypesAreRefused() {
      assertThat(CutBlocksRules.permitProblem("A11", null, "HI", "SSS", true))
          .isEqualTo(CutBlocksRules.MSG_SALVAGE);
      assertThat(CutBlocksRules.permitProblem("B08", null, "HI", "SSS", true))
          .isEqualTo(CutBlocksRules.MSG_SALVAGE);
    }

    @Test
    void needsAPrimaryMark() {
      assertThat(CutBlocksRules.permitProblem("A01", null, "HI", "SSS", false))
          .isEqualTo(CutBlocksRules.MSG_NO_MARK);
    }
  }

  @Nested
  @DisplayName("tab rules")
  class Of {

    @Test
    void refusedFileTypeListsNothing() {
      CutBlocksRules r = CutBlocksRules.of("B01", "HI", false, List.of());
      assertThat(r.listable()).isFalse();
      assertThat(r.add()).isFalse();
      assertThat(r.addReason()).isEqualTo(
          "The File Type (B01) associated with the queried File is invalid for this screen.");
      assertThat(CutBlocksRules.of("R01", "HI", true, List.of()).listable()).isFalse();
    }

    @Test
    void peFileTakesNoUpdates() {
      CutBlocksRules r = CutBlocksRules.of("A01", "PE", false, List.of(permit(null)));
      assertThat(r.listable()).isTrue();
      assertThat(r.add()).isFalse();
      assertThat(r.addReason()).isEqualTo(CutBlocksRules.MSG_STATUS_PE);
    }

    @Test
    void timberTenureWithoutPermitsNeedsACp() {
      assertThat(CutBlocksRules.of("A01", "HI", false, List.of()).addReason())
          .isEqualTo(CutBlocksRules.MSG_NO_CP);
    }

    @Test
    void oneEligiblePermitEnablesAdd() {
      CutBlocksRules r = CutBlocksRules.of(
          "A01", "HI", false, List.of(permit(CutBlocksRules.MSG_SALVAGE), permit(null)));
      assertThat(r.add()).isTrue();
      assertThat(r.addReason()).isNull();
    }

    @Test
    void reasonPrefersSalvageOverInbox() {
      assertThat(CutBlocksRules.of("A01", "HI", false,
          List.of(permit(CutBlocksRules.MSG_INBOX), permit(CutBlocksRules.MSG_SALVAGE)))
          .addReason()).isEqualTo(CutBlocksRules.MSG_SALVAGE);
      assertThat(CutBlocksRules.of("A01", "HI", false, List.of(permit(CutBlocksRules.MSG_INBOX)))
          .addReason()).isEqualTo(CutBlocksRules.MSG_INBOX);
    }
  }

  @Nested
  @DisplayName("FTA903 row Delete")
  class Delete {

    @Test
    void peBlockAndRetiredPermitAndPrivateMarkAreRefused() {
      assertThat(CutBlocksRules.deleteProblem("PE", "N")).isNotNull();
      assertThat(CutBlocksRules.deleteProblem("HB", "Y")).isNotNull();
      assertThat(CutBlocksRules.deleteProblem("HB", null)).isNotNull();
      assertThat(CutBlocksRules.deleteProblem("HB", "N")).isNull();
    }
  }

  @Nested
  @DisplayName("FTA904 add-mode field checks")
  class Fields {

    private CutBlockCreateRequest req(String id, String gross, String net, String sp) {
      return new CutBlockCreateRequest(1L, id, null, null,
          gross == null ? null : new BigDecimal(gross), net == null ? null : new BigDecimal(net),
          null, sp, "Y", null, null, null);
    }

    @Test
    void acceptsAValidBlock() {
      assertThat(CutBlocksFieldChecks.problems(req("12A", "10", "8", "N"), "A01", "SSD"))
          .isEmpty();
    }

    @Test
    void mandatoryFields() {
      assertThat(CutBlocksFieldChecks.problems(req(null, null, null, null), "A01", null))
          .containsExactly(
              "Cut Block is mandatory.",
              "Planned Gross Area (ha) is mandatory.",
              "Planned Net Area (ha) is mandatory.",
              "SP Exempt is mandatory.");
    }

    @Test
    void netAtMostGross() {
      assertThat(CutBlocksFieldChecks.problems(req("1", "5", "6", "N"), "A01", null))
          .containsExactly("Planned net area must be less than or equal to planned gross area.");
    }

    @Test
    void sssBlocksAreUnderOneHectare() {
      assertThat(CutBlocksFieldChecks.problems(req("1", "1", "0.5", "N"), "A01", "SSS"))
          .containsExactly(
              "For salvage blocks the planned gross area must be less than one hectare.");
      assertThat(CutBlocksFieldChecks.problems(req("1", "0.9", "0.9", "N"), "B07", null))
          .isEmpty();
    }

    @Test
    void bbrNetMayBeExactlyOne() {
      assertThat(CutBlocksFieldChecks.problems(req("1", "0.99", "1", "N"), "A01", "BBR"))
          .containsExactly("Planned net area must be less than or equal to planned gross area.");
      assertThat(CutBlocksFieldChecks.problems(req("1", "1", "1", "N"), "A01", "BBR"))
          .containsExactly(
              "For salvage blocks the planned gross area cannot be greater than 1 hectare.");
    }

    @Test
    void lengthsAndRange() {
      assertThat(CutBlocksFieldChecks.problems(req("12345678901", "99999999", "0", "X"), "A01", null))
          .containsExactly(
              "Cut Block must not exceed 10 characters.",
              "Planned Gross Area (ha) field must be between 0 and 9999999.9999.",
              "SP Exempt must be Y or N.");
    }
  }

  @Test
  void legacyErrorsReadAsText() {
    assertThat(CutBlocksDeleteLegacy.readable(
        "fta.web.error.user.custom.msg:Cut Block cannot be deleted. It is linked to an opening;"))
        .isEqualTo("Cut Block cannot be deleted. It is linked to an opening.");
    assertThat(CutBlocksDeleteLegacy.readable(
        "fta.web.usr.database.record.modified:FTA_DELETE_FF_CP_CB,FTA_DSEEB,CUT_BLOCK;"))
        .startsWith("The block was changed");
  }
}
