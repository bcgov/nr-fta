package ca.bc.gov.nrs.fta.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.billing.dto.InvoicePreviewDto.Line;
import ca.bc.gov.nrs.fta.billing.service.InvoicePreviewRules.Invoice;
import ca.bc.gov.nrs.fta.billing.service.InvoicePreviewRules.Provision;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test | InvoicePreviewRules")
class InvoicePreviewRulesTest {

  private static BigDecimal d(String s) {
    return new BigDecimal(s);
  }

  @Test
  void grazingLicence() {
    Map<String, BigDecimal> rates = Map.of(
        "GLF", d("25"), "GGR", d("0.5"), "GUF", d("1.25"), "GNF", d("0.4"));
    Provision p = new Provision(d("100"), d("10.4"), d("3"), d("5.5"), d("80.6"));

    Invoice inv = InvoicePreviewRules.compute("E01", "Grazing Licence", rates, p, d("5"));

    assertThat(inv.lines()).extracting(Line::description).containsExactly(
        "Grazing Licence @ 25.00 per Licence / Permit Fee",
        "Authorized AUM plus Temp. Increase @ 0.50 per AUM Ground Rent",
        "Utilized AUM @ 1.25 per AUM Use Fee",
        "Non-Util AUM Billable @ 0.40 per AUM Non-Use Fee",
        "Non-Util AUM Non-Billable @0.00");
    // Quantities are rounded to whole units before pricing: 110 AUM, 81 AUM.
    assertThat(inv.lines()).extracting(Line::quantity)
        .containsExactly(null, "110", "81", "6", "3");
    assertThat(inv.lines()).extracting(Line::amount)
        .containsExactly(d("25.00"), d("55.00"), d("101.25"), d("2.20"), d("0.00"));
    assertThat(inv.subtotal()).isEqualByComparingTo("183.45");
    assertThat(inv.taxAmount()).isEqualByComparingTo("9.17");
    assertThat(inv.total()).isEqualByComparingTo("192.62");
  }

  @Test
  void hayPermitWithAMissingRate() {
    Map<String, BigDecimal> rates = Map.of("HPF", d("10"), "HGR", d("2"));
    Provision p = new Provision(d("4"), d("0"), d("2"), d("0"), d("3"));

    Invoice inv = InvoicePreviewRules.compute("H02", "Hay Cutting Permit", rates, p, d("0"));

    assertThat(inv.lines()).extracting(Line::description).containsExactly(
        "Hay Cutting Permit @ 10.00 per Licence / Permit Fee",
        "Authorized Tonnes plus Temp. Increase @ 2.00 per Tonne Ground Rent",
        "Utilized Tonnes @  per Tonne Use Fee",
        "Non-Util 2 Ton Non-Bill @  per Tonne Non-Use Fee");
    assertThat(inv.lines().get(2).amount()).isNull();
    assertThat(inv.subtotal()).isEqualByComparingTo("18.00");
    assertThat(inv.total()).isEqualByComparingTo("18.00");
  }
}
