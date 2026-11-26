package ca.bc.gov.nrs.fta.shared.service;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test | OrgUnitMaintRules (SIL99)")
class OrgUnitMaintRulesTest {

  private static final CodeOptionDto DCC = new CodeOptionDto("1642", "DCC - Cariboo-Chilcotin");
  private static final CodeOptionDto DKM = new CodeOptionDto("1817", "DKM - Coast Mountains");
  private static final CodeOptionDto DQC = new CodeOptionDto("1906", "DQC - Haida Gwaii");
  private static final List<CodeOptionDto> DISTRICTS = List.of(DCC, DKM, DQC);

  @Test
  void anUnscopedAdministratorHasEveryDistrict() {
    assertThat(OrgUnitMaintRules.userOrgUnits(DISTRICTS, true, List.of()))
        .containsExactly(DCC, DKM, DQC);
  }

  @Test
  void aScopedAdministratorHasTheDistrictsTheGrantNames() {
    assertThat(OrgUnitMaintRules.userOrgUnits(DISTRICTS, false, List.of("DQC", "DCC")))
        .containsExactly(DCC, DQC);
    assertThat(OrgUnitMaintRules.userOrgUnits(DISTRICTS, false, List.of("DXX"))).isEmpty();
  }

  @Test
  void theSavedDefaultIsCheckedWhenItIsOneOfTheUsersOrgUnits() {
    assertThat(OrgUnitMaintRules.checked(DISTRICTS, "1817")).isEqualTo("1817");
  }

  @Test
  void withNoSavedDefaultTheFirstOrgUnitIsChecked() {
    assertThat(OrgUnitMaintRules.checked(DISTRICTS, null)).isEqualTo("1642");
    assertThat(OrgUnitMaintRules.checked(DISTRICTS, " ")).isEqualTo("1642");
    assertThat(OrgUnitMaintRules.checked(List.of(), null)).isNull();
  }

  @Test
  void aSavedDefaultNoLongerOneOfTheUsersOrgUnitsLeavesNoneChecked() {
    assertThat(OrgUnitMaintRules.checked(List.of(DCC), "1817")).isNull();
  }

  @Test
  void theOtherScreensUseTheSavedDefaultWhateverItIs() {
    assertThat(OrgUnitMaintRules.effective(List.of(DCC), "1817")).isEqualTo("1817");
    assertThat(OrgUnitMaintRules.effective(List.of(), "1817")).isEqualTo("1817");
  }

  @Test
  void elseTheFirstOfTheUsersOrgUnitsElseNone() {
    assertThat(OrgUnitMaintRules.effective(List.of(DKM, DQC), null)).isEqualTo("1817");
    assertThat(OrgUnitMaintRules.effective(List.of(), null)).isNull();
  }

  @Test
  void saveAcceptsOneOfTheUsersOrgUnits() {
    assertThat(OrgUnitMaintRules.validate(DISTRICTS, " 1906 ")).isEmpty();
  }

  @Test
  void saveRefusesAUserWithNoOrgUnits() {
    assertThat(OrgUnitMaintRules.validate(List.of(), "1906"))
        .containsExactly(OrgUnitMaintRules.NO_ORGS_FOR_USER);
  }

  @Test
  void saveRefusesNoOrgUnit() {
    assertThat(OrgUnitMaintRules.validate(DISTRICTS, null))
        .containsExactly(OrgUnitMaintRules.ORG_UNIT_REQUIRED);
    assertThat(OrgUnitMaintRules.validate(DISTRICTS, ""))
        .containsExactly(OrgUnitMaintRules.ORG_UNIT_REQUIRED);
  }

  @Test
  void saveRefusesAnOrgUnitThatIsNotTheUsers() {
    assertThat(OrgUnitMaintRules.validate(List.of(DCC), "1817"))
        .containsExactly("Org unit 1817 is not one of your org units.");
  }

  @Test
  void packageMessagesReadAsLegacyShowedThem() {
    assertThat(OrgUnitMaintRules.packageErrors(null)).isEmpty();
    assertThat(OrgUnitMaintRules.packageErrors("  ")).isEmpty();
    assertThat(OrgUnitMaintRules.packageErrors(
        "sil.web.usr.database.record.toomany:SIL_99_USER_ORG_DEFAULT,GET,MOF_USER_ORG_DEFAULT,-1422;"))
        .containsExactly("Multiple records were found in table MOF_USER_ORG_DEFAULT when "
            + "expecting an exact fetch by package/procedure SIL_99_USER_ORG_DEFAULT,GET.");
    assertThat(OrgUnitMaintRules.packageErrors(
        "sil.web.usr.database.invalid.function:SIL_99_USER_ORG_DEFAULT,MAINLINE,FOO;"))
        .containsExactly("Unknown database action - SIL_99_USER_ORG_DEFAULT.");
    assertThat(OrgUnitMaintRules.packageErrors("ORA-00001: something"))
        .containsExactly("ORA-00001: something");
  }

  @Test
  void anOptionsCodeIsWhatItsLabelStartsWith() {
    assertThat(OrgUnitMaintRules.codeOf(DKM)).isEqualTo("DKM");
    assertThat(OrgUnitMaintRules.codeOf(new CodeOptionDto("1", "DXX"))).isEqualTo("DXX");
  }
}
