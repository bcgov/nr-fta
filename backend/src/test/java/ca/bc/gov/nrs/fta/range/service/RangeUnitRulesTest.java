package ca.bc.gov.nrs.fta.range.service;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.range.dto.RangeUnitSaveRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test | RangeUnitRules")
class RangeUnitRulesTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

  private static RangeUnitSaveRequest save(String id, String name, String status, String asOf,
      String district, String zone, Integer revision) {
    return new RangeUnitSaveRequest(id, name, status, asOf, district, zone, revision);
  }

  @Test
  @DisplayName("a valid add and save pass")
  void valid() {
    assertThat(RangeUnitRules.validateSave(
        save(null, "Big Creek", null, "", "1847", "ab1", null), true)).isEmpty();
    assertThat(RangeUnitRules.validateSave(
        save(null, "Big Creek", "A", "2026-01-31", "1847", "AB1", 3), false)).isEmpty();
  }

  @Test
  @DisplayName("Save Range's required fields, in the form's order")
  void required() {
    assertThat(RangeUnitRules.validateSave(save(null, " ", "", null, "", "", 1), false))
        .containsExactly(RangeUnitRules.NAME_REQUIRED, RangeUnitRules.ZONE_REQUIRED,
            RangeUnitRules.STATUS_REQUIRED, RangeUnitRules.DISTRICT_REQUIRED);
  }

  @Test
  @DisplayName("an add needs no status: it is always P")
  void addNeedsNoStatus() {
    assertThat(RangeUnitRules.validateSave(save(null, "N", null, null, "1", "Z", null), true))
        .doesNotContain(RangeUnitRules.STATUS_REQUIRED);
  }

  @Test
  @DisplayName("zone must be alphanumeric; it is upper-cased first")
  void zone() {
    assertThat(RangeUnitRules.validateSave(save(null, "N", "A", null, "1", "a-1", 1), false))
        .containsExactly(RangeUnitRules.ZONE_NOT_ALPHANUMERIC);
    assertThat(RangeUnitRules.zone(" ab1 ")).isEqualTo("AB1");
    assertThat(RangeUnitRules.zone(" ")).isNull();
  }

  @Test
  @DisplayName("an As of that is not yyyy-MM-dd is refused")
  void asOfFormat() {
    assertThat(RangeUnitRules.validateSave(save(null, "N", "A", "2026-02-30", "1", "Z", 1), false))
        .containsExactly(RangeUnitRules.AS_OF_FORMAT);
    assertThat(RangeUnitRules.validateSave(save(null, "N", "A", "26/01/01", "1", "Z", 1), false))
        .containsExactly(RangeUnitRules.AS_OF_FORMAT);
  }

  @Test
  @DisplayName("an id typed on an add: Range Unit will be generated")
  void blankOnNew() {
    assertThat(RangeUnitRules.validateSave(save("RU9", "N", null, null, "1", "Z", null), true))
        .containsExactly(RangeUnitRules.BLANK_ON_NEW);
  }

  @Test
  @DisplayName("a save without the revision count it read is refused")
  void revisionRequired() {
    assertThat(RangeUnitRules.validateSave(save(null, "N", "A", null, "1", "Z", null), false))
        .containsExactly(RangeUnitRules.REVISION_REQUIRED);
  }

  @Test
  @DisplayName("over-long name and zone are refused")
  void lengths() {
    assertThat(RangeUnitRules.validateSave(
        save(null, "x".repeat(31), "A", null, "1", "ABCDE", 1), false))
        .containsExactly(RangeUnitRules.tooLong("Range Unit Name", 30),
            RangeUnitRules.tooLong("Zone", 4));
  }

  @Test
  @DisplayName("a changed code must be current; an unchanged one may be expired")
  void expiredCode() {
    Set<String> current = Set.of("A", "P");
    assertThat(RangeUnitRules.expiredCode("Status", "X", "A", current))
        .isEqualTo("Status (X) is an expired code.");
    assertThat(RangeUnitRules.expiredCode("Status", "X", "X", current)).isNull();
    assertThat(RangeUnitRules.expiredCode("Status", "P", null, current)).isNull();
    assertThat(RangeUnitRules.expiredCode("Status", "", "A", current)).isNull();
  }

  @Test
  @DisplayName("a status change with no As of is as of today, with legacy's warning")
  void asOfDefault() {
    assertThat(RangeUnitRules.asOf("A", "P", "", TODAY))
        .isEqualTo(new RangeUnitRules.AsOf("2026-11-26", RangeUnitRules.STATUS_DATE_CHANGE));
    assertThat(RangeUnitRules.asOf("P", null, null, TODAY).warning())
        .isEqualTo(RangeUnitRules.STATUS_DATE_CHANGE);
    assertThat(RangeUnitRules.asOf("A", "P", "2026-01-01", TODAY))
        .isEqualTo(new RangeUnitRules.AsOf("2026-01-01", null));
    assertThat(RangeUnitRules.asOf("A", "A", "", TODAY))
        .isEqualTo(new RangeUnitRules.AsOf(null, null));
  }

  @Test
  @DisplayName("pasture: ID required, an integer of at most two characters; description required")
  void pasture() {
    assertThat(RangeUnitRules.validatePasture("01", "North", true)).isEmpty();
    assertThat(RangeUnitRules.validatePasture("", "", true))
        .containsExactly(RangeUnitRules.PASTURE_ID_REQUIRED, RangeUnitRules.PASTURE_NAME_REQUIRED);
    assertThat(RangeUnitRules.validatePasture("A1", "North", true))
        .containsExactly(RangeUnitRules.PASTURE_ID_INTEGER);
    assertThat(RangeUnitRules.validatePasture("123", "North", true))
        .containsExactly(RangeUnitRules.tooLong("Pasture ID", 2));
    // Update locks the id.
    assertThat(RangeUnitRules.validatePasture("whatever", "North", false)).isEmpty();
  }

  @Test
  @DisplayName("pasture 00 may not be deleted")
  void firstPasture() {
    assertThat(RangeUnitRules.pastureDeleteBlock("00"))
        .isEqualTo(RangeUnitRules.DELETE_FIRST_PASTURE);
    assertThat(RangeUnitRules.pastureDeleteBlock("01")).isNull();
  }

  @Test
  @DisplayName("parse: legacy text, warnings, the modified and authority flags")
  void parse() {
    assertThat(RangeUnitRules.parse("").failed()).isFalse();

    RangeUnitRules.Parsed warn = RangeUnitRules.parse("fta.web.warning.CbStatusDateChanged:~W;");
    assertThat(warn.failed()).isFalse();
    assertThat(warn.warnings()).containsExactly(RangeUnitRules.CB_STATUS_DATE_CHANGED);

    RangeUnitRules.Parsed dup =
        RangeUnitRules.parse("fta.web.usr.database.record.DuplicatePastureName;");
    assertThat(dup.errors()).containsExactly(RangeUnitRules.DUPLICATE_PASTURE);

    RangeUnitRules.Parsed modified = RangeUnitRules.parse(
        "fta.web.usr.database.record.modified:fta_630_mn_rg_un_pst,CHANGE,range_unit 2;");
    assertThat(modified.modified()).isTrue();
    assertThat(modified.errors()).containsExactly(RangeUnitRules.MODIFIED);

    RangeUnitRules.Parsed auth = RangeUnitRules.parse("fta.web.error.districtNotInRegion;");
    assertThat(auth.notAuthorized()).isTrue();
    assertThat(auth.errors()).containsExactly(RangeUnitRules.DISTRICT_NOT_IN_REGION);

    RangeUnitRules.Parsed cims = RangeUnitRules.parse("fta.web.error.user.custom.msg:Range Unit"
        + " cannot be deleted. It is being referenced in a CIMS range inspection;");
    assertThat(cims.errors()).containsExactly(RangeUnitRules.UNIT_INSPECTION);
    assertThat(cims.deleteBlocks()).containsExactly(RangeUnitRules.UNIT_INSPECTION);

    RangeUnitRules.Parsed unexpected = RangeUnitRules.parse(
        "fta.web.usr.database.unexpected:fta_630_mn_rg_un_pst,ADD,-1,ORA-00001;");
    assertThat(unexpected.errors()).containsExactly("An unexpected error has occurred in"
        + " package/procedure fta_630_mn_rg_un_pst, ADD - -1, ORA-00001 Please contact System"
        + " Support.");
  }

  @Test
  @DisplayName("REMOVE_ITEM's livestock message has no ';': it is still found, and is the block")
  void livestockWithoutSemicolon() {
    RangeUnitRules.Parsed p = RangeUnitRules.parse(
        "fta.web.usr.database.record.DeleteNotAllowed_Livestock"
            + "fta.web.usr.database.record.modified:fta_630_mn_rg_un_pst,REMOVE,range_unit_pasture;");
    assertThat(p.errors()).containsExactly(RangeUnitRules.DELETE_LIVESTOCK, RangeUnitRules.MODIFIED);
    assertThat(p.deleteBlocks()).isEqualTo(List.of(RangeUnitRules.DELETE_LIVESTOCK));

    assertThat(RangeUnitRules.parse("fta.web.usr.database.record.DeleteNotAllowed").errors())
        .containsExactly(RangeUnitRules.DELETE_FIRST_PASTURE);
  }
}
