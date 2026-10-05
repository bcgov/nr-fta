import { Add, UserFollow } from '@carbon/icons-react';
import {
  Button,
  DatePicker,
  DatePickerInput,
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
} from '@carbon/react';
import { useEffect, useState, type FC } from 'react';

import ClientComboBox from '@/components/ClientComboBox';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import { getFileClientTypes, type CodeOption } from '@/services/codeLists';
import { addClient, type MarkAssociatedClient } from '@/services/mark_detail';
import { formatDate } from '@/utils/formatDate';

const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;

/** yyyy-mm-dd in local time. */
const toIsoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

/** FTA_513's date rules by client type: which dates a type needs, and which it must not have. */
const startRequired = (type: string) => ['A', 'B', 'C'].includes(type);
const endRequired = (type: string) => ['C', 'P'].includes(type);
const endForbidden = (type: string) => ['A', 'B'].includes(type);

interface Form {
  clientNumber: string;
  clientLocnCode: string;
  /** Text typed in the client box without picking a client. */
  clientName: string;
  fileClientType: string;
  start: string;
  end: string;
}

type Errors = Partial<Record<keyof Form, string>>;

const EMPTY: Form = {
  clientNumber: '',
  clientLocnCode: '',
  clientName: '',
  fileClientType: '',
  start: '',
  end: '',
};

/** The legacy form's and save_pmc/save_ffc's field checks; the backend repeats them. */
const validate = (f: Form, hasMainLicensee: boolean): Errors => {
  const e: Errors = {};
  if (!f.clientNumber) {
    e.clientName = f.clientName ? 'Pick a client from the list.' : 'Client is required.';
  }
  if (!f.fileClientType) e.fileClientType = 'Client type is required.';
  else {
    if (!hasMainLicensee && f.fileClientType !== 'A')
      e.fileClientType = 'Add the Main Licensee (A) first.';
    if (startRequired(f.fileClientType) && !f.start)
      e.start = `Required for type ${f.fileClientType}.`;
    if (endRequired(f.fileClientType) && !f.end) e.end = `Required for type ${f.fileClientType}.`;
    if (endForbidden(f.fileClientType) && f.end) e.end = 'Must be blank for a licensee (A or B).';
    if (f.fileClientType === 'M' && f.start && !f.end)
      e.end = 'Required when a start date is given.';
  }
  if (f.end && !f.start && !e.start) e.start = 'Required when an end date is given.';
  if (f.start && f.end && f.start > f.end) e.end = 'Must be on or after the start date.';
  return e;
};

interface Props {
  /** The detail route's id: a timber mark, or a certificate with `byCertificate`. */
  id: string;
  byCertificate: boolean;
  rows: MarkAssociatedClient[];
  /** Whether the user may add (the mark's `editRules.clients`). */
  canAdd: boolean;
  /** Why not, when `canAdd` is false — shown beside the disabled button. */
  disabledReason: string | null;
  /** Called after an add, to re-read the mark. */
  onAdded: () => void;
}

/**
 * The Associated clients tab of the private-mark detail — legacy FTA513. Laid
 * out exactly as the Land index tab (nr-fsp-new's Attachments pattern): an empty
 * state, or the table with an Add button above it, and a small modal to add.
 *
 * Who may add, and when, is the backend's `editRules.clients` (FTA_513: HI, PI
 * or PA only, not B15/B16); the backend enforces it and the field rules too.
 */
const ClientsPanel: FC<Props> = ({ id, byCertificate, rows, canAdd, disabledReason, onAdded }) => {
  const { display } = useNotification();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<Form>(EMPTY);
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);
  const [types, setTypes] = useState<CodeOption[]>([]);
  const [typesLoading, setTypesLoading] = useState(false);
  const hasMainLicensee = rows.some((r) => r.fileClientType === 'A');

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setTypesLoading(true);
    getFileClientTypes()
      .then((t) => {
        if (!cancelled) setTypes(t);
      })
      .catch(() => {
        if (!cancelled) {
          display({
            kind: 'error',
            title: 'Could not load the client types',
            subtitle: 'Close the dialog and try again.',
            timeout: 6000,
          });
        }
      })
      .finally(() => {
        if (!cancelled) setTypesLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [open, display]);

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
    // The first client must be the Main Licensee, so preselect it.
    setForm({ ...EMPTY, fileClientType: hasMainLicensee ? '' : 'A' });
    setErrors({});
    setOpen(true);
  };

  const closeDialog = () => {
    if (!saving) setOpen(false);
  };

  const submit = async () => {
    const found = validate(form, hasMainLicensee);
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    setSaving(true);
    try {
      const { note } = await addClient(
        id,
        {
          clientNumber: form.clientNumber,
          clientLocnCode: form.clientLocnCode,
          fileClientType: form.fileClientType,
          licenseeStartDate: form.start || null,
          licenseeEndDate: form.end || null,
        },
        byCertificate,
      );
      display({ kind: 'success', title: 'Client added', subtitle: note, timeout: 7000 });
      setOpen(false);
      onAdded();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not add the client',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  const type = form.fileClientType;
  const dateInput = (key: 'start' | 'end', label: string, disabled = false) => (
    <DatePicker
      datePickerType="single"
      dateFormat="Y-m-d"
      className="detail-dialog__date"
      value={form[key]}
      invalid={!!errors[key]}
      onChange={(dates: Date[]) => set(key, dates[0] ? toIsoDate(dates[0]) : '')}
    >
      <DatePickerInput
        id={`client-${key}`}
        labelText={label}
        placeholder="yyyy-mm-dd"
        invalidText={errors[key]}
        disabled={saving || disabled}
        onChange={(e) => {
          if (e.target.value.trim() === '') set(key, '');
        }}
      />
    </DatePicker>
  );

  return (
    <div className="fsp-info__tab-panel">
      {rows.length === 0 ? (
        <EmptyState
          icon={<UserFollow size={48} />}
          title="No clients for this mark"
          body="Add the Main Licensee first, then any other licensees or agents."
          action={
            // Always shown; disabled, with the reason, when adding isn't allowed.
            <div className="detail-tab__empty-action">
              <Button kind="primary" renderIcon={Add} disabled={!canAdd} onClick={openDialog}>
                Add client
              </Button>
              {!canAdd && disabledReason && <p className="detail-tab__reason">{disabledReason}</p>}
            </div>
          }
        />
      ) : (
        <div>
          <header className="detail-tab__actions">
            {!canAdd && disabledReason && <p className="detail-tab__reason">{disabledReason}</p>}
            <Button
              kind="tertiary"
              size="sm"
              renderIcon={Add}
              disabled={!canAdd}
              onClick={openDialog}
            >
              Add client
            </Button>
          </header>
          <div className="bordered-table">
            <TableContainer>
              <Table size="md" useZebraStyles>
                <TableHead>
                  <TableRow>
                    <TableHeader>Client #</TableHeader>
                    <TableHeader>Location</TableHeader>
                    <TableHeader>Name</TableHeader>
                    <TableHeader>City</TableHeader>
                    <TableHeader>Type</TableHeader>
                    <TableHeader>Start Date</TableHeader>
                    <TableHeader>End Date</TableHeader>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((c) => (
                    <TableRow
                      key={
                        c.forClientLinkSkey ??
                        `${c.clientNumber}-${c.clientLocnCode}-${c.fileClientType}`
                      }
                    >
                      <TableCell>{dash(c.clientNumber)}</TableCell>
                      <TableCell>{dash(c.clientLocnCode)}</TableCell>
                      <TableCell>{dash(c.clientName)}</TableCell>
                      <TableCell>{dash(c.clientCity)}</TableCell>
                      <TableCell>{dash(c.fileClientTypeDesc ?? c.fileClientType)}</TableCell>
                      <TableCell>{dash(formatDate(c.licenseeStartDt))}</TableCell>
                      <TableCell>{dash(formatDate(c.licenseeEndDate))}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </div>
        </div>
      )}

      {/* Add dialog — the Land index dialog's shape. */}
      <Modal
        open={open}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading="Add client"
        onRequestClose={closeDialog}
        preventCloseOnClickOutside
      >
        <Stack gap={5}>
          <p className="detail-dialog__subtitle">All fields are required unless marked optional.</p>
          <ClientComboBox
            id="client-picker"
            titleText="Client"
            helperText="Pick the client and location."
            clientNumber={form.clientNumber}
            clientLocnCode={form.clientLocnCode}
            clientName={form.clientName}
            invalid={!!errors.clientName}
            invalidText={errors.clientName}
            disabled={saving}
            onChange={({ clientNumber, clientLocnCode, clientName }) => {
              set('clientNumber', clientNumber);
              set('clientLocnCode', clientLocnCode);
              set('clientName', clientName);
            }}
          />
          <Select
            id="client-type"
            labelText="Client type"
            value={type}
            helperText={
              !hasMainLicensee
                ? 'The first client must be the Main Licensee (A).'
                : type === 'A'
                  ? 'Adding a Main Licensee makes the current one the previous licensee (C).'
                  : undefined
            }
            invalid={!!errors.fileClientType}
            invalidText={errors.fileClientType}
            disabled={saving || typesLoading}
            onChange={(e) => {
              set('fileClientType', e.target.value);
              // A licensee takes no end date.
              if (endForbidden(e.target.value)) set('end', '');
            }}
          >
            <SelectItem value="" text={typesLoading ? 'Loading…' : '— Select client type —'} />
            {types.map((o) => (
              <SelectItem key={o.code} value={o.code} text={o.description || o.code} />
            ))}
          </Select>
          <div className="detail-dialog__pair">
            {dateInput(
              'start',
              type && !startRequired(type) ? 'Start date (optional)' : 'Start date',
            )}
            {dateInput(
              'end',
              type && endRequired(type) ? 'End date' : 'End date (optional)',
              endForbidden(type),
            )}
          </div>
        </Stack>
        <div className="detail-dialog__actions">
          <Button kind="tertiary" disabled={saving} onClick={closeDialog}>
            Cancel
          </Button>
          <Button kind="primary" disabled={saving} onClick={() => void submit()}>
            {saving ? 'Adding…' : 'Add client'}
          </Button>
        </div>
      </Modal>
    </div>
  );
};

export default ClientsPanel;
