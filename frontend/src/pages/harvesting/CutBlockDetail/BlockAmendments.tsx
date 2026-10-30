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

import AsyncBoundary from '@/components/AsyncBoundary';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { useApiResource } from '@/hooks/useApiResource';
import { getBlockAmendments } from '@/services/tenure_amendments';
import { formatDate } from '@/utils/formatDate';

const num = (v: number | null) =>
  v === null
    ? '—'
    : v.toLocaleString('en-CA', { minimumFractionDigits: 1, maximumFractionDigits: 4 });
const date = (v: string | null) => formatDate(v) || '—';

/** FTA905's block view: every amendment of the block, read-only. Amendment 0 is the original. */
const BlockAmendments: FC<{ forestFileId: string; cbSkey: number }> = ({
  forestFileId,
  cbSkey,
}) => {
  const fetcher = useCallback(
    () => getBlockAmendments(forestFileId, cbSkey),
    [forestFileId, cbSkey],
  );
  const { data, loading, error, reload } = useApiResource(fetcher, [forestFileId, cbSkey]);
  const rows = data?.amendments ?? [];

  return (
    <AsyncBoundary
      loading={loading}
      error={error}
      onRetry={reload}
      loadingText="Loading amendments…"
    >
      {data &&
        (rows.length === 0 ? (
          <EmptyState
            icon={<Tree size={48} />}
            title="No amendments for this cut block"
            body="Amendments to the block's area and cruise volume appear here."
          />
        ) : (
          <div className="bordered-table">
            <TableContainer title="Amendments" description={`${rows.length} amendment(s)`}>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableHeader>Amend ID</TableHeader>
                    <TableHeader>Net (ha)</TableHeader>
                    <TableHeader>Gross (ha)</TableHeader>
                    <TableHeader>Cruise (m³)</TableHeader>
                    <TableHeader>Date</TableHeader>
                    <TableHeader>Status</TableHeader>
                    <TableHeader>Status Date</TableHeader>
                    <TableHeader>Reason</TableHeader>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((a) => (
                    <TableRow key={a.amendmentId ?? -1}>
                      <TableCell>{a.amendmentId === 0 ? 'ORIG' : (a.amendmentId ?? '—')}</TableCell>
                      <TableCell>{num(a.netArea)}</TableCell>
                      <TableCell>{num(a.grossArea)}</TableCell>
                      <TableCell>{num(a.cruiseVolume)}</TableCell>
                      <TableCell>{date(a.applicationDate)}</TableCell>
                      <TableCell>{a.statusCode ?? '—'}</TableCell>
                      <TableCell>{date(a.statusDate)}</TableCell>
                      <TableCell>{a.reasonCode ?? '—'}</TableCell>
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

export default BlockAmendments;
