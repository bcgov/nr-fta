import { Add, Stamp } from '@carbon/icons-react';
import {
  Button,
  Checkbox,
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
import { Link } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { Modal } from '@/components/Modal';
import { statusCodeVariant } from '@/components/StatusTag/statusCodes';
import StatusTag from '@/components/StatusTag/StatusTag';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import {
  getCascadeSplits,
  getDistricts,
  getMarkingInstruments,
  getMarkingMethods,
  getSalvageTypes,
  type CodeOption,
} from '@/services/codeLists';
import { addCuttingPermit, getTenureCuttingPermits } from '@/services/tenure_detail';
import { formatDate } from '@/utils/formatDate';

const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;
const date = (v: string | null | undefined) => dash(formatDate(v));

/** Legacy FTA902's limits; the backend enforces them too. */
const MAX_LOCATION = 50;
const AREA_PATTERN = /^\d{1,8}(\.\d{1,4})?$/;

interface Form {
  cpId: string;
  district: string;
  term: string;
  area: string;
  location: string;
  method: string;
  instrument: string;
  salvage: string;
  cascade: string;
  deciduous: boolean;
  catastrophic: boolean;
  cruiseBased: boolean;
}

type Errors = Partial<Record<keyof Form, string>>;

const validate = (f: Form, maxTerm: number): Errors => {
  const e: Errors = {};
  if (!f.cpId.trim()) e.cpId = 'Cutting Permit ID is mandatory.';
  if (!f.district) e.district = 'District is mandatory.';
  const term = Number(f.term);
  if (!f.term.trim()) e.term = 'Term is mandatory.';
  else if (!Number.isInteger(term) || term < 1 || term > maxTerm)
    e.term = `Enter 1 to ${maxTerm} months.`;
  if (f.area.trim() && !AREA_PATTERN.test(f.area.trim()))
    e.area = 'Enter hectares, with up to 4 decimal places.';
  if (f.location.trim().length > MAX_LOCATION) e.location = `At most ${MAX_LOCATION} characters.`;
  if (!f.method) e.method = 'Compliance Method is mandatory.';
  if (f.method !== 'E' && !f.instrument) e.instrument = 'Marking Instrument is mandatory.';
  return e;
};

interface Props {
  forestFileId: string;
  fileTypeCode: string | null;
  /** The tenure's organization unit code, e.g. "DND" — the new permit's default district. */
  orgUnitCode: string | null;
  /** Whether the user's role may add (FTA_ADMIN). */
  canEdit: boolean;
}

/**
 * The Cutting permit / mark tab of the tenure detail — legacy FTA901 (Cutting
 * Permit List). Laid out as the private-mark detail's tabs: an empty state, or
 * the table with an Add button above it, and a dialog to add.
 *
 * Adding creates the permit as ESF's tenure-application load did (legacy's own
 * Add New never saved one): status PE, with a timber mark the backend generates
 * by the tenure's file type, after FTA902's checks.
 */
const CuttingPermitsPanel: FC<Props> = ({ forestFileId, fileTypeCode, orgUnitCode, canEdit }) => {
  const { display } = useNotification();
  const fetcher = useCallback(() => getTenureCuttingPermits(forestFileId), [forestFileId]);
  const { data: rows, loading, error, reload } = useApiResource(fetcher, [forestFileId]);

  const timberTenure = (fileTypeCode ?? '').startsWith('A');
  // Why the Add button is disabled, when it says why: a role without the right
  // just sees it disabled.
  const allowed = canEdit && timberTenure;
  const maxTerm = fileTypeCode === 'A11' ? 60 : 48;
  const salvageFixed = fileTypeCode === 'A31';

  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState<Errors>({});
  const [lists, setLists] = useState<{
    districts: CodeOption[];
    methods: CodeOption[];
    instruments: CodeOption[];
    salvage: CodeOption[];
    cascades: CodeOption[];
  } | null>(null);
  const emptyForm = (): Form => ({
    cpId: '',
    district: '',
    term: '',
    area: '',
    location: '',
    // ESF's / FTA510's defaults: full standard marking with a hammer.
    method: 'S',
    instrument: 'H',
    salvage: salvageFixed ? 'SSS' : '',
    cascade: '',
    deciduous: false,
    catastrophic: false,
    cruiseBased: false,
  });
  const [form, setForm] = useState<Form>(emptyForm);

  // The dialog's lists, once it opens (cached app-wide). The tenure's own
  // district is preselected when the list has it.
  useEffect(() => {
    if (!open || lists) return;
    let cancelled = false;
    Promise.all([
      getDistricts(),
      getMarkingMethods(),
      getMarkingInstruments(),
      getSalvageTypes(),
      getCascadeSplits(),
    ])
      .then(([districts, methods, instruments, salvage, cascades]) => {
        if (cancelled) return;
        setLists({ districts, methods, instruments, salvage, cascades });
        const own = districts.find((d) => orgUnitCode && d.description?.startsWith(orgUnitCode));
        if (own) setForm((f) => (f.district ? f : { ...f, district: own.code }));
      })
      .catch(() => {
        if (!cancelled) {
          display({
            kind: 'error',
            title: 'Could not load the cutting permit lists',
            subtitle: 'Close the dialog and try again.',
            timeout: 6000,
          });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [open, lists, orgUnitCode, display]);

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
    const own = lists?.districts.find((d) => orgUnitCode && d.description?.startsWith(orgUnitCode));
    setForm({ ...emptyForm(), district: own?.code ?? '' });
    setErrors({});
    setOpen(true);
  };

  const closeDialog = () => {
    if (!saving) setOpen(false);
  };

  const submit = async () => {
    const found = validate(form, maxTerm);
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    setSaving(true);
    try {
      const created = await addCuttingPermit(forestFileId, {
        cuttingPermitId: form.cpId.trim().toUpperCase(),
        forestDistrict: form.district,
        location: form.location.trim() || null,
        tenureTerm: Number(form.term),
        harvestArea: form.area.trim() ? Number(form.area.trim()) : null,
        markingMethodCode: form.method,
        markingInstrumentCode: form.method === 'E' ? form.instrument || null : form.instrument,
        salvageTypeCode: form.salvage || null,
        cascadeSplitCode: form.cascade || null,
        deciduous: form.deciduous,
        catastrophic: form.catastrophic,
        cruiseBased: form.cruiseBased,
      });
      display({
        kind: 'success',
        title: `Cutting permit ${created.cuttingPermitId} added`,
        subtitle: `Timber mark ${created.timberMark}, status PE.`,
        timeout: 7000,
      });
      setOpen(false);
      reload();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not add the cutting permit',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  const listsLoading = open && !lists;
  const options = (items: CodeOption[] | undefined) =>
    (items ?? []).map((o) => (
      <SelectItem key={o.code} value={o.code} text={o.description || o.code} />
    ));

  const addButton = (primary: boolean) => (
    <Button
      kind={primary ? 'primary' : 'tertiary'}
      size={primary ? undefined : 'sm'}
      renderIcon={Add}
      disabled={!allowed}
      onClick={openDialog}
    >
      Add cutting permit
    </Button>
  );

  return (
    <div className="fsp-info__tab-panel">
      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Loading cutting permits…"
      >
        {rows &&
          (rows.length === 0 ? (
            <EmptyState
              icon={<Stamp size={48} />}
              title="No cutting permits for this tenure"
              body="Add a cutting permit; its timber mark is generated from the tenure."
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
                        <TableHeader>Cutting Permit</TableHeader>
                        <TableHeader>Timber Mark</TableHeader>
                        <TableHeader>District</TableHeader>
                        <TableHeader>Status</TableHeader>
                        <TableHeader>Issue Date</TableHeader>
                        <TableHeader>Expiry Date</TableHeader>
                        <TableHeader>Extend Date</TableHeader>
                        <TableHeader>Salvage</TableHeader>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {rows.map((cp) => (
                        <TableRow key={cp.hvaSkey ?? `${cp.cuttingPermitId}-${cp.timberMark}`}>
                          <TableCell>
                            {cp.cuttingPermitId ? (
                              <Link
                                to={`/harvesting-authority/${encodeURIComponent(
                                  cp.cuttingPermitId,
                                )}?forestFileId=${encodeURIComponent(forestFileId)}`}
                              >
                                {cp.cuttingPermitId}
                              </Link>
                            ) : (
                              '—'
                            )}
                          </TableCell>
                          <TableCell>{dash(cp.timberMark)}</TableCell>
                          <TableCell>{dash(cp.orgUnitCode)}</TableCell>
                          <TableCell>
                            {cp.statusCode ? (
                              <StatusTag
                                status={cp.statusDesc ?? cp.statusCode}
                                variant={statusCodeVariant(cp.statusCode)}
                              />
                            ) : (
                              '—'
                            )}
                          </TableCell>
                          <TableCell>{date(cp.issueDate)}</TableCell>
                          <TableCell>{date(cp.expiryDate)}</TableCell>
                          <TableCell>{date(cp.extendDate)}</TableCell>
                          <TableCell>{dash(cp.salvageTypeCode)}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </div>
            </div>
          ))}
      </AsyncBoundary>

      {/* Add dialog — the private-mark tabs' dialog, a size up for more fields. */}
      <Modal
        open={open}
        passiveModal
        size="md"
        className="detail-dialog"
        modalHeading="Add cutting permit"
        onRequestClose={closeDialog}
        preventCloseOnClickOutside
      >
        <Stack gap={5}>
          <p className="detail-dialog__subtitle">
            All fields are required unless marked optional. The permit starts as PE and its timber
            mark is generated from the tenure.
          </p>
          <div className="detail-dialog__pair">
            <TextInput
              id="cp-id"
              labelText="Cutting Permit ID"
              value={form.cpId}
              maxLength={3}
              helperText="Up to 3 characters; the tenure type may allow fewer."
              invalid={!!errors.cpId}
              invalidText={errors.cpId}
              disabled={saving}
              onChange={(e) => set('cpId', e.target.value.toUpperCase())}
            />
            <Select
              id="cp-district"
              labelText="District"
              value={form.district}
              invalid={!!errors.district}
              invalidText={errors.district}
              disabled={saving || listsLoading}
              onChange={(e) => set('district', e.target.value)}
            >
              <SelectItem value="" text={listsLoading ? 'Loading…' : '— Select district —'} />
              {options(lists?.districts)}
            </Select>
          </div>
          <div className="detail-dialog__pair">
            <TextInput
              id="cp-term"
              labelText="Term (months)"
              inputMode="numeric"
              value={form.term}
              helperText={`Up to ${maxTerm} months.`}
              invalid={!!errors.term}
              invalidText={errors.term}
              disabled={saving}
              onChange={(e) => set('term', e.target.value)}
            />
            <TextInput
              id="cp-area"
              labelText="Area (Hectares) (optional)"
              inputMode="decimal"
              value={form.area}
              invalid={!!errors.area}
              invalidText={errors.area}
              disabled={saving}
              onChange={(e) => set('area', e.target.value)}
            />
          </div>
          <TextInput
            id="cp-location"
            labelText="Location (optional)"
            value={form.location}
            maxLength={MAX_LOCATION}
            invalid={!!errors.location}
            invalidText={errors.location}
            disabled={saving}
            onChange={(e) => set('location', e.target.value)}
          />
          <div className="detail-dialog__pair">
            <Select
              id="cp-method"
              labelText="Compliance Method"
              value={form.method}
              invalid={!!errors.method}
              invalidText={errors.method}
              disabled={saving || listsLoading}
              onChange={(e) => set('method', e.target.value)}
            >
              <SelectItem value="" text={listsLoading ? 'Loading…' : '— Select method —'} />
              {options(lists?.methods)}
            </Select>
            <Select
              id="cp-instrument"
              labelText={
                form.method === 'E' ? 'Marking Instrument (optional)' : 'Marking Instrument'
              }
              value={form.instrument}
              invalid={!!errors.instrument}
              invalidText={errors.instrument}
              disabled={saving || listsLoading}
              onChange={(e) => set('instrument', e.target.value)}
            >
              <SelectItem value="" text={listsLoading ? 'Loading…' : '— Select instrument —'} />
              {options(lists?.instruments)}
            </Select>
          </div>
          <div className="detail-dialog__pair">
            <Select
              id="cp-salvage"
              labelText={salvageFixed ? 'Salvage Type' : 'Salvage Type (optional)'}
              value={form.salvage}
              helperText={salvageFixed ? 'Always SSS for an A31.' : undefined}
              disabled={saving || listsLoading || salvageFixed}
              onChange={(e) => set('salvage', e.target.value)}
            >
              <SelectItem value="" text={listsLoading ? 'Loading…' : '— None —'} />
              {options(lists?.salvage)}
            </Select>
            <Select
              id="cp-cascade"
              labelText="Cascade Split (optional)"
              value={form.cascade}
              disabled={saving || listsLoading}
              onChange={(e) => set('cascade', e.target.value)}
            >
              <SelectItem value="" text={listsLoading ? 'Loading…' : "District's default"} />
              {options(lists?.cascades)}
            </Select>
          </div>
          <fieldset className="detail-dialog__checks">
            <legend className="cds--label">Indicators</legend>
            <Checkbox
              id="cp-deciduous"
              labelText="Deciduous"
              checked={form.deciduous}
              disabled={saving}
              onChange={(_, { checked }) => set('deciduous', checked)}
            />
            <Checkbox
              id="cp-catastrophic"
              labelText="Catastrophic"
              checked={form.catastrophic}
              disabled={saving}
              onChange={(_, { checked }) => set('catastrophic', checked)}
            />
            <Checkbox
              id="cp-cruise"
              labelText="Cruise based"
              checked={form.cruiseBased}
              disabled={saving}
              onChange={(_, { checked }) => set('cruiseBased', checked)}
            />
          </fieldset>
        </Stack>
        <div className="detail-dialog__actions">
          <Button kind="tertiary" disabled={saving} onClick={closeDialog}>
            Cancel
          </Button>
          <Button kind="primary" disabled={saving} onClick={() => void submit()}>
            {saving ? 'Adding…' : 'Add cutting permit'}
          </Button>
        </div>
      </Modal>
    </div>
  );
};

export default CuttingPermitsPanel;
