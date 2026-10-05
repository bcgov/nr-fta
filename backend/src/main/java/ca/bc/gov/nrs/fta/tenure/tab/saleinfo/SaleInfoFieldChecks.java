package ca.bc.gov.nrs.fta.tenure.tab.saleinfo;

import ca.bc.gov.nrs.fta.tenure.tab.saleinfo.SaleInfoDtos.SaleInfoUpdateRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The field checks of legacy FTA940's save, with legacy's message texts: first
 * {@code Fta940SaleInfoForm}'s "Save" validators (legacy stopped there when any failed), then
 * the checks of {@code FTA_940_SALE_INFO.SAVE} / {@code CHANGE} / {@code ADD} that need no
 * database. Pure, so it is unit-tested without one; the code-table and admin-area-file checks
 * that do need it are in {@link SaleInfoService}.
 */
public final class SaleInfoFieldChecks {

  private SaleInfoFieldChecks() {}

  /**
   * The outcome: blocking errors, legacy's "~W" warnings, and the Salvage Indicator to store
   * (the PL/SQL overrides it to Y for small scale salvage and A31s).
   */
  public record Result(List<String> errors, List<String> warnings, String salvageInd) {}

  static final BigDecimal MAX_MONEY = new BigDecimal("999999999.99");
  static final BigDecimal MAX_EST_VOL = new BigDecimal("9999999999.99");
  static final BigDecimal MAX_SALE_VOL = new BigDecimal("99999999.9");

  private static final Set<String> DIRECT_AWARD_FN_TYPES =
      Set.of("A41", "A44", "B04", "A18", "A29", "A31");

  private static final Set<String> SALVAGE_LICENCE_TYPES = Set.of("A01", "A41", "A31");

  /**
   * Checks the record about to be saved.
   *
   * @param v        the values to store — closed fields already replaced by stored ones
   * @param rules    the record's rules
   * @param type     the file type
   * @param status   the file status
   * @param purpose  the licence-to-cut purpose: B07's {@code LICENCE_TO_CUT_CD}, or SS for an
   *                 A18 with a small scale salvage permit; null otherwise
   * @param payment  the stored payment method (read-only here)
   * @param adding   whether the record does not exist yet (legacy ADD, not CHANGE)
   */
  public static Result check(
      SaleInfoUpdateRequest v,
      SaleInfoRules rules,
      String type,
      String status,
      String purpose,
      String payment,
      boolean adding) {
    String t = type == null ? "" : type;
    String s = status == null ? "" : status;
    List<String> e = new ArrayList<>();

    // ── Fta940SaleInfoForm, "Save" ──────────────────────────────────────────
    if (v.salvageInd() == null) {
      e.add("Salvage Indicator is mandatory.");
    }
    if (rules.bctsFileType() && v.plannedSbCatCode() == null) {
      e.add("Planned BCTS Cat. is mandatory.");
    }
    if (rules.soldCatRequired() && v.soldSbCatCode() == null) {
      e.add("Sold BCTS Cat. is mandatory.");
    }
    if (!rules.bcts() && v.ftaBonusOffer() != null && v.ftaBonusBid() != null) {
      e.add("Only one of Bonus Bid or Bonus Offer may be entered.");
    }
    if (rules.bctsFileType() && v.plannedSaleDate() == null) {
      e.add("Planned Sale Date is mandatory.");
    }
    decimal(e, "Security Deposit Amount", v.scrtyDepositAmt(), MAX_MONEY, 2);
    decimal(e, "Other Deposit Amount", v.otherDepositAmt(), MAX_MONEY, 2);
    decimal(e, "Total Dollars Received", v.cashSaleTotDol(), MAX_MONEY, 2);
    decimal(e, "Estimated Volume", v.cashSaleEstVol(), MAX_EST_VOL, 2);
    decimal(e, "Sales Volume", v.saleVolume(), MAX_SALE_VOL, 1);
    if (v.totalBidders() != null && (v.totalBidders() < 0 || v.totalBidders() > 9999)) {
      e.add("Total Bidders field must be between 0 and 9999.");
    }
    if (!rules.category3Allowed() && "3".equals(v.plannedSbCatCode())) {
      e.add("Planned BCTS Cat. Code of 3 is not allowed for this file type.");
    }
    if (!rules.category3Allowed() && "3".equals(v.soldSbCatCode())) {
      e.add("Sold BCTS Cat. Code of 3 is not allowed for this file type.");
    }
    if (rules.tenderRequired() && v.tenderOpeningDate() == null) {
      e.add("Tender Date is Mandatory when not Cash Sales and when in Harvesting Status.");
    }
    if (rules.cashFieldsRequired() && v.cashSaleEstVol() == null) {
      e.add("Estimated Volume mandatory for this File Type, File Status and Payment Method"
          + " Code.");
    }
    if (rules.cashFieldsRequired() && v.cashSaleTotDol() == null) {
      e.add("Total Dollars mandatory for this File Type, File Status and Payment Method Code.");
    }
    deposit(e, "Security Deposit", v.scrtyDepositCode(), v.scrtyDepositAmt());
    deposit(e, "Other Deposit", v.otherDepositCode(), v.otherDepositAmt());
    if (!rules.saleMethodAllowed().isEmpty()
        && !rules.saleMethodAllowed().contains(v.saleMethodCode())) {
      e.add("Sales Method Code must be " + String.join(" or ", rules.saleMethodAllowed())
          + " for this File Type.");
    }
    if (!rules.bcts()) {
      decimal(e, "FTA Bonus Bid", v.ftaBonusBid(), MAX_MONEY, 2);
      decimal(e, "FTA Bonus Offer", v.ftaBonusOffer(), MAX_MONEY, 2);
    }
    if (!e.isEmpty()) {
      return new Result(e, List.of(), v.salvageInd());
    }

    // ── FTA_940_SALE_INFO.SAVE ──────────────────────────────────────────────
    List<String> w = new ArrayList<>();
    String method = v.saleMethodCode();
    String saleType = v.saleTypeCode();
    String salvage = v.salvageInd();
    if ("N".equals(method) && !DIRECT_AWARD_FN_TYPES.contains(t)) {
      e.add("Sale method code - N - Direct Award First Nations only applies to file types A41"
          + " A44 A18 A31 and B04");
    }
    if ("A29".equals(t) && method != null && !"N".equals(method)) {
      e.add("Sale method code must be N for A29 file types");
    }
    if ("A29".equals(t)
        && (v.scrtyDepositAmt() == null || v.scrtyDepositAmt().signum() == 0)) {
      e.add("Security Deposit Amount greater than 0 is mandatory for A29 file types");
    }
    if (saleType != null && SALVAGE_LICENCE_TYPES.contains(t) && "Y".equals(salvage)) {
      e.add("Sale type must be blank if Forest Licence is for small scale salvage");
    }
    if ("B07".equals(t) || "A18".equals(t)) {
      boolean application = "CA".equals(saleType) || "PA".equals(saleType);
      if ("SS".equals(purpose)) {
        if (!application) {
          e.add("Sale type must be Conventional Application or Professional Application for"
              + " Small Scale Salvage");
        }
        if (!"Y".equals(salvage)) {
          e.add("Salvage Ind must be Y for Small Scale Salvage");
        }
      } else {
        if ("A18".equals(t) && "Y".equals(salvage) && !application) {
          e.add("Sale type must be Conventional Application or Professional Application if"
              + " this A18 is for Small Scale Salvage");
        }
        if (saleType != null && "B07".equals(t)) {
          e.add("Sale type must be blank if Forestry Licence to Cut is not small scale"
              + " salvage");
        }
      }
      // PL/SQL's "l_purpose <> 'CH'" is not true for a null purpose.
      if ("C".equals(payment) && purpose != null && !"CH".equals(purpose)
          && (over(v.saleVolume(), 50) || over(v.cashSaleEstVol(), 50))) {
        w.add("Warning: Sales Volume should not exceed 50m3 for cash sales");
      }
      if ("SS".equals(purpose) && over(v.saleVolume(), 2000)) {
        e.add("Sales Volume cannot exceed 2000m3 for Small Scale Salvage");
      } else if ("EX".equals(purpose) && over(v.saleVolume(), 500)) {
        e.add("Sales Volume cannot exceed 500m3 for Experimental");
      } else if ("GT".equals(purpose) && over(v.saleVolume(), 50)) {
        e.add("Sales Volume cannot exceed 50m3 for Green Timber");
      }
    }
    if ((("B07".equals(t) || "A18".equals(t)) && "SS".equals(purpose)) || "A31".equals(t)) {
      salvage = "Y";
    }
    if (!s.startsWith("P") && "B07".equals(t)) {
      boolean cash = "C".equals(payment);
      if (v.saleVolume() == null) {
        e.add("Sales volume is mandatory for B07");
      }
      if (v.cashSaleEstVol() == null && cash) {
        e.add("Estimated Sales volume is mandatory for B07 Cash sale");
      }
      if (v.cashSaleTotDol() == null && cash) {
        e.add("Total Dollars Received is mandatory for B07 Cash sale");
      }
    }
    // CHECK_SALE_METHOD
    if (rules.saleMethodMandatory() && method == null) {
      e.add("Sale Method is required.");
    }
    // ADD / CHANGE: the admin area file
    if ("Y".equals(v.adminAreaInd())) {
      if (v.adminAreaFile() == null && (adding || s.startsWith("P"))) {
        e.add("Admin Area File is required.");
      }
    } else if (!adding && v.adminAreaFile() != null && s.startsWith("P")) {
      e.add("Admin Area File must be blank when Admin Area Ind is No.");
    }
    return new Result(e, w, salvage);
  }

  private static boolean over(BigDecimal value, int limit) {
    return value != null && value.compareTo(BigDecimal.valueOf(limit)) > 0;
  }

  /** The deposit pairing: an amount needs a type, and a type (but N…) needs an amount. */
  private static void deposit(List<String> e, String label, String code, BigDecimal amount) {
    if (amount == null && code != null && !code.startsWith("N")) {
      e.add(label + " Amount must not be blank when " + label + " Type is provided.");
    } else if (code == null && amount != null) {
      e.add(label + " Type must not be blank when " + label + " Amount is provided.");
    }
  }

  /** Legacy's FloatRange (0 to max) and DecimalPlaces validators for one value. */
  private static void decimal(
      List<String> e, String label, BigDecimal value, BigDecimal max, int places) {
    if (value == null) {
      return;
    }
    if (value.signum() < 0 || value.compareTo(max) > 0) {
      e.add(label + " field must be between 0.0 and " + max.toPlainString() + ".");
    } else if (value.stripTrailingZeros().scale() > places) {
      e.add(places == 1
          ? "Only one decimal-place is permitted for " + label
          : label + " cannot contain more than " + places + " places of decimal.");
    }
  }
}
