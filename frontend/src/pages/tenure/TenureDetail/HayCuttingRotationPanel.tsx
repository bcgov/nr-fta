import { Add, Edit, Report, TrashCan, Wheat } from '@carbon/icons-react';
import {
  Button,
  Checkbox,
  InlineNotification,
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
import { useCallback, useState, type FC } from 'react';

import AsyncBoundary from '@/components/AsyncBoundary';
import DetailTile, { type DetailField } from '@/components/DetailTile';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import UserName from '@/components/UserName';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import {
  getHayRotations,
  saveHayRotations,
  type HayRotation,
  type HayRotations,
} from '@/services/tenure_rotations';
import { formatDate } from '@/utils/formatDate';

import type { TenurePanelProps } from './panelProps';

const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;
const str = (v: number | string | null | undefined) =>
  v === null || v === undefined ? '' : String(v);
const billableText = (v: string | null | undefined) =>
  v === 'Y' ? 'Y - Billable' : v === 'N' ? 'N - Non-billable' : '—';

/** The legacy grid's input lengths and Auth Harvest limit; the backend checks them too. */
const MAX_BLOCK = 4;
const MAX_RANGE_UNIT = 6;
const MAX_MEADOW = 22;
const MAX_HARVEST = 99999;

/** A row of the grid being edited: an existing rotation (skey set) or a new one. */
interface DraftRow {
  key: string;
  skey: number | null;
  revisionCount: number | null;
  delete: boolean;
  permitBlock: string;
  rangeUnit: string;
  meadowName: string;
  harvest: string;
  source: HayRotation | null;
}

interface Draft {
  nonUse: string;
  billable: string;
  rows: DraftRow[];
}

type RowField = 'permitBlock' | 'rangeUnit' | 'meadowName' | 'harvest';

/** Errors by `nonUse`, or `<row key>.<field>`. */
type Errors = Record<string, string>;

let newRowSeq = 0;
const blankRow = (): DraftRow => ({
  key: `new-${(newRowSeq += 1)}`,
  skey: null,
  revisionCount: null,
  delete: false,
  permitBlock: '',
  rangeUnit: '',
  meadowName: '',
  harvest: '',
  source: null,
});

const draftOf = (data: HayRotations, extraBlank: number): Draft => ({
  nonUse: str(data.provision?.nonUse ?? 0),
  billable: data.provision?.billableInd ?? 'N',
  rows: [
    ...data.rotations.map((r) => ({
      key: `row-${r.meadowRotationSkey}`,
      skey: r.meadowRotationSkey,
      revisionCount: r.revisionCount,
      delete: false,
      permitBlock: r.permitBlockId ?? '',
      rangeUnit: r.rangeUnitId ?? '',
      meadowName: r.meadowName ?? '',
      harvest: str(r.authorizedHarvestableForage),
      source: r,
    })),
    ...Array.from({ length: extraBlank }, blankRow),
  ],
});

const isBlank = (r: DraftRow) =>
  !r.permitBlock.trim() && !r.rangeUnit.trim() && !r.meadowName.trim() && !r.harvest.trim();

const harvestOf = (raw: string) => {
  const v = raw.trim();
  return /^\d+$/.test(v) ? Number(v) : 0;
};

/** The year's harvest (rows kept) plus non-use — what must equal the authorized tonnes. */
const totalOf = (d: Draft) =>
  d.rows.filter((r) => !r.delete).reduce((sum, r) => sum + harvestOf(r.harvest), 0) +
  harvestOf(d.nonUse);

/** Fta612HaycutRotatForm's "Save" checks, per field. */
const validate = (d: Draft): Errors => {
  const e: Errors = {};
  const nonUse = d.nonUse.trim();
  if (!nonUse) e.nonUse = 'Non-Use is mandatory.';
  else if (!/^-?\d+$/.test(nonUse)) e.nonUse = 'Non-Use must be an integer.';
  else if (Number(nonUse) < 0 || Number(nonUse) > 99999)
    e.nonUse = 'Non-Use field must be between 0 and 99999.';
  d.rows.forEach((r) => {
    if (r.delete || isBlank(r)) return;
    if (!r.permitBlock.trim()) e[`${r.key}.permitBlock`] = 'Permit Block is required.';
    if (!r.rangeUnit.trim()) e[`${r.key}.rangeUnit`] = 'Range Unit is required.';
    if (!r.meadowName.trim()) e[`${r.key}.meadowName`] = 'Meadow Name is required.';
    const h = r.harvest.trim();
    if (h && !/^-?\d+$/.test(h)) e[`${r.key}.harvest`] = 'Auth Harvest must be an integer.';
    else if (h && (Number(h) < 0 || Number(h) > MAX_HARVEST))
      e[`${r.key}.harvest`] = `Must be in the range [0,${MAX_HARVEST}].`;
  });
  return e;
};

/**
 * The Hay cutting rotation tab of the tenure detail — legacy FTA612 (Hay Cutting Rotations),
 * for hay cutting licences and permits. A Year dropdown picks the year; its provision and
 * meadow rotations show read-only, and Edit opens the whole year in place, as legacy's grid
 * was: non-use and billable, every row (Delete marks one for removal), and new rows. One Save
 * writes it all or nothing, because the year's harvest plus non-use must equal the tenure's
 * authorized forage tonnes — rows cannot be added one at a time.
 */
const HayCuttingRotationPanel: FC<TenurePanelProps> = ({ tenure, canEdit }) => {
  const forestFileId = tenure.forestFileId;
  const { display } = useNotification();
  const [year, setYear] = useState<number | null>(null);
  const fetcher = useCallback(() => getHayRotations(forestFileId, year), [forestFileId, year]);
  const { data, loading, error, reload } = useApiResource(fetcher, [forestFileId, year]);

  const rules = data?.rules;
  const shownYear = data?.calendarYear ?? null;
  const allowed = canEdit && !!rules?.edit && shownYear !== null;
  const disabledReason = canEdit && rules && !rules.edit ? rules.reason : null;
  const authorized = data?.provision?.authorizedForageTonnes ?? 0;

  const [draft, setDraft] = useState<Draft | null>(null);
  const [errors, setErrors] = useState<Errors>({});
  const [totalError, setTotalError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const editing = draft !== null;

  const startEdit = (extraBlank: number) => {
    if (!data) return;
    setDraft(draftOf(data, extraBlank));
    setErrors({});
    setTotalError(null);
  };

  const clearError = (key: string) =>
    setErrors((prev) => {
      if (!prev[key]) return prev;
      const next = { ...prev };
      delete next[key];
      return next;
    });

  const setProvision = (key: 'nonUse' | 'billable', value: string) => {
    setDraft((d) => (d ? { ...d, [key]: value } : d));
    clearError(key);
    setTotalError(null);
  };

  const setRow = (rowKey: string, field: RowField, value: string) => {
    setDraft((d) =>
      d ? { ...d, rows: d.rows.map((r) => (r.key === rowKey ? { ...r, [field]: value } : r)) } : d,
    );
    clearError(`${rowKey}.${field}`);
    if (field === 'harvest') setTotalError(null);
  };

  const toggleDelete = (rowKey: string, del: boolean) => {
    setDraft((d) =>
      d ? { ...d, rows: d.rows.map((r) => (r.key === rowKey ? { ...r, delete: del } : r)) } : d,
    );
    setTotalError(null);
  };

  const removeNewRow = (rowKey: string) => {
    setDraft((d) => (d ? { ...d, rows: d.rows.filter((r) => r.key !== rowKey) } : d));
    setTotalError(null);
  };

  const addRow = () => setDraft((d) => (d ? { ...d, rows: [...d.rows, blankRow()] } : d));

  const onSave = async () => {
    if (!draft || shownYear === null) return;
    const found = validate(draft);
    const total = totalOf(draft);
    const mismatch =
      total !== authorized
        ? `The total harvest plus the non-use (${total}) must equal the Authorized Forage Tonnes (${authorized}). Authorized Forage Tonnes are updatable via the main tenure screen.`
        : null;
    setErrors(found);
    setTotalError(mismatch);
    if (Object.keys(found).length > 0 || mismatch) return;
    setSaving(true);
    try {
      const result = await saveHayRotations(forestFileId, shownYear, {
        nonUse: draft.nonUse.trim(),
        billableInd: draft.billable,
        provisionRevisionCount: data?.provision?.revisionCount ?? null,
        rows: draft.rows
          .filter((r) => r.skey !== null || !isBlank(r))
          .map((r) => ({
            meadowRotationSkey: r.skey,
            revisionCount: r.revisionCount,
            delete: r.delete,
            permitBlockId: r.permitBlock.trim(),
            rangeUnitId: r.rangeUnit.trim().toUpperCase(),
            meadowName: r.meadowName.trim().toUpperCase(),
            authorizedHarvest: r.harvest.trim(),
          })),
      });
      display({ kind: 'success', title: result.message, timeout: 7000 });
      setDraft(null);
      if (year === null) setYear(shownYear);
      else reload();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not save the hay cutting rotations',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 12000,
      });
    } finally {
      setSaving(false);
    }
  };

  // -------------------------------------------------------------- rendering
  const p = data?.provision ?? null;
  const provisionFields: DetailField[] = [
    { label: 'Authorized Forage Tonnes', value: dash(p?.authorizedForageTonnes) },
    {
      label: 'Non-Use',
      value: draft ? (
        <div className="detail-edit__input detail-edit__input--sm">
          <TextInput
            id="hay-non-use"
            labelText="Non-Use"
            hideLabel
            inputMode="numeric"
            maxLength={5}
            value={draft.nonUse}
            invalid={!!errors.nonUse}
            invalidText={errors.nonUse}
            disabled={saving}
            onChange={(e) => setProvision('nonUse', e.target.value)}
          />
        </div>
      ) : (
        dash(p?.nonUse)
      ),
    },
    {
      label: 'Billable',
      value: draft ? (
        <div className="detail-edit__input detail-edit__input--cell">
          <Select
            id="hay-billable"
            labelText="Billable"
            hideLabel
            value={draft.billable}
            disabled={saving}
            onChange={(e) => setProvision('billable', e.target.value)}
          >
            <SelectItem value="N" text="N - Non-billable" />
            <SelectItem value="Y" text="Y - Billable" />
          </Select>
        </div>
      ) : (
        billableText(p?.billableInd)
      ),
    },
    {
      label: '+ Auth Harvest',
      value: draft
        ? draft.rows.filter((r) => !r.delete).reduce((s, r) => s + harvestOf(r.harvest), 0)
        : dash(p?.plusAuthHarvest),
    },
    { label: '= Authorized', value: dash(p?.equalAuthorized) },
  ];

  const cell = (r: DraftRow, field: RowField, label: string, maxLength: number) => (
    <div className="detail-edit__input detail-edit__input--cell">
      <TextInput
        id={`hay-${r.key}-${field}`}
        labelText={label}
        hideLabel
        size="sm"
        maxLength={maxLength}
        inputMode={field === 'harvest' ? 'numeric' : undefined}
        value={r[field]}
        invalid={!!errors[`${r.key}.${field}`]}
        invalidText={errors[`${r.key}.${field}`]}
        disabled={saving || r.delete}
        onChange={(e) =>
          setRow(
            r.key,
            field,
            field === 'rangeUnit' || field === 'meadowName'
              ? e.target.value.toUpperCase()
              : e.target.value,
          )
        }
      />
    </div>
  );

  const rows = data?.rotations ?? [];

  return (
    <div className={editing ? 'fsp-info__tab-panel detail-edit' : 'fsp-info__tab-panel'}>
      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Loading hay cutting rotations…"
      >
        {data &&
          (!data.rules.applies ? (
            <EmptyState
              icon={<Wheat size={48} />}
              title="Hay cutting rotations don't apply to this tenure"
              body={data.rules.reason ?? 'This tenure does not take hay cutting rotations.'}
              action={
                <div className="detail-tab__empty-action">
                  <Button renderIcon={Add} disabled>
                    Add rotations
                  </Button>
                </div>
              }
            />
          ) : (
            <Stack gap={6}>
              <div className="fsp-info__toolbar">
                {draft ? (
                  <p className="detail-edit__strap">
                    Permit block, range unit and meadow name are required on each row; Auth Harvest
                    is optional.
                  </p>
                ) : (
                  rows.length > 0 && (
                    <Button
                      kind="tertiary"
                      size="sm"
                      renderIcon={Edit}
                      disabled={!allowed}
                      onClick={() => startEdit(0)}
                    >
                      Edit rotations
                    </Button>
                  )
                )}
                <Select
                  id="hay-year"
                  labelText="Year"
                  inline
                  size="sm"
                  value={shownYear === null ? '' : String(shownYear)}
                  disabled={editing || data.years.length === 0}
                  onChange={(e) => setYear(e.target.value ? Number(e.target.value) : null)}
                >
                  {data.years.length === 0 && <SelectItem value="" text="No term" />}
                  {data.years.map((y) => (
                    <SelectItem key={y} value={String(y)} text={String(y)} />
                  ))}
                </Select>
              </div>

              <DetailTile
                title={shownYear === null ? 'Range provision' : `Range provision ${shownYear}`}
                icon={Report}
                fields={provisionFields}
              />

              {!draft && rows.length === 0 ? (
                <EmptyState
                  icon={<Wheat size={48} />}
                  title={
                    shownYear === null
                      ? 'No hay cutting rotations'
                      : `No hay cutting rotations for ${shownYear}`
                  }
                  body="Add the year's meadows: their harvest plus the non-use must equal the authorized forage tonnes."
                  action={
                    <div className="detail-tab__empty-action">
                      <Button renderIcon={Add} disabled={!allowed} onClick={() => startEdit(1)}>
                        Add rotations
                      </Button>
                      {disabledReason && <p className="detail-tab__reason">{disabledReason}</p>}
                    </div>
                  }
                />
              ) : draft ? (
                <div>
                  {totalError && (
                    <InlineNotification
                      kind="error"
                      lowContrast
                      hideCloseButton
                      title="Totals do not match"
                      subtitle={totalError}
                    />
                  )}
                  <header className="detail-tab__actions">
                    <p className="fsp-info__toolbar-note">
                      Harvest plus non-use: {totalOf(draft)} of {authorized} authorized tonnes.
                    </p>
                    <Button
                      kind="tertiary"
                      size="sm"
                      renderIcon={Add}
                      disabled={saving}
                      onClick={addRow}
                    >
                      Add row
                    </Button>
                  </header>
                  <div className="bordered-table">
                    <TableContainer>
                      <Table size="md" useZebraStyles>
                        <TableHead>
                          <TableRow>
                            <TableHeader>Delete</TableHeader>
                            <TableHeader>Permit Block</TableHeader>
                            <TableHeader>Range Unit</TableHeader>
                            <TableHeader>Meadow Name</TableHeader>
                            <TableHeader>Auth Harvest</TableHeader>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {draft.rows.map((r) => (
                            <TableRow key={r.key}>
                              <TableCell>
                                {r.skey !== null ? (
                                  <Checkbox
                                    id={`hay-${r.key}-delete`}
                                    labelText={`Delete ${r.meadowName || 'row'}`}
                                    hideLabel
                                    checked={r.delete}
                                    disabled={saving}
                                    onChange={(_, { checked }) => toggleDelete(r.key, checked)}
                                  />
                                ) : (
                                  <Button
                                    kind="ghost"
                                    size="sm"
                                    hasIconOnly
                                    renderIcon={TrashCan}
                                    iconDescription="Remove new row"
                                    disabled={saving}
                                    onClick={() => removeNewRow(r.key)}
                                  />
                                )}
                              </TableCell>
                              <TableCell>
                                {cell(r, 'permitBlock', 'Permit Block', MAX_BLOCK)}
                              </TableCell>
                              <TableCell>
                                {cell(r, 'rangeUnit', 'Range Unit', MAX_RANGE_UNIT)}
                              </TableCell>
                              <TableCell>
                                {cell(r, 'meadowName', 'Meadow Name', MAX_MEADOW)}
                              </TableCell>
                              <TableCell>{cell(r, 'harvest', 'Auth Harvest', 5)}</TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  </div>
                </div>
              ) : (
                <div>
                  <header className="detail-tab__actions">
                    {disabledReason && <p className="detail-tab__reason">{disabledReason}</p>}
                    <Button
                      kind="tertiary"
                      size="sm"
                      renderIcon={Add}
                      disabled={!allowed}
                      onClick={() => startEdit(1)}
                    >
                      Add rotations
                    </Button>
                  </header>
                  <div className="bordered-table">
                    <TableContainer>
                      <Table size="md" useZebraStyles>
                        <TableHead>
                          <TableRow>
                            <TableHeader>Year</TableHeader>
                            <TableHeader>Permit Block</TableHeader>
                            <TableHeader>Range Unit</TableHeader>
                            <TableHeader>Meadow Name</TableHeader>
                            <TableHeader>Auth Harvest</TableHeader>
                            <TableHeader>Updated</TableHeader>
                            <TableHeader>User ID</TableHeader>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {rows.map((r) => (
                            <TableRow key={r.meadowRotationSkey}>
                              <TableCell>{r.calendarYear}</TableCell>
                              <TableCell>{dash(r.permitBlockId)}</TableCell>
                              <TableCell>{dash(r.rangeUnitId)}</TableCell>
                              <TableCell>{dash(r.meadowName)}</TableCell>
                              <TableCell>{dash(r.authorizedHarvestableForage)}</TableCell>
                              <TableCell>{dash(formatDate(r.updateTimestamp))}</TableCell>
                              <TableCell>
                                <UserName userId={r.updateUserid} />
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  </div>
                </div>
              )}

              {draft && (
                <div className="detail-edit__actions">
                  <Button
                    kind="tertiary"
                    size="md"
                    disabled={saving}
                    onClick={() => setDraft(null)}
                  >
                    Cancel
                  </Button>
                  <Button kind="primary" size="md" disabled={saving} onClick={() => void onSave()}>
                    {saving ? 'Saving…' : 'Save changes'}
                  </Button>
                </div>
              )}
            </Stack>
          ))}
      </AsyncBoundary>
    </div>
  );
};

export default HayCuttingRotationPanel;
