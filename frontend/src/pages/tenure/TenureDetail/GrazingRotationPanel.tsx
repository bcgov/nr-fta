import { Add, Edit, Sprout, Report } from '@carbon/icons-react';
import {
  Button,
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
import { useCallback, useEffect, useState, type FC } from 'react';

import AsyncBoundary from '@/components/AsyncBoundary';
import DetailTile, { type DetailField } from '@/components/DetailTile';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import type { CodeOption } from '@/services/codeLists';
import {
  addGrazingRotation,
  deleteGrazingRotation,
  getGrazingRotations,
  getLivestockCodes,
  saveGrazingProvision,
  updateGrazingRotation,
  type GrazingProvision,
  type GrazingRotation,
} from '@/services/tenure_rotations';

import type { TenurePanelProps } from './panelProps';

const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;
const str = (v: number | string | null | undefined) =>
  v === null || v === undefined ? '' : String(v);
const billableText = (v: string | null | undefined) =>
  v === 'Y' ? 'Y - Billable' : v === 'N' ? 'N - Non-billable' : '—';

/** Legacy's integer check with its messages: blank passes unless a required label is given. */
const intError = (
  raw: string,
  label: string,
  max: number,
  requiredLabel?: string,
): string | undefined => {
  const v = raw.trim();
  if (!v) return requiredLabel ? `${requiredLabel} is mandatory.` : undefined;
  if (!/^-?\d+$/.test(v)) return `${label} must be an integer.`;
  const n = Number(v);
  if (n < 0 || n > max) return `${label} field must be between 0 and ${max}.`;
  return undefined;
};

/** A real MM-DD day in `year` (02-29 only in a leap year), as legacy's DateValidator. */
const monthDay = (raw: string, year: number): Date | null => {
  const m = /^(\d{2})-(\d{2})$/.exec(raw.trim());
  if (!m) return null;
  const month = Number(m[1]);
  const day = Number(m[2]);
  const d = new Date(year, month - 1, day);
  return d.getFullYear() === year && d.getMonth() === month - 1 && d.getDate() === day ? d : null;
};

// ---------------------------------------------------------------- provision (Save Provision)

interface ProvisionForm {
  nonUse: string;
  billable: string;
  cattle: string;
  horses: string;
  sheep: string;
  other: string;
}

type ProvisionErrors = Partial<Record<keyof ProvisionForm, string>>;

const provisionForm = (p: GrazingProvision | null): ProvisionForm => ({
  nonUse: str(p?.nonUseForageTonnes),
  billable: p?.billableNonUseInd ?? 'N',
  cattle: str(p?.maxCattle),
  horses: str(p?.maxHorses),
  sheep: str(p?.maxSheep),
  other: str(p?.maxOtherLivestock),
});

/** Fta611GrazeRotatnForm's "Save Provision" checks. */
const validateProvision = (f: ProvisionForm): ProvisionErrors => {
  const e: ProvisionErrors = {};
  const set = (k: keyof ProvisionForm, msg: string | undefined) => {
    if (msg) e[k] = msg;
  };
  set('nonUse', intError(f.nonUse, 'Non-Use', 99999));
  set('cattle', intError(f.cattle, 'Cattle', 99999));
  set('horses', intError(f.horses, 'Horse', 9999));
  set('sheep', intError(f.sheep, 'Sheep', 9999));
  set('other', intError(f.other, 'Other', 9999));
  return e;
};

// ---------------------------------------------------------------- rotation dialog (Save)

interface RotationForm {
  livestockCode: string;
  count: string;
  begin: string;
  end: string;
  rangeUnit: string;
  pasture: string;
  aums: string;
  pld: string;
}

type RotationErrors = Partial<Record<keyof RotationForm, string>>;

const emptyRotation = (): RotationForm => ({
  livestockCode: '',
  count: '',
  begin: '',
  end: '',
  rangeUnit: '',
  pasture: '',
  aums: '',
  // Fta611GrazeRotatnForm.setDefaults: PLD starts at 0.
  pld: '0',
});

const rotationForm = (r: GrazingRotation): RotationForm => ({
  livestockCode: r.livestockCode ?? '',
  count: str(r.livestockCount),
  begin: r.beginRotationDate ?? '',
  end: r.endRotationDate ?? '',
  rangeUnit: r.rangeUnitId ?? '',
  pasture: r.pastureId ?? '',
  aums: str(r.authorizedGrazableForage),
  pld: str(r.privateLandGrazableForage),
});

/** Fta611GrazeRotatnForm's "Save" checks; the backend repeats them and checks the pasture. */
const validateRotation = (f: RotationForm, year: number): RotationErrors => {
  const e: RotationErrors = {};
  if (!f.livestockCode) e.livestockCode = 'Animal is mandatory.';
  const count = intError(f.count, 'Livestock Count', 9999, 'No.');
  if (count) e.count = count;
  const begin = f.begin.trim() ? monthDay(f.begin, year) : null;
  const end = f.end.trim() ? monthDay(f.end, year) : null;
  if (!f.begin.trim()) e.begin = 'Start is mandatory.';
  else if (!begin) e.begin = 'Start must be a valid day in the format MM-DD.';
  if (!f.end.trim()) e.end = 'End is mandatory.';
  else if (!end) e.end = 'End must be a valid day in the format MM-DD.';
  if (begin && end && begin > end) e.end = 'Start must be less than or equal to End.';
  if (!f.rangeUnit.trim()) e.rangeUnit = 'Range Unit is mandatory.';
  if (!f.pasture.trim()) e.pasture = 'Range Pasture is mandatory.';
  const aums = intError(f.aums, 'TTL AUMs', 99999);
  if (aums) e.aums = aums;
  const pld = intError(f.pld, 'PLD', 99999, 'PLD');
  if (pld) e.pld = pld;
  return e;
};

/**
 * The Grazing rotation tab of the tenure detail — legacy FTA611 (Grazing Rotations), for
 * grazing licences and permits. A Year dropdown (the term's years) picks the year; its range
 * provision shows in a tile that edits in place (Save Provision), and its livestock rotations
 * in a table with an Add button and a row menu to edit or delete. TTL AUMs left blank are
 * calculated by the backend, as legacy's were.
 */
const GrazingRotationPanel: FC<TenurePanelProps> = ({ tenure, canEdit }) => {
  const forestFileId = tenure.forestFileId;
  const { display } = useNotification();
  // Null until the user picks one: the backend then answers with legacy's default year.
  const [year, setYear] = useState<number | null>(null);
  const fetcher = useCallback(() => getGrazingRotations(forestFileId, year), [forestFileId, year]);
  const { data, loading, error, reload } = useApiResource(fetcher, [forestFileId, year]);

  const rules = data?.rules;
  const shownYear = data?.calendarYear ?? null;
  const allowed = canEdit && !!rules?.edit && shownYear !== null;
  // Why writes are off, when it is not the role: a role without the right just sees the
  // buttons disabled.
  const disabledReason = canEdit && rules && !rules.edit ? rules.reason : null;

  /** Re-read the same year after a write (the default year could move otherwise). */
  const refresh = () => {
    if (year === null && shownYear !== null) setYear(shownYear);
    else reload();
  };

  // -------------------------------------------------------------- provision editing
  const [editing, setEditing] = useState(false);
  const [pForm, setPForm] = useState<ProvisionForm>(() => provisionForm(null));
  const [pErrors, setPErrors] = useState<ProvisionErrors>({});
  const [pSaving, setPSaving] = useState(false);

  const setP = (key: keyof ProvisionForm, value: string) => {
    setPForm((prev) => ({ ...prev, [key]: value }));
    setPErrors((prev) => {
      if (!prev[key]) return prev;
      const next = { ...prev };
      delete next[key];
      return next;
    });
  };

  const onEditProvision = () => {
    setPForm(provisionForm(data?.provision ?? null));
    setPErrors({});
    setEditing(true);
  };

  const onSaveProvision = async () => {
    if (shownYear === null) return;
    const found = validateProvision(pForm);
    if (Object.keys(found).length > 0) {
      setPErrors(found);
      return;
    }
    setPSaving(true);
    try {
      await saveGrazingProvision(forestFileId, shownYear, {
        nonUseForageTonnes: pForm.nonUse,
        billableNonUseInd: pForm.billable,
        maxCattle: pForm.cattle,
        maxHorses: pForm.horses,
        maxSheep: pForm.sheep,
        maxOtherLivestock: pForm.other,
        revisionCount: data?.provision?.exists ? data.provision.revisionCount : null,
      });
      display({
        kind: 'success',
        title: 'Save successful.',
        subtitle: `Range provision for ${shownYear} saved.`,
        timeout: 6000,
      });
      setEditing(false);
      refresh();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not save the range provision',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setPSaving(false);
    }
  };

  // -------------------------------------------------------------- rotation dialog
  const [dialog, setDialog] = useState<{ row: GrazingRotation | null } | null>(null);
  const [form, setForm] = useState<RotationForm>(emptyRotation);
  const [errors, setErrors] = useState<RotationErrors>({});
  const [saving, setSaving] = useState(false);
  const [codes, setCodes] = useState<CodeOption[] | null>(null);

  useEffect(() => {
    if (!dialog || codes) return;
    let cancelled = false;
    getLivestockCodes()
      .then((list) => {
        if (!cancelled) setCodes(list);
      })
      .catch(() => {
        if (!cancelled) {
          display({
            kind: 'error',
            title: 'Could not load the livestock codes',
            subtitle: 'Close the dialog and try again.',
            timeout: 6000,
          });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [dialog, codes, display]);

  const set = (key: keyof RotationForm, value: string) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    setErrors((prev) => {
      if (!prev[key]) return prev;
      const next = { ...prev };
      delete next[key];
      return next;
    });
  };

  const openAdd = () => {
    setForm(emptyRotation());
    setErrors({});
    setDialog({ row: null });
  };

  const openEdit = (row: GrazingRotation) => {
    setForm(rotationForm(row));
    setErrors({});
    setDialog({ row });
  };

  const closeDialog = () => {
    if (!saving) setDialog(null);
  };

  const submit = async () => {
    if (!dialog || shownYear === null) return;
    const found = validateRotation(form, shownYear);
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    setSaving(true);
    const body = {
      livestockCode: form.livestockCode,
      livestockCount: form.count.trim(),
      beginRotationDate: form.begin.trim(),
      endRotationDate: form.end.trim(),
      rangeUnitId: form.rangeUnit.trim().toUpperCase(),
      pastureId: form.pasture.trim().toUpperCase(),
      authorizedGrazableForage: form.aums.trim(),
      privateLandGrazableForage: form.pld.trim(),
      revisionCount: dialog.row?.revisionCount ?? null,
    };
    try {
      if (dialog.row) {
        await updateGrazingRotation(
          forestFileId,
          shownYear,
          dialog.row.livestockRotationSkey,
          body,
        );
      } else {
        await addGrazingRotation(forestFileId, shownYear, body);
      }
      display({
        kind: 'success',
        title: 'Save successful.',
        subtitle: `${dialog.row ? 'Rotation changed' : 'Rotation added'} for ${shownYear}.`,
        timeout: 6000,
      });
      setDialog(null);
      refresh();
    } catch (err) {
      display({
        kind: 'error',
        title: dialog.row ? 'Could not change the rotation' : 'Could not add the rotation',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  // -------------------------------------------------------------- delete
  const [pendingDelete, setPendingDelete] = useState<GrazingRotation | null>(null);
  const [deleting, setDeleting] = useState(false);

  const confirmDelete = async () => {
    if (!pendingDelete || shownYear === null) return;
    setDeleting(true);
    try {
      await deleteGrazingRotation(
        forestFileId,
        shownYear,
        pendingDelete.livestockRotationSkey,
        pendingDelete.revisionCount,
      );
      display({ kind: 'success', title: 'Delete successful.', timeout: 6000 });
      setPendingDelete(null);
      refresh();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not delete the rotation',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setDeleting(false);
    }
  };

  // -------------------------------------------------------------- rendering
  const addButton = (primary: boolean) => (
    <Button
      kind={primary ? 'primary' : 'tertiary'}
      size={primary ? undefined : 'sm'}
      renderIcon={Add}
      disabled={!allowed || editing}
      onClick={openAdd}
    >
      Add rotation
    </Button>
  );

  const pInput = (key: keyof ProvisionForm, label: string, maxLength: number) => (
    <div className="detail-edit__input detail-edit__input--sm">
      <TextInput
        id={`grazing-provision-${key}`}
        labelText={label}
        hideLabel
        inputMode="numeric"
        maxLength={maxLength}
        value={pForm[key]}
        invalid={!!pErrors[key]}
        invalidText={pErrors[key]}
        disabled={pSaving}
        onChange={(e) => setP(key, e.target.value)}
      />
    </div>
  );

  const p = data?.provision ?? null;
  const provisionFields: DetailField[] = [
    {
      label: 'Non-Use',
      value: editing ? pInput('nonUse', 'Non-Use', 5) : dash(p?.nonUseForageTonnes ?? 0),
    },
    {
      label: 'Billable',
      value: editing ? (
        <div className="detail-edit__input detail-edit__input--cell">
          <Select
            id="grazing-provision-billable"
            labelText="Billable"
            hideLabel
            value={pForm.billable}
            disabled={pSaving}
            onChange={(e) => setP('billable', e.target.value)}
          >
            <SelectItem value="N" text="N - Non-billable" />
            <SelectItem value="Y" text="Y - Billable" />
          </Select>
        </div>
      ) : (
        billableText(p?.billableNonUseInd)
      ),
    },
    { label: '+ TTL AUMs', value: dash(p?.totalAuthorizedGrazableForage ?? 0) },
    { label: '− PLD', value: dash(p?.totalPrivateLandGrazableForage ?? 0) },
    { label: '= Net Authorized', value: dash(p?.netAuthorized ?? 0) },
  ];
  const livestockFields: DetailField[] = [
    {
      label: 'Cattle',
      value: editing ? pInput('cattle', 'Maximum cattle', 5) : dash(p?.maxCattle),
    },
    {
      label: 'Horses',
      value: editing ? pInput('horses', 'Maximum horses', 4) : dash(p?.maxHorses),
    },
    {
      label: 'Sheep',
      value: editing ? pInput('sheep', 'Maximum sheep', 4) : dash(p?.maxSheep),
    },
    {
      label: 'Other',
      value: editing ? pInput('other', 'Maximum other livestock', 4) : dash(p?.maxOtherLivestock),
    },
  ];

  // The Animal list, plus a row's own code should it have expired since.
  const codeOptions = (() => {
    const list = codes ?? [];
    const own = dialog?.row;
    if (own?.livestockCode && !list.some((c) => c.code === own.livestockCode)) {
      return [
        { code: own.livestockCode, description: own.livestockDesc ?? own.livestockCode },
        ...list,
      ];
    }
    return list;
  })();

  const rows = data?.rotations ?? [];

  return (
    <div className={editing ? 'fsp-info__tab-panel detail-edit' : 'fsp-info__tab-panel'}>
      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Loading grazing rotations…"
      >
        {data &&
          (!data.rules.applies ? (
            <EmptyState
              icon={<Sprout size={48} />}
              title="Grazing rotations don't apply to this tenure"
              body={data.rules.reason ?? 'This tenure does not take grazing rotations.'}
              action={
                <div className="detail-tab__empty-action">
                  <Button renderIcon={Add} disabled>
                    Add rotation
                  </Button>
                </div>
              }
            />
          ) : (
            <Stack gap={6}>
              <div className="fsp-info__toolbar">
                {editing ? (
                  <p className="detail-edit__strap">
                    All fields are optional; billable defaults to non-billable.
                  </p>
                ) : (
                  <Button
                    kind="tertiary"
                    size="sm"
                    renderIcon={Edit}
                    disabled={!allowed}
                    onClick={onEditProvision}
                  >
                    Edit provision
                  </Button>
                )}
                <Select
                  id="grazing-year"
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

              {/* Side by side; they stack when the pane is too narrow. */}
              <div className="fsp-info__tile-row">
                <DetailTile
                  title={shownYear === null ? 'Range provision' : `Range provision ${shownYear}`}
                  icon={Report}
                  fields={provisionFields}
                />
                <DetailTile title="Maximum livestock" icon={Sprout} fields={livestockFields} />
              </div>
              {editing && (
                <div className="detail-edit__actions">
                  <Button
                    kind="tertiary"
                    size="md"
                    disabled={pSaving}
                    onClick={() => setEditing(false)}
                  >
                    Cancel
                  </Button>
                  <Button
                    kind="primary"
                    size="md"
                    disabled={pSaving}
                    onClick={() => void onSaveProvision()}
                  >
                    {pSaving ? 'Saving…' : 'Save changes'}
                  </Button>
                </div>
              )}

              {rows.length === 0 ? (
                <EmptyState
                  icon={<Sprout size={48} />}
                  title={
                    shownYear === null
                      ? 'No grazing rotations'
                      : `No livestock rotations for ${shownYear}`
                  }
                  body="Add a rotation: the animal, head count, days and range unit pasture it grazes."
                  action={
                    <div className="detail-tab__empty-action">
                      {addButton(true)}
                      {disabledReason && <p className="detail-tab__reason">{disabledReason}</p>}
                    </div>
                  }
                />
              ) : (
                <div>
                  <header className="detail-tab__actions">
                    {disabledReason && <p className="detail-tab__reason">{disabledReason}</p>}
                    {addButton(false)}
                  </header>
                  <div className="bordered-table">
                    <TableContainer>
                      <Table size="md" useZebraStyles>
                        <TableHead>
                          <TableRow>
                            <TableHeader>Year</TableHeader>
                            <TableHeader>Animal</TableHeader>
                            <TableHeader>No.</TableHeader>
                            <TableHeader>Start</TableHeader>
                            <TableHeader>End</TableHeader>
                            <TableHeader>Range Unit &amp; Pasture</TableHeader>
                            <TableHeader>TTL AUMs</TableHeader>
                            <TableHeader>PLD</TableHeader>
                            <TableHeader aria-label="Actions" />
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {rows.map((r) => (
                            <TableRow key={r.livestockRotationSkey}>
                              <TableCell>{r.calendarYear}</TableCell>
                              <TableCell>{dash(r.livestockDesc)}</TableCell>
                              <TableCell>{dash(r.livestockCount)}</TableCell>
                              <TableCell>{dash(r.beginRotationDate)}</TableCell>
                              <TableCell>{dash(r.endRotationDate)}</TableCell>
                              <TableCell>
                                {r.rangeUnitId || r.pastureId
                                  ? `${r.rangeUnitId ?? ''} - ${r.pastureId ?? ''}`
                                  : '—'}
                              </TableCell>
                              <TableCell>{dash(r.authorizedGrazableForage)}</TableCell>
                              <TableCell>{dash(r.privateLandGrazableForage)}</TableCell>
                              <TableCell>
                                <OverflowMenu
                                  size="sm"
                                  flipped
                                  iconDescription={`Rotation ${r.livestockCode ?? ''} actions`}
                                >
                                  <OverflowMenuItem
                                    itemText="Edit"
                                    disabled={!allowed || editing}
                                    onClick={() => openEdit(r)}
                                  />
                                  <OverflowMenuItem
                                    itemText="Delete"
                                    isDelete
                                    hasDivider
                                    disabled={!allowed || editing}
                                    onClick={() => setPendingDelete(r)}
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
              )}
            </Stack>
          ))}
      </AsyncBoundary>

      {/* Add / edit dialog. */}
      <Modal
        open={!!dialog}
        passiveModal
        size="md"
        className="detail-dialog"
        modalHeading={dialog?.row ? 'Edit rotation' : 'Add rotation'}
        onRequestClose={closeDialog}
        preventCloseOnClickOutside
      >
        <Stack gap={5}>
          <p className="detail-dialog__subtitle">
            All fields are required unless marked optional. Rotation days are in {shownYear}.
          </p>
          <Select
            id="grazing-animal"
            labelText="Animal"
            value={form.livestockCode}
            invalid={!!errors.livestockCode}
            invalidText={errors.livestockCode}
            disabled={saving || !codes}
            onChange={(e) => set('livestockCode', e.target.value)}
          >
            <SelectItem value="" text={codes ? '— Select animal —' : 'Loading…'} />
            {codeOptions.map((c) => (
              <SelectItem key={c.code} value={c.code} text={c.description || c.code} />
            ))}
          </Select>
          <div className="detail-dialog__pair">
            <TextInput
              id="grazing-count"
              labelText="No. (head)"
              inputMode="numeric"
              maxLength={4}
              value={form.count}
              invalid={!!errors.count}
              invalidText={errors.count}
              disabled={saving}
              onChange={(e) => set('count', e.target.value)}
            />
            <TextInput
              id="grazing-pld"
              labelText="PLD"
              helperText="Private land deduction, in AUMs."
              inputMode="numeric"
              maxLength={5}
              value={form.pld}
              invalid={!!errors.pld}
              invalidText={errors.pld}
              disabled={saving}
              onChange={(e) => set('pld', e.target.value)}
            />
          </div>
          <div className="detail-dialog__pair">
            <TextInput
              id="grazing-start"
              labelText="Start"
              placeholder="MM-DD"
              maxLength={5}
              value={form.begin}
              invalid={!!errors.begin}
              invalidText={errors.begin}
              disabled={saving}
              onChange={(e) => set('begin', e.target.value)}
            />
            <TextInput
              id="grazing-end"
              labelText="End"
              placeholder="MM-DD"
              maxLength={5}
              value={form.end}
              invalid={!!errors.end}
              invalidText={errors.end}
              disabled={saving}
              onChange={(e) => set('end', e.target.value)}
            />
          </div>
          <div className="detail-dialog__pair">
            <TextInput
              id="grazing-range-unit"
              labelText="Range Unit"
              maxLength={6}
              value={form.rangeUnit}
              invalid={!!errors.rangeUnit}
              invalidText={errors.rangeUnit}
              disabled={saving}
              onChange={(e) => set('rangeUnit', e.target.value.toUpperCase())}
            />
            <TextInput
              id="grazing-pasture"
              labelText="Pasture"
              maxLength={2}
              value={form.pasture}
              invalid={!!errors.pasture}
              invalidText={errors.pasture}
              disabled={saving}
              onChange={(e) => set('pasture', e.target.value.toUpperCase())}
            />
          </div>
          <TextInput
            id="grazing-aums"
            labelText="TTL AUMs (optional)"
            helperText="Left blank, calculated from the head count and the days (sheep count a quarter)."
            inputMode="numeric"
            maxLength={5}
            value={form.aums}
            invalid={!!errors.aums}
            invalidText={errors.aums}
            disabled={saving}
            onChange={(e) => set('aums', e.target.value)}
          />
        </Stack>
        <div className="detail-dialog__actions">
          <Button kind="tertiary" disabled={saving} onClick={closeDialog}>
            Cancel
          </Button>
          <Button kind="primary" disabled={saving} onClick={() => void submit()}>
            {saving ? 'Saving…' : dialog?.row ? 'Save changes' : 'Add rotation'}
          </Button>
        </div>
      </Modal>

      {/* Delete confirmation. */}
      <Modal
        open={!!pendingDelete}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading="Delete rotation?"
        onRequestClose={() => {
          if (!deleting) setPendingDelete(null);
        }}
        preventCloseOnClickOutside
      >
        {pendingDelete && (
          <p className="detail-dialog__subtitle">
            {`This removes the ${pendingDelete.livestockDesc ?? pendingDelete.livestockCode ?? ''} rotation, ${
              pendingDelete.beginRotationDate ?? ''
            } to ${pendingDelete.endRotationDate ?? ''} on ${pendingDelete.rangeUnitId ?? ''} - ${
              pendingDelete.pastureId ?? ''
            }, and updates the year's totals.`}
          </p>
        )}
        <div className="detail-dialog__actions">
          <Button kind="tertiary" disabled={deleting} onClick={() => setPendingDelete(null)}>
            Cancel
          </Button>
          <Button kind="danger" disabled={deleting} onClick={() => void confirmDelete()}>
            {deleting ? 'Deleting…' : 'Delete'}
          </Button>
        </div>
      </Modal>
    </div>
  );
};

export default GrazingRotationPanel;
