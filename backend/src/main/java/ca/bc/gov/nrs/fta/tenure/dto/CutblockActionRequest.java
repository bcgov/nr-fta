package ca.bc.gov.nrs.fta.tenure.dto;

/**
 * Request body for a cut-block write action ({@code POST
 * /api/fta/cut-blocks/{blockId}/actions}). The {@code blockId} identifying the
 * {@code THE.CUT_BLOCK} row comes from the path — not this body.
 *
 * <p>The {@code action} selects which legacy PL/SQL flow is mirrored:
 * <ul>
 *   <li>{@code amend} — FTA_905_BLK_AMEND (block amendment)</li>
 *   <li>{@code suspend} — FTA_914_SUSPEND_BLOCK (suspend block)</li>
 *   <li>{@code relabel} — FTA_231_CUTBLK_RELABEL (re-label block)</li>
 * </ul>
 * Field relevance depends on {@code action}: {@code reason} carries the
 * amendment description / suspension reason; {@code newCutBlockId} carries the
 * new label for a re-label.
 */
public record CutblockActionRequest(
    String action,
    String reason,
    String newCutBlockId) {}
