/**
 * Pill text and colour for a client's FOREST_CLIENT.CLIENT_STATUS_CODE.
 *
 * The client APIs return the bare code, which StatusTag's description matcher
 * cannot read ("ACT" holds no "active"), so the codes are spelled out here.
 * Only an active client is green; the rest are clients a mark should not
 * normally go to.
 */
const CLIENT_STATUSES: Record<string, { label: string; variant: string }> = {
  ACT: { label: 'Active', variant: 'approved' },
  DAC: { label: 'Deactivated', variant: 'cancelled' },
  DEC: { label: 'Deceased', variant: 'retired' },
  REC: { label: 'Receivership', variant: 'opportunity' },
  SPN: { label: 'Suspended', variant: 'opportunity' },
};

/** The pill for a client status code; an unknown code shows as itself, in grey. */
export const clientStatus = (
  code: string | null | undefined,
): { label: string; variant: string } | null => {
  const c = code?.trim();
  if (!c) return null;
  return CLIENT_STATUSES[c] ?? { label: c, variant: 'default' };
};
