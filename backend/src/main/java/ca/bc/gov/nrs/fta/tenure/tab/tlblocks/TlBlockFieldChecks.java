package ca.bc.gov.nrs.fta.tenure.tab.tlblocks;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The field checks of a TL block save — legacy {@code Fta980TlblockForm}'s "Save" validators
 * and {@code FTA_980_TLBLOCK.SAVE}'s gross/eliminated check, with their message texts:
 *
 * <ul>
 *   <li>Block: required, alpha numeric, upper-cased ({@code forceFormats}); at most 10
 *       characters, the width legacy's {@code LPAD(tl_block_id, 10)} sort assumes;
 *   <li>Gross and Eliminated: numeric, 0 to 9999999.9, one decimal place at most. Gross is
 *       required and above 0 (the package's "P_TL_BLOCK_GROSS_HA MUST BE &gt; 0"); Eliminated
 *       is optional and saved as 0 when blank, as the package's {@code NVL} does;
 *   <li>"Gross Value must be greater or equal to Eliminated."
 * </ul>
 */
public final class TlBlockFieldChecks {

  static final int MAX_BLOCK_ID = 10;

  static final BigDecimal MAX_HA = new BigDecimal("9999999.9");

  private TlBlockFieldChecks() {}

  /** The block id as legacy saved it: trimmed and upper-cased; null when blank. */
  public static String normalizeBlockId(String blockId) {
    return blockId == null || blockId.isBlank()
        ? null
        : blockId.trim().toUpperCase(Locale.ROOT);
  }

  /** Problems with a new block's id; empty when it is fine. */
  public static List<String> blockIdProblems(String blockId) {
    List<String> e = new ArrayList<>();
    if (blockId == null) {
      e.add("Block is mandatory.");
    } else if (!blockId.matches("[A-Z0-9]+")) {
      e.add("Block must be alpha numeric [A-Z, 0-9]");
    } else if (blockId.length() > MAX_BLOCK_ID) {
      e.add("Block can be at most " + MAX_BLOCK_ID + " characters.");
    }
    return e;
  }

  /** Problems with a block's areas; empty when they are fine. */
  public static List<String> areaProblems(BigDecimal gross, BigDecimal eliminated) {
    List<String> e = new ArrayList<>();
    boolean grossOk = false;
    if (gross == null) {
      e.add("Gross is mandatory.");
    } else if (gross.signum() <= 0 || gross.compareTo(MAX_HA) > 0) {
      e.add("Gross must have a value between 0.1 and 9999999.9 inclusive.");
    } else if (decimals(gross) > 1) {
      e.add("Only one decimal-place is permitted for Gross (ha)");
    } else {
      grossOk = true;
    }
    boolean elimOk = false;
    if (eliminated == null) {
      elimOk = true;
    } else if (eliminated.signum() < 0 || eliminated.compareTo(MAX_HA) > 0) {
      e.add("Eliminated must have a value between 0 and 9999999.9 inclusive.");
    } else if (decimals(eliminated) > 1) {
      e.add("Only one decimal-place is permitted for Eliminated (ha)");
    } else {
      elimOk = true;
    }
    if (grossOk && elimOk && eliminated != null && gross.compareTo(eliminated) < 0) {
      e.add("Gross Value must be greater or equal to Eliminated.");
    }
    return e;
  }

  private static int decimals(BigDecimal v) {
    return Math.max(0, v.stripTrailingZeros().scale());
  }
}
