import { Add, UserMultiple } from '@carbon/icons-react';
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
} from '@carbon/react';
import { useCallback, useEffect, useState, type FC } from 'react';

import AsyncBoundary from '@/components/AsyncBoundary';
import ClientComboBox from '@/components/ClientComboBox';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import { getFileClientTypes, type CodeOption } from '@/services/codeLists';
import {
  addTenureAssociatedClient,
  deleteTenureAssociatedClient,
  getTenureAssociatedClients,
  updateTenureAssociatedClient,
  type TenureAssociatedClient,
} from '@/services/tenure_assocclients';
import { formatDate } from '@/utils/formatDate';
import { parseTypedDate, TYPED_DATE_PATTERN } from '@/utils/typedDate';

import type { TenurePanelProps } from './panelProps';

const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;

/** yyyy-mm-dd in local time. */
const toIsoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

/** FTA_920's date rules by client type: which dates a type needs, and which it must not have. */
const startRequired = (type: string) => ['A', 'B', 'C'].includes(type);
const endRequired = (type: string) => ['C', 'P'].includes(type);
const endForbidden = (type: string) => ['A', 'B'].includes(type);
/** A Main (A) or Previous (C) licensee keeps its client number and type. */
const identityLocked = (type: string | null | undefined) => type === 'A' || type === 'C';

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

/** Fta920AssoClientsForm's and the PL/SQL's field checks; the backend repeats them all. */
const validate = (
  f: Form,
  hasOtherMainLicensee: boolean,
  editing: TenureAssociatedClient | null,
): Errors => {
  const e: Errors = {};
  const type = f.fileClientType;
  if (!f.clientNumber) {
    e.clientName = f.clientName ? 'Pick a client from the list.' : 'Client is required.';
  } else if (
    editing &&
    identityLocked(editing.fileClientType) &&
    f.clientNumber !== editing.clientNumber
  ) {
    e.clientName =
      'Cannot change the Client Number for Main or Previous Licensee (A or C type). To change the Main Licensee, add the new one.';
  }
  if (!type) e.fileClientType = 'Client Type is mandatory.';
  else {
    if (type === 'O')
      e.fileClientType = 'O-Type clients can only be attached at the Cut Block level.';
    else if (!editing && !hasOtherMainLicensee && type !== 'A')
      e.fileClientType = 'At least one Main Licensee (A type) required — add it first.';
    if (startRequired(type) && !f.start) e.start = 'Licensee Start Date is required.';
    if (endRequired(type) && !f.end) e.end = 'Licensee End Date is required.';
    if (endForbidden(type) && f.end) e.end = 'Licensee End Date must be blank.';
    if (type === 'M' && f.start && !f.end)
      e.end = 'Licensee End Date must be entered when Licensee Start Date is.';
  }
  if (f.end && !f.start && !e.start)
    e.start = 'If Licensee Start Date is blank, Licensee End Date must be blank.';
  if (f.start && f.end && f.start > f.end && !e.end)
    e.end = 'Licensee Start Date must be less than or equal to Licensee End Date.';
  return e;
};

/**
 * The Associated clients tab of the tenure detail — legacy FTA920 (Associated Clients) at the
 * file level: the tenure's Main Licensee (A), licensees, previous licensees (C) and other
 * clients. Laid out as the private mark's Clients tab, with an Edit/Delete row menu.
 *
 * A new Main Licensee makes the current one the previous licensee. Legacy lets only a
 * previous licensee (C) or an S client be deleted; a Main or Previous Licensee keeps its client
 * number and type. Who may write, and when, is the backend's (`canEdit`, `editReason`); it
 * enforces every rule again.
 */
const AssociatedClientsPanel: FC<TenurePanelProps> = ({ tenure, canEdit, onTenureChanged }) => {
  const forestFileId = tenure.forestFileId;
  const { display } = useNotification();
  const fetcher = useCallback(() => getTenureAssociatedClients(forestFileId), [forestFileId]);
  const { data, loading, error, reload } = useApiResource(fetcher, [forestFileId]);

  const allowed = canEdit && !!data?.canEdit;

  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<TenureAssociatedClient | null>(null);
  const [form, setForm] = useState<Form>(EMPTY);
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);
  const [types, setTypes] = useState<CodeOption[] | null>(null);
  const [pendingDelete, setPendingDelete] = useState<TenureAssociatedClient | null>(null);
  const [deleting, setDeleting] = useState(false);

  const rows = data?.rows;
  const hasOtherMainLicensee = (rows ?? []).some(
    (r) => r.fileClientType === 'A' && r.forestFileClientSkey !== editing?.forestFileClientSkey,
  );

  useEffect(() => {
    if (!open || types) return;
    let cancelled = false;
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
      });
    return () => {
      cancelled = true;
    };
  }, [open, types, display]);

  const set = <K extends keyof Form>(key: K, value: Form[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    setErrors((prev) => {
      if (!prev[key]) return prev;
      const next = { ...prev };
      delete next[key];
      return next;
    });
  };

  const openAdd = () => {
    const anyMain = (rows ?? []).some((r) => r.fileClientType === 'A');
    // The first client must be the Main Licensee, so preselect it.
    setEditing(null);
    setForm({ ...EMPTY, fileClientType: anyMain ? '' : 'A' });
    setErrors({});
    setOpen(true);
  };

  const openEdit = (r: TenureAssociatedClient) => {
    setEditing(r);
    setForm({
      clientNumber: r.clientNumber ?? '',
      clientLocnCode: r.clientLocnCode ?? '',
      clientName: '',
      fileClientType: r.fileClientType ?? '',
      start: r.licenseeStartDate ?? '',
      end: r.licenseeEndDate ?? '',
    });
    setErrors({});
    setOpen(true);
  };

  const closeDialog = () => {
    if (!saving) setOpen(false);
  };

  const submit = async () => {
    const found = validate(form, hasOtherMainLicensee, editing);
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    setSaving(true);
    try {
      const request = {
        clientNumber: form.clientNumber,
        clientLocnCode: form.clientLocnCode,
        fileClientType: form.fileClientType,
        licenseeStartDate: form.start || null,
        licenseeEndDate: form.end || null,
      };
      const { note } = editing
        ? await updateTenureAssociatedClient(forestFileId, editing.forestFileClientSkey, {
            ...request,
            revisionCount: editing.revisionCount,
          })
        : await addTenureAssociatedClient(forestFileId, request);
      display({
        kind: 'success',
        title: editing ? 'Client updated' : 'Client added',
        subtitle: note ?? undefined,
        timeout: note ? 9000 : 6000,
      });
      setOpen(false);
      reload();
      // The header shows the Main Licensee.
      if (form.fileClientType === 'A' || editing?.fileClientType === 'A') onTenureChanged();
    } catch (err) {
      display({
        kind: 'error',
        title: editing ? 'Could not update the client' : 'Could not add the client',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  const confirmDelete = async () => {
    if (!pendingDelete) return;
    setDeleting(true);
    try {
      await deleteTenureAssociatedClient(forestFileId, pendingDelete);
      display({
        kind: 'success',
        title: `Client ${pendingDelete.clientNumber ?? ''} deleted`,
        timeout: 6000,
      });
      setPendingDelete(null);
      reload();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not delete the client',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setDeleting(false);
    }
  };

  const type = form.fileClientType;
  const typesLoading = open && !types;
  const typeLocked = !!editing && identityLocked(editing.fileClientType);

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
        id={`tenure-client-${key}`}
        labelText={label}
        placeholder="yyyy-mm-dd"
        invalidText={errors[key]}
        disabled={saving || disabled}
        pattern={TYPED_DATE_PATTERN}
        onChange={(e) => {
          const text = e.target.value;
          if (text.trim() === '') set(key, '');
          else {
            const typed = parseTypedDate(text);
            if (typed) set(key, typed);
          }
        }}
      />
    </DatePicker>
  );

  const typeHelper = typeLocked
    ? "A Main or Previous Licensee's type cannot change."
    : !editing && !hasOtherMainLicensee
      ? 'The first client must be the Main Licensee (A).'
      : type === 'A' && hasOtherMainLicensee
        ? 'The current Main Licensee becomes the previous licensee (C).'
        : undefined;

  const addButton = (primary: boolean) => (
    <Button
      kind={primary ? 'primary' : 'tertiary'}
      size={primary ? undefined : 'sm'}
      renderIcon={Add}
      disabled={!allowed}
      onClick={openAdd}
    >
      Add client
    </Button>
  );

  return (
    <div className="fsp-info__tab-panel">
      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Loading clients…"
      >
        {rows &&
          (rows.length === 0 ? (
            <EmptyState
              icon={<UserMultiple size={48} />}
              title="No clients for this tenure"
              body="Add the Main Licensee first, then any other licensees or agents."
              action={<div className="detail-tab__empty-action">{addButton(true)}</div>}
            />
          ) : (
            <div>
              <header className="detail-tab__actions">{addButton(false)}</header>
              <div className="bordered-table">
                <TableContainer>
                  <Table size="md" useZebraStyles>
                    <TableHead>
                      <TableRow>
                        <TableHeader>Client #</TableHeader>
                        <TableHeader>Location</TableHeader>
                        <TableHeader>Name</TableHeader>
                        <TableHeader>Type</TableHeader>
                        <TableHeader>Start Date</TableHeader>
                        <TableHeader>End Date</TableHeader>
                        <TableHeader aria-label="Actions" />
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {rows.map((c) => (
                        <TableRow key={c.forestFileClientSkey}>
                          <TableCell>{dash(c.clientNumber)}</TableCell>
                          <TableCell>{dash(c.clientLocnCode)}</TableCell>
                          <TableCell>{dash(c.clientName)}</TableCell>
                          <TableCell>{dash(c.fileClientTypeDesc ?? c.fileClientType)}</TableCell>
                          <TableCell>{dash(formatDate(c.licenseeStartDate))}</TableCell>
                          <TableCell>{dash(formatDate(c.licenseeEndDate))}</TableCell>
                          <TableCell>
                            <OverflowMenu
                              size="sm"
                              flipped
                              iconDescription={`Client ${c.clientNumber ?? ''} actions`}
                            >
                              <OverflowMenuItem
                                itemText="Edit"
                                disabled={!allowed}
                                onClick={() => openEdit(c)}
                              />
                              <OverflowMenuItem
                                itemText="Delete"
                                isDelete
                                hasDivider
                                disabled={!canEdit || !c.canDelete}
                                onClick={() => setPendingDelete(c)}
                              />
                            </OverflowMenu>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </div>
              {canEdit && data?.canEdit && (
                <p className="fsp-info__toolbar-note">
                  Only a previous licensee (C) or an S type client can be deleted.
                </p>
              )}
            </div>
          ))}
      </AsyncBoundary>

      {/* Add / edit dialog — the private mark's client dialog. */}
      <Modal
        open={open}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading={editing ? 'Edit client' : 'Add client'}
        onRequestClose={closeDialog}
        preventCloseOnClickOutside
      >
        <Stack gap={5}>
          <p className="detail-dialog__subtitle">All fields are required unless marked optional.</p>
          <ClientComboBox
            id="tenure-client-picker"
            titleText="Client"
            helperText={
              typeLocked
                ? 'Only the location can change for a Main or Previous Licensee.'
                : 'Pick the client and location.'
            }
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
            id="tenure-client-type"
            labelText="Client type"
            value={type}
            helperText={typeHelper}
            invalid={!!errors.fileClientType}
            invalidText={errors.fileClientType}
            disabled={saving || typesLoading || typeLocked}
            onChange={(e) => {
              set('fileClientType', e.target.value);
              // A licensee takes no end date.
              if (endForbidden(e.target.value)) set('end', '');
            }}
          >
            <SelectItem value="" text={typesLoading ? 'Loading…' : '— Select client type —'} />
            {/* An existing row's type may since have expired; keep it selectable. */}
            {editing &&
              editing.fileClientType &&
              !(types ?? []).some((o) => o.code === editing.fileClientType) && (
                <SelectItem
                  value={editing.fileClientType}
                  text={editing.fileClientTypeDesc ?? editing.fileClientType}
                />
              )}
            {(types ?? []).map((o) => (
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
            {saving ? (editing ? 'Saving…' : 'Adding…') : editing ? 'Save changes' : 'Add client'}
          </Button>
        </div>
      </Modal>

      {/* Delete confirmation. */}
      <Modal
        open={!!pendingDelete}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading="Delete client?"
        onRequestClose={() => {
          if (!deleting) setPendingDelete(null);
        }}
        preventCloseOnClickOutside
      >
        {pendingDelete && (
          <p className="detail-dialog__subtitle">
            {`This removes ${pendingDelete.clientNumber ?? ''} ${pendingDelete.clientLocnCode ?? ''}${
              pendingDelete.clientName ? ` (${pendingDelete.clientName})` : ''
            }, ${pendingDelete.fileClientTypeDesc ?? pendingDelete.fileClientType ?? ''}, from this tenure.`}
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

export default AssociatedClientsPanel;
