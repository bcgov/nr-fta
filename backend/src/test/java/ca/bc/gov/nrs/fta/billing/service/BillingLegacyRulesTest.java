package ca.bc.gov.nrs.fta.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.fta.billing.dto.InvoicePreviewDto.Line;
import ca.bc.gov.nrs.fta.billing.service.BillingInstructionRules.EditFacts;
import ca.bc.gov.nrs.fta.billing.service.BillingInstructionRules.RowState;
import ca.bc.gov.nrs.fta.billing.service.PreBillingRules.Amounts;
import java.math.BigDecimal;
import java.time.Year;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Legacy rules the billing screens had to match: the instruction code check (CodeExpiryValidator),
 * the explicit E01–E03 / H01–H03 file type lists of the packages, and FTA695's year limit.
 */
@DisplayName("Unit Test | Range billing legacy rules")
class BillingLegacyRulesTest {

  private static final Set<String> ACTIVE = Set.of("C", "N", "R", "RI", "Y", "YI");

  @Test
  void aBlankOrOfferedInstructionIsAllowed() {
    assertThat(BillingInstructionRules.codeError("", "Y", ACTIVE)).isNull();
    assertThat(BillingInstructionRules.codeError(null, "Y", ACTIVE)).isNull();
    assertThat(BillingInstructionRules.codeError("RI", "Y", ACTIVE)).isNull();
  }

  @Test
  void anInstructionNotOfferedIsRefused() {
    assertThat(BillingInstructionRules.codeError("B", "Y", ACTIVE))
        .isEqualTo("Instr (B) is an expired code.");
    assertThat(BillingInstructionRules.codeError(" X ", "", ACTIVE))
        .isEqualTo("Instr (X) is an expired code.");
  }

  @Test
  void theRowsOwnInstructionIsExemptEvenIfExpired() {
    assertThat(BillingInstructionRules.codeError("Q", "Q", ACTIVE)).isNull();
  }

  @Test
  void onlyE01ToE03AreGrazingWhenTheProvisionIsMissing() {
    RowState row = new RowState("", "", true, false);
    EditFacts noProvision = new EditFacts(false, "OPN", null, null, false, null);
    assertThat(BillingInstructionRules.edit(row, "E03", "Y", noProvision).error())
        .isEqualTo(BillingInstructionRules.GRAZING_NOT_FOUND);
    assertThat(BillingInstructionRules.edit(row, "E99", "Y", noProvision).error())
        .isEqualTo(BillingInstructionRules.HAY_NOT_FOUND);
    assertThat(BillingInstructionRules.edit(row, null, "Y", noProvision).error())
        .isEqualTo(BillingInstructionRules.HAY_NOT_FOUND);
  }

  @Test
  void invoicePreviewPricesOnlyTheListedTypes() {
    var p = new InvoicePreviewRules.Provision(
        BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN);
    Map<String, BigDecimal> rates = Map.of("GGR", BigDecimal.ONE, "GUF", BigDecimal.ONE);
    assertThat(InvoicePreviewRules.compute("E02", "Grazing Permit", rates, p, null).lines())
        .hasSize(3);
    // Not in either list: the licence fee line only, as the package's IF/ELSIF fell through.
    assertThat(InvoicePreviewRules.compute("E99", "Other", rates, p, null).lines())
        .extracting(Line::description)
        .containsExactly("Other @  per Licence / Permit Fee");
  }

  @Test
  void preBillingNonUseFeeNeedsAListedType() {
    var p = new PreBillingRules.Provision(
        BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN);
    Map<String, BigDecimal> rates = Map.of(
        "GGR", BigDecimal.ONE, "GUF", BigDecimal.ONE, "GNF", BigDecimal.ONE);
    Amounts listed = PreBillingRules.charge("E01", "YI", p, rates, BigDecimal.ZERO);
    assertThat(listed.nonUseBillableAmount()).isEqualByComparingTo("10");
    // Rent and use fee go by the first letter (FTAR408_GET_RANGE_RENT / _USAGE_FEE), the
    // non-use fee by the explicit list (FTAR408_GET_RANGE_NONUSAGE_FEE).
    Amounts other = PreBillingRules.charge("E99", "YI", p, rates, BigDecimal.ZERO);
    assertThat(other.rent()).isEqualByComparingTo("10");
    assertThat(other.fee()).isEqualByComparingTo("10");
    assertThat(other.nonUseBillableAmount()).isZero();
    Amounts none = PreBillingRules.charge("X01", "YI", p, rates, BigDecimal.ZERO);
    assertThat(none.rent()).isZero();
    assertThat(none.fee()).isZero();
  }

  @Test
  void invoicePreviewDefaultsAndLimitsTheYear() {
    int now = Year.now(ZoneId.of("America/Vancouver")).getValue();
    assertThat(InvoicePreviewService.year(null)).isEqualTo(String.valueOf(now));
    assertThat(InvoicePreviewService.year(" " + now + " ")).isEqualTo(String.valueOf(now));
    assertThat(InvoicePreviewService.year("2011")).isEqualTo("2011");
    assertThatThrownBy(() -> InvoicePreviewService.year(String.valueOf(now + 1)))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(e.getReason()).isEqualTo("Year must be less than or equal to Current Year.");
        });
    assertThatThrownBy(() -> InvoicePreviewService.year("25"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getReason()).isEqualTo(InvoicePreviewService.YEAR_INVALID));
  }
}
