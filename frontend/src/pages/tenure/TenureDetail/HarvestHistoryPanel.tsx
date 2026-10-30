import { Money, Receipt } from '@carbon/icons-react';
import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { useCallback, type FC } from 'react';

import AsyncBoundary from '@/components/AsyncBoundary';
import DetailTile from '@/components/DetailTile';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { useApiResource } from '@/hooks/useApiResource';
import {
  getTenureHarvestHistory,
  getTimberMarkHarvestHistory,
  type HarvestHistoryRow,
  type TenureHarvestHistory,
} from '@/services/tenure_harvesthistory';

import UnavailableNotice from './UnavailableNotice';

import type { TenurePanelProps } from './panelProps';

const money = new Intl.NumberFormat('en-CA', {
  style: 'currency',
  currency: 'CAD',
  minimumFractionDigits: 2,
});
/** m³ — legacy shows two decimals. */
const m3 = new Intl.NumberFormat('en-CA', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;
const dollars = (v: number | null | undefined) =>
  v === null || v === undefined ? '—' : money.format(v);
const volume = (v: number | null | undefined) =>
  v === null || v === undefined ? '—' : m3.format(v);

const rowKey = (r: HarvestHistoryRow, i: number) =>
  r.subtotal
    ? `${r.billingYear}-total`
    : `${r.billingYear}-${r.billingType ?? ''}-${r.species ?? ''}-${i}`;

/**
 * The Harvest history tab of the tenure detail — legacy FTA990, which FTA100's
 * "Harvest History" button opened for a B-type file with a hauling authority.
 * Read-only: the timber mark's appraisal rates and billed totals, then what was
 * billed per calendar year, billing type and species, each year closed by its
 * total row (emphasized, as legacy's bold "Total:" rows).
 */
const HarvestHistoryPanel: FC<TenurePanelProps> = ({ tenure }) => {
  const forestFileId = tenure.forestFileId;
  const fetcher = useCallback(() => getTenureHarvestHistory(forestFileId), [forestFileId]);
  return <HarvestHistoryView fetcher={fetcher} />;
};

/** The same view for one timber mark — the cutting permit detail's tab (FTA902's button). */
export const TimberMarkHarvestHistory: FC<{ timberMark: string }> = ({ timberMark }) => {
  const fetcher = useCallback(() => getTimberMarkHarvestHistory(timberMark), [timberMark]);
  return <HarvestHistoryView fetcher={fetcher} />;
};

const HarvestHistoryView: FC<{ fetcher: () => Promise<TenureHarvestHistory> }> = ({ fetcher }) => {
  const { data, loading, error, reload } = useApiResource(fetcher, [fetcher]);

  return (
    <div className="fsp-info__tab-panel">
      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Loading harvest history…"
      >
        {data &&
          (!data.available ? (
            <EmptyState
              icon={<Money size={48} />}
              title="No harvest history for this tenure"
              body={data.unavailableReason ?? ''}
            />
          ) : (
            <>
              <UnavailableNotice notices={data.unavailable} />
              <DetailTile
                title={`Timber mark ${data.timberMark ?? ''}`}
                icon={Money}
                fields={[
                  { label: 'Oldest Billing Accessed', value: dash(data.oldestBillingAccessed) },
                  { label: 'Fixed/Adjustable Rate', value: dash(data.adjustQuarterlyInd) },
                  { label: 'Appraisal Status', value: dash(data.appraisalStatus) },
                  {
                    label: 'Cruised Net (m³)',
                    value: volume(data.netCruiseVolume),
                    rowStart: true,
                  },
                  { label: 'Billed (m³)', value: volume(data.billedVolume) },
                  { label: 'Billed $', value: dollars(data.totalBilled) },
                  { label: 'Initial Rate', value: dollars(data.initialRate), rowStart: true },
                  { label: 'Current Rate', value: dollars(data.currentRate) },
                  { label: 'Original Upset Rate', value: dollars(data.originalUpsetRate) },
                  { label: 'Bonus Bid', value: dollars(data.bonusBid), rowStart: true },
                  { label: 'Silviculture Levy', value: dollars(data.silvicultureLevy) },
                  { label: 'Development Levy', value: dollars(data.developmentLevy) },
                ]}
              />
              {data.rows.length === 0 ? (
                <EmptyState
                  icon={<Money size={48} />}
                  title="Nothing billed yet"
                  body={`No harvest has been billed to timber mark ${data.timberMark ?? ''}.`}
                />
              ) : (
                <section className="fsp-info__tile">
                  <header className="fsp-info__tile-header">
                    <h2 className="fsp-info__section-title">
                      <Receipt size={20} />
                      <span>Billing by calendar year</span>
                    </h2>
                    <span className="detail-tab__reason">
                      {data.rows.length} {data.rows.length === 1 ? 'row' : 'rows'}
                    </span>
                  </header>
                  <div className="bordered-table">
                    <TableContainer description="Total $ Billed YTD = Upset $ Billed YTD + Bonus $ Billed YTD">
                      <Table size="md" useZebraStyles>
                        <TableHead>
                          <TableRow>
                            <TableHeader>Calendar Year</TableHeader>
                            <TableHeader>Billing Type</TableHeader>
                            <TableHeader>Species</TableHeader>
                            <TableHeader>m³ Billed YTD</TableHeader>
                            <TableHeader>Total $ Billed YTD</TableHeader>
                            <TableHeader>Upset $ Billed YTD</TableHeader>
                            <TableHeader>Bonus $ Billed YTD</TableHeader>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {data.rows.map((r, i) => (
                            <TableRow
                              key={rowKey(r, i)}
                              style={r.subtotal ? { fontWeight: 600 } : undefined}
                            >
                              <TableCell>
                                {r.subtotal ? `${dash(r.billingYear)} Total` : dash(r.billingYear)}
                              </TableCell>
                              <TableCell>{r.subtotal ? '' : dash(r.billingType)}</TableCell>
                              <TableCell>{r.subtotal ? '' : dash(r.species)}</TableCell>
                              <TableCell>{volume(r.volumeBilled)}</TableCell>
                              <TableCell>{dollars(r.totalBilled)}</TableCell>
                              <TableCell>{dollars(r.upsetBilled)}</TableCell>
                              <TableCell>{dollars(r.bonusBilled)}</TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  </div>
                </section>
              )}
            </>
          ))}
      </AsyncBoundary>
    </div>
  );
};

export default HarvestHistoryPanel;
