import type { RangeTenureDetail } from '@/services/range_tenure_detail';

/**
 * What every range-only tab panel of the range tenure page receives (100R, 920R, 613R, 615R).
 * Each panel owns its file; the tabs shared with the tenure page (910, 950x, 970) take the tenure
 * page's SharedTenurePanelProps instead.
 */
export interface RangePanelProps {
  /** The range tenure header (FTA_100RANGE_TENURE), with its usage, land base and conditions. */
  tenure: RangeTenureDetail;
  /**
   * Whether the user may edit this range file — the backend's `canEdit` (FTA_ADMIN or
   * FTA_RANGE_DISTRICT_ADMIN for the file's district, or FTA_RANGE_HEADQUARTERS_ADMIN). Edit buttons
   * are disabled when it is false.
   */
  canEdit: boolean;
  /** Call after a write that changes the range tenure, to re-read it. */
  onTenureChanged: () => void;
}
