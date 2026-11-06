import { RecentlyViewed } from '@carbon/icons-react';
import {
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { useCallback, useState, type FC } from 'react';

import AsyncBoundary from '@/components/AsyncBoundary';
import DefinitionGrid from '@/components/DefinitionGrid';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import UserName from '@/components/UserName';
import { useApiResource } from '@/hooks/useApiResource';
import { getAptCommitmentHistory, type AptCommitmentHistory } from '@/services/apt/commitments';
import { formatDate } from '@/utils/formatDate';

const nf = new Intl.NumberFormat('en-CA');
const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;

const OPERATIONS: Record<string, string> = { INS: 'Added', UPD: 'Changed', DEL: 'Deleted' };
const operationLabel = (op: string | null | undefined) => (op ? (OPERATIONS[op] ?? op) : '—');

/** `Mon D, YYYY HH:MM` from an ISO date-time. */
const formatDateTime = (v: string | null | undefined) => {
  if (!v) return '—';
  const time = /T(\d{2}:\d{2})/.exec(v)?.[1];
  return time ? `${formatDate(v)} ${time}` : formatDate(v);
};

interface Props {
  commitmentId: number | string;
  /** Called once the journal has loaded, so a page can link to the licence. */
  onLoaded?: (rows: AptCommitmentHistory[]) => void;
}

/**
 * APT2's APTM6200 Commitment History: the journal, newest first, and the commitment as the
 * selected entry left it. Shown as a page and in the commitment lists' History dialog.
 */
const CommitmentHistoryView: FC<Props> = ({ commitmentId, onLoaded }) => {
  const fetcher = useCallback(
    () =>
      getAptCommitmentHistory(commitmentId).then((rows) => {
        onLoaded?.(rows);
        return rows;
      }),
    [commitmentId, onLoaded],
  );
  const { data, loading, error, reload } = useApiResource(fetcher, [fetcher]);
  const [selectedRowId, setSelectedRowId] = useState<string | null>(null);

  const rows = data ?? [];
  const selected = rows.find((r) => r.rowId === selectedRowId) ?? rows[0];

  return (
    <AsyncBoundary loading={loading} error={error} onRetry={reload} loadingText="Loading history…">
      {rows.length === 0 ? (
        <EmptyState
          icon={<RecentlyViewed size={48} />}
          title="No history"
          body="This commitment has no journal entries."
        />
      ) : (
        <Stack gap={6}>
          <div className="bordered-table">
            <TableContainer>
              <Table size="md" useZebraStyles aria-label="Commitment journal">
                <TableHead>
                  <TableRow>
                    <TableHeader>Date and time</TableHeader>
                    <TableHeader>Change</TableHeader>
                    <TableHeader>User</TableHeader>
                    <TableHeader>Reason</TableHeader>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((r) => {
                    const isSelected = r.rowId === selected?.rowId;
                    const choose = () => setSelectedRowId(r.rowId);
                    return (
                      <TableRow
                        key={r.rowId}
                        isSelected={isSelected}
                        aria-selected={isSelected}
                        className="fsp-search__row--selectable"
                        tabIndex={0}
                        onClick={choose}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter' || e.key === ' ') {
                            e.preventDefault();
                            choose();
                          }
                        }}
                      >
                        <TableCell>{formatDateTime(r.jnDatetime)}</TableCell>
                        <TableCell>{operationLabel(r.jnOperation)}</TableCell>
                        <TableCell>
                          <UserName userId={r.jnOracleUser} />
                        </TableCell>
                        <TableCell>{dash(r.jnNotes)}</TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          </div>
          {selected && (
            <section aria-label="Selected entry">
              <h3 className="cds--type-heading-compact-01">
                {operationLabel(selected.jnOperation)} {formatDateTime(selected.jnDatetime)}
              </h3>
              <DefinitionGrid
                items={[
                  { label: 'Forest File ID', value: dash(selected.forestFileId) },
                  { label: 'Partition Type', value: dash(selected.aptPartitionSt) },
                  { label: 'Commitment Type', value: dash(selected.commitTypeSt) },
                  {
                    label: 'Allowable Annual Cut (m³)',
                    value:
                      selected.allowableAnnualCut == null
                        ? '—'
                        : nf.format(selected.allowableAnnualCut),
                  },
                  {
                    label: 'Incl in Licence AAC',
                    value: selected.includeInLicAacInd === 'Y' ? 'Yes' : 'No',
                  },
                  {
                    label: 'Determination Date',
                    value: dash(formatDate(selected.determinationDate)),
                  },
                  { label: 'Effective Date', value: dash(formatDate(selected.effectiveDate)) },
                  { label: 'Expiry Date', value: dash(formatDate(selected.expiryDate)) },
                  { label: 'User', value: <UserName userId={selected.jnOracleUser} /> },
                  { label: 'Reason', value: dash(selected.jnNotes) },
                ]}
              />
            </section>
          )}
        </Stack>
      )}
    </AsyncBoundary>
  );
};

export default CommitmentHistoryView;
