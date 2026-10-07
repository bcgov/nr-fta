import { Tree } from '@carbon/icons-react';
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
import { Link } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { statusCodeVariant } from '@/components/StatusTag/statusCodes';
import StatusTag from '@/components/StatusTag/StatusTag';
import { useAuth } from '@/context/auth/useAuth';
import { useApiResource } from '@/hooks/useApiResource';
import { isPathAllowedForUser } from '@/routes/access';
import { blockLink, getTenureCutBlocks } from '@/services/tenure_cutblocks';
import { formatDate } from '@/utils/formatDate';

const dash = (v: string | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;
const date = (v: string | null | undefined) => dash(formatDate(v));

interface Props {
  /** The permit's tenure: its blocks are listed with the tenure's (FTA903). */
  forestFileId: string;
  /** The permit, or a Fort St. John authority's HVA id; null for a single-mark tenure. */
  cuttingPermitId: string | null;
}

/**
 * The permit's cut blocks — the tenure's Cut block list (FTA903) narrowed to
 * this permit, read-only. Adding and deleting blocks stays on the tenure's tab.
 */
const PermitCutBlocks: FC<Props> = ({ forestFileId, cuttingPermitId }) => {
  const { user } = useAuth();
  const fetcher = useCallback(() => getTenureCutBlocks(forestFileId), [forestFileId]);
  const { data, loading, error, reload } = useApiResource(fetcher, [forestFileId]);
  const blocks = (data?.blocks ?? []).filter(
    (b) => (b.cuttingPermitId ?? '') === (cuttingPermitId ?? ''),
  );
  // The timber mark roles can't open the cut block detail page.
  const linkable = isPathAllowedForUser(user, '/cut-block/x');

  return (
    <AsyncBoundary
      loading={loading}
      error={error}
      onRetry={reload}
      loadingText="Loading cut blocks…"
    >
      {data &&
        (blocks.length === 0 ? (
          <EmptyState
            icon={<Tree size={48} />}
            title="No cut blocks for this permit"
            body="Blocks are added on the tenure's Cut block tab."
          />
        ) : (
          <div className="bordered-table">
            <TableContainer>
              <Table size="md" useZebraStyles>
                <TableHead>
                  <TableRow>
                    <TableHeader>Cut Block</TableHeader>
                    <TableHeader>Block Status</TableHeader>
                    <TableHeader>Salvage</TableHeader>
                    <TableHeader>Harvest Start – Complete</TableHeader>
                    <TableHeader>Planned Gross (ha)</TableHeader>
                    <TableHeader>Planned Net (ha)</TableHeader>
                    <TableHeader>Actual Gross (ha)</TableHeader>
                    <TableHeader>Authorized File / CP</TableHeader>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {blocks.map((b) => (
                    <TableRow key={b.cbSkey}>
                      <TableCell>
                        {linkable ? <Link to={blockLink(b)}>{b.cutBlockId}</Link> : b.cutBlockId}
                      </TableCell>
                      <TableCell>
                        {b.blockStatusCode ? (
                          <StatusTag
                            status={b.blockStatus ?? b.blockStatusCode}
                            variant={statusCodeVariant(b.blockStatusCode)}
                          />
                        ) : (
                          '—'
                        )}
                      </TableCell>
                      <TableCell>{dash(b.salvageTypeCode)}</TableCell>
                      <TableCell>
                        {b.startDate || b.endDate
                          ? `${date(b.startDate)} – ${date(b.endDate)}`
                          : '—'}
                      </TableCell>
                      <TableCell>{dash(b.plannedGross)}</TableCell>
                      <TableCell>{dash(b.plannedNet)}</TableCell>
                      <TableCell>{dash(b.actualGross)}</TableCell>
                      <TableCell>
                        {b.authorizedFileId ? (
                          <>
                            <Link to={`/tenures/${encodeURIComponent(b.authorizedFileId)}`}>
                              {b.authorizedFileId}
                            </Link>
                            {b.authorizedCpId ? ` / ${b.authorizedCpId}` : ''}
                          </>
                        ) : (
                          '—'
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </div>
        ))}
    </AsyncBoundary>
  );
};

export default PermitCutBlocks;
