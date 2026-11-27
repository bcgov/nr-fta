package ca.bc.gov.nrs.fta.range.service;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.range.service.RangeUsageLegacy.Outcome;
import ca.bc.gov.nrs.fta.range.service.RangeUsageLegacy.Values;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test | RangeUsageLegacy (FTA_613R_RANGE_USAGE.MAINLINE)")
class RangeUsageLegacyTest {

  @Test
  void theBlockBindsEveryInputAndReadsTheErrorBack() {
    long binds = RangeUsageLegacy.BLOCK.chars().filter(c -> c == '?').count();
    Values v = new Values("RAN001", "2023-01-01", "2027-12-31", "IDIR\\ME", 2025, "100", null,
        null, null, null, "20", "INC", 120);
    List<String> in = RangeUsageLegacy.binds("SAVE", v, "0", null, null, null);
    assertThat(binds).isEqualTo(in.size() + 1);
    assertThat(in.get(0)).isEqualTo("SAVE");
    assertThat(in.get(5)).isEqualTo("2025");
    assertThat(in.get(13)).isEqualTo("120");
    assertThat(RangeUsageLegacy.BLOCK)
        .contains("the.fta_613r_range_usage.mainline(")
        .contains("p_range_usage_results => v_results");
  }

  @Test
  void noMessageMeansWritten() {
    assertThat(RangeUsageLegacy.parse(null).errors()).isEmpty();
    assertThat(RangeUsageLegacy.parse("").errors()).isEmpty();
  }

  @Test
  void customMessagesAndWarningsAreErrorsWithoutDuplicates() {
    Outcome o = RangeUsageLegacy.parse(
        "fta.web.error.user.custom.msg:~W,The From and To years must be entered.;"
            + "fta.web.error.user.custom.msg:~W,You may only add or update range usage for years"
            + " within the current licensing term;");
    assertThat(o.errors()).containsExactly(
        "The From and To years must be entered.",
        "You may only add or update range usage for years within the current licensing term.");
    assertThat(o.modified()).isFalse();
  }

  @Test
  void aModifiedRecordAsksForAReload() {
    Outcome o = RangeUsageLegacy.parse(
        "fta.web.usr.database.record.modified:fta_613r_range_usage,SAVE ,RANGE_PROVISION;");
    assertThat(o.errors()).containsExactly(RangeUsageLegacy.MSG_MODIFIED);
    assertThat(o.modified()).isTrue();
  }
}
