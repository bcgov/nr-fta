package ca.bc.gov.nrs.fta.billing.service;

import ca.bc.gov.nrs.fta.billing.dto.InvoicePreviewDto.Line;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * FTA695's invoice arithmetic, from {@code FTA_695_INVOICEPREVIEW.GET}: the
 * licence fee line, then ground rent, use fee and non-use lines from the
 * tenure's range provision, then GST/HST.
 *
 * <p>Rounding follows the procedure's {@code TO_CHAR} masks: quantities to
 * whole units ({@code FM999990}) before they are priced, amounts and tax to
 * cents ({@code FM9999990.00}), both rounding half away from zero. A missing
 * rate leaves its amount blank and counts as zero in the subtotal.
 */
final class InvoicePreviewRules {

  /** The licence / permit fee rate type per range file type. */
  private static final Map<String, String> LICENCE_FEE_RATE = Map.of(
      "E01", "GLF", "E02", "GPF", "E03", "NGI",
      "H01", "HLF", "H02", "HPF", "H03", "NHI");

  private InvoicePreviewRules() {}

  /**
   * The tenure's RANGE_PROVISION row for the year (nulls read as zero).
   */
  record Provision(
      BigDecimal authorizedUse,
      BigDecimal tempIncrease,
      BigDecimal nonUseNonBillable,
      BigDecimal nonUseBillable,
      BigDecimal totalAnnualUse) {}

  /** The lines, subtotal, tax and total. */
  record Invoice(
      List<Line> lines,
      BigDecimal subtotal,
      BigDecimal taxRate,
      BigDecimal taxAmount,
      BigDecimal total) {}

  /**
   * @param fileTypeCode        E01–E03 or H01–H03
   * @param fileTypeDescription the range file type's description
   * @param rates               the year's rate per rate type code for this file type
   * @param p                   the range provision
   * @param taxRatePct          the GST/HST percentage, 0 when exempt or unknown
   */
  static Invoice compute(
      String fileTypeCode,
      String fileTypeDescription,
      Map<String, BigDecimal> rates,
      Provision p,
      BigDecimal taxRatePct) {
    List<Line> lines = new ArrayList<>();

    BigDecimal fee = rates.get(LICENCE_FEE_RATE.get(fileTypeCode));
    lines.add(new Line(null,
        fileTypeDescription + " @ " + rate(fee) + " per Licence / Permit Fee",
        cents(fee)));

    BigDecimal authorized = whole(nz(p.authorizedUse()).add(nz(p.tempIncrease())));
    BigDecimal used = whole(nz(p.totalAnnualUse()));
    if (fileTypeCode.startsWith("E")) {
      BigDecimal ground = rates.get("GGR");
      lines.add(new Line(qty(authorized),
          "Authorized AUM plus Temp. Increase @ " + rate(ground) + " per AUM Ground Rent",
          cents(times(ground, authorized))));
      BigDecimal use = rates.get("GUF");
      lines.add(new Line(qty(used),
          "Utilized AUM @ " + rate(use) + " per AUM Use Fee",
          cents(times(use, used))));
      if (nz(p.nonUseBillable()).signum() > 0) {
        BigDecimal nonUse = rates.get("GNF");
        lines.add(new Line(qty(whole(p.nonUseBillable())),
            "Non-Util AUM Billable @ " + rate(nonUse) + " per AUM Non-Use Fee",
            cents(times(nonUse, p.nonUseBillable()))));
      }
      if (nz(p.nonUseNonBillable()).signum() > 0) {
        lines.add(new Line(p.nonUseNonBillable().stripTrailingZeros().toPlainString(),
            "Non-Util AUM Non-Billable @0.00", cents(BigDecimal.ZERO)));
      }
    } else {
      BigDecimal ground = rates.get("HGR");
      lines.add(new Line(qty(authorized),
          "Authorized Tonnes plus Temp. Increase @ " + rate(ground) + " per Tonne Ground Rent",
          cents(times(ground, authorized))));
      BigDecimal use = rates.get("HUF");
      lines.add(new Line(qty(used),
          "Utilized Tonnes @ " + rate(use) + " per Tonne Use Fee",
          cents(times(use, used))));
      if (nz(p.nonUseNonBillable()).signum() > 0) {
        // Legacy blanks the rate here, so the description reads "@  per".
        lines.add(new Line(null,
            "Non-Util " + p.nonUseNonBillable().stripTrailingZeros().toPlainString()
                + " Ton Non-Bill @  per Tonne Non-Use Fee",
            cents(BigDecimal.ZERO)));
      }
    }

    BigDecimal subtotal = lines.stream()
        .map(Line::amount)
        .map(InvoicePreviewRules::nz)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal taxRate = nz(taxRatePct);
    BigDecimal tax = cents(taxRate.movePointLeft(2).multiply(subtotal));
    return new Invoice(lines, cents(subtotal), taxRate, tax, cents(subtotal.add(tax)));
  }

  private static BigDecimal times(BigDecimal rate, BigDecimal quantity) {
    return rate == null ? null : rate.multiply(quantity);
  }

  private static BigDecimal whole(BigDecimal n) {
    return n.setScale(0, RoundingMode.HALF_UP);
  }

  static BigDecimal cents(BigDecimal n) {
    return n == null ? null : n.setScale(2, RoundingMode.HALF_UP);
  }

  private static String qty(BigDecimal n) {
    return n.toPlainString();
  }

  /** {@code TO_CHAR(rate, 'FM999990.00')}; blank when there is no rate. */
  private static String rate(BigDecimal r) {
    return r == null ? "" : cents(r).toPlainString();
  }

  private static BigDecimal nz(BigDecimal n) {
    return n == null ? BigDecimal.ZERO : n;
  }
}
