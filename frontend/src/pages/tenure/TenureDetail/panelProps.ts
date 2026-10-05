import type { TenureDetail } from '@/services/tenure_detail';

/**
 * What every tab panel of the tenure detail receives. Each panel loads its own
 * data from its own endpoint; `tenure` is the header (FTA_100_TENURE) the page
 * already has.
 */
export interface TenurePanelProps {
  tenure: TenureDetail;
  /** Whether the user's role may write tenures (FTA_ADMIN). */
  canEdit: boolean;
  /** Call after a write that changes the tenure header, to re-read it. */
  onTenureChanged: () => void;
}
