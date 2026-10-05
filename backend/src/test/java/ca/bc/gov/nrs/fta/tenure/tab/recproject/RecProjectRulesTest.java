package ca.bc.gov.nrs.fta.tenure.tab.recproject;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins the FTA701 gating ({@code FTA_RECREATION_SECURITY}) and field checks (the form's
 * validators and {@code validate_fee}): the tab enables its buttons by these rules and the
 * endpoints enforce them, so a regression here lets through a write legacy refused.
 */
@DisplayName("Unit Test | RecProjectRules and RecProjectChecks")
class RecProjectRulesTest {

  private static RecProjectRequests.Save save(
      String name, String view, String zone, String easting, String northing, String row) {
    return new RecProjectRequests.Save(null, name, null, null, null, zone, easting, northing, row,
        null, null, null, "N", null, "N", view, "Y", null, null, null, null, null, null, null,
        null);
  }

  private static RecProjectRequests.Fee fee(
      String code, String amount, LocalDate start, LocalDate end, boolean mon, boolean sat) {
    return new RecProjectRequests.Fee(
        null, code, amount, start, end, mon, false, false, false, false, sat, false);
  }

  @Nested
  @DisplayName("rules")
  class Rules {

    @Test
    void hiWithSpatialAndProjectAllowsEverything() {
      RecProjectRules r = RecProjectRules.of("HI", true, true);
      assertThat(r.project()).isTrue();
      assertThat(r.child()).isTrue();
      assertThat(r.projectReason()).isNull();
    }

    @Test
    void notHiBlocksBoth() {
      RecProjectRules r = RecProjectRules.of("HX", true, true);
      assertThat(r.project()).isFalse();
      assertThat(r.child()).isFalse();
      assertThat(r.projectReason()).isEqualTo(RecProjectRules.NOT_HI);
    }

    @Test
    void noSpatialBlocksBoth() {
      RecProjectRules r = RecProjectRules.of("HI", false, true);
      assertThat(r.project()).isFalse();
      assertThat(r.childReason()).isEqualTo(RecProjectRules.NO_SPATIAL);
    }

    @Test
    void childrenNeedTheProjectRecord() {
      RecProjectRules r = RecProjectRules.of("HI", true, false);
      assertThat(r.project()).isTrue();
      assertThat(r.child()).isFalse();
      assertThat(r.childReason()).isEqualTo(RecProjectRules.NO_PROJECT);
    }

    @Test
    void applicability() {
      RecProjectService.Context c = new RecProjectService.Context(
          "F05", "HI", true, true, 1L, "RTR", null, null, null, null, null);
      assertThat(c.notApplicableReason("REC1234")).isNull();
      assertThat(c.notApplicableReason("K1234")).isEqualTo(RecProjectService.NOT_REC_FORMAT);
      assertThat(c.trailProject()).isTrue();
      RecProjectService.Context timber = new RecProjectService.Context(
          "A01", "HI", true, true, 1L, null, null, null, null, null, null);
      assertThat(timber.notApplicableReason("A12345"))
          .isEqualTo(RecProjectService.NOT_RECREATION);
      assertThat(timber.rules("A12345").project()).isFalse();
    }
  }

  @Nested
  @DisplayName("project checks")
  class Project {

    @Test
    void valid() {
      assertThat(RecProjectChecks.project(save("Lake", "N", null, null, null, null), false))
          .isEmpty();
    }

    @Test
    void nameRequired() {
      assertThat(RecProjectChecks.project(save(" ", "N", null, null, null, null), false))
          .containsExactly("Project Name is required.");
    }

    @Test
    void utmMandatoryWhenWebsiteIsYes() {
      assertThat(RecProjectChecks.project(save("Lake", "Y", "10", "500000", null, null), false))
          .containsExactly(
              "UTM Easting, UTM Northing and UTM Zone are mandatory when Webmap is Yes.");
    }

    @Test
    void utmNumbersAndZone() {
      List<String> e =
          RecProjectChecks.project(save("Lake", "N", "12", "5a", "5600000", null), false);
      assertThat(e).containsExactly("UTM Easting must be an integer.");
      assertThat(RecProjectChecks.project(save("Lake", "N", "12", "500000", "5600000", null),
          false)).containsExactly("12 is not a supported UTM Zone.");
      assertThat(RecProjectChecks.project(save("Lake", "N", "10", "500000", "5600000", null),
          false)).isEmpty();
    }

    @Test
    void rightOfWayOnTrailsOnly() {
      assertThat(RecProjectChecks.project(save("Lake", "N", null, null, null, null), true))
          .containsExactly("Right of Way is required.");
      assertThat(RecProjectChecks.project(save("Lake", "N", null, null, null, "2.25"), true))
          .containsExactly("Right Of Way cannot contain more than 1 places of decimal.");
      assertThat(RecProjectChecks.project(save("Lake", "N", null, null, null, "100000"), true))
          .containsExactly("Right of Way field must be between 0.0 and 99999.9.");
      assertThat(RecProjectChecks.project(save("Lake", "N", null, null, null, "x"), false))
          .isEmpty();
    }
  }

  @Nested
  @DisplayName("fee checks")
  class Fees {

    private final LocalDate mon = LocalDate.of(2026, 6, 1); // a Monday

    @Test
    void formRequiredAndRange() {
      assertThat(RecProjectChecks.feeForm(fee(null, "1000", null, null, true, false)))
          .containsExactly(
              "Fee Type is required.",
              "Amount field must be between 0.0 and 999.99.",
              "Start Date is required.",
              "End Date is required.");
      assertThat(RecProjectChecks.feeForm(fee("CAMP", "10", mon.plusDays(1), mon, true, false)))
          .containsExactly("Start Date must be less than or equal to End Date.");
    }

    @Test
    void atLeastOneDay() {
      assertThat(RecProjectChecks.feeDays(fee("CAMP", "10", mon, mon, false, false)))
          .containsExactly("You must specify at least one day of the week");
    }

    @Test
    void daysOutsideTheRange() {
      // Monday to Wednesday has no Saturday.
      assertThat(RecProjectChecks.feeDays(fee("CAMP", "10", mon, mon.plusDays(2), true, true)))
          .containsExactly("The day(s) of Saturday are invalid for the date range provided");
      assertThat(RecProjectChecks.feeDays(fee("CAMP", "10", mon, mon.plusDays(30), true, true)))
          .isEmpty();
    }
  }
}
