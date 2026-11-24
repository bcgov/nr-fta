package ca.bc.gov.nrs.fta.tenure.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Legacy FTA100 / ADD_NEW_REC's file-type rules for a new tenure. */
@DisplayName("Unit Test | TenureCreateRules")
class TenureCreateRulesTest {

  @Test
  void onlyFourTypesEnterTheirIdAndB10MayLeaveItBlank() {
    assertThat(TenureCreateRules.entersId("A02")).isTrue();
    assertThat(TenureCreateRules.entersId("A06")).isTrue();
    assertThat(TenureCreateRules.entersId("A30")).isTrue();
    assertThat(TenureCreateRules.entersId("B40")).isTrue();
    assertThat(TenureCreateRules.entersId("A01")).isFalse();
    assertThat(TenureCreateRules.entersId("B10")).isFalse();
    assertThat(TenureCreateRules.optionalId("B10")).isTrue();
  }

  @Test
  void aNewFileStartsPpWhereLegacyAllowsItAndPaOtherwise() {
    assertThat(TenureCreateRules.initialStatus("A01", false)).isEqualTo("PP");
    assertThat(TenureCreateRules.initialStatus("U1", false)).isEqualTo("PP");
    assertThat(TenureCreateRules.initialStatus("B07", false)).isEqualTo("PP");
    assertThat(TenureCreateRules.initialStatus("S01", true)).isEqualTo("PP");
    assertThat(TenureCreateRules.initialStatus("B10", false)).isEqualTo("PA");
    assertThat(TenureCreateRules.initialStatus("S01", false)).isEqualTo("PA");
  }

  @Test
  void adminOrgAndDistrictShowAsInFta100() {
    assertThat(TenureCreateRules.showsAdminOrg("A01")).isTrue();
    assertThat(TenureCreateRules.showsAdminOrg("U1")).isFalse();
    assertThat(TenureCreateRules.showsAdminOrg("U2")).isFalse();
    assertThat(TenureCreateRules.showsAdminOrg("U3")).isTrue();
    assertThat(TenureCreateRules.showsAdminOrg("P01")).isTrue();
    assertThat(TenureCreateRules.showsAdminOrg("B07")).isFalse();
    assertThat(TenureCreateRules.showsDistrict("A01")).isFalse();
    assertThat(TenureCreateRules.showsDistrict("P01")).isFalse();
    assertThat(TenureCreateRules.showsDistrict("U1")).isTrue();
    assertThat(TenureCreateRules.requiredAdminOrgLevel("A04")).isEqualTo("D");
    assertThat(TenureCreateRules.requiredAdminOrgLevel("A11")).isEqualTo("D");
    assertThat(TenureCreateRules.requiredAdminOrgLevel("A01")).isEqualTo("R");
    assertThat(TenureCreateRules.requiredAdminOrgLevel("A18")).isEqualTo("R");
  }

  @Test
  void theStoredAdminOrgIsTheDistrictForUbsehcFiles() {
    assertThat(TenureCreateRules.storedAdminOrg("U1", "10", "20", "1")).isEqualTo("20");
    assertThat(TenureCreateRules.storedAdminOrg("B07", "", "20", "1")).isEqualTo("20");
    assertThat(TenureCreateRules.storedAdminOrg("A01", "10", "", "1")).isEqualTo("10");
    assertThat(TenureCreateRules.storedAdminOrg("R01", "", "20", "1")).isEqualTo("1");
  }

  @Test
  void licencesToCutGetTheirTimberMarkAndHauling() {
    assertThat(TenureCreateRules.timberMark("B05", "L12345")).isEqualTo("L12345");
    assertThat(TenureCreateRules.timberMark("B07", "D12345")).isEqualTo("12345");
    assertThat(TenureCreateRules.salvageType("SS")).isEqualTo("SSS");
    assertThat(TenureCreateRules.salvageType("OT")).isNull();
    assertThat(TenureCreateRules.createsHaulingAuthority("B05", "DT")).isFalse();
    assertThat(TenureCreateRules.createsHaulingAuthority("B07", "DT")).isTrue();
    assertThat(TenureCreateRules.createsHaulingAuthority("B06", "OT")).isTrue();
  }

  @Test
  void tenureTermForAandUfilesAndB08() {
    assertThat(TenureCreateRules.createsTenureTerm("A04")).isTrue();
    assertThat(TenureCreateRules.createsTenureTerm("U2")).isTrue();
    assertThat(TenureCreateRules.createsTenureTerm("B08")).isTrue();
    assertThat(TenureCreateRules.createsTenureTerm("B07")).isFalse();
  }

  @Test
  void idFormatsAndTheA02Designate() {
    assertThat(TenureCreateRules.fileIdFormatError("A06", "T1234")).isNull();
    assertThat(TenureCreateRules.fileIdFormatError("A06", "T123")).isNotNull();
    assertThat(TenureCreateRules.fileIdFormatError("A02", "TFL48")).isNull();
    assertThat(TenureCreateRules.fileIdFormatError("A02", "TF48")).isNotNull();
    assertThat(TenureCreateRules.a30FormatError("TKA01")).isNull();
    assertThat(TenureCreateRules.a30FormatError("XKA01")).isEqualTo("File must begin with T.");
    assertThat(TenureCreateRules.a30FormatError("TKA0")).isNotNull();
    assertThat(TenureCreateRules.a30District("TKA01")).isEqualTo("DKA");
    assertThat(TenureCreateRules.a02Designate("TFL48")).isEqualTo("48/");
    assertThat(TenureCreateRules.a02MgmtUnitMatches("TFL48", "T", "48")).isTrue();
    assertThat(TenureCreateRules.a02MgmtUnitMatches("TFL48", "T", "49")).isFalse();
    assertThat(TenureCreateRules.a02MgmtUnitMatches("TFL48", "U", "01")).isTrue();
  }

  @Test
  void harvestSaleCodesFollowCreateHarvestSale() {
    assertThat(TenureCreateRules.harvestSale("A03", null))
        .isEqualTo(new TenureCreateRules.HarvestSale("T", "SL", "N", "N"));
    assertThat(TenureCreateRules.harvestSale("A04", null))
        .isEqualTo(new TenureCreateRules.HarvestSale(null, null, "N", null));
    assertThat(TenureCreateRules.harvestSale("A31", null).salvageInd()).isEqualTo("Y");
    assertThat(TenureCreateRules.harvestSale("B07", "SS"))
        .isEqualTo(new TenureCreateRules.HarvestSale("D", null, "Y", null));
    assertThat(TenureCreateRules.harvestSale("B02", null).saleType()).isEqualTo("SP");
    assertThat(TenureCreateRules.harvestSale("A23", null).plannedCategory()).isEqualTo("2");
  }

  @Test
  void designatesForNewWoodlotsAndTimberLicencesInPpOrPl() {
    assertThat(TenureCreateRules.assignsWoodlotOrTlDesignate("A04", "PP")).isTrue();
    assertThat(TenureCreateRules.assignsWoodlotOrTlDesignate("A29", "PL")).isTrue();
    assertThat(TenureCreateRules.assignsWoodlotOrTlDesignate("A04", "PA")).isFalse();
    assertThat(TenureCreateRules.assignsWoodlotOrTlDesignate("A01", "PP")).isFalse();
  }
}
