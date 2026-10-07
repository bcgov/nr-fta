import { Add, Grid } from '@carbon/icons-react';
import {
  Button,
  OverflowMenu,
  OverflowMenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TextInput,
} from '@carbon/react';
import { useCallback, useState, type FC } from 'react';

import AsyncBoundary from '@/components/AsyncBoundary';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { Modal } from '@/components/Modal';
import StatusTag from '@/components/StatusTag/StatusTag';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import {
  addTlBlock,
  deleteTlBlock,
  getTlBlocks,
  setTlBlockRetired,
  updateTlBlock,
  type TlBlock,
} from '@/services/tenure_tlblocks';
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

/** Legacy Fta980TlblockForm's limits; the backend enforces them too. */
const MAX_BLOCK_ID = 10;
const MAX_HA = 9999999.9;
const HA_PATTERN = /^\d+(\.\d+)?$/;

interface Form {
  blockId: string;
  gross: string;
  elim: string;
}

type Errors = Partial<Record<keyof Form, string>>;

/** Legacy's "Save" validators and FTA_980_TLBLOCK.SAVE's gross/eliminated check. */
const validate = (f: Form, adding: boolean): Errors => {
  const e: Errors = {};
  const id = f.blockId.trim();
  if (adding) {
    if (!id) e.blockId = 'Block is mandatory.';
    else if (!/^[A-Z0-9]+$/i.test(id)) e.blockId = 'Block must be alpha numeric [A-Z, 0-9]';
    else if (id.length > MAX_BLOCK_ID) e.blockId = `At most ${MAX_BLOCK_ID} characters.`;
  }
  const area = (raw: string, label: string, min: number, required: boolean) => {
    const v = raw.trim();
    if (!v) return required ? `${label} is mandatory.` : undefined;
    if (!HA_PATTERN.test(v)) return `${label} must be numeric.`;
    const n = Number(v);
    if (n < min || n > MAX_HA)
      return `${label} must have a value between ${min} and ${MAX_HA} inclusive.`;
    if ((v.split('.')[1] ?? '').replace(/0+$/, '').length > 1)
      return `Only one decimal-place is permitted for ${label} (ha)`;
    return undefined;
  };
  const gross = area(f.gross, 'Gross', 0.1, true);
  const elim = area(f.elim, 'Eliminated', 0, false);
  if (gross) e.gross = gross;
  if (elim) e.elim = elim;
  if (!gross && !elim && f.elim.trim() && Number(f.gross) < Number(f.elim))
    e.elim = 'Gross Value must be greater or equal to Eliminated.';
  return e;
};

/** A pending row action that needs confirming. */
type Pending = { kind: 'delete' | 'retire' | 'unretire'; block: TlBlock };

const CONFIRM: Record<
  Pending['kind'],
  { heading: string; body: (id: string) => string; action: string; busy: string; done: string }
> = {
  delete: {
    heading: 'Delete TL block',
    body: (id) => `TL block ${id} is deleted and the licence's areas are recalculated.`,
    action: 'Delete',
    busy: 'Deleting…',
    done: 'deleted',
  },
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
 * Blocks can be added, have their areas changed, and be deleted (FTA_980's SAVE
 * and REMOVE); each recalculates the licence's areas. Retire / Un-retire set or
 * clear a block's retirement date, once the tenure is issued. The tab applies
 * only to Timber Licences (A06).
 */
const TlBlocksPanel: FC<TenurePanelProps> = ({ tenure, canEdit }) => {
  const forestFileId = tenure.forestFileId;
  const { display } = useNotification();
  const fetcher = useCallback(() => getTlBlocks(forestFileId), [forestFileId]);
  const { data, loading, error, reload } = useApiResource(fetcher, [forestFileId]);

  const rules = data?.rules;
  const canAdd = canEdit && !!rules?.edit;
  const canRetire = canEdit && !!rules?.retire;

  // Add / edit dialog — `editing` is the block being changed, null when adding.
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<TlBlock | null>(null);
  const [form, setForm] = useState<Form>({ blockId: '', gross: '', elim: '' });
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);

  const [pending, setPending] = useState<Pending | null>(null);
  const [acting, setActing] = useState(false);

  const set = (key: keyof Form, value: string) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    setErrors((prev) => {
      if (!prev[key]) return prev;
      const next = { ...prev };
      delete next[key];
      return next;
    });
  };

  const openAdd = () => {
    setEditing(null);
    setForm({ blockId: '', gross: '', elim: '' });
    setErrors({});
    setOpen(true);
  };

  const openEdit = (b: TlBlock) => {
    setEditing(b);
    setForm({
      blockId: b.tlBlockId,
      gross: b.grossHa === null ? '' : String(b.grossHa),
      elim: b.eliminHa === null ? '' : String(b.eliminHa),
    });
    setErrors({});
    setOpen(true);
  };

  const closeDialog = () => {
    if (!saving) setOpen(false);
  };

  const submit = async () => {
    const found = validate(form, !editing);
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    const grossHa = Number(form.gross.trim());
    const eliminHa = form.elim.trim() ? Number(form.elim.trim()) : null;
    setSaving(true);
    try {
      if (editing) {
        await updateTlBlock(forestFileId, editing.tlBlockId, {
          grossHa,
          eliminHa,
          revisionCount: editing.revisionCount,
        });
      } else {
        await addTlBlock(forestFileId, {
          tlBlockId: form.blockId.trim().toUpperCase(),
          grossHa,
          eliminHa,
        });
      }
      const id = editing?.tlBlockId ?? form.blockId.trim().toUpperCase();
      display({
        kind: 'success',
        title: `TL block ${id} ${editing ? 'saved' : 'added'}`,
        subtitle: "The licence's areas were recalculated.",
        timeout: 6000,
      });
      setOpen(false);
      reload();
    } catch (err) {
      display({
        kind: 'error',
        title: `Could not ${editing ? 'save' : 'add'} the TL block`,
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  const confirm = async () => {
    if (!pending) return;
    const { kind, block } = pending;
    setActing(true);
    try {
      if (kind === 'delete') {
        await deleteTlBlock(forestFileId, block.tlBlockId, block.revisionCount);
      } else {
        await setTlBlockRetired(
          forestFileId,
          block.tlBlockId,
          kind === 'retire',
          block.revisionCount,
        );
      }
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

  const addButton = (primary: boolean) => (
    <Button
      kind={primary ? 'primary' : 'tertiary'}
      size={primary ? undefined : 'sm'}
      renderIcon={Add}
      disabled={!canAdd}
      onClick={openAdd}
    >
      Add TL block
    </Button>
  );

  const net = (() => {
    const g = Number(form.gross);
    const e = form.elim.trim() ? Number(form.elim) : 0;
    return form.gross.trim() && Number.isFinite(g) && Number.isFinite(e) && g >= e
      ? haFormat.format(g - e)
      : null;
  })();

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
                  ? 'Add the blocks within the Timber Licence area, with their gross and eliminated hectares.'
                  : 'This tab lists the blocks within a Timber Licence (A06) area.'
              }
              action={
                // Always shown; disabled when adding isn't allowed.
                <div className="detail-tab__empty-action">{addButton(true)}</div>
              }
            />
          ) : (
            <div>
              <header className="detail-tab__actions">{addButton(false)}</header>
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
                                  itemText="Edit"
                                  disabled={!canAdd}
                                  onClick={() => openEdit(b)}
                                />
                                <OverflowMenuItem
                                  itemText={retired ? 'Un-retire' : 'Retire'}
                                  disabled={!canRetire}
                                  onClick={() =>
                                    setPending({ kind: retired ? 'unretire' : 'retire', block: b })
                                  }
                                />
                                <OverflowMenuItem
                                  itemText="Delete"
                                  isDelete
                                  hasDivider
                                  disabled={!canAdd}
                                  onClick={() => setPending({ kind: 'delete', block: b })}
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
            </div>
          ))}
      </AsyncBoundary>

      {/* Add / edit dialog. */}
      <Modal
        open={open}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading={editing ? `Edit TL block ${editing.tlBlockId}` : 'Add TL block'}
        onRequestClose={closeDialog}
        preventCloseOnClickOutside
      >
        <Stack gap={5}>
          <p className="detail-dialog__subtitle">
            All fields are required unless marked optional. Net is gross less eliminated; the
            licence&apos;s areas are recalculated on save.
          </p>
          <TextInput
            id="tlb-id"
            labelText="Block"
            value={form.blockId}
            maxLength={MAX_BLOCK_ID}
            helperText={editing ? undefined : `Letters and digits, up to ${MAX_BLOCK_ID}.`}
            invalid={!!errors.blockId}
            invalidText={errors.blockId}
            disabled={saving || !!editing}
            onChange={(e) => set('blockId', e.target.value.toUpperCase())}
          />
          <div className="detail-dialog__pair">
            <TextInput
              id="tlb-gross"
              labelText="Gross (ha)"
              inputMode="decimal"
              value={form.gross}
              helperText="One decimal place at most."
              invalid={!!errors.gross}
              invalidText={errors.gross}
              disabled={saving}
              onChange={(e) => set('gross', e.target.value)}
            />
            <TextInput
              id="tlb-elim"
              labelText="Eliminated (ha) (optional)"
              inputMode="decimal"
              value={form.elim}
              helperText={net ? `Net ${net} ha.` : 'Blank is 0.'}
              invalid={!!errors.elim}
              invalidText={errors.elim}
              disabled={saving}
              onChange={(e) => set('elim', e.target.value)}
            />
          </div>
        </Stack>
        <div className="detail-dialog__actions">
          <Button kind="tertiary" disabled={saving} onClick={closeDialog}>
            Cancel
          </Button>
          <Button kind="primary" disabled={saving} onClick={() => void submit()}>
            {saving ? 'Saving…' : editing ? 'Save changes' : 'Add TL block'}
          </Button>
        </div>
      </Modal>

      {/* Delete / retire / un-retire confirmation — the tabs' small dialog shape. */}
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
          <Button
            kind={pending?.kind === 'delete' ? 'danger' : 'primary'}
            disabled={acting}
            onClick={() => void confirm()}
          >
            {acting ? pendingText?.busy : pendingText?.action}
          </Button>
        </div>
      </Modal>
    </div>
  );
};

export default TlBlocksPanel;
