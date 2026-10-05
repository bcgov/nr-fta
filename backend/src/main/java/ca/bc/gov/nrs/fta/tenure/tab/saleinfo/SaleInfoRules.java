package ca.bc.gov.nrs.fta.tenure.tab.saleinfo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * What may be changed on a tenure's Sale info tab — the gating and protection states of legacy
 * FTA940 ({@code FTA_940_SALE_INFO.mainline} GET's {@code p_save_yn}, and
 * {@code Fta940SaleInfoForm.setProtectionStates} / the JSP's enable flags). Computed once from
 * the stored record, returned with the GET and applied again by the save, which keeps the
 * stored value of every field these rules close. Role checks are not here: writes are
 * FTA_ADMIN by the generic rule, and the page disables Edit for other roles.
 *
 * <p>Org levels: legacy's ownership checks (district / region / BCTS org of the user against
 * the file's) are not applied — this app has no org units, and every FTA_ADMIN gets
 * Headquarters' rules, which keeps only {@code FTA_FILE_LEVEL_AUTHORITY}'s FILE authority for
 * Headquarters on the file type ({@code FTA_FILE_CP_AUTHORITY}). Payment Method is open only to
 * legacy's super user, which this app does not have, so it is always read-only.
 *
 * @param editable             whether the record can be saved at all
 * @param reason               why not, when {@code editable} is false
 * @param saleMethodMandatory  legacy {@code p_sale_method_code_mand}: Sale Method is required
 * @param defaultSaleMethodCode the Sale Method a new record starts with (D for some types)
 * @param saleMethodAllowed    the only Sale Methods this file type takes; empty for any
 * @param bcts                 BCTS file (legacy {@code getIsBcts}): bonus bid / offer come from
 *                             the awarded bidder and are read-only
 * @param bctsFileType         a {@code BCTS_FILE_TYPE_CODE} type: Planned BCTS Cat. and Planned
 *                             Sale Date are required
 * @param soldCatRequired      Sold BCTS Cat. is required (BCTS file type in an H status)
 * @param category3Allowed     whether BCTS category 3 may be chosen
 * @param withinAdminArea      Within Admin Area may be changed (status PP, PI or PL)
 * @param adminAreaFile        Admin Area File may be changed
 * @param salvage              Salvage Indicator may be changed (not for an A31: always Y)
 * @param minorFacility        Minor Processing Facility may be changed (A04 only)
 * @param cashSale             the Cash Sale section applies (payment C on an A21, B21 or B07)
 * @param estimatedVolume      Estimated Volume may be changed
 * @param cashFieldsRequired   Estimated Volume and Total Dollars are required (A21/B21, cash,
 *                             status P…)
 * @param tenderRequired       Tender Date is required (not cash, status H…, file id D…)
 */
public record SaleInfoRules(
    boolean editable,
    String reason,
    boolean saleMethodMandatory,
    String defaultSaleMethodCode,
    List<String> saleMethodAllowed,
    boolean bcts,
    boolean bctsFileType,
    boolean soldCatRequired,
    boolean category3Allowed,
    boolean withinAdminArea,
    boolean adminAreaFile,
    boolean salvage,
    boolean minorFacility,
    boolean cashSale,
    boolean estimatedVolume,
    boolean cashFieldsRequired,
    boolean tenderRequired) {

  /**
   * What the rules are computed from.
   *
   * @param scheduleBAac        the current AAC period's Schedule B total (A03's rule), or null
   * @param bctsFileType        whether the type is a current {@code BCTS_FILE_TYPE_CODE}
   * @param privateMarkType     whether the type is a private mark type (no sale info)
   * @param headquartersAuthority whether Headquarters has FILE authority on the type
   */
  public record Facts(
      String forestFileId,
      String fileTypeCode,
      String statusCode,
      boolean recordExists,
      String paymentMethodCode,
      String adminAreaInd,
      String adminAreaFile,
      String bctsFundInd,
      BigDecimal scheduleBAac,
      boolean bctsFileType,
      boolean privateMarkType,
      boolean headquartersAuthority) {}

  /** Types for which Sale Method is mandatory ({@code CHECK_SALE_METHOD_MAND}), with A03. */
  static final Set<String> SALE_METHOD_MANDATORY = Set.of(
      "A20", "A21", "A23", "A25", "A26", "A27", "B01", "B40", "B20", "B21", "B22", "B23");

  /** A new record's Sale Method is D for these types ({@code setProtectionStates}). */
  static final Set<String> DEFAULT_D = Set.of("A18", "A31", "B07", "B04", "A29", "A44", "A41");

  /** Legacy {@code getIsBcts}'s file types. */
  static final Set<String> BCTS_TYPES = Set.of(
      "A03", "A20", "A21", "A23", "A24", "A25", "A26", "B20", "B21", "B23", "A27");

  /** BCTS category 3 is refused for these types (form and PL/SQL). */
  static final Set<String> NO_CATEGORY_3 = Set.of("A23", "A25", "A26", "A27", "B23");

  static final Set<String> CASH_SALE_TYPES = Set.of("A21", "B21", "B07");

  private static final Set<String> ADMIN_AREA_STATUSES = Set.of("PP", "PI", "PL");

  public static final String PE_MESSAGE =
      "No updates can be performed on this file when the status is PE.";

  /** The rules for a file described by {@code f}. */
  public static SaleInfoRules of(Facts f) {
    String type = f.fileTypeCode() == null ? "" : f.fileTypeCode();
    String status = f.statusCode() == null ? "" : f.statusCode();
    String payment = f.paymentMethodCode() == null ? "" : f.paymentMethodCode();

    String reason = null;
    if (type.isEmpty()
        || "EHF".indexOf(type.charAt(0)) >= 0
        || f.privateMarkType()
        || "S01".equals(type)) {
      reason = "The File Type (" + (type.isEmpty() ? "none" : type)
          + ") associated with the queried File is invalid for this screen.";
    } else if (status.startsWith("PE")) {
      reason = PE_MESSAGE;
    } else if (!f.headquartersAuthority()) {
      reason = "Sale info cannot be updated for file type " + type
          + ": no file-level authority is set up for it.";
    }

    boolean mandatory = "A03".equals(type)
        ? f.scheduleBAac() != null && f.scheduleBAac().compareTo(BigDecimal.valueOf(10000)) < 0
        : SALE_METHOD_MANDATORY.contains(type);

    List<String> allowed = switch (type) {
      case "B20" -> List.of("A", "T");
      case "B21" -> List.of("D");
      case "A23", "A24", "B23" -> List.of("T");
      default -> List.of();
    };

    boolean bcts = BCTS_TYPES.contains(type) || "Y".equals(f.bctsFundInd());
    String status2 = status.length() > 1 ? status.substring(0, 2) : "";
    boolean adminStatus = ADMIN_AREA_STATUSES.contains(status2);
    // getIsAdminAreaEnabled, as legacy wrote it.
    boolean adminFile =
        (adminStatus
            && (payment.isEmpty() || !payment.startsWith("A") || payment.startsWith("C")))
        || ("Y".equals(f.adminAreaInd())
            && (f.adminAreaFile() == null || f.adminAreaFile().isEmpty()));
    boolean cashSale = "C".equals(payment) && CASH_SALE_TYPES.contains(type);
    boolean forestFileD = f.forestFileId() != null && f.forestFileId().startsWith("D");

    return new SaleInfoRules(
        reason == null,
        reason,
        mandatory,
        !f.recordExists() && DEFAULT_D.contains(type) ? "D" : null,
        allowed,
        bcts,
        f.bctsFileType(),
        f.bctsFileType() && status.startsWith("H"),
        !NO_CATEGORY_3.contains(type),
        adminStatus,
        adminFile,
        !"A31".equals(type),
        "A04".equals(type),
        cashSale,
        cashSale && !"A04".equals(type),
        ("A21".equals(type) || "B21".equals(type)) && "C".equals(payment)
            && status.startsWith("P"),
        status.startsWith("H") && forestFileD && !cashSale);
  }
}
