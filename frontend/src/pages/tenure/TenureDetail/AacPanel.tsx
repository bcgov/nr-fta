import { Add, Area, ChartColumn, Edit } from '@carbon/icons-react';
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
  TextInput,
} from '@carbon/react';
import { Fragment, useCallback, useEffect, useState, type FC, type ReactNode } from 'react';

import AsyncBoundary from '@/components/AsyncBoundary';
import DetailTile, { type DetailField } from '@/components/DetailTile';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { Modal } from '@/components/Modal';
import UserName from '@/components/UserName';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import type { CodeOption } from '@/services/codeLists';
import {
  addAac,
  deleteAac,
  getAacAdjustmentReasons,
  getAacAreaTypes,
  getAacCutTypes,
  getAacHarvestUnits,
  getTenureAac,
  saveAacAreas,
  updateAac,
  type AacData,
  type AacRow,
} from '@/services/tenure_aac';
import { formatDate } from '@/utils/formatDate';
import { parseTypedDate, TYPED_DATE_PATTERN } from '@/utils/typedDate';

import type { TenurePanelProps } from './panelProps';

const nf = new Intl.NumberFormat('en-CA', { maximumFractionDigits: 4 });

const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;
const num = (v: number | null | undefined) => (v === null || v === undefined ? '—' : nf.format(v));
const date = (v: string | null | undefined) => dash(formatDate(v));
const label = (code: string | null, desc: string | null) =>
  code ? (desc ? `${code} - ${desc}` : code) : '—';
const shareLabel = (v: string | null) => (v === 'Y' ? 'Yes' : v === 'N' ? 'No' : '—');

/** yyyy-mm-dd in local time — what the backend's LocalDate expects. */
const toIsoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

/** Legacy FTA930's limits; the backend enforces them too. */
const FOUR_DP = /^\d+(\.\d{1,4})?$/;
const MAX_AMOUNT = 9999999.9999;
const MAX_FRA = 999999999.9999;
const MAX_COMMENT = 100;

/** A period of the history: its rows, most recent period first (the GET's order). */
interface Period {
  periodId: number;
  effectiveDate: string;
  unit: string;
  rows: AacRow[];
  total: number;
}

const periodsOf = (rows: AacRow[]): Period[] => {
  const out: Period[] = [];
  for (const r of rows) {
    const last = out[out.length - 1];
    if (last && last.periodId === r.periodId) {
      last.rows.push(r);
      last.total += r.amount;
    } else {
      out.push({
        periodId: r.periodId,
        effectiveDate: r.effectiveDate,
        unit: r.unitOfMeasureCode,
        rows: [r],
        total: r.amount,
      });
    }
  }
  return out;
};

/** Checks a decimal field: blank (when allowed), a number ≥ 0 up to `max`, ≤ 4 places. */
const decimalError = (value: string, max: number, required: boolean): string | undefined => {
  const v = value.trim();
  if (!v) return required ? 'Required.' : undefined;
  if (!FOUR_DP.test(v)) return 'Enter a number with up to 4 decimal places.';
  if (Number(v) > max) return `Cannot exceed ${nf.format(max)}.`;
  return undefined;
};

// ─── AAC row dialog ───────────────────────────────────────────────────────

interface RowForm {
  effectiveDate: string;
  unit: string;
  areaType: string;
  cutType: string;
  amount: string;
  reason: string;
  comment: string;
  shareable: string;
  fra: string;
}

type RowErrors = Partial<Record<keyof RowForm, string>>;

const EMPTY_ROW: RowForm = {
  effectiveDate: '',
  unit: '',
  areaType: '',
  cutType: '',
  amount: '',
  reason: '',
  comment: '',
  shareable: '',
  fra: '',
};

const formOf = (r: AacRow): RowForm => ({
  effectiveDate: r.effectiveDate,
  unit: r.unitOfMeasureCode,
  areaType: r.areaTypeCode,
  cutType: r.cutTypeCode,
  amount: String(r.amount),
  reason: r.reasonCode ?? '',
  comment: r.comment ?? '',
  shareable: r.revenueShareable === 'Y' || r.revenueShareable === 'N' ? r.revenueShareable : '',
  fra: r.fra2003Volume === null ? '' : String(r.fra2003Volume),
});

/** Fta930AacForm's "SaveAAC" checks that need no database; the backend repeats them all. */
const validateRow = (f: RowForm, data: AacData, editing: AacRow | null): RowErrors => {
  const e: RowErrors = {};
  if (!f.effectiveDate) e.effectiveDate = 'Effective Date is mandatory.';
  else if (data.awardDate && f.effectiveDate < data.awardDate)
    e.effectiveDate = `Must be on or after the tenure's award date (${formatDate(data.awardDate)}).`;
  else if (data.expiryDate && f.effectiveDate > data.expiryDate)
    e.effectiveDate = 'Effective Date cannot be after the tenure Expiry Date.';
  if (!f.unit) e.unit = 'Unit of Measure is mandatory.';
  else if (!editing && f.effectiveDate) {
    const period = data.rows.find((r) => r.effectiveDate === f.effectiveDate);
    if (period && period.unitOfMeasureCode !== f.unit)
      e.unit = `The ${formatDate(period.effectiveDate)} period is in ${period.unitOfMeasureCode}.`;
  }
  if (!f.areaType) e.areaType = 'Area Type is mandatory.';
  else if (f.areaType === 'A' && !data.rules.scheduleAAllowed)
    e.areaType = 'Private/Schedule A is only permitted for A02, A04, A44, A28 and A29.';
  if (!f.cutType) e.cutType = 'Cut type is mandatory.';
  else if (
    !editing &&
    data.rows.some(
      (r) =>
        r.effectiveDate === f.effectiveDate &&
        r.areaTypeCode === f.areaType &&
        r.cutTypeCode === f.cutType,
    )
  )
    e.cutType = 'This date already has this area type and cut type.';
  const amount = decimalError(f.amount, MAX_AMOUNT, true);
  if (amount) e.amount = amount === 'Required.' ? 'Amount is mandatory.' : amount;
  if (!f.reason) e.reason = 'Reason is mandatory.';
  if (f.reason === 'OTH' && !f.comment.trim())
    e.comment = 'Comment is mandatory when Reason is OTH-Other.';
  if (f.comment.trim().length > MAX_COMMENT) e.comment = `At most ${MAX_COMMENT} characters.`;
  const fra = decimalError(f.fra, MAX_FRA, f.shareable === 'Y');
  if (fra) e.fra = fra === 'Required.' ? 'Required when Revenue Share is Yes.' : fra;
  else if (f.shareable !== 'Y' && f.fra.trim()) e.fra = 'Leave blank unless Revenue Share is Yes.';
  else if (f.unit === 'M3' && f.fra.trim() && !e.amount && Number(f.fra) > Number(f.amount))
    e.fra = 'Cannot be more than the Amount when the unit is M3.';
  return e;
};

interface Lists {
  units: CodeOption[];
  areaTypes: CodeOption[];
  cutTypes: CodeOption[];
  reasons: CodeOption[];
}

/** The list, plus the stored value when the list no longer carries it (an expired code). */
const withCurrent = (options: CodeOption[], code: string, desc?: string | null) =>
  code && !options.some((o) => o.code === code)
    ? [{ code, description: desc ? `${code} - ${desc}` : code }, ...options]
    : options;

/**
 * The AAC tab of the tenure detail — legacy FTA930 (Allowable Annual Cut): the
 * current AAC and the Schedule A / B areas side by side, the areas edited in
 * place, then the AAC history, a row per allocation amount grouped by period,
 * with Add, and Edit / Delete on each row.
 *
 * A new row joins the period already starting on its date (in that period's
 * unit), else starts a new period; deleting a period's last row deletes the
 * period. The backend applies FTA930's gates and checks again.
 */
const AacPanel: FC<TenurePanelProps> = ({ tenure, canEdit }) => {
  const forestFileId = tenure.forestFileId;
  const { display } = useNotification();
  const fetcher = useCallback(() => getTenureAac(forestFileId), [forestFileId]);
  const { data, loading, error, reload } = useApiResource(fetcher, [forestFileId]);
  const rules = data?.rules;

  // ─── Areas, edited in place ───────────────────────────────────────────────
  const [editingAreas, setEditingAreas] = useState(false);
  const [areaForm, setAreaForm] = useState({ a: '', b: '' });
  const [areaErrors, setAreaErrors] = useState<{ a?: string; b?: string }>({});
  const [savingAreas, setSavingAreas] = useState(false);

  const openAreas = () => {
    if (!data) return;
    setAreaForm({
      a: data.scheduleAArea === null ? '' : String(data.scheduleAArea),
      b: data.scheduleBArea === null ? '' : String(data.scheduleBArea),
    });
    setAreaErrors({});
    setEditingAreas(true);
  };

  const saveAreas = async () => {
    if (!data || data.areaRevisionCount === null) return;
    const found = {
      a: decimalError(areaForm.a, 9999999.9999, false),
      b: decimalError(areaForm.b, 9999999.9999, false),
    };
    if (found.a || found.b) {
      setAreaErrors(found);
      return;
    }
    setSavingAreas(true);
    try {
      await saveAacAreas(forestFileId, {
        scheduleAArea: areaForm.a.trim() ? Number(areaForm.a) : null,
        scheduleBArea: areaForm.b.trim() ? Number(areaForm.b) : null,
        revisionCount: data.areaRevisionCount,
      });
      setEditingAreas(false);
      display({ kind: 'success', title: 'Areas saved', timeout: 5000 });
      reload();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not save the areas',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSavingAreas(false);
    }
  };

  // ─── AAC row dialog ───────────────────────────────────────────────────────
  const [dialog, setDialog] = useState<{ editing: AacRow | null } | null>(null);
  const [form, setForm] = useState<RowForm>(EMPTY_ROW);
  const [errors, setErrors] = useState<RowErrors>({});
  const [saving, setSaving] = useState(false);
  const [lists, setLists] = useState<Lists | null>(null);
  const [deleting, setDeleting] = useState<AacRow | null>(null);
  const [removing, setRemoving] = useState(false);

  useEffect(() => {
    if (!dialog || lists) return;
    let cancelled = false;
    Promise.all([
      getAacHarvestUnits(),
      getAacAreaTypes(),
      getAacCutTypes(),
      getAacAdjustmentReasons(),
    ])
      .then(([units, areaTypes, cutTypes, reasons]) => {
        if (!cancelled) setLists({ units, areaTypes, cutTypes, reasons });
      })
      .catch(() => {
        if (!cancelled) {
          display({
            kind: 'error',
            title: 'Could not load the AAC lists',
            subtitle: 'Close the dialog and try again.',
            timeout: 6000,
          });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [dialog, lists, display]);

  const set = <K extends keyof RowForm>(key: K, value: RowForm[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    setErrors((prev) => {
      if (!prev[key]) return prev;
      const next = { ...prev };
      delete next[key];
      return next;
    });
  };

  const openDialog = (editing: AacRow | null) => {
    setForm(editing ? formOf(editing) : EMPTY_ROW);
    setErrors({});
    setDialog({ editing });
  };

  const closeDialog = () => {
    if (!saving) setDialog(null);
  };

  const submit = async () => {
    if (!data || !dialog) return;
    const found = validateRow(form, data, dialog.editing);
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    const body = {
      effectiveDate: form.effectiveDate,
      unitOfMeasureCode: form.unit,
      areaTypeCode: form.areaType,
      cutTypeCode: form.cutType,
      amount: Number(form.amount),
      reasonCode: form.reason,
      comment: form.comment.trim() || null,
      revenueShareable: form.shareable || null,
      fra2003Volume: form.fra.trim() ? Number(form.fra) : null,
    };
    setSaving(true);
    try {
      if (dialog.editing) {
        await updateAac(forestFileId, dialog.editing.amountId, {
          ...body,
          periodRevisionCount: dialog.editing.periodRevisionCount,
          amountRevisionCount: dialog.editing.amountRevisionCount,
        });
      } else {
        await addAac(forestFileId, body);
      }
      display({
        kind: 'success',
        title: dialog.editing ? 'AAC updated' : 'AAC added',
        timeout: 5000,
      });
      setDialog(null);
      reload();
    } catch (err) {
      display({
        kind: 'error',
        title: dialog.editing ? 'Could not update the AAC' : 'Could not add the AAC',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    if (!deleting) return;
    setRemoving(true);
    try {
      await deleteAac(forestFileId, deleting);
      display({ kind: 'success', title: 'AAC deleted', timeout: 5000 });
      setDeleting(null);
      reload();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not delete the AAC',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setRemoving(false);
    }
  };

  // ─── View ─────────────────────────────────────────────────────────────────

  const periods = data ? periodsOf(data.rows) : [];
  const current = periods[0];
  const allowed = canEdit && !!rules?.edit && !editingAreas;

  // The current period's amounts by area type, as legacy's Current AAC block sums them.
  const currentFields: DetailField[] = current
    ? [
        { label: 'Effective Date', value: date(current.effectiveDate) },
        { label: 'Unit of Measure', value: current.unit },
        ...Object.values(
          current.rows.reduce<Record<string, { title: string; rows: AacRow[] }>>((acc, r) => {
            const entry = (acc[r.areaTypeCode] ??= {
              title: r.areaTypeDesc ?? r.areaTypeCode,
              rows: [],
            });
            entry.rows.push(r);
            return acc;
          }, {}),
        ).map(({ title, rows }) => ({
          label: title,
          value: (
            <>
              {nf.format(rows.reduce((s, r) => s + r.amount, 0))}
              {rows.length > 1 &&
                rows.map((r) => (
                  <span key={r.amountId}>
                    <br />
                    {r.cutTypeDesc ?? r.cutTypeCode}: {nf.format(r.amount)}
                  </span>
                ))}
            </>
          ),
        })),
        {
          label: `Total AAC (${current.unit})`,
          value: <strong>{nf.format(current.total)}</strong>,
        },
      ]
    : [{ label: 'Current AAC', value: 'None recorded' }];

  const areaInput = (key: 'a' | 'b', text: string) => (
    <div className="detail-edit__input detail-edit__input--sm">
      <TextInput
        id={`aac-area-${key}`}
        labelText={text}
        hideLabel
        inputMode="decimal"
        value={areaForm[key]}
        invalid={!!areaErrors[key]}
        invalidText={areaErrors[key]}
        disabled={savingAreas}
        onChange={(e) => {
          const value = e.target.value;
          setAreaForm((prev) => ({ ...prev, [key]: value }));
          setAreaErrors((prev) => ({ ...prev, [key]: undefined }));
        }}
      />
    </div>
  );

  const areaTotal = editingAreas
    ? (Number(areaForm.a) || 0) + (Number(areaForm.b) || 0)
    : (data?.scheduleAArea ?? 0) + (data?.scheduleBArea ?? 0);
  const areaFields: DetailField[] = [
    {
      label: editingAreas ? 'Private/Schedule A (optional)' : 'Private/Schedule A',
      value: editingAreas ? areaInput('a', 'Private/Schedule A') : num(data?.scheduleAArea),
    },
    {
      label: editingAreas ? 'Crown/Schedule B (optional)' : 'Crown/Schedule B',
      value: editingAreas ? areaInput('b', 'Crown/Schedule B') : num(data?.scheduleBArea),
    },
    { label: 'Total', value: nf.format(areaTotal) },
    { label: 'Management Unit', value: dash(tenure.managementUnit) },
  ];

  const addButton = (primary: boolean) => (
    <Button
      kind={primary ? 'primary' : 'tertiary'}
      size={primary ? undefined : 'sm'}
      renderIcon={Add}
      disabled={!allowed}
      onClick={() => openDialog(null)}
    >
      Add AAC
    </Button>
  );

  const listsLoading = !!dialog && !lists;
  const options = (items: CodeOption[]) =>
    items.map((o) => <SelectItem key={o.code} value={o.code} text={o.description || o.code} />);
  const editing = dialog?.editing ?? null;
  const areaTypeOptions = (lists?.areaTypes ?? []).filter(
    (o) => o.code !== 'A' || rules?.scheduleAAllowed || editing?.areaTypeCode === 'A',
  );

  const historyTable: ReactNode = (
    <div className="bordered-table">
      <TableContainer>
        <Table size="md" useZebraStyles>
          <TableHead>
            <TableRow>
              <TableHeader>Effective Date</TableHeader>
              <TableHeader>Unit</TableHeader>
              <TableHeader>Area Type</TableHeader>
              <TableHeader>Cut Type</TableHeader>
              <TableHeader>Amount</TableHeader>
              <TableHeader>Reason</TableHeader>
              <TableHeader>Revenue Share</TableHeader>
              <TableHeader>FRA (Bill 28, 2003) m³</TableHeader>
              <TableHeader>Comment</TableHeader>
              <TableHeader>Updated By</TableHeader>
              <TableHeader aria-label="Actions" />
            </TableRow>
          </TableHead>
          <TableBody>
            {periods.map((p) => (
              <Fragment key={p.periodId}>
                {p.rows.map((r, i) => (
                  <TableRow key={r.amountId}>
                    <TableCell>{i === 0 ? date(p.effectiveDate) : ''}</TableCell>
                    <TableCell>{i === 0 ? p.unit : ''}</TableCell>
                    <TableCell>{r.areaTypeDesc ?? r.areaTypeCode}</TableCell>
                    <TableCell>{r.cutTypeDesc ?? r.cutTypeCode}</TableCell>
                    <TableCell>{nf.format(r.amount)}</TableCell>
                    <TableCell>{label(r.reasonCode, r.reasonDesc)}</TableCell>
                    <TableCell>{shareLabel(r.revenueShareable)}</TableCell>
                    <TableCell>{num(r.fra2003Volume)}</TableCell>
                    <TableCell className="detail-tab__long-text">{dash(r.comment)}</TableCell>
                    <TableCell>
                      <UserName userId={r.updateUserid} />
                    </TableCell>
                    <TableCell>
                      <OverflowMenu
                        size="sm"
                        flipped
                        aria-label="Row actions"
                        iconDescription="Row actions"
                        disabled={!allowed}
                      >
                        <OverflowMenuItem itemText="Edit" onClick={() => openDialog(r)} />
                        <OverflowMenuItem
                          itemText="Delete"
                          isDelete
                          hasDivider
                          onClick={() => setDeleting(r)}
                        />
                      </OverflowMenu>
                    </TableCell>
                  </TableRow>
                ))}
                <TableRow>
                  <TableCell colSpan={4}>
                    <strong>Sub-total AAC</strong>
                  </TableCell>
                  <TableCell>
                    <strong>{nf.format(p.total)}</strong>
                  </TableCell>
                  <TableCell colSpan={6} />
                </TableRow>
              </Fragment>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </div>
  );

  return (
    <div className="fsp-info__tab-panel">
      <AsyncBoundary
        loading={loading && !data}
        error={data ? undefined : error}
        onRetry={reload}
        loadingText="Loading AAC…"
      >
        {data && (
          <>
            <div className="detail-edit__toolbar">
              {editingAreas ? (
                <p className="detail-edit__strap">
                  Areas are in hectares, with up to 4 decimal places.
                </p>
              ) : (
                <>
                  <Button
                    kind="tertiary"
                    size="sm"
                    renderIcon={Edit}
                    disabled={!canEdit || !data.rules.areas}
                    onClick={openAreas}
                  >
                    Edit areas
                  </Button>
                </>
              )}
            </div>

            <div className={editingAreas ? 'detail-edit' : undefined}>
              <div className="fsp-info__tile-row">
                <DetailTile title="Current AAC" icon={ChartColumn} fields={currentFields} />
                <DetailTile title="Area (ha)" icon={Area} fields={areaFields} />
              </div>
              {editingAreas && (
                <div className="detail-edit__actions">
                  <Button
                    kind="tertiary"
                    size="md"
                    disabled={savingAreas}
                    onClick={() => setEditingAreas(false)}
                  >
                    Cancel
                  </Button>
                  <Button
                    kind="primary"
                    size="md"
                    disabled={savingAreas}
                    onClick={() => void saveAreas()}
                  >
                    {savingAreas ? 'Saving…' : 'Save changes'}
                  </Button>
                </div>
              )}
            </div>

            {data.rows.length === 0 ? (
              <EmptyState
                icon={<ChartColumn size={48} />}
                title="No AAC history for this tenure"
                body="Add the tenure's allowable annual cut, by area type and cut type."
                action={<div className="detail-tab__empty-action">{addButton(true)}</div>}
              />
            ) : (
              <div>
                <header className="detail-tab__actions">{addButton(false)}</header>
                {historyTable}
              </div>
            )}
          </>
        )}
      </AsyncBoundary>

      {/* Add / edit dialog — the tabs' dialog, a size up for its fields. */}
      <Modal
        open={!!dialog}
        passiveModal
        size="md"
        className="detail-dialog"
        modalHeading={editing ? 'Edit AAC' : 'Add AAC'}
        onRequestClose={closeDialog}
        preventCloseOnClickOutside
      >
        <Stack gap={5}>
          <p className="detail-dialog__subtitle">
            All fields are required unless marked optional.
            {editing &&
              data &&
              data.rows.filter((r) => r.periodId === editing.periodId).length > 1 &&
              ' The date and unit belong to the period, so changing them changes every row of it.'}
          </p>
          <div className="detail-dialog__pair">
            <DatePicker
              datePickerType="single"
              dateFormat="Y-m-d"
              className="detail-dialog__date"
              value={form.effectiveDate}
              invalid={!!errors.effectiveDate}
              onChange={(dates: Date[]) =>
                set('effectiveDate', dates[0] ? toIsoDate(dates[0]) : '')
              }
            >
              <DatePickerInput
                id="aac-effective-date"
                labelText="Effective Date"
                placeholder="yyyy-mm-dd"
                invalidText={errors.effectiveDate}
                disabled={saving}
                pattern={TYPED_DATE_PATTERN}
                onChange={(e) => {
                  const text = e.target.value;
                  if (text.trim() === '') set('effectiveDate', '');
                  else {
                    const typed = parseTypedDate(text);
                    if (typed) set('effectiveDate', typed);
                  }
                }}
              />
            </DatePicker>
            <Select
              id="aac-unit"
              labelText="Unit of Measure"
              value={form.unit}
              invalid={!!errors.unit}
              invalidText={errors.unit}
              disabled={saving || listsLoading}
              onChange={(e) => set('unit', e.target.value)}
            >
              <SelectItem value="" text={listsLoading ? 'Loading…' : '— Select unit —'} />
              {options(withCurrent(lists?.units ?? [], editing?.unitOfMeasureCode ?? ''))}
            </Select>
          </div>
          <div className="detail-dialog__pair">
            <Select
              id="aac-area-type"
              labelText="Area Type"
              value={form.areaType}
              invalid={!!errors.areaType}
              invalidText={errors.areaType}
              disabled={saving || listsLoading}
              onChange={(e) => set('areaType', e.target.value)}
            >
              <SelectItem value="" text={listsLoading ? 'Loading…' : '— Select area type —'} />
              {options(
                withCurrent(areaTypeOptions, editing?.areaTypeCode ?? '', editing?.areaTypeDesc),
              )}
            </Select>
            <Select
              id="aac-cut-type"
              labelText="Cut Type"
              value={form.cutType}
              invalid={!!errors.cutType}
              invalidText={errors.cutType}
              disabled={saving || listsLoading}
              onChange={(e) => set('cutType', e.target.value)}
            >
              <SelectItem value="" text={listsLoading ? 'Loading…' : '— Select cut type —'} />
              {options(
                withCurrent(
                  lists?.cutTypes ?? [],
                  editing?.cutTypeCode ?? '',
                  editing?.cutTypeDesc,
                ),
              )}
            </Select>
          </div>
          <div className="detail-dialog__pair">
            <TextInput
              id="aac-amount"
              labelText="Amount"
              inputMode="decimal"
              value={form.amount}
              invalid={!!errors.amount}
              invalidText={errors.amount}
              disabled={saving}
              onChange={(e) => set('amount', e.target.value)}
            />
            <Select
              id="aac-reason"
              labelText="Reason"
              value={form.reason}
              invalid={!!errors.reason}
              invalidText={errors.reason}
              disabled={saving || listsLoading}
              onChange={(e) => set('reason', e.target.value)}
            >
              <SelectItem value="" text={listsLoading ? 'Loading…' : '— Select reason —'} />
              {options(
                withCurrent(lists?.reasons ?? [], editing?.reasonCode ?? '', editing?.reasonDesc),
              )}
            </Select>
          </div>
          <div className="detail-dialog__pair">
            <Select
              id="aac-shareable"
              labelText="Revenue Share (optional)"
              value={form.shareable}
              disabled={saving}
              onChange={(e) => set('shareable', e.target.value)}
            >
              <SelectItem value="" text="— Unknown —" />
              <SelectItem value="Y" text="Yes" />
              <SelectItem value="N" text="No" />
            </Select>
            <TextInput
              id="aac-fra"
              labelText={
                form.shareable === 'Y'
                  ? 'FRA (Bill 28, 2003) m³'
                  : 'FRA (Bill 28, 2003) m³ (optional)'
              }
              helperText={form.shareable === 'Y' ? undefined : 'Only when Revenue Share is Yes.'}
              inputMode="decimal"
              value={form.fra}
              invalid={!!errors.fra}
              invalidText={errors.fra}
              disabled={saving}
              onChange={(e) => set('fra', e.target.value)}
            />
          </div>
          <TextInput
            id="aac-comment"
            labelText={form.reason === 'OTH' ? 'Comment' : 'Comment (optional)'}
            value={form.comment}
            maxLength={MAX_COMMENT}
            invalid={!!errors.comment}
            invalidText={errors.comment}
            disabled={saving}
            onChange={(e) => set('comment', e.target.value)}
          />
        </Stack>
        <div className="detail-dialog__actions">
          <Button kind="tertiary" disabled={saving} onClick={closeDialog}>
            Cancel
          </Button>
          <Button kind="primary" disabled={saving} onClick={() => void submit()}>
            {saving ? 'Saving…' : editing ? 'Save changes' : 'Add AAC'}
          </Button>
        </div>
      </Modal>

      {/* Delete confirmation — the tabs' small dialog. */}
      <Modal
        open={!!deleting}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading="Delete AAC"
        onRequestClose={() => {
          if (!removing) setDeleting(null);
        }}
        preventCloseOnClickOutside
      >
        {deleting && (
          <p className="detail-dialog__subtitle">
            {label(deleting.areaTypeCode, deleting.areaTypeDesc)},{' '}
            {label(deleting.cutTypeCode, deleting.cutTypeDesc)}: {nf.format(deleting.amount)}{' '}
            {deleting.unitOfMeasureCode} from {formatDate(deleting.effectiveDate)}.
            {data && data.rows.filter((r) => r.periodId === deleting.periodId).length === 1
              ? ' It is the last row of its period, so the period is deleted too.'
              : ''}
          </p>
        )}
        <div className="detail-dialog__actions">
          <Button kind="tertiary" disabled={removing} onClick={() => setDeleting(null)}>
            Cancel
          </Button>
          <Button kind="danger" disabled={removing} onClick={() => void remove()}>
            {removing ? 'Deleting…' : 'Delete'}
          </Button>
        </div>
      </Modal>
    </div>
  );
};

export default AacPanel;
