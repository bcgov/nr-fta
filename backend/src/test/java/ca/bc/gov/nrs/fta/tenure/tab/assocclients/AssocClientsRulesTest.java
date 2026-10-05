package ca.bc.gov.nrs.fta.tenure.tab.assocclients;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Legacy FTA920's gates and database-free checks, with their message texts. */
@DisplayName("Unit Test | AssocClientsRules")
class AssocClientsRulesTest {

  private static final LocalDate JAN = LocalDate.of(2024, 1, 1);
  private static final LocalDate JUN = LocalDate.of(2024, 6, 1);

  @Test
  void viewOnlyFilesBlockEverything() {
    AssocClientsRules pm = AssocClientsRules.of("HI", true, false, true);
    assertThat(pm.canEdit()).isFalse();
    assertThat(pm.canDelete("C")).isFalse();
    assertThat(pm.editReason()).startsWith("This is a Private Mark");

    AssocClientsRules pa = AssocClientsRules.of("PA", false, false, true);
    assertThat(pa.canEdit()).isFalse();
    assertThat(pa.deleteReason()).startsWith("Cannot attach client at this time");

    assertThat(AssocClientsRules.of("HI", false, true, true).editReason())
        .isEqualTo("You may only view as file type is not supported.");
  }

  @Test
  void peAndAuthorityBlockEditsButNotDeletes() {
    AssocClientsRules pe = AssocClientsRules.of("PE", false, false, true);
    assertThat(pe.canEdit()).isFalse();
    assertThat(pe.editReason())
        .isEqualTo("No updates can be performed on this file when the status is PE.");
    assertThat(pe.canDelete("C")).isTrue();
    assertThat(pe.canDelete("A")).isFalse();

    AssocClientsRules noAuth = AssocClientsRules.of("HI", false, false, false);
    assertThat(noAuth.editReason())
        .isEqualTo("You are not authorized to attach clients at the FILE level.");
    assertThat(noAuth.canDelete("S")).isTrue();

    AssocClientsRules ok = AssocClientsRules.of("HI", false, false, true);
    assertThat(ok.canEdit()).isTrue();
    assertThat(ok.canDelete("B")).isFalse();
  }

  @Test
  void fieldChecks() {
    assertThat(AssocClientsRules.validateFields(null, null, null, null, null))
        .containsExactly(
            "Client Type is mandatory.",
            "Client Number is mandatory.",
            "Client Location Code is mandatory.");
    assertThat(AssocClientsRules.validateFields("00001234", "AB", "M", JUN, JAN))
        .containsExactly(
            "Licensee Start Date must be less than or equal to Licensee End Date.",
            "Client Location Code field must be between 0 and 99.");
    assertThat(AssocClientsRules.validateFields("00001234", "00", "O", null, null))
        .containsExactly("O-Type clients can only be attached at the Cut Block level.");
  }

  @Test
  void dateRulesByType() {
    assertThat(AssocClientsRules.dateRules("A", null, JUN))
        .containsExactly("Licensee Start Date is required.", "Licensee End Date must be blank.");
    assertThat(AssocClientsRules.dateRules("C", JAN, null))
        .containsExactly("Licensee End Date is required.");
    assertThat(AssocClientsRules.dateRules("M", JAN, null))
        .containsExactly("Licensee End Date must be entered when Licensee Start Date is.");
    assertThat(AssocClientsRules.dateRules("S", null, JUN))
        .containsExactly("If Licensee Start Date is blank, Licensee End Date must be blank.");
    assertThat(AssocClientsRules.dateRules("S", null, null)).isEmpty();
  }

  @Test
  void licenseeStartMustFollowPreviousAndCurrent() {
    assertThat(AssocClientsRules.licenseeStartProblem("A", JAN, JUN, null))
        .isEqualTo("Licensee Start Date must be after previous Licensee End Date.");
    assertThat(AssocClientsRules.licenseeStartProblem("B", JAN, null, JUN))
        .isEqualTo("Licensee Start Date must be after current Licensee Start Date.");
    assertThat(AssocClientsRules.licenseeStartProblem("A", JUN, JUN, JAN)).isNull();
    assertThat(AssocClientsRules.licenseeStartProblem("S", JAN, JUN, JUN)).isNull();
  }

  @Test
  void managerFileTypes() {
    assertThat(AssocClientsRules.managerProblem("C01", false, true, "A")).isNull();
    assertThat(AssocClientsRules.managerProblem("C01", false, true, "B"))
        .isEqualTo("For C01 files only the District Manager may be the Main Licensee.");
    assertThat(AssocClientsRules.managerProblem("B40", true, false, "A"))
        .isEqualTo("For BCTS funded B40 files only the TSO Manager may be the Main Licensee.");
    assertThat(AssocClientsRules.managerProblem("A01", false, false, "B")).isNull();
  }

  @Test
  void sTypeRule() {
    assertThat(AssocClientsRules.sTypeProblem("S", false, 0)).startsWith("You may not add S type");
    assertThat(AssocClientsRules.sTypeProblem("S", true, 1)).startsWith("Only one S type");
    assertThat(AssocClientsRules.sTypeProblem("S", true, 0)).isNull();
    assertThat(AssocClientsRules.sTypeProblem("B", false, 3)).isNull();
  }

  @Test
  void mainAndPreviousLicenseesKeepTheirIdentity() {
    assertThat(AssocClientsRules.updateIdentityRules("A", "1", "2", "A"))
        .containsExactly(
            "Cannot change the Client Number for Main or Previous Licensee (A or C type).");
    assertThat(AssocClientsRules.updateIdentityRules("A", "1", "1", "B"))
        .containsExactly(
            "Cannot change the Client Type for Main or Previous Licensee (A or C type).",
            "At least one Main Licensee (A type) required.");
    assertThat(AssocClientsRules.updateIdentityRules("B", "1", "2", "A")).isEmpty();
  }
}
