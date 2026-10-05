import { ArrowLeft, RecentlyViewed, Tree } from '@carbon/icons-react';
import {
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { useCallback, useState, type FC } from 'react';
import { Link } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import DetailTile from '@/components/DetailTile';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { statusCodeVariant } from '@/components/StatusTag/statusCodes';
import StatusTag from '@/components/StatusTag/StatusTag';
import { useApiResource } from '@/hooks/useApiResource';
import {
  getBlockAmendments,
  getTenureAmendments,
  type AmendedBlock,
} from '@/services/tenure_amendments';
import { formatDate } from '@/utils/formatDate';

import type { TenurePanelProps } from './panelProps';

const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;
const date = (v: string | null | undefined) => dash(formatDate(v));
/** Hectares / m³ — legacy shows up to 4 decimals. */
const num = (v: number | null | undefined) =>
  v === null || v === undefined
    ? '—'
    : v.toLocaleString('en-CA', { minimumFractionDigits: 1, maximumFractionDigits: 4 });

const blockLink = (forestFileId: string, b: { cutBlockId: string | null }) =>
  b.cutBlockId ? (
    <Link
      to={`/cut-block/${encodeURIComponent(b.cutBlockId)}?forestFileId=${encodeURIComponent(
        forestFileId,
      )}`}
    >
      {b.cutBlockId}
    </Link>
  ) : (
    '—'
  );

/** FTA905's block view ("Amend Details"): the block's areas and every amendment. */
const BlockAmendmentsView: FC<{
  forestFileId: string;
  block: AmendedBlock;
  onBack: () => void;
}> = ({ forestFileId, block, onBack }) => {
  const cbSkey = block.cbSkey ?? 0;
  const fetcher = useCallback(
    () => getBlockAmendments(forestFileId, cbSkey),
    [forestFileId, cbSkey],
  );
  const { data, loading, error, reload } = useApiResource(fetcher, [forestFileId, cbSkey]);

  // A fragment: its parts sit directly in the tab panel, which spaces them.
  return (
    <>
      <div>
        <Button kind="ghost" size="sm" renderIcon={ArrowLeft} onClick={onBack}>
          All amended blocks
        </Button>
      </div>
      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Loading the block's amendments…"
      >
        {data && (
          <>
            <DetailTile
              title={`Cut block ${data.cutBlockId ?? ''}`}
              icon={Tree}
              fields={[
                {
                  label: data.fsj ? 'HVA ID' : 'Cutting Permit',
                  value: dash(data.cuttingPermitId),
                },
                { label: 'Timber Mark', value: dash(data.timberMark) },
                {
                  label: 'Block Status',
                  value: data.blockStatusCode ? (
                    <StatusTag
                      status={data.blockStatus ?? data.blockStatusCode}
                      variant={statusCodeVariant(data.blockStatusCode)}
                    />
                  ) : (
                    '—'
                  ),
                },
                { label: 'Status Date', value: date(data.blockStatusDate) },
                { label: 'Planned Net Area (ha)', value: num(data.plannedNetArea), rowStart: true },
                { label: 'Planned Gross Area (ha)', value: num(data.plannedGrossArea) },
                { label: 'Actual Gross Harvest (ha)', value: num(data.disturbanceGrossArea) },
              ]}
            />
            <div className="bordered-table">
              <TableContainer>
                <Table size="md" useZebraStyles>
                  <TableHead>
                    <TableRow>
                      <TableHeader>Amend ID</TableHeader>
                      <TableHeader>Net Amend (ha)</TableHeader>
                      <TableHeader>Gross Amend (ha)</TableHeader>
                      <TableHeader>Cruise Volume (m³)</TableHeader>
                      <TableHeader>Application Date</TableHeader>
                      <TableHeader>Status</TableHeader>
                      <TableHeader>Status Date</TableHeader>
                      <TableHeader>Reason</TableHeader>
                      <TableHeader>Feature Image</TableHeader>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {data.amendments.map((a, i) => (
                      <TableRow key={`${a.amendmentId ?? 'amend'}-${i}`}>
                        <TableCell>{a.amendmentId === 0 ? 'ORIG' : dash(a.amendmentId)}</TableCell>
                        <TableCell>{num(a.netArea)}</TableCell>
                        <TableCell>{num(a.grossArea)}</TableCell>
                        <TableCell>{num(a.cruiseVolume)}</TableCell>
                        <TableCell>{date(a.applicationDate)}</TableCell>
                        <TableCell>{dash(a.statusCode)}</TableCell>
                        <TableCell>{date(a.statusDate)}</TableCell>
                        <TableCell>{dash(a.reasonCode)}</TableCell>
                        <TableCell>
                          {a.hasImage
                            ? `Yes${a.imageMimeTypeCode ? ` (${a.imageMimeTypeCode})` : ''}`
                            : 'No'}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </div>
          </>
        )}
      </AsyncBoundary>
    </>
  );
};

/**
 * The CP/CB amendments tab of the tenure detail — legacy FTA905 (Cutting
 * Permit / Cut Block Amendments), which is read-only. The file's amended cut
 * blocks with their amendment totals; "Amend details" drills into one block's
 * amendments in place, as legacy's button did. File types FTA905 rejects show
 * legacy's message instead.
 */
const CpCbAmendmentsPanel: FC<TenurePanelProps> = ({ tenure }) => {
  const forestFileId = tenure.forestFileId;
  const fetcher = useCallback(() => getTenureAmendments(forestFileId), [forestFileId]);
  const { data, loading, error, reload } = useApiResource(fetcher, [forestFileId]);
  const [selected, setSelected] = useState<AmendedBlock | null>(null);

  if (selected) {
    return (
      <div className="fsp-info__tab-panel">
        <BlockAmendmentsView
          forestFileId={forestFileId}
          block={selected}
          onBack={() => setSelected(null)}
        />
      </div>
    );
  }

  return (
    <div className="fsp-info__tab-panel">
      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Loading amendments…"
      >
        {data &&
          (!data.available || data.blocks.length === 0 ? (
            <EmptyState
              icon={<RecentlyViewed size={48} />}
              title={
                data.available
                  ? 'No cut block amendments for this tenure'
                  : 'Amendments do not apply to this tenure'
              }
              body={
                data.available
                  ? "None of this tenure's cut blocks has been amended."
                  : (data.unavailableReason ?? '')
              }
            />
          ) : (
            <div className="bordered-table">
              <TableContainer>
                <Table size="md" useZebraStyles>
                  <TableHead>
                    <TableRow>
                      <TableHeader>CP / HVA ID</TableHeader>
                      <TableHeader>Timber Mark</TableHeader>
                      <TableHeader>Cut Block</TableHeader>
                      <TableHeader>Total Amend to Net (ha)</TableHeader>
                      <TableHeader>Total Amend to Gross (ha)</TableHeader>
                      <TableHeader>Total Amend to Cruise (m³)</TableHeader>
                      <TableHeader>
                        <span className="cds--visually-hidden">Actions</span>
                      </TableHeader>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {data.blocks.map((b, i) => (
                      <TableRow key={b.cbSkey ?? `${b.cuttingPermitId}-${b.cutBlockId}-${i}`}>
                        <TableCell>{dash(b.cuttingPermitId)}</TableCell>
                        <TableCell>{dash(b.timberMark)}</TableCell>
                        <TableCell>{blockLink(forestFileId, b)}</TableCell>
                        <TableCell>{num(b.totalNetArea)}</TableCell>
                        <TableCell>{num(b.totalGrossArea)}</TableCell>
                        <TableCell>{num(b.totalCruiseVolume)}</TableCell>
                        <TableCell>
                          <Button
                            kind="ghost"
                            size="sm"
                            disabled={b.cbSkey === null}
                            onClick={() => setSelected(b)}
                          >
                            Amend details
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </div>
          ))}
      </AsyncBoundary>
    </div>
  );
};

export default CpCbAmendmentsPanel;
