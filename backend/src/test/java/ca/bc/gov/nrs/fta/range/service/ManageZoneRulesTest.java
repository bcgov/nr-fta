package ca.bc.gov.nrs.fta.range.service;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.range.dto.ManageZoneAddRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test | ManageZoneRules (FTA631R)")
class ManageZoneRulesTest {

  static ManageZoneAddRequest valid() {
    return new ManageZoneAddRequest(
        "ab1", "North zone", "1234", null, "jsmith", "2505551234", "j.smith@example.com");
  }

  @Test
  void aCompleteZonePasses() {
    assertThat(ManageZoneRules.validate(valid())).isEmpty();
  }

  @Test
  void districtZoneContactPhoneAndEmailAreMandatory() {
    assertThat(ManageZoneRules.validate(
        new ManageZoneAddRequest(" ", null, null, null, "", null, " ")))
        .containsExactly(
            ManageZoneRules.DISTRICT_REQUIRED,
            ManageZoneRules.ZONE_REQUIRED,
            ManageZoneRules.CONTACT_REQUIRED,
            ManageZoneRules.PHONE_REQUIRED,
            ManageZoneRules.EMAIL_REQUIRED);
    assertThat(ManageZoneRules.validate(null)).hasSize(5);
  }

  @Test
  void theDescriptionIsOptional() {
    ManageZoneAddRequest r = valid();
    assertThat(ManageZoneRules.validate(new ManageZoneAddRequest(r.rangeZoneCode(), null,
        r.adminForestDistrictNo(), null, r.contactUserId(), r.contactPhoneNumber(),
        r.contactEmailAddress()))).isEmpty();
  }

  @Test
  void maximumLengths() {
    assertThat(ManageZoneRules.validate(new ManageZoneAddRequest(
        "ABCDE", "x".repeat(121), "1234", null, "jsmith", "25055512345", "e".repeat(121))))
        .containsExactly(
            "Zone cannot be longer than 4 characters.",
            "Zone description cannot be longer than 120 characters.",
            "Contact phone number cannot be longer than 10 characters.",
            "Contact email cannot be longer than 120 characters.");
    assertThat(ManageZoneRules.validate(new ManageZoneAddRequest(
        "ABCD", "x".repeat(120), "1234", null, "jsmith", "2505551234", "e".repeat(120))))
        .isEmpty();
  }

  @Test
  void theDistrictIsAnOrgUnitNumber() {
    assertThat(ManageZoneRules.validate(new ManageZoneAddRequest(
        "AB", null, "DCC", null, "jsmith", "1", "e")))
        .containsExactly(ManageZoneRules.DISTRICT_INVALID);
  }

  @Test
  void theContactIsAnIdirUserId() {
    assertThat(ManageZoneRules.validate(new ManageZoneAddRequest(
        "AB", null, "1", null, "BCEID\\jsmith", "1", "e")))
        .containsExactly(ManageZoneRules.CONTACT_NOT_IDIR);
  }

  @Test
  void zoneCodeAndContactAreUpperCased() {
    assertThat(ManageZoneRules.zoneCode(" ab1 ")).isEqualTo("AB1");
    assertThat(ManageZoneRules.contactUserId("jsmith")).isEqualTo("IDIR\\JSMITH");
    assertThat(ManageZoneRules.contactUserId(" idir\\JSmith ")).isEqualTo("IDIR\\JSMITH");
    assertThat(ManageZoneRules.bareIdir("IDIR\\jsmith")).isEqualTo("JSMITH");
    assertThat(ManageZoneRules.contactName("Jane", "Smith")).isEqualTo("JANE SMITH");
    assertThat(ManageZoneRules.contactName(null, " ")).isNull();
  }
}
