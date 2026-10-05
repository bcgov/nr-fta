package ca.bc.gov.nrs.fta.tenure.tab.cutblocks;

import java.util.List;
import java.util.Set;

/**
 * What the Cut block tab allows — legacy FTA903's gating, computed once and served both to the
 * tab (which buttons are enabled, and why not) and to the writes (which refuse what it refuses).
 *
 * <p>Legacy decides some of this by organization level (only a district may add to its own
 * permits). This app has no organization levels: every {@code FTA_ADMIN} gets Headquarters'
 * rules, so the district check of {@code FTA903_ADD_NEW} is not applied.
 *
 * @param listable  whether the file type takes cut blocks at all ({@code FTA_903_CUTBLK_LST}
 *                  warns and lists nothing for the others)
 * @param add       whether Add New is enabled
 * @param addReason why not, when {@code add} is false
 */
public record CutBlocksRules(boolean listable, boolean add, String addReason) {

  /** {@code FTA_903_CUTBLK_LST.mainline}: file types the screen refuses, besides range/recreation. */
  static final Set<String> INVALID_FILE_TYPES = Set.of("B40", "B02", "B01", "S01", "S02", "C01");

  static final String MSG_STATUS_PE =
      "No updates can be performed on this file when the status is PE.";
  static final String MSG_NO_CP = "CP is required when adding a cut block.";
  static final String MSG_INBOX = "You cannot add blocks to a mark that is currently in the Inbox.";
  static final String MSG_SALVAGE =
      "Blocks may only be added to the salvage cp(s) or to B04s and B07s with a purpose of SS.";
  static final String MSG_NO_MARK = "The permit has no primary timber mark to put the block under.";

  /** {@code fta.web.error.invalid.fileType}. */
  static String invalidFileType(String fileTypeCode) {
    return "The File Type (" + fileTypeCode
        + ") associated with the queried File is invalid for this screen.";
  }

  /** Whether {@code FTA_903_CUTBLK_LST} refuses the file type (range/recreation passed in). */
  static boolean fileTypeRefused(String fileTypeCode, boolean rangeOrRecreation) {
    return fileTypeCode == null || INVALID_FILE_TYPES.contains(fileTypeCode) || rangeOrRecreation;
  }

  /**
   * {@code FTA903_ADD_NEW}, without its district check: null when a block may be added under
   * the permit, else why not. {@code hasPrimaryMark} is {@code FTA_904.GET_DEFAULTS}' own
   * requirement — it reads the new block's mark from the permit's primary mark.
   */
  static String permitProblem(
      String fileTypeCode,
      String licenceToCutCode,
      String statusCode,
      String salvageTypeCode,
      boolean hasPrimaryMark) {
    boolean singleMark = "B07".equals(fileTypeCode) || "B04".equals(fileTypeCode);
    if (fileTypeCode == null
        || "A11".equals(fileTypeCode)
        || !(fileTypeCode.startsWith("A") || singleMark)) {
      return MSG_SALVAGE;
    }
    if ("PE".equals(statusCode)) {
      return MSG_INBOX;
    }
    if (!(singleMark && "SS".equals(licenceToCutCode)) && blank(salvageTypeCode)) {
      return MSG_SALVAGE;
    }
    if (!hasPrimaryMark) {
      return MSG_NO_MARK;
    }
    return null;
  }

  /**
   * The tab's rules.
   *
   * @param fileTypeCode      the tenure's file type
   * @param fileStatusCode    the tenure's status
   * @param rangeOrRecreation whether the type is a range or recreation file type
   * @param permits           the tenure's permits with their verdicts
   */
  static CutBlocksRules of(
      String fileTypeCode,
      String fileStatusCode,
      boolean rangeOrRecreation,
      List<CutBlocksDtos.CutBlockPermitOption> permits) {
    if (fileTypeRefused(fileTypeCode, rangeOrRecreation)) {
      return new CutBlocksRules(false, false, invalidFileType(fileTypeCode));
    }
    if ("PE".equals(fileStatusCode)) {
      return new CutBlocksRules(true, false, MSG_STATUS_PE);
    }
    if (permits.isEmpty()) {
      return new CutBlocksRules(
          true, false, fileTypeCode.startsWith("A") ? MSG_NO_CP : MSG_SALVAGE);
    }
    if (permits.stream().anyMatch(CutBlocksDtos.CutBlockPermitOption::eligible)) {
      return new CutBlocksRules(true, true, null);
    }
    boolean allInbox = permits.stream().allMatch(p -> MSG_INBOX.equals(p.reason()));
    String reason = allInbox ? MSG_INBOX
        : permits.stream().map(CutBlocksDtos.CutBlockPermitOption::reason)
            .filter(r -> !MSG_INBOX.equals(r)).findFirst().orElse(MSG_SALVAGE);
    return new CutBlocksRules(true, false, reason);
  }

  /**
   * FTA903's per-row Delete: disabled for a PE block and when the permit is retired (or, for a
   * private mark's block, which has no permit retirement, always).
   *
   * @param retiredPermitInd legacy's {@code retired_permit_ind}: Y, N, or null for a private
   *                         mark's block
   */
  static String deleteProblem(String blockStatusCode, String retiredPermitInd) {
    if ("PE".equals(blockStatusCode)) {
      return "A block in status PE cannot be deleted.";
    }
    if ("Y".equals(retiredPermitInd)) {
      return "The block's cutting permit is retired.";
    }
    if (!"N".equals(retiredPermitInd)) {
      return "A private mark's blocks cannot be deleted here.";
    }
    return null;
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }
}
