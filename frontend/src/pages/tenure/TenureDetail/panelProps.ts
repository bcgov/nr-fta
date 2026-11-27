import type { TenureDetail } from '@/services/tenure_detail';

/**
 * What the tabs the range tenure page shares with this one read of the file (910 Associated
 * files, 950x Tenure application, 970 Notes — legacy's FtaRangeTabs reused them). The range page
 * builds it from its own header.
 */
export type TenureFileRef = Pick<TenureDetail, 'forestFileId' | 'awardDate' | 'expiryDate'>;

/** The props of a tab shared with the range tenure page. */
export interface SharedTenurePanelProps {
  tenure: TenureFileRef;
  /** Whether the user's role may write tenures (FTA_ADMIN). */
  canEdit: boolean;
  /** Call after a write that changes the tenure header, to re-read it. */
  onTenureChanged: () => void;
}

/**
 * What every tab panel of the tenure detail receives. Each panel loads its own
 * data from its own endpoint; `tenure` is the header (FTA_100_TENURE) the page
 * already has.
 */
export interface TenurePanelProps extends SharedTenurePanelProps {
  tenure: TenureDetail;
}
