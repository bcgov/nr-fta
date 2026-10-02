/**
 * Pill colours for FTA's status codes — private marks (PRIVATE_MARK_STATUS_CODE
 * and its amendment statuses), harvesting authorities i.e. timber marks
 * (HARVEST_AUTH_STATUS_CODE), cut blocks (BLOCK_STATUS_CODE, "a subset of the
 * Timber Status Code" per the legacy help) and tenure files
 * (TENURE_FILE_STATUS_CODE).
 *
 * Keyed on the code, not the description: the descriptions ("Pending
 * Issuance", "Disallowed by HQ") match none of the words StatusTag's
 * description matcher looks for, so they would all come out grey.
 *
 * One map for both code sets, because they share codes and the codes share a
 * grammar: legacy groups statuses by first letter — P pending, H issued,
 * D disallowed (Fta_Edit_Status_Change: "Px or Dx to Hx"). So a code is the
 * same colour on every screen. One colour per stage of a mark's life:
 *
 *   draft (purple)       PP, PL, PA   proposed / not yet submitted
 *   submitted (blue)     PI, PE, PD   pending — with headquarters to process
 *   update (teal)        HN           approved, notice not yet issued
 *   approved (green)     HI, HA, HB   issued — live (HB: harvesting under way)
 *   opportunity (yellow) HS, HP       suspended
 *   retired (indigo)     LC, HC       logging complete / closed — finished normally
 *   cancelled (red)      HX, HRS      cancelled / harvesting rights surrendered
 *   rejected (pink)      DV, DR       disallowed
 *   deleted (mauve)      DD           disallowed and acknowledged — closed
 *   expired (brown)      EE
 *
 * Anything else falls back to StatusTag's own matching.
 */
const VARIANTS: Record<string, string> = {
  PP: 'draft',
  PL: 'draft',
  PA: 'draft',
  PI: 'submitted',
  PE: 'submitted',
  PD: 'submitted',
  HN: 'update',
  HI: 'approved',
  HA: 'approved',
  // Cut block: harvesting under way; legacy moves a suspended (HS) block back to it.
  HB: 'approved',
  HS: 'opportunity',
  HP: 'opportunity',
  LC: 'retired',
  HC: 'retired',
  HX: 'cancelled',
  HRS: 'cancelled',
  DV: 'rejected',
  DR: 'rejected',
  DD: 'deleted',
  EE: 'expired',
};

/** The StatusTag variant for an FTA status code (private mark, amendment or timber mark). */
export const statusCodeVariant = (code: string | null | undefined): string | undefined =>
  code ? VARIANTS[code.trim()] : undefined;
