import { Add, PauseOutline, Tree } from '@carbon/icons-react';
import {
  Button,
  DatePicker,
  DatePickerInput,
  OverflowMenu,
  OverflowMenuItem,
  Select,
  SelectItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TextArea,
  TextInput,
} from '@carbon/react';
import { useCallback, useEffect, useMemo, useState, type FC } from 'react';
import { Link } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { Modal } from '@/components/Modal';
import { statusCodeVariant } from '@/components/StatusTag/statusCodes';
import StatusTag from '@/components/StatusTag/StatusTag';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import type { CodeOption } from '@/services/codeLists';
import {
  addTenureCutBlock,
  deleteTenureCutBlock,
  getFireHarvestingReasons,
  getTenureCutBlocks,
  type TenureCutBlock,
  type TenureCutBlockPermit,
} from '@/services/tenure_cutblocks';
import { formatDate } from '@/utils/formatDate';

import type { TenurePanelProps } from './panelProps';

const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;
const date = (v: string | null | undefined) => dash(formatDate(v));

/** Legacy's Harvest Start and Complete, in one cell. */
const harvest = (b: TenureCutBlock) =>
  b.startDate || b.endDate ? `${date(b.startDate)} – ${date(b.endDate)}` : '—';

/** yyyy-mm-dd in local time. */
const toIsoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

/** Legacy FTA904's limits; the backend enforces them too. */
const MAX_BLOCK_ID = 10;
const MAX_DESCRIPTION = 120;
const MAX_COMMENT = 200;
const MAX_AREA = 9999999.9999;
const AREA_PATTERN = /^\d+(\.\d+)?$/;

/** The cut block detail page, keyed as it looks a block up (block id + its file and CP). */
const blockLink = (b: TenureCutBlock) => {
  const qs = new URLSearchParams();
  if (b.blockForestFileId) qs.set('forestFileId', b.blockForestFileId);
  if (b.blockCuttingPermitId) qs.set('cuttingPermitId', b.blockCuttingPermitId);
  const q = qs.toString();
  return `/cut-block/${encodeURIComponent(b.cutBlockId)}${q ? `?${q}` : ''}`;
};

const permitLabel = (p: TenureCutBlockPermit) =>
  `${p.cuttingPermitId ?? 'Single mark'}${p.timberMark ? ` — ${p.timberMark}` : ''}${
    p.salvageTypeCode ? ` (salvage ${p.salvageTypeCode})` : ''
  }`;

interface Form {
  hvaSkey: string;
  cutBlockId: string;
  statusDate: string;
  plannedStart: string;
  gross: string;
  net: string;
  spExempt: string;
  waste: string;
  underPartition: string;
  fireCode: string;
  reportedFire: string;
  description: string;
}

type Errors = Partial<Record<keyof Form, string>>;

const emptyForm = (hvaSkey: string): Form => ({
  hvaSkey,
  cutBlockId: '',
  statusDate: toIsoDate(new Date()),
  plannedStart: '',
  gross: '',
  net: '',
  // FTA904's selects open on their first option (Yes) and GET_DEFAULTS' waste default (Y).
  spExempt: 'Y',
  waste: 'Y',
  underPartition: '',
  fireCode: '',
  reportedFire: '',
  description: '',
});

/** FTA904's add-mode checks, as far as the browser can make them. */
const validate = (f: Form, permit: TenureCutBlockPermit | undefined, fileType: string | null) => {
  const e: Errors = {};
  if (!f.hvaSkey) e.hvaSkey = 'CP is required when adding a cut block.';
  if (!f.cutBlockId.trim()) e.cutBlockId = 'Cut Block is mandatory.';
  const area = (key: 'gross' | 'net', label: string) => {
    const v = f[key].trim();
    if (!v) e[key] = `${label} is mandatory.`;
    else if (!AREA_PATTERN.test(v)) e[key] = `${label} must be numeric.`;
    else if (Number(v) > MAX_AREA) e[key] = `${label} field must be between 0 and 9999999.9999.`;
  };
  area('gross', 'Planned Gross Area (ha)');
  area('net', 'Planned Net Area (ha)');
  const gross = Number(f.gross);
  const net = Number(f.net);
  const salvage = permit?.salvageTypeCode;
  if (!e.gross && !e.net) {
    if (salvage === 'SSS' || fileType === 'B07') {
      if (gross >= 1)
        e.gross = 'For salvage blocks the planned gross area must be less than one hectare.';
      if (net >= 1)
        e.net = 'For salvage blocks the planned net area must be less than one hectare.';
    } else if (salvage === 'BBR') {
      if (gross >= 1)
        e.gross = 'For salvage blocks the planned gross area cannot be greater than 1 hectare.';
      if (net > 1)
        e.net = 'For salvage blocks the planned net area cannot be greater than 1 hectares.';
    }
    if (!e.net && gross < net)
      e.net = 'Planned net area must be less than or equal to planned gross area.';
  }
  return e;
};

/**
 * The Cut block tab of the tenure detail — legacy FTA903 (Cut Block List): the file's cut
 * blocks across all its CPs (filterable by CP, as legacy's tombstone CP filtered them), each
 * linking to the cut block detail, the blocks' suspensions, and legacy's two actions:
 *
 * - Add — FTA903's Add New opened FTA904 in add mode: a block in status PP on a permit
 *   legacy allows adding to (a salvage CP, or a B04/B07 of purpose SS), after FTA904's checks;
 * - Delete — per row, with legacy's mandatory comment (FTA231's confirm), through legacy's
 *   own FTA_DELETE_FF_CP_CB, which refuses blocks referenced by openings, CIMS/ERA or SPAR.
 *
 * Results, Map View and Suspend Blocks were links into other systems/screens and are not here.
 */
const CutBlocksPanel: FC<TenurePanelProps> = ({ tenure, canEdit }) => {
  const forestFileId = tenure.forestFileId;
  const { display } = useNotification();
  const fetcher = useCallback(() => getTenureCutBlocks(forestFileId), [forestFileId]);
  const { data, loading, error, reload } = useApiResource(fetcher, [forestFileId]);

  const [cpFilter, setCpFilter] = useState('');
  const rows = useMemo(() => data?.blocks ?? [], [data]);
  const cps = useMemo(() => [...new Set(rows.map((b) => b.cuttingPermitId ?? ''))].sort(), [rows]);
  const shown = cpFilter ? rows.filter((b) => (b.cuttingPermitId ?? '') === cpFilter) : rows;
  const suspensions = (data?.suspensions ?? []).filter(
    (s) => !cpFilter || (s.cuttingPermitId ?? '') === cpFilter,
  );

  const rules = data?.rules;
  const allowed = canEdit && !!rules?.add;
  // Role without the right: just disabled. Otherwise the business reason, beside the button.
  const disabledReason = canEdit && rules && !rules.add ? rules.addReason : null;
  const eligible = (data?.permits ?? []).filter((p) => p.eligible);

  // ── Add dialog ──
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState<Errors>({});
  const [form, setForm] = useState<Form>(emptyForm(''));
  const [fireCodes, setFireCodes] = useState<CodeOption[] | null>(null);

  useEffect(() => {
    if (!open || fireCodes) return;
    let cancelled = false;
    getFireHarvestingReasons()
      .then((codes) => {
        if (!cancelled) setFireCodes(codes);
      })
      .catch(() => {
        if (!cancelled) setFireCodes([]);
      });
    return () => {
      cancelled = true;
    };
  }, [open, fireCodes]);

  const set = <K extends keyof Form>(key: K, value: Form[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    setErrors((prev) => {
      if (!prev[key]) return prev;
      const next = { ...prev };
      delete next[key];
      return next;
    });
  };

  const openDialog = () => {
    // The CP filter, when it names an eligible permit, preselects it — legacy's Add New took
    // the tombstone CP.
    const fromFilter = eligible.find((p) => (p.cuttingPermitId ?? '') === cpFilter);
    const only = eligible.length === 1 ? eligible[0] : undefined;
    const pick = fromFilter ?? only;
    setForm(emptyForm(pick ? String(pick.hvaSkey) : ''));
    setErrors({});
    setOpen(true);
  };

  const chosen = eligible.find((p) => String(p.hvaSkey) === form.hvaSkey);

  const submit = async () => {
    const found = validate(form, chosen, tenure.fileTypeCode);
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    setSaving(true);
    try {
      const created = await addTenureCutBlock(forestFileId, {
        hvaSkey: Number(form.hvaSkey),
        cutBlockId: form.cutBlockId.trim().toUpperCase(),
        blockStatusDate: form.statusDate || null,
        description: form.description.trim() || null,
        plannedGrossArea: Number(form.gross),
        plannedNetArea: Number(form.net),
        plannedStartDate: form.plannedStart || null,
        spExempt: form.spExempt === 'N' ? 'N' : 'Y',
        wasteAssessmentRequired: form.waste === 'N' ? 'N' : 'Y',
        underPartitionOrder:
          form.underPartition === 'Y' || form.underPartition === 'N' ? form.underPartition : null,
        fireHarvestingReasonCode: form.fireCode || null,
        reportedFireDate: form.reportedFire || null,
      });
      display({
        kind: 'success',
        title: `Cut block ${created.cutBlockId} added`,
        subtitle: `Status PP${created.timberMark ? `, timber mark ${created.timberMark}` : ''}.`,
        timeout: 7000,
      });
      setOpen(false);
      reload();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not add the cut block',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  // ── Delete dialog ──
  const [deleting, setDeleting] = useState<TenureCutBlock | null>(null);
  const [comment, setComment] = useState('');
  const [commentError, setCommentError] = useState<string | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);

  const askDelete = (b: TenureCutBlock) => {
    setDeleting(b);
    setComment('');
    setCommentError(null);
  };

  const confirmDelete = async () => {
    if (!deleting) return;
    const text = comment.trim();
    if (!text) {
      setCommentError('Deletion comment is mandatory.');
      return;
    }
    setDeleteBusy(true);
    try {
      await deleteTenureCutBlock(forestFileId, deleting.cbSkey, {
        revisionCount: deleting.revisionCount,
        comment: text,
      });
      display({
        kind: 'success',
        title: `Cut block ${deleting.cutBlockId} deleted`,
        subtitle:
          'Delete successful. Please regenerate the exhibit A using TUT and transmit a copy to the licensee.',
        timeout: 9000,
      });
      setDeleting(null);
      reload();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not delete the cut block',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setDeleteBusy(false);
    }
  };

  const addButton = (primary: boolean) => (
    <Button
      kind={primary ? 'primary' : 'tertiary'}
      size={primary ? undefined : 'sm'}
      renderIcon={Add}
      disabled={!allowed}
      onClick={openDialog}
    >
      Add cut block
    </Button>
  );

  const dateInput = (key: 'statusDate' | 'plannedStart' | 'reportedFire', label: string) => (
    <DatePicker
      datePickerType="single"
      dateFormat="Y-m-d"
      className="detail-dialog__date"
      value={form[key]}
      invalid={!!errors[key]}
      onChange={(dates: Date[]) => set(key, dates[0] ? toIsoDate(dates[0]) : '')}
    >
      <DatePickerInput
        id={`cb-${key}`}
        labelText={label}
        placeholder="yyyy-mm-dd"
        invalidText={errors[key]}
        disabled={saving}
        onChange={(e) => {
          if (e.target.value.trim() === '') set(key, '');
        }}
      />
    </DatePicker>
  );

  const yesNo = (blank?: string) => (
    <>
      {blank !== undefined && <SelectItem value="" text={blank} />}
      <SelectItem value="Y" text="Yes" />
      <SelectItem value="N" text="No" />
    </>
  );

  return (
    <div className="fsp-info__tab-panel">
      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Loading cut blocks…"
      >
        {data &&
          rules &&
          (!rules.listable ? (
            <EmptyState
              icon={<Tree size={48} />}
              title="No cut blocks for this file type"
              body={rules.addReason}
            />
          ) : rows.length === 0 ? (
            <EmptyState
              icon={<Tree size={48} />}
              title="No cut blocks for this tenure"
              body="Add a cut block to one of the tenure's salvage cutting permits."
              action={
                <div className="detail-tab__empty-action">
                  {addButton(true)}
                  {disabledReason && <p className="detail-tab__reason">{disabledReason}</p>}
                </div>
              }
            />
          ) : (
            <>
              <div>
                <header className="detail-tab__actions">
                  {cps.length > 1 && (
                    <Select
                      id="cb-cp-filter"
                      labelText="Cutting permit"
                      hideLabel
                      size="sm"
                      inline
                      value={cpFilter}
                      onChange={(e) => setCpFilter(e.target.value)}
                    >
                      <SelectItem value="" text="All cutting permits" />
                      {cps.map((cp) => (
                        <SelectItem key={cp || 'none'} value={cp} text={cp || 'No CP'} />
                      ))}
                    </Select>
                  )}
                  {disabledReason && <p className="detail-tab__reason">{disabledReason}</p>}
                  {addButton(false)}
                </header>
                <div className="bordered-table">
                  <TableContainer>
                    <Table size="md" useZebraStyles>
                      <TableHead>
                        <TableRow>
                          <TableHeader>CP / HVA</TableHeader>
                          <TableHeader>Cut Block</TableHeader>
                          <TableHeader>Timber Mark</TableHeader>
                          <TableHeader>Salvage</TableHeader>
                          <TableHeader>Mark Status</TableHeader>
                          <TableHeader>Block Status</TableHeader>
                          <TableHeader>Harvest Start – Complete</TableHeader>
                          <TableHeader>Planned Gross (ha)</TableHeader>
                          <TableHeader>Planned Net (ha)</TableHeader>
                          <TableHeader>Actual Gross (ha)</TableHeader>
                          <TableHeader>Authorized File / CP</TableHeader>
                          <TableHeader aria-label="Actions" />
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {shown.map((b) => (
                          <TableRow key={b.cbSkey}>
                            <TableCell>{dash(b.cuttingPermitId)}</TableCell>
                            <TableCell>
                              <Link to={blockLink(b)}>{b.cutBlockId}</Link>
                            </TableCell>
                            <TableCell>{dash(b.timberMark)}</TableCell>
                            <TableCell>{dash(b.salvageTypeCode)}</TableCell>
                            <TableCell>
                              {b.markStatusCode ? (
                                <StatusTag
                                  status={b.markStatusCode}
                                  variant={statusCodeVariant(b.markStatusCode)}
                                />
                              ) : (
                                '—'
                              )}
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
                            <TableCell>{harvest(b)}</TableCell>
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
                            <TableCell>
                              <OverflowMenu
                                size="sm"
                                flipped
                                iconDescription={`Actions for cut block ${b.cutBlockId}`}
                              >
                                <OverflowMenuItem
                                  itemText="Delete"
                                  isDelete
                                  disabled={!canEdit || !b.canDelete}
                                  title={canEdit && !b.canDelete ? (b.deleteReason ?? '') : ''}
                                  onClick={() => askDelete(b)}
                                />
                              </OverflowMenu>
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </div>
              </div>

              {suspensions.length > 0 && (
                <section className="fsp-info__tile">
                  <header className="fsp-info__tile-header">
                    <h2 className="fsp-info__section-title">
                      <PauseOutline size={20} />
                      <span>Cut block suspensions</span>
                    </h2>
                    <span className="detail-tab__reason">
                      {suspensions.length} {suspensions.length === 1 ? 'row' : 'rows'}
                    </span>
                  </header>
                  <div className="bordered-table">
                    <TableContainer>
                      <Table size="md" useZebraStyles>
                        <TableHead>
                          <TableRow>
                            <TableHeader>CP</TableHeader>
                            <TableHeader>Cut Block</TableHeader>
                            <TableHeader>Suspension Order No</TableHeader>
                            <TableHeader>Under Partition</TableHeader>
                            <TableHeader>Start</TableHeader>
                            <TableHeader>End</TableHeader>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {suspensions.map((s, i) => (
                            <TableRow key={`${s.cuttingPermitId}-${s.cutBlockId}-${i}`}>
                              <TableCell>{dash(s.cuttingPermitId)}</TableCell>
                              <TableCell>{s.cutBlockId}</TableCell>
                              <TableCell>{dash(s.suspensionOrderNo)}</TableCell>
                              <TableCell>{dash(s.underPartition)}</TableCell>
                              <TableCell>{date(s.startDate)}</TableCell>
                              <TableCell>{date(s.endDate)}</TableCell>
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

      {/* Add dialog — FTA904 add mode. */}
      <Modal
        open={open}
        passiveModal
        size="md"
        className="detail-dialog"
        modalHeading="Add cut block"
        onRequestClose={() => !saving && setOpen(false)}
        preventCloseOnClickOutside
      >
        <Stack gap={5}>
          <p className="detail-dialog__subtitle">
            All fields are required unless marked optional. The block starts in status PP under the
            permit&apos;s timber mark.
          </p>
          <div className="detail-dialog__pair">
            <Select
              id="cb-permit"
              labelText="Cutting permit"
              value={form.hvaSkey}
              invalid={!!errors.hvaSkey}
              invalidText={errors.hvaSkey}
              disabled={saving}
              onChange={(e) => set('hvaSkey', e.target.value)}
            >
              <SelectItem value="" text="— Select cutting permit —" />
              {eligible.map((p) => (
                <SelectItem key={p.hvaSkey} value={String(p.hvaSkey)} text={permitLabel(p)} />
              ))}
            </Select>
            <TextInput
              id="cb-id"
              labelText="Cut Block"
              value={form.cutBlockId}
              maxLength={MAX_BLOCK_ID}
              helperText={`Up to ${MAX_BLOCK_ID} characters.`}
              invalid={!!errors.cutBlockId}
              invalidText={errors.cutBlockId}
              disabled={saving}
              onChange={(e) => set('cutBlockId', e.target.value.toUpperCase())}
            />
          </div>
          <div className="detail-dialog__pair">
            {dateInput('statusDate', 'Status as of (optional)')}
            {dateInput('plannedStart', 'Planned start date (optional)')}
          </div>
          <div className="detail-dialog__pair">
            <TextInput
              id="cb-gross"
              labelText="Planned Gross Area (ha)"
              inputMode="decimal"
              value={form.gross}
              invalid={!!errors.gross}
              invalidText={errors.gross}
              disabled={saving}
              onChange={(e) => set('gross', e.target.value)}
            />
            <TextInput
              id="cb-net"
              labelText="Planned Net Area (ha)"
              inputMode="decimal"
              value={form.net}
              invalid={!!errors.net}
              invalidText={errors.net}
              disabled={saving}
              onChange={(e) => set('net', e.target.value)}
            />
          </div>
          <div className="detail-dialog__pair">
            <Select
              id="cb-sp"
              labelText="SP Exempt"
              value={form.spExempt}
              disabled={saving}
              onChange={(e) => set('spExempt', e.target.value)}
            >
              {yesNo()}
            </Select>
            <Select
              id="cb-waste"
              labelText="Waste Assessment Required"
              value={form.waste}
              disabled={saving}
              onChange={(e) => set('waste', e.target.value)}
            >
              {yesNo()}
            </Select>
          </div>
          <div className="detail-dialog__pair">
            <Select
              id="cb-partition"
              labelText="Under Partition Order (optional)"
              value={form.underPartition}
              disabled={saving}
              onChange={(e) => set('underPartition', e.target.value)}
            >
              {yesNo('— None —')}
            </Select>
            <Select
              id="cb-fire"
              labelText="Fire Harvesting Reason (optional)"
              value={form.fireCode}
              disabled={saving || (open && !fireCodes)}
              onChange={(e) => set('fireCode', e.target.value)}
            >
              <SelectItem value="" text={open && !fireCodes ? 'Loading…' : '— None —'} />
              {(fireCodes ?? []).map((c) => (
                <SelectItem key={c.code} value={c.code} text={c.description || c.code} />
              ))}
            </Select>
          </div>
          <div className="detail-dialog__pair">
            {dateInput('reportedFire', 'Reported fire date (optional)')}
          </div>
          <TextInput
            id="cb-description"
            labelText="Block Description (optional)"
            value={form.description}
            maxLength={MAX_DESCRIPTION}
            disabled={saving}
            onChange={(e) => set('description', e.target.value)}
          />
        </Stack>
        <div className="detail-dialog__actions">
          <Button kind="tertiary" disabled={saving} onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button kind="primary" disabled={saving} onClick={() => void submit()}>
            {saving ? 'Adding…' : 'Add cut block'}
          </Button>
        </div>
      </Modal>

      {/* Delete confirm — legacy FTA231's "reason for cut block deletion". */}
      <Modal
        open={!!deleting}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading={`Delete cut block ${deleting?.cutBlockId ?? ''}`}
        onRequestClose={() => !deleteBusy && setDeleting(null)}
        preventCloseOnClickOutside
      >
        <Stack gap={5}>
          <p className="detail-dialog__subtitle">
            The block{deleting?.cuttingPermitId ? ` on CP ${deleting.cuttingPermitId}` : ''} and its
            geometry are removed. Please enter the reason for the deletion.
          </p>
          <TextArea
            id="cb-delete-comment"
            labelText="Deletion comment"
            rows={3}
            value={comment}
            enableCounter
            maxCount={MAX_COMMENT}
            invalid={!!commentError}
            invalidText={commentError ?? undefined}
            disabled={deleteBusy}
            onChange={(e) => {
              setComment(e.target.value);
              setCommentError(null);
            }}
          />
        </Stack>
        <div className="detail-dialog__actions">
          <Button kind="tertiary" disabled={deleteBusy} onClick={() => setDeleting(null)}>
            Cancel
          </Button>
          <Button kind="danger" disabled={deleteBusy} onClick={() => void confirmDelete()}>
            {deleteBusy ? 'Deleting…' : 'Delete cut block'}
          </Button>
        </div>
      </Modal>
    </div>
  );
};

export default CutBlocksPanel;
