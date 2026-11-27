package ca.bc.gov.nrs.fta.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test | RangeAccess")
class RangeAccessTest {

  private static final String ADMIN = "FTA_ADMIN";
  private static final String SENIOR = "FTA_RANGE_HEADQUARTERS_ADMIN";
  private static final String DISTRICT = "FTA_RANGE_DISTRICT_ADMIN";

  @Test
  void anUnscopedAdministratorActsEverywhere() {
    List<String> roles = List.of(ADMIN);
    assertThat(RangeAccess.actsForEveryDistrict(roles)).isTrue();
    assertThat(RangeAccess.administersDistrict(roles, "DKA")).isTrue();
    assertThat(RangeAccess.mayEditDistrict(roles, "DKA")).isTrue();
  }

  @Test
  void aScopedAdministratorActsOnlyInItsDistricts() {
    List<String> roles = List.of(ADMIN + "_DISTRICT-DCC");
    assertThat(RangeAccess.actsForEveryDistrict(roles)).isFalse();
    assertThat(RangeAccess.administersDistrict(roles, "DCC")).isTrue();
    assertThat(RangeAccess.administersDistrict(roles, "DKA")).isFalse();
    assertThat(RangeAccess.mayEditDistrict(roles, "DCC")).isTrue();
    assertThat(RangeAccess.mayEditDistrict(roles, "DKA")).isFalse();
    assertThat(RangeAccess.mayEditDistrict(roles, null)).isFalse();
  }

  @Test
  void theSeniorRangeRoleActsEverywhereHoweverItIsGranted() {
    for (List<String> roles : List.of(List.of(SENIOR), List.of(SENIOR + "_DISTRICT-DCC"))) {
      assertThat(RangeAccess.isHeadquarters(roles)).isTrue();
      assertThat(RangeAccess.actsForEveryDistrict(roles)).isTrue();
      assertThat(RangeAccess.administersDistrict(roles, "DKA")).isTrue();
      assertThat(RangeAccess.mayEditDistrict(roles, "DKA")).isTrue();
    }
  }

  @Test
  void theDistrictRangeRoleEditsButAdministersNothing() {
    List<String> unscoped = List.of(DISTRICT);
    assertThat(RangeAccess.mayEditDistrict(unscoped, "DKA")).isTrue();
    assertThat(RangeAccess.administersDistrict(unscoped, "DKA")).isFalse();
    assertThat(RangeAccess.actsForEveryDistrict(unscoped)).isFalse();

    List<String> scoped = List.of(DISTRICT + "_DISTRICT-DCC", DISTRICT + "_DISTRICT-DMH");
    assertThat(RangeAccess.mayEditDistrict(scoped, "DCC")).isTrue();
    assertThat(RangeAccess.mayEditDistrict(scoped, "DMH")).isTrue();
    assertThat(RangeAccess.mayEditDistrict(scoped, "DKA")).isFalse();
    assertThat(RangeAccess.administersDistrict(scoped, "DCC")).isFalse();
  }

  @Test
  void otherRolesAndFamSidecarsGrantNothing() {
    List<String> roles =
        List.of("FTA_VIEWER", "FTA_HARVEST_AUTH_SENIOR_ADMIN", "FAM:EXPIRES:2030-01-01:" + SENIOR);
    assertThat(RangeAccess.isHeadquarters(roles)).isFalse();
    assertThat(RangeAccess.mayEditDistrict(roles, "DCC")).isFalse();
    assertThat(RangeAccess.actsForEveryDistrict(roles)).isFalse();
  }
}
