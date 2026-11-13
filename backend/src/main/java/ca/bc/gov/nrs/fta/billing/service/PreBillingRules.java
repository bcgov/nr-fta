package ca.bc.gov.nrs.fta.billing.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FTA408's per-tenure arithmetic, from the {@code FTAR408_*} functions FTAR408P calls, with
 * the database reads done by the caller.
 *
 * <p>A tenure instructed C, R or RI gets a reversal row ({@code sort_by '1'}): every amount is
 * the matching line of its latest invoice, negated. A tenure instructed Y, YI, R or RI gets a
 * charge row ({@code sort_by '2'}), priced from its range provision and the year's rates. R and
 * RI get both.
 *
 * <p>Each function read one invoice line with {@code SELECT … INTO} and returned 0 on any
 * error, so an amount is 0 unless exactly one line matches.
 */
final class PreBillingRules {

  static final Set<String> REVERSED = Set.of("C", "R", "RI");
  static final Set<String> CHARGED = Set.of("Y", "YI", "R", "RI");

  private static final Set<String> RENT = Set.of("RGL", "RGP", "RNG", "RHL", "RHP", "RNH");
  private static final Set<String> USAGE = Set.of("RLU", "RPU", "RNU", "RHU", "RHF", "RNF");
  private static final Set<String> NON_USE = Set.of("RGN", "RHN");
  private static final Set<String> ISSUANCE = Set.of("RLF");

  /** The licence / permit fee rate type per range file type (FTAR408_GET_RANGE_ISSUE_FEE). */
  private static final Map<String, String> LICENCE_FEE = Map.of(
      "E01", "GLF", "E02", "GPF", "E03", "NGI",
      "H01", "HLF", "H02", "HPF", "H03", "NHI");

  private PreBillingRules() {}

  /** One line of the tenure's latest invoice. */
  record InvoiceLine(String revenueClass, BigDecimal amount, BigDecimal units) {}

  /** The tenure's RANGE_PROVISION for the year (nulls read as zero). */
  record Provision(
      BigDecimal authorizedUse,
      BigDecimal tempIncrease,
      BigDecimal nonUseNonBillable,
      BigDecimal nonUseBillable) {}

  /** The amounts of one report row. */
  record Amounts(
      BigDecimal authAmount,
      BigDecimal rent,
      BigDecimal fee,
      BigDecimal nonUseBillableAmount,
      BigDecimal issueFee,
      BigDecimal totalInclTax) {}

  /**
   * The reversal row: the latest invoice's lines, negated. {@code taxClass} is the GST/HST
   * line the tenure was taxed on, or null.
   */
  static Amounts reversal(List<InvoiceLine> latest, String taxClass) {
    return new Amounts(
        negate(one(latest, RENT, true)),
        negate(one(latest, RENT, false)),
        negate(one(latest, USAGE, false)),
        negate(one(latest, NON_USE, false)),
        negate(one(latest, ISSUANCE, false)),
        taxClass == null ? BigDecimal.ZERO : negate(one(latest, Set.of(taxClass), false)));
  }

  /**
   * The charge row, priced from the provision and the year's rates for the file type.
   *
   * @param taxRatePct the GST rate in force today, 0 for a tax-exempt client. FTAR408P puts
   *                   the rate, not an amount, in {@code total_incl_tax} on a charge row; the
   *                   report applies it.
   */
  static Amounts charge(
      String fileTypeCode,
      String instruction,
      Provision p,
      Map<String, BigDecimal> rates,
      BigDecimal taxRatePct) {
    boolean grazing = fileTypeCode != null && fileTypeCode.startsWith("E");
    BigDecimal authorized = nz(p.authorizedUse());
    BigDecimal authorizedPlusTemp = authorized.add(nz(p.tempIncrease()));
    BigDecimal used = authorizedPlusTemp.subtract(nz(p.nonUseNonBillable()));
    BigDecimal nonUseBillable = nz(p.nonUseBillable());

    BigDecimal rent = authorizedPlusTemp.multiply(rate(rates, grazing ? "GGR" : "HGR"));
    BigDecimal fee = used.multiply(rate(rates, grazing ? "GUF" : "HUF"));
    BigDecimal nonUse = nonUseBillable.signum() > 0
        ? nonUseBillable.multiply(rate(rates, grazing ? "GNF" : "HNF"))
        : BigDecimal.ZERO;
    // FTAR408P's DECODE: only R and Y carry the licence fee.
    BigDecimal issue = "R".equals(instruction) || "Y".equals(instruction)
        ? rate(rates, LICENCE_FEE.get(fileTypeCode))
        : BigDecimal.ZERO;
    return new Amounts(authorized, rent, fee, nonUse, issue, nz(taxRatePct));
  }

  /** The value of the single line in {@code classes}, or 0 when there is not exactly one. */
  private static BigDecimal one(List<InvoiceLine> lines, Set<String> classes, boolean units) {
    List<InvoiceLine> match = lines.stream()
        .filter(l -> classes.contains(l.revenueClass()))
        .toList();
    if (match.size() != 1) {
      return BigDecimal.ZERO;
    }
    return nz(units ? match.get(0).units() : match.get(0).amount());
  }

  private static BigDecimal rate(Map<String, BigDecimal> rates, String type) {
    return type == null ? BigDecimal.ZERO : nz(rates.get(type));
  }

  private static BigDecimal negate(BigDecimal n) {
    return n.signum() == 0 ? BigDecimal.ZERO : n.negate();
  }

  static BigDecimal nz(BigDecimal n) {
    return n == null ? BigDecimal.ZERO : n;
  }
}
