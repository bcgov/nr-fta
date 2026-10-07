package ca.bc.gov.nrs.fta.tenure.tab.cutblocks;

import java.sql.CallableStatement;
import java.sql.Types;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Deletes a cut block with legacy's own procedure, {@code FTA_DELETE_FF_CP_CB.DELETE_FF_CP_CB}
 * at level {@code BLK} — what FTA903's Delete ran ({@code FTA_903_CUTBLK_LST.mainline}
 * REMOVE). It is called rather than ported: before deleting it checks the block's references
 * in CIMS/ERA, RESULTS openings, disturbance results and SPAR, then removes the block's
 * geometry, regenerates the permit's spatial area (Fta_spatial_utils), clears
 * {@code FDC_AUDIT_LOG}/{@code HARVEST_AMEND}/{@code CUT_BLOCK_OPEN_ADMIN}, deletes the
 * {@code CUT_BLOCK} row under its revision count and writes the {@code CUT_BLOCK_KEY_EVENT}
 * 'D' record carrying the deletion comment.
 *
 * <p>It reports failure as message keys in {@code p_error_message}, having possibly deleted
 * part of the block already: the caller must roll back when {@link Result#error} is set.
 */
@Component
public class CutBlocksDeleteLegacy {

  /** The procedure's outcome: null error when the block was deleted. */
  public record Result(String error, boolean modified) {}

  private final JdbcTemplate jdbc;

  public CutBlocksDeleteLegacy(NamedParameterJdbcTemplate named) {
    this.jdbc = named.getJdbcTemplate();
  }

  /**
   * Runs the delete.
   *
   * @param cuttingPermitId the block's CUT_BLOCK.CUTTING_PERMIT_ID; legacy passes a blank as
   *                        one space
   */
  public Result delete(
      String forestFileId,
      String cuttingPermitId,
      String cutBlockId,
      Long hvaSkey,
      long cbSkey,
      String comment,
      long revisionCount,
      String userId) {
    String raw = jdbc.execute(
        (java.sql.Connection con) -> con.prepareCall(
            "DECLARE v_err VARCHAR2(4000); BEGIN"
                + " the.fta_delete_ff_cp_cb.delete_ff_cp_cb(?, ?, ?, ?, ?, ?, 'BLK', ?, ?, v_err);"
                + " ? := v_err; END;"),
        (CallableStatement cs) -> {
          cs.setString(1, forestFileId);
          cs.setString(2, cuttingPermitId == null || cuttingPermitId.isEmpty()
              ? " " : cuttingPermitId);
          cs.setString(3, cutBlockId);
          if (hvaSkey == null) {
            cs.setNull(4, Types.NUMERIC);
          } else {
            cs.setLong(4, hvaSkey);
          }
          cs.setLong(5, cbSkey);
          cs.setString(6, comment);
          cs.setString(7, Long.toString(revisionCount));
          cs.setString(8, userId);
          cs.registerOutParameter(9, Types.VARCHAR);
          cs.execute();
          return cs.getString(9);
        });
    if (raw == null || raw.isBlank()) {
      return new Result(null, false);
    }
    return new Result(readable(raw), raw.contains("fta.web.usr.database.record.modified"));
  }

  /**
   * Legacy error strings are {@code key;} or {@code key:args;} pairs — the
   * ApplicationResources text of each.
   */
  static String readable(String error) {
    StringBuilder sb = new StringBuilder();
    for (String part : error.split(";")) {
      String p = part.trim();
      if (p.isEmpty()) {
        continue;
      }
      int colon = p.indexOf(':');
      String key = colon < 0 ? p : p.substring(0, colon);
      String args = colon < 0 ? "" : p.substring(colon + 1);
      String text = switch (key) {
        case "fta.web.error.user.custom.msg" -> args.endsWith(".") ? args : args + ".";
        case "fta.web.usr.database.record.modified" ->
            "The block was changed by someone else. Reload the list and try again.";
        case "fta.web.usr.database.record.ExternalRef" ->
            "Cut Block is referenced by external systems. Cannot delete.";
        case "sil.error.usr.invalid.value" -> "Invalid " + args + ".";
        case "fta.web.usr.database.record.invalid" -> "No Records Found (" + args + ").";
        case "fta.web.usr.database.unexpected" ->
            "An unexpected error has occurred in package/procedure " + args
                + ". Please contact System Support.";
        default -> p;
      };
      if (!sb.isEmpty()) {
        sb.append(' ');
      }
      sb.append(text);
    }
    return sb.toString();
  }
}
