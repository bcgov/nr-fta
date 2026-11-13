package ca.bc.gov.nrs.fta.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.billing.service.PreBillingRules.Amounts;
import ca.bc.gov.nrs.fta.billing.service.PreBillingRules.InvoiceLine;
import ca.bc.gov.nrs.fta.billing.service.PreBillingRules.Provision;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test | PreBillingRules")
class PreBillingRulesTest {

  private static BigDecimal d(String s) {
    return new BigDecimal(s);
  }

  private static final Map<String, BigDecimal> RATES = Map.of(
      "GLF", d("25"), "GGR", d("0.5"), "GUF", d("1.25"), "GNF", d("0.4"));

  @Test
  void aChargeIsPricedFromTheProvision() {
    Provision p = new Provision(d("100"), d("20"), d("3"), d("10"));

    Amounts a = PreBillingRules.charge("E01", "Y", p, RATES, d("5"));

    assertThat(a.authAmount()).isEqualByComparingTo("100");
    assertThat(a.rent()).isEqualByComparingTo("60");      // (100 + 20) × 0.50
    assertThat(a.fee()).isEqualByComparingTo("146.25");   // (120 − 3) × 1.25
    assertThat(a.nonUseBillableAmount()).isEqualByComparingTo("4");
    assertThat(a.issueFee()).isEqualByComparingTo("25");
    assertThat(a.totalInclTax()).isEqualByComparingTo("5");
  }

  @Test
  void onlyRAndYCarryTheLicenceFee() {
    Provision p = new Provision(d("1"), null, null, null);
    assertThat(PreBillingRules.charge("E01", "YI", p, RATES, d("5")).issueFee()).isZero();
    assertThat(PreBillingRules.charge("E01", "RI", p, RATES, d("5")).issueFee()).isZero();
    assertThat(PreBillingRules.charge("E01", "R", p, RATES, d("5")).issueFee())
        .isEqualByComparingTo("25");
  }

  @Test
  void aReversalNegatesTheLatestInvoice() {
    List<InvoiceLine> latest = List.of(
        new InvoiceLine("RGL", d("60"), d("120")),
        new InvoiceLine("RLU", d("150"), d("120")),
        new InvoiceLine("RLF", d("25"), d("1")),
        new InvoiceLine("GST", d("11.75"), d("1")));

    Amounts a = PreBillingRules.reversal(latest, "GST");

    assertThat(a.authAmount()).isEqualByComparingTo("-120");
    assertThat(a.rent()).isEqualByComparingTo("-60");
    assertThat(a.fee()).isEqualByComparingTo("-150");
    assertThat(a.nonUseBillableAmount()).isZero();
    assertThat(a.issueFee()).isEqualByComparingTo("-25");
    assertThat(a.totalInclTax()).isEqualByComparingTo("-11.75");
  }

  @Test
  void twoLinesOfAClassReverseAsZeroAsLegacysSelectIntoDid() {
    List<InvoiceLine> latest = List.of(
        new InvoiceLine("RGL", d("60"), d("120")),
        new InvoiceLine("RGP", d("10"), d("20")));
    assertThat(PreBillingRules.reversal(latest, null).rent()).isZero();
  }

  @Test
  void theErrorSummaryNamesTheFirstMissingRate() {
    assertThat(PreBillingReport.missingRate(List.of("GGR"), false,
        "GGR", "Grazing Ground Rent rate", "GUF", "Grazing Usage Fee",
        "GNF", "Grazing Rate Non-Usage Fee"))
        .isEqualTo("Grazing Usage Fee not found for billing year.");
    assertThat(PreBillingReport.missingRate(List.of("GGR", "GUF"), true,
        "GGR", "Grazing Ground Rent rate", "GUF", "Grazing Usage Fee",
        "GNF", "Grazing Rate Non-Usage Fee"))
        .isEqualTo("Grazing Rate Non-Usage Fee not found for billing year.");
    assertThat(PreBillingReport.missingRate(List.of("GGR", "GUF"), false,
        "GGR", "Grazing Ground Rent rate", "GUF", "Grazing Usage Fee",
        "GNF", "Grazing Rate Non-Usage Fee"))
        .isNull();
  }
}
