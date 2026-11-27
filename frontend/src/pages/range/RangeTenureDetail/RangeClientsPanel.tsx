import { UserFollow, UserMultiple } from '@carbon/icons-react';
import {
  Button,
  DatePicker,
  DatePickerInput,
  InlineNotification,
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
import ClientComboBox from '@/components/ClientComboBox';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import { safeErrorMessage } from '@/lib/errorMessage';
import {
  assignRangeClient,
  getRangeClients,
  getRelatedClients,
  type RangeClient,
  type RelatedClient,
} from '@/services/range_tenure_clients';
import { formatDate } from '@/utils/formatDate';
import { parseTypedDate, TYPED_DATE_PATTERN } from '@/utils/typedDate';

import { dash } from './format';

import type { RangePanelProps } from './panelProps';

/** yyyy-mm-dd in local time. */
const toIsoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

/** Legacy's "Client #-Location". */
const clientId = (c: { clientNumber: string | null; clientLocnCode: string | null }) =>
  c.clientNumber ? [c.clientNumber, c.clientLocnCode].filter(Boolean).join(' - ') : '—';

/** Legacy's "Client Type": "A - Licensee". */
const clientType = (c: RangeClient) =>
  c.fileClientType
    ? [c.fileClientType, c.fileClientTypeDesc].filter(Boolean).join(' - ')
    : dash(c.fileClientTypeDesc);

interface Form {
  clientNumber: string;
  clientLocnCode: string;
  /** Text typed in the client box without picking a client. */
  clientName: string;
  start: string;
  end: string;
}

const EMPTY: Form = { clientNumber: '', clientLocnCode: '', clientName: '', start: '', end: '' };

/** The client the last Get was for, and what it returned. */
interface Got {
  clientNumber: string;
  clientLocnCode: string;
  rows: RelatedClient[];
}

/**
 * The Assoc. Client tab — legacy FTA920R (Range Associated Clients).
 *
 * "Licensee (Current & Previous)" lists every client of the file, current licensees first.
 * Assign/Transfer to Client: pick a client and location, Get shows it as the licensee (A) with
 * its joint-venture partners (B) from the client system, and Save makes them the file's
 * licensees: the current A and B become C and P, ending on the Previous Licensee End Date (the
 * Licensee Start Date when blank). Save is enabled when the backend says so (`canEdit`: the
 * file's district, and no application in the Inbox); the backend checks it all again.
 */
const RangeClientsPanel: FC<RangePanelProps> = ({ tenure, canEdit, onTenureChanged }) => {
  const forestFileId = tenure.forestFileId;
  const { display } = useNotification();
  const fetcher = useCallback(() => getRangeClients(forestFileId), [forestFileId]);
  const { data, loading, error, reload } = useApiResource(fetcher, [forestFileId]);
  const allowed = canEdit && !!data?.canEdit;
  const rows = data?.rows;

  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<Form>(EMPTY);
  const [clientError, setClientError] = useState<string | undefined>();
  const [startError, setStartError] = useState<string | undefined>();
  const [got, setGot] = useState<Got | null>(null);
  const [getting, setGetting] = useState(false);
  const [saving, setSaving] = useState(false);
  const busy = getting || saving;

  const openDialog = () => {
    setForm(EMPTY);
    setClientError(undefined);
    setStartError(undefined);
    setGot(null);
    setOpen(true);
  };

  const closeDialog = () => {
    if (!busy) setOpen(false);
  };

  const get = async () => {
    if (!form.clientNumber || !form.clientLocnCode) {
      setClientError('Client Number is mandatory.');
      return;
    }
    setGetting(true);
    setGot(null);
    try {
      const related = await getRelatedClients(forestFileId, form.clientNumber, form.clientLocnCode);
      setGot({
        clientNumber: form.clientNumber,
        clientLocnCode: form.clientLocnCode,
        rows: related,
      });
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not get the client.',
        subtitle: safeErrorMessage(err),
        timeout: 0,
      });
    } finally {
      setGetting(false);
    }
  };

  const save = async () => {
    if (!got) return;
    if (!form.start) {
      setStartError('Licensee Start Date is mandatory.');
      return;
    }
    setSaving(true);
    try {
      const { message } = await assignRangeClient(forestFileId, {
        clientNumber: got.clientNumber,
        clientLocnCode: got.clientLocnCode,
        licenseeStartDate: form.start,
        previousLicenseeEndDate: form.end || null,
      });
      display({ kind: 'success', title: message, timeout: 6000 });
      setOpen(false);
      reload();
      // The header shows the licensee.
      onTenureChanged();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not save.',
        subtitle: safeErrorMessage(err),
        timeout: 0,
      });
    } finally {
      setSaving(false);
    }
  };

  const setDate = (key: 'start' | 'end', value: string) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    if (key === 'start') setStartError(undefined);
  };

  const dateInput = (key: 'start' | 'end', label: string) => (
    <DatePicker
      datePickerType="single"
      dateFormat="Y-m-d"
      className="detail-dialog__date"
      value={form[key]}
      invalid={key === 'start' && !!startError}
      onChange={(dates: Date[]) => setDate(key, dates[0] ? toIsoDate(dates[0]) : '')}
    >
      <DatePickerInput
        id={`range-client-${key}`}
        labelText={label}
        placeholder="yyyy-mm-dd"
        invalidText={key === 'start' ? startError : undefined}
        disabled={busy}
        pattern={TYPED_DATE_PATTERN}
        onChange={(e) => {
          const text = e.target.value;
          if (text.trim() === '') setDate(key, '');
          else {
            const typed = parseTypedDate(text);
            if (typed) setDate(key, typed);
          }
        }}
      />
    </DatePicker>
  );

  const assignButton = (primary: boolean) => (
    <Button
      kind={primary ? 'primary' : 'tertiary'}
      size={primary ? undefined : 'sm'}
      renderIcon={UserFollow}
      disabled={!allowed}
      onClick={openDialog}
    >
      Assign/Transfer to Client
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
        {data && rows && (
          <Stack gap={5}>
            <InlineNotification
              kind="info"
              lowContrast
              hideCloseButton
              title="Any change requires new legal documentation to be produced."
            />
            {data.warnings.map((w) => (
              <InlineNotification
                key={w}
                kind="warning"
                lowContrast
                hideCloseButton
                title="Warning:"
                subtitle={w}
              />
            ))}
            {rows.length === 0 ? (
              <EmptyState
                icon={<UserMultiple size={48} />}
                title="No records found"
                body="This range tenure has no licensee."
                action={<div className="detail-tab__empty-action">{assignButton(true)}</div>}
              />
            ) : (
              <div>
                <header className="detail-tab__actions">{assignButton(false)}</header>
                <div className="bordered-table">
                  <TableContainer
                    title="Licensee (Current & Previous)"
                    description={`${rows.length} row(s)`}
                  >
                    <Table size="md" useZebraStyles>
                      <TableHead>
                        <TableRow>
                          <TableHeader>Client # - Location</TableHeader>
                          <TableHeader>Name</TableHeader>
                          <TableHeader>Address</TableHeader>
                          <TableHeader>Phone-Fax</TableHeader>
                          <TableHeader>eMail</TableHeader>
                          <TableHeader>Client Type</TableHeader>
                          <TableHeader>Licensee Start Date</TableHeader>
                          <TableHeader>Licensee End Date</TableHeader>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {rows.map((c, i) => (
                          <TableRow key={c.forestFileClientSkey ?? `row-${i}`}>
                            <TableCell>{clientId(c)}</TableCell>
                            <TableCell>{dash(c.clientName)}</TableCell>
                            <TableCell>{dash(c.clientAddress)}</TableCell>
                            <TableCell>{dash(c.clientPhoneFax)}</TableCell>
                            <TableCell>{dash(c.clientEmail)}</TableCell>
                            <TableCell>{clientType(c)}</TableCell>
                            <TableCell>{dash(formatDate(c.licenseeStartDate))}</TableCell>
                            <TableCell>{dash(formatDate(c.licenseeEndDate))}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </div>
              </div>
            )}
          </Stack>
        )}
      </AsyncBoundary>

      {/* Assign/Transfer to Client — legacy's section of the same name, with its Get and Save. */}
      <Modal
        open={open}
        passiveModal
        size="lg"
        className="detail-dialog"
        modalHeading="Assign/Transfer to Client"
        onRequestClose={closeDialog}
        preventCloseOnClickOutside
      >
        <Stack gap={5}>
          <div className="detail-dialog__grid">
            <div className="detail-dialog__full">
              <ClientComboBox
                id="range-client-picker"
                inDialog
                titleText="Client"
                helperText="Pick the client and location."
                clientNumber={form.clientNumber}
                clientLocnCode={form.clientLocnCode}
                clientName={form.clientName}
                invalid={!!clientError}
                invalidText={clientError}
                disabled={busy}
                onChange={({ clientNumber, clientLocnCode, clientName }) => {
                  setForm((prev) => ({ ...prev, clientNumber, clientLocnCode, clientName }));
                  setClientError(undefined);
                  // Save is for the client Get showed.
                  setGot(null);
                }}
              />
            </div>
            {dateInput('start', 'Licensee Start Date')}
            {dateInput('end', 'Previous Licensee End Date (optional)')}
          </div>
          <div>
            <Button kind="tertiary" disabled={busy} onClick={() => void get()}>
              {getting ? 'Getting…' : 'Get'}
            </Button>
          </div>
          {got && (
            <div className="bordered-table">
              <TableContainer
                title="Selected Clients from the Client System"
                description={`${got.rows.length} row(s)`}
              >
                <Table size="md" useZebraStyles>
                  <TableHead>
                    <TableRow>
                      <TableHeader>Client # - Location</TableHeader>
                      <TableHeader>Name</TableHeader>
                      <TableHeader>Address</TableHeader>
                      <TableHeader>Phone-Fax</TableHeader>
                      <TableHeader>eMail</TableHeader>
                      <TableHeader>Client Type</TableHeader>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {got.rows.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={6}>No records found</TableCell>
                      </TableRow>
                    ) : (
                      got.rows.map((c) => (
                        <TableRow key={`${c.clientNumber ?? ''}-${c.clientLocnCode ?? ''}`}>
                          <TableCell>{clientId(c)}</TableCell>
                          <TableCell>{dash(c.clientName)}</TableCell>
                          <TableCell>{dash(c.clientAddress)}</TableCell>
                          <TableCell>{dash(c.clientPhoneFax)}</TableCell>
                          <TableCell>{dash(c.clientEmail)}</TableCell>
                          <TableCell>{dash(c.fileClientTypeDesc ?? c.fileClientType)}</TableCell>
                        </TableRow>
                      ))
                    )}
                  </TableBody>
                </Table>
              </TableContainer>
            </div>
          )}
        </Stack>
        <div className="detail-dialog__actions">
          <Button kind="tertiary" disabled={busy} onClick={closeDialog}>
            Cancel
          </Button>
          <Button
            kind="primary"
            disabled={busy || !allowed || !got || got.rows.length === 0}
            onClick={() => void save()}
          >
            {saving ? 'Saving…' : 'Save'}
          </Button>
        </div>
      </Modal>
    </div>
  );
};

export default RangeClientsPanel;
