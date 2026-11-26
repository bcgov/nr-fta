import { Grid } from '@carbon/icons-react';
import {
  Button,
  OverflowMenu,
  OverflowMenuItem,
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
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { Modal } from '@/components/Modal';
import StatusTag from '@/components/StatusTag/StatusTag';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import { getTlBlocks, setTlBlockRetired, type TlBlock } from '@/services/tenure_tlblocks';
import { formatDate } from '@/utils/formatDate';

import type { TenurePanelProps } from './panelProps';

const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;

const haFormat = new Intl.NumberFormat('en-CA', {
  minimumFractionDigits: 1,
  maximumFractionDigits: 4,
});
const ha = (v: number | null | undefined) =>
  v === null || v === undefined ? '—' : haFormat.format(v);

/** A pending row action that needs confirming. */
type Pending = { kind: 'retire' | 'unretire'; block: TlBlock };

const CONFIRM: Record<
  Pending['kind'],
  { heading: string; body: (id: string) => string; action: string; busy: string; done: string }
> = {
  retire: {
    // Legacy's confirm: "Are you sure you want to retire the selected TL Block?"
    heading: 'Retire TL block',
    body: (id) => `Are you sure you want to retire TL block ${id}? Its retirement date is today.`,
    action: 'Retire',
    busy: 'Retiring…',
    done: 'retired',
  },
  unretire: {
    heading: 'Un-retire TL block',
    body: (id) =>
      `Are you sure you want to un-retire TL block ${id}? Its retirement date is cleared.`,
    action: 'Un-retire',
    busy: 'Un-retiring…',
    done: 'un-retired',
  },
};

/**
 * The TL blocks tab of the tenure detail — legacy FTA980 (TL Block Summary): the
 * Timber Licence's blocks (not cut blocks or cutting permits) with gross,
 * eliminated and net hectares, their totals, and the retirement date.
 *
 * As in legacy, the only actions are Retire / Un-retire, which set or clear a
 * block's retirement date once the tenure is issued. The tab applies only to
 * Timber Licences (A06).
 */
const TlBlocksPanel: FC<TenurePanelProps> = ({ tenure, canEdit }) => {
  const forestFileId = tenure.forestFileId;
  const { display } = useNotification();
  const fetcher = useCallback(() => getTlBlocks(forestFileId), [forestFileId]);
  const { data, loading, error, reload } = useApiResource(fetcher, [forestFileId]);

  const canRetire = canEdit && !!data?.rules.retire;

  const [pending, setPending] = useState<Pending | null>(null);
  const [acting, setActing] = useState(false);

  const confirm = async () => {
    if (!pending) return;
    const { kind, block } = pending;
    setActing(true);
    try {
      await setTlBlockRetired(
        forestFileId,
        block.tlBlockId,
        kind === 'retire',
        block.revisionCount,
      );
      display({
        kind: 'success',
        title: `TL block ${block.tlBlockId} ${CONFIRM[kind].done}`,
        timeout: 6000,
      });
      setPending(null);
      reload();
    } catch (err) {
      display({
        kind: 'error',
        title: `Could not ${CONFIRM[kind].action.toLowerCase()} the TL block`,
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setActing(false);
    }
  };

  const pendingText = pending ? CONFIRM[pending.kind] : null;

  return (
    <div className="fsp-info__tab-panel">
      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Loading TL blocks…"
      >
        {data &&
          (data.blocks.length === 0 ? (
            <EmptyState
              icon={<Grid size={48} />}
              title={
                data.rules.timberLicence
                  ? 'No TL blocks for this licence'
                  : 'TL blocks apply only to Timber Licences'
              }
              body={
                data.rules.timberLicence
                  ? 'This licence has no blocks within its Timber Licence area.'
                  : 'This tab lists the blocks within a Timber Licence (A06) area.'
              }
            />
          ) : (
            <div className="bordered-table">
              <TableContainer>
                <Table size="md" useZebraStyles>
                  <TableHead>
                    <TableRow>
                      <TableHeader>Block</TableHeader>
                      <TableHeader>Gross (ha)</TableHeader>
                      <TableHeader>Eliminated (ha)</TableHeader>
                      <TableHeader>Net (ha)</TableHeader>
                      <TableHeader>Status</TableHeader>
                      <TableHeader>Retirement Date</TableHeader>
                      <TableHeader aria-label="Actions" />
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {data.blocks.map((b) => {
                      const retired = !!b.retirementDate;
                      return (
                        <TableRow key={b.tlBlockId}>
                          <TableCell>{b.tlBlockId}</TableCell>
                          <TableCell>{ha(b.grossHa)}</TableCell>
                          <TableCell>{ha(b.eliminHa)}</TableCell>
                          <TableCell>{ha(b.netHa)}</TableCell>
                          <TableCell>
                            <StatusTag status={retired ? 'Retired' : 'Active'} />
                          </TableCell>
                          <TableCell>{dash(formatDate(b.retirementDate))}</TableCell>
                          <TableCell>
                            <OverflowMenu
                              size="sm"
                              flipped
                              iconDescription={`TL block ${b.tlBlockId} actions`}
                            >
                              <OverflowMenuItem
                                itemText={retired ? 'Un-retire' : 'Retire'}
                                disabled={!canRetire}
                                onClick={() =>
                                  setPending({ kind: retired ? 'unretire' : 'retire', block: b })
                                }
                              />
                            </OverflowMenu>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                    <TableRow style={{ fontWeight: 600 }}>
                      <TableCell>Totals</TableCell>
                      <TableCell>{ha(data.totalGrossHa)}</TableCell>
                      <TableCell>{ha(data.totalEliminHa)}</TableCell>
                      <TableCell>{ha(data.totalNetHa)}</TableCell>
                      <TableCell />
                      <TableCell />
                      <TableCell />
                    </TableRow>
                  </TableBody>
                </Table>
              </TableContainer>
            </div>
          ))}
      </AsyncBoundary>

      {/* Retire / un-retire confirmation — the tabs' small dialog shape. */}
      <Modal
        open={!!pending}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading={pendingText?.heading ?? ''}
        onRequestClose={() => {
          if (!acting) setPending(null);
        }}
        preventCloseOnClickOutside
      >
        <p className="detail-dialog__subtitle">
          {pending && pendingText ? pendingText.body(pending.block.tlBlockId) : ''}
        </p>
        <div className="detail-dialog__actions">
          <Button kind="tertiary" disabled={acting} onClick={() => setPending(null)}>
            Cancel
          </Button>
          <Button kind="primary" disabled={acting} onClick={() => void confirm()}>
            {acting ? pendingText?.busy : pendingText?.action}
          </Button>
        </div>
      </Modal>
    </div>
  );
};

export default TlBlocksPanel;
