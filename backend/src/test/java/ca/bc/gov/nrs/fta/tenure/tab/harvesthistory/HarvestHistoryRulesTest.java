package ca.bc.gov.nrs.fta.tenure.tab.harvesthistory;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.tenure.tab.harvesthistory.HarvestHistoryRules.Appraisal;
import ca.bc.gov.nrs.fta.tenure.tab.harvesthistory.HarvestHistoryRules.AppraisalRow;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pins FTA100's Harvest History gate and FTA_990_HARV_HIST.GET's appraisal header. */
class HarvestHistoryRulesTest {

  @ParameterizedTest
  @ValueSource(strings = {"B01", "B02", "B07", "B20", "B40"})
  void everyBTypeFileWithAMarkApplies(String type) {
    // B40 included: the JSP's "except B40" comment is not what its condition does.
    assertThat(HarvestHistoryRules.unavailableReason(type, "EG1234")).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"A01", "A06", "S01", "E01", ""})
  void otherFileTypesDoNot(String type) {
    assertThat(HarvestHistoryRules.unavailableReason(type, "EG1234"))
        .isEqualTo(HarvestHistoryRules.NOT_B_TYPE);
    assertThat(HarvestHistoryRules.unavailableReason(null, "EG1234"))
        .isEqualTo(HarvestHistoryRules.NOT_B_TYPE);
  }

  @Test
  void noHaulingAuthorityMeansNoHistory() {
    assertThat(HarvestHistoryRules.unavailableReason("B01", null))
        .isEqualTo(HarvestHistoryRules.NO_HAULING_AUTHORITY);
    assertThat(HarvestHistoryRules.unavailableReason("B01", " "))
        .isEqualTo(HarvestHistoryRules.NO_HAULING_AUTHORITY);
  }

  @Test
  void noConfirmedAppraisalLeavesTheHeaderBlank() {
    Appraisal a = HarvestHistoryRules.appraisal(List.of());
    assertThat(a).isEqualTo(new Appraisal(null, null, null, null, null, null, null, null, null));
  }

  @Test
  void currentIsTheNewestRateAndInitialTheOldest() {
    // newest first, as the SQL orders them
    Appraisal a = HarvestHistoryRules.appraisal(List.of(
        rate("30.5", "1200", "5.25", "1.1", "0.4", "A"),
        rate("28", "1100", "4", "1", "0.3", "F"),
        rate("20.004", "900", "3", "0.9", "0.2", "F")));
    assertThat(a.currentRate()).isEqualByComparingTo("30.50");
    assertThat(a.initialRate()).isEqualByComparingTo("20.00");
    assertThat(a.bonusBid()).isEqualByComparingTo("5.25");
    assertThat(a.silvicultureLevy()).isEqualByComparingTo("1.10");
    assertThat(a.developmentLevy()).isEqualByComparingTo("0.40");
    assertThat(a.adjustQuarterlyInd()).isEqualTo("A");
    assertThat(a.netCruiseVolume()).isEqualByComparingTo("1200");
    assertThat(a.appraisalStatus()).isEqualTo("CNF");
    // initial rate less the (current) bonus bid
    assertThat(a.originalUpsetRate()).isEqualByComparingTo("14.75");
  }

  @Test
  void cruiseIsTheNewestWorksheetsEvenWithoutARate() {
    Appraisal a = HarvestHistoryRules.appraisal(List.of(
        noRate("1500", "A"),
        rate("12", "1000", "2", "1", "0.5", "F")));
    assertThat(a.netCruiseVolume()).isEqualByComparingTo("1500");
    assertThat(a.currentRate()).isEqualByComparingTo("12");
    assertThat(a.initialRate()).isEqualByComparingTo("12");
    assertThat(a.adjustQuarterlyInd()).isEqualTo("F");
    assertThat(a.originalUpsetRate()).isEqualByComparingTo("10");
  }

  @Test
  void noRateAtAllLeavesRatesBlank() {
    Appraisal a = HarvestHistoryRules.appraisal(List.of(noRate("1500", "A"), noRate("900", "F")));
    assertThat(a.netCruiseVolume()).isEqualByComparingTo("1500");
    assertThat(a.currentRate()).isNull();
    assertThat(a.initialRate()).isNull();
    assertThat(a.originalUpsetRate()).isNull();
    assertThat(a.bonusBid()).isNull();
    // legacy's loop ran to the last row
    assertThat(a.adjustQuarterlyInd()).isEqualTo("F");
  }

  @Test
  void billingPeriodComparesAsANumber() {
    assertThat(HarvestHistoryRules.billingPeriod(201909L)).isEqualTo("2019-09");
    assertThat(HarvestHistoryRules.billingPeriod(202012L)).isEqualTo("2020-12");
    assertThat(HarvestHistoryRules.billingPeriod(null)).isNull();
  }

  private static AppraisalRow rate(
      String rate, String cruise, String bonus, String silv, String dev, String adjust) {
    return new AppraisalRow(true, new BigDecimal(rate), new BigDecimal(cruise),
        new BigDecimal(bonus), new BigDecimal(silv), new BigDecimal(dev), adjust, "CNF");
  }

  private static AppraisalRow noRate(String cruise, String adjust) {
    return new AppraisalRow(false, null, new BigDecimal(cruise), null, null, null, adjust, "CNF");
  }
}
