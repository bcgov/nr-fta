import {
  Add,
  Currency,
  DocumentPdf,
  Enterprise,
  Road,
  type CarbonIconType,
} from '@carbon/icons-react';
import {
  Button,
  Checkbox,
  DatePicker,
  DatePickerInput,
  FileUploaderDropContainer,
  FileUploaderItem,
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
import { useEffect, useState, type FC, type ReactNode } from 'react';

import { EmptyState } from '@/components/EmptyState/EmptyState';
import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import type { CodeOption } from '@/services/codeLists';
import {
  addRecAccess,
  addRecAttachment,
  addRecDistrict,
  addRecFee,
  deleteRecAccess,
  deleteRecAttachment,
  deleteRecFee,
  getRecAttachment,
  getRecProjectLookups,
  removeRecDistrict,
  updateRecFee,
  type RecProject,
  type RecProjectFee,
  type RecProjectLookups,
} from '@/services/tenure_recproject';
import { formatDate } from '@/utils/formatDate';

const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;
const date = (v: string | null | undefined) => dash(formatDate(v));
const coded = (code: string, desc: string | null) => (desc ? `${code} - ${desc}` : code);
const money = new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' });

/** yyyy-mm-dd in local time. */
const toIsoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

const DAYS = [
  ['monday', 'Mon'],
  ['tuesday', 'Tue'],
  ['wednesday', 'Wed'],
  ['thursday', 'Thu'],
  ['friday', 'Fri'],
  ['saturday', 'Sat'],
  ['sunday', 'Sun'],
] as const;
type Day = (typeof DAYS)[number][0];

const feeDays = (f: RecProjectFee) => {
  const on = DAYS.filter(([k]) => f[k]).map(([, l]) => l);
  return on.length === 7 ? 'Every day' : on.length ? on.join(', ') : '—';
};

/** Legacy's limits: 4 MB (web.xml max-upload-file-size), PDF only. */
const MAX_PDF_BYTES = 4194304;

const fileSize = (bytes: number | null) =>
  bytes == null
    ? '—'
    : bytes < 1024 * 1024
      ? `${Math.max(1, Math.round(bytes / 1024))} KB`
      : `${(bytes / (1024 * 1024)).toFixed(1)} MB`;

const errMsg = (err: unknown) => (err instanceof Error ? err.message : 'Request failed');

/** A white section card holding a list — DetailTile's frame, a table for its fields. */
const Section: FC<{
  title: string;
  icon: CarbonIconType;
  action?: ReactNode;
  children: ReactNode;
}> = ({ title, icon: Icon, action, children }) => (
  <section className="fsp-info__tile">
    <header className="fsp-info__tile-header">
      <h2 className="fsp-info__section-title">
        <Icon size={20} />
        <span>{title}</span>
      </h2>
      {action}
    </header>
    {children}
  </section>
);

interface FeeForm {
  feeCode: string;
  amount: string;
  startDate: string;
  endDate: string;
  days: Record<Day, boolean>;
}

type FeeErrors = Partial<Record<'feeCode' | 'amount' | 'startDate' | 'endDate' | 'days', string>>;

const NO_DAYS: Record<Day, boolean> = {
  monday: false,
  tuesday: false,
  wednesday: false,
  thursday: false,
  friday: false,
  saturday: false,
  sunday: false,
};

/** Fta701MaintainProjectForm's SaveFee checks and validate_fee's day checks; the backend repeats them. */
const validateFee = (f: FeeForm): FeeErrors => {
  const e: FeeErrors = {};
  if (!f.feeCode) e.feeCode = 'Fee Type is required.';
  const amount = f.amount.trim();
  if (!amount) e.amount = 'Amount is required.';
  else if (!/^\d+(\.\d{1,2})?$/.test(amount) || Number(amount) > 999.99)
    e.amount = 'Enter 0.00 to 999.99.';
  if (!f.startDate) e.startDate = 'Start Date is required.';
  if (!f.endDate) e.endDate = 'End Date is required.';
  else if (f.startDate && f.startDate > f.endDate)
    e.endDate = 'Must be on or after the start date.';
  if (!Object.values(f.days).some(Boolean))
    e.days = 'You must specify at least one day of the week.';
  return e;
};

interface Confirm {
  title: string;
  body: string;
  action: string;
  run: () => Promise<void>;
  done: string;
}

interface Props {
  project: RecProject;
  forestFileId: string;
  canEdit: boolean;
  /** True while the project details are being edited: the lists' actions wait. */
  locked: boolean;
  onChanged: () => void;
}

/**
 * The lists beneath FTA701's project details — Recreation Districts, Fees,
 * Access and Establishment Order — each as the private-mark tabs' lists: a
 * table (or an empty state) with an Add button that is always shown and
 * disabled, with the business reason, when adding isn't allowed; row actions
 * in an overflow menu; small dialogs to add and confirm.
 *
 * Gates are the project's `rules`: everything needs the "child" rule (the
 * project saved, file HI, spatial approved), except removing a district, which
 * legacy gates by the project's own Save.
 */
const RecProjectSections: FC<Props> = ({ project, forestFileId, canEdit, locked, onChanged }) => {
  const { display } = useNotification();
  const rules = project.rules;
  const childAllowed = canEdit && rules.child && !locked;
  const childReason = canEdit && !rules.child ? rules.childReason : null;
  const removeDistrictAllowed = canEdit && rules.project && !locked;

  const [lookups, setLookups] = useState<RecProjectLookups | null>(null);
  const [needLookups, setNeedLookups] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!needLookups || lookups) return;
    let cancelled = false;
    getRecProjectLookups()
      .then((l) => {
        if (!cancelled) setLookups(l);
      })
      .catch(() => {
        if (!cancelled) {
          display({
            kind: 'error',
            title: 'Could not load the lists',
            subtitle: 'Close the dialog and try again.',
            timeout: 6000,
          });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [needLookups, lookups, display]);

  const listsLoading = needLookups && !lookups;
  const options = (items: CodeOption[] | undefined) =>
    (items ?? []).map((o) => (
      <SelectItem key={o.code} value={o.code} text={o.description || o.code} />
    ));

  /** Runs a write, toasts the outcome, re-reads the project on success. */
  const run = async (fn: () => Promise<void>, ok: string, fail: string): Promise<boolean> => {
    setBusy(true);
    try {
      await fn();
      display({ kind: 'success', title: ok, timeout: 5000 });
      onChanged();
      return true;
    } catch (err) {
      display({ kind: 'error', title: fail, subtitle: errMsg(err), timeout: 9000 });
      return false;
    } finally {
      setBusy(false);
    }
  };

  // ─── Confirm (delete / remove) ──────────────────────────────────────────

  const [confirm, setConfirm] = useState<Confirm | null>(null);
  const onConfirm = async () => {
    if (!confirm) return;
    if (await run(confirm.run, confirm.done, `Could not ${confirm.action.toLowerCase()}`)) {
      setConfirm(null);
    }
  };

  // ─── Districts ───────────────────────────────────────────────────────────

  const [districtOpen, setDistrictOpen] = useState(false);
  const [district, setDistrict] = useState('');
  const [districtError, setDistrictError] = useState<string | undefined>();
  const openDistrict = () => {
    setDistrict('');
    setDistrictError(undefined);
    setNeedLookups(true);
    setDistrictOpen(true);
  };
  const submitDistrict = async () => {
    if (!district) {
      setDistrictError('Recreation District is required.');
      return;
    }
    if (
      await run(
        () => addRecDistrict(forestFileId, district),
        'Recreation district added',
        'Could not add the district',
      )
    ) {
      setDistrictOpen(false);
    }
  };

  // ─── Fees ────────────────────────────────────────────────────────────────

  const [feeOpen, setFeeOpen] = useState(false);
  const [editingFee, setEditingFee] = useState<RecProjectFee | null>(null);
  const [fee, setFee] = useState<FeeForm>({
    feeCode: '',
    amount: '',
    startDate: '',
    endDate: '',
    days: NO_DAYS,
  });
  const [feeErrors, setFeeErrors] = useState<FeeErrors>({});
  const openFee = (row: RecProjectFee | null) => {
    setEditingFee(row);
    setFee(
      row
        ? {
            feeCode: row.feeCode,
            amount: row.amount == null ? '' : row.amount.toFixed(2),
            startDate: row.startDate ?? '',
            endDate: row.endDate ?? '',
            days: Object.fromEntries(DAYS.map(([k]) => [k, row[k]])) as Record<Day, boolean>,
          }
        : { feeCode: '', amount: '', startDate: '', endDate: '', days: NO_DAYS },
    );
    setFeeErrors({});
    setNeedLookups(true);
    setFeeOpen(true);
  };
  const setFeeField = <K extends keyof Omit<FeeForm, 'days'>>(key: K, value: string) => {
    setFee((f) => ({ ...f, [key]: value }));
    setFeeErrors((e) => ({ ...e, [key]: undefined }));
  };
  const submitFee = async () => {
    const found = validateFee(fee);
    if (Object.values(found).some(Boolean)) {
      setFeeErrors(found);
      return;
    }
    const request = {
      revisionCount: editingFee?.revisionCount ?? null,
      feeCode: fee.feeCode,
      amount: fee.amount.trim(),
      startDate: fee.startDate,
      endDate: fee.endDate,
      ...fee.days,
    };
    const ok = await run(
      () =>
        editingFee
          ? updateRecFee(forestFileId, editingFee.feeId, request)
          : addRecFee(forestFileId, request),
      editingFee ? 'Fee updated' : 'Fee added',
      editingFee ? 'Could not update the fee' : 'Could not add the fee',
    );
    if (ok) setFeeOpen(false);
  };

  // ─── Access ──────────────────────────────────────────────────────────────

  const [accessOpen, setAccessOpen] = useState(false);
  const [access, setAccess] = useState({ type: '', sub: '' });
  const [accessErrors, setAccessErrors] = useState<{ type?: string; sub?: string }>({});
  const openAccess = () => {
    setAccess({ type: '', sub: '' });
    setAccessErrors({});
    setNeedLookups(true);
    setAccessOpen(true);
  };
  // Legacy's access type / sub type filter: the sub types RECREATION_ACCESS_XREF allows.
  const subOptions = (lookups?.subAccessTypes ?? []).filter((o) =>
    lookups?.accessPairs.some((p) => p.accessCode === access.type && p.subAccessCode === o.code),
  );
  const submitAccess = async () => {
    const e: { type?: string; sub?: string } = {};
    if (!access.type) e.type = 'Access Type is required.';
    if (!access.sub) e.sub = 'Access Sub Type is required.';
    if (e.type || e.sub) {
      setAccessErrors(e);
      return;
    }
    if (
      await run(
        () => addRecAccess(forestFileId, access.type, access.sub),
        'Access type added',
        'Could not add the access type',
      )
    ) {
      setAccessOpen(false);
    }
  };

  // ─── Establishment orders ────────────────────────────────────────────────

  const [orderOpen, setOrderOpen] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | undefined>();
  const openOrder = () => {
    setFile(null);
    setFileError(undefined);
    setOrderOpen(true);
  };
  const pick = (f: File | undefined) => {
    setFile(f ?? null);
    if (!f) setFileError(undefined);
    else if (!f.name.toLowerCase().endsWith('.pdf'))
      setFileError('Establishment Order must be a PDF.');
    else if (f.size > MAX_PDF_BYTES) setFileError('The PDF can be at most 4 MB.');
    else if (f.name.length > 50) setFileError('The file name can be at most 50 characters.');
    else setFileError(undefined);
  };
  const submitOrder = async () => {
    if (!file) {
      setFileError('Choose a PDF to upload.');
      return;
    }
    if (fileError) return;
    if (
      await run(
        () => addRecAttachment(forestFileId, file),
        'Establishment order uploaded',
        'Could not upload the establishment order',
      )
    ) {
      setOrderOpen(false);
    }
  };
  const view = async (attachmentId: number, name: string) => {
    // Open the tab inside the click, or the browser blocks it as a popup.
    const tab = window.open('', '_blank');
    try {
      const pdf = await getRecAttachment(forestFileId, attachmentId);
      const url = URL.createObjectURL(pdf);
      if (tab) tab.location.href = url;
      else {
        const a = document.createElement('a');
        a.href = url;
        a.download = name;
        document.body.appendChild(a);
        a.click();
        a.remove();
      }
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err) {
      tab?.close();
      display({
        kind: 'error',
        title: 'Could not open the establishment order',
        subtitle: errMsg(err),
        timeout: 9000,
      });
    }
  };

  // ─── Shared pieces ───────────────────────────────────────────────────────

  const addButton = (label: string, onClick: () => void, primary: boolean) => (
    <Button
      kind={primary ? 'primary' : 'tertiary'}
      size={primary ? undefined : 'sm'}
      renderIcon={Add}
      disabled={!childAllowed || busy}
      onClick={onClick}
    >
      {label}
    </Button>
  );

  /** The header's Add button, with the reason beside it — not when the empty state carries it. */
  const headerAction = (empty: boolean, label: string, onClick: () => void) =>
    empty ? undefined : (
      <div className="detail-tab__actions">
        {childReason && <p className="detail-tab__reason">{childReason}</p>}
        {addButton(label, onClick, false)}
      </div>
    );

  const empty = (
    icon: ReactNode,
    title: string,
    body: string,
    label: string,
    onClick: () => void,
  ) => (
    <EmptyState
      icon={icon}
      title={title}
      body={body}
      action={
        <div className="detail-tab__empty-action">
          {addButton(label, onClick, true)}
          {childReason && <p className="detail-tab__reason">{childReason}</p>}
        </div>
      }
    />
  );

  const table = (headers: string[], rows: ReactNode) => (
    <div className="bordered-table">
      <TableContainer>
        <Table size="md" useZebraStyles>
          <TableHead>
            <TableRow>
              {headers.map((h) => (
                <TableHeader key={h}>{h}</TableHeader>
              ))}
              <TableHeader aria-label="Actions" />
            </TableRow>
          </TableHead>
          <TableBody>{rows}</TableBody>
        </Table>
      </TableContainer>
    </div>
  );

  const dialogActions = (label: string, onSubmit: () => void, onCancel: () => void) => (
    <div className="detail-dialog__actions">
      <Button kind="tertiary" disabled={busy} onClick={onCancel}>
        Cancel
      </Button>
      <Button kind="primary" disabled={busy} onClick={onSubmit}>
        {busy ? 'Saving…' : label}
      </Button>
    </div>
  );

  const { districts, fees, accesses, attachments } = project;

  return (
    <>
      {/* Recreation Districts */}
      <Section
        title="Recreation districts"
        icon={Enterprise}
        action={headerAction(districts.length === 0, 'Add district', openDistrict)}
      >
        {districts.length === 0
          ? empty(
              <Enterprise size={48} />,
              'No recreation districts',
              'Add the recreation districts this project falls in.',
              'Add district',
              openDistrict,
            )
          : table(
              ['Recreation District'],
              districts.map((d) => (
                <TableRow key={d.districtCode}>
                  <TableCell>{coded(d.districtCode, d.districtDesc)}</TableCell>
                  <TableCell>
                    <OverflowMenu
                      size="sm"
                      flipped
                      iconDescription={`District ${d.districtCode} actions`}
                    >
                      <OverflowMenuItem
                        itemText="Remove"
                        isDelete
                        disabled={!removeDistrictAllowed || busy}
                        onClick={() =>
                          setConfirm({
                            title: 'Remove recreation district',
                            body: `Remove ${coded(d.districtCode, d.districtDesc)} from the project?`,
                            action: 'Remove district',
                            done: 'Recreation district removed',
                            run: () => removeRecDistrict(forestFileId, d.districtCode),
                          })
                        }
                      />
                    </OverflowMenu>
                  </TableCell>
                </TableRow>
              )),
            )}
      </Section>

      {/* Fees */}
      <Section
        title="Fees"
        icon={Currency}
        action={headerAction(fees.length === 0, 'Add fee', () => openFee(null))}
      >
        {fees.length === 0
          ? empty(
              <Currency size={48} />,
              'No fees',
              'Add the fees charged at this project, by type, dates and days.',
              'Add fee',
              () => openFee(null),
            )
          : table(
              ['Fee Type', 'Amount', 'Start Date', 'End Date', 'Days'],
              fees.map((f) => (
                <TableRow key={f.feeId}>
                  <TableCell>{coded(f.feeCode, f.feeDesc)}</TableCell>
                  <TableCell>{f.amount == null ? '—' : money.format(f.amount)}</TableCell>
                  <TableCell>{date(f.startDate)}</TableCell>
                  <TableCell>{date(f.endDate)}</TableCell>
                  <TableCell>{feeDays(f)}</TableCell>
                  <TableCell>
                    <OverflowMenu size="sm" flipped iconDescription="Fee actions">
                      <OverflowMenuItem
                        itemText="Edit"
                        disabled={!childAllowed || busy}
                        onClick={() => openFee(f)}
                      />
                      <OverflowMenuItem
                        itemText="Delete"
                        isDelete
                        disabled={!childAllowed || busy}
                        onClick={() =>
                          setConfirm({
                            title: 'Delete fee',
                            body: `Delete the ${coded(f.feeCode, f.feeDesc)} fee of ${
                              f.amount == null ? '—' : money.format(f.amount)
                            } (${date(f.startDate)} to ${date(f.endDate)})?`,
                            action: 'Delete fee',
                            done: 'Fee deleted',
                            run: () => deleteRecFee(forestFileId, f.feeId, f.revisionCount),
                          })
                        }
                      />
                    </OverflowMenu>
                  </TableCell>
                </TableRow>
              )),
            )}
      </Section>

      {/* Access */}
      <Section
        title="Access"
        icon={Road}
        action={headerAction(accesses.length === 0, 'Add access type', openAccess)}
      >
        {accesses.length === 0
          ? empty(
              <Road size={48} />,
              'No access types',
              'Add how the project is reached: an access type and its sub type.',
              'Add access type',
              openAccess,
            )
          : table(
              ['Access Type', 'Access Sub Type'],
              accesses.map((a) => (
                <TableRow key={`${a.accessCode}-${a.subAccessCode}`}>
                  <TableCell>{coded(a.accessCode, a.accessDesc)}</TableCell>
                  <TableCell>{coded(a.subAccessCode, a.subAccessDesc)}</TableCell>
                  <TableCell>
                    <OverflowMenu size="sm" flipped iconDescription="Access type actions">
                      <OverflowMenuItem
                        itemText="Delete"
                        isDelete
                        disabled={!childAllowed || busy}
                        onClick={() =>
                          setConfirm({
                            title: 'Delete access type',
                            body: `Delete ${coded(a.accessCode, a.accessDesc)} / ${coded(
                              a.subAccessCode,
                              a.subAccessDesc,
                            )}?`,
                            action: 'Delete access type',
                            done: 'Access type deleted',
                            run: () => deleteRecAccess(forestFileId, a),
                          })
                        }
                      />
                    </OverflowMenu>
                  </TableCell>
                </TableRow>
              )),
            )}
      </Section>

      {/* Establishment Order */}
      <Section
        title="Establishment orders"
        icon={DocumentPdf}
        action={headerAction(attachments.length === 0, 'Upload order', openOrder)}
      >
        {attachments.length === 0
          ? empty(
              <DocumentPdf size={48} />,
              'No establishment orders',
              'Upload the project’s establishment order as a PDF.',
              'Upload order',
              openOrder,
            )
          : table(
              ['Establishment Order', 'Size'],
              attachments.map((a) => (
                <TableRow key={a.attachmentId}>
                  <TableCell>
                    <Button
                      kind="ghost"
                      size="sm"
                      renderIcon={DocumentPdf}
                      onClick={() => void view(a.attachmentId, a.fileName)}
                    >
                      {a.fileName}
                    </Button>
                  </TableCell>
                  <TableCell>{fileSize(a.sizeBytes)}</TableCell>
                  <TableCell>
                    <OverflowMenu size="sm" flipped iconDescription="Establishment order actions">
                      <OverflowMenuItem
                        itemText="Delete"
                        isDelete
                        disabled={!childAllowed || busy}
                        onClick={() =>
                          setConfirm({
                            title: 'Delete establishment order',
                            body: `Delete ${a.fileName}?`,
                            action: 'Delete order',
                            done: 'Establishment order deleted',
                            run: () => deleteRecAttachment(forestFileId, a.attachmentId),
                          })
                        }
                      />
                    </OverflowMenu>
                  </TableCell>
                </TableRow>
              )),
            )}
      </Section>

      {/* Add district */}
      <Modal
        open={districtOpen}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading="Add recreation district"
        onRequestClose={() => !busy && setDistrictOpen(false)}
        preventCloseOnClickOutside
      >
        <Stack gap={5}>
          <p className="detail-dialog__subtitle">All fields are required unless marked optional.</p>
          <Select
            id="rec-district"
            labelText="Recreation District"
            value={district}
            invalid={!!districtError}
            invalidText={districtError}
            disabled={busy || listsLoading}
            onChange={(e) => {
              setDistrict(e.target.value);
              setDistrictError(undefined);
            }}
          >
            <SelectItem value="" text={listsLoading ? 'Loading…' : '— Select district —'} />
            {options(
              lookups?.districts.filter((o) => !districts.some((d) => d.districtCode === o.code)),
            )}
          </Select>
        </Stack>
        {dialogActions(
          'Add district',
          () => void submitDistrict(),
          () => setDistrictOpen(false),
        )}
      </Modal>

      {/* Add / edit fee */}
      <Modal
        open={feeOpen}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading={editingFee ? 'Edit fee' : 'Add fee'}
        onRequestClose={() => !busy && setFeeOpen(false)}
        preventCloseOnClickOutside
      >
        <Stack gap={5}>
          <p className="detail-dialog__subtitle">All fields are required unless marked optional.</p>
          <div className="detail-dialog__pair">
            <Select
              id="rec-fee-type"
              labelText="Fee Type"
              value={fee.feeCode}
              invalid={!!feeErrors.feeCode}
              invalidText={feeErrors.feeCode}
              disabled={busy || listsLoading}
              onChange={(e) => setFeeField('feeCode', e.target.value)}
            >
              <SelectItem value="" text={listsLoading ? 'Loading…' : '— Select type —'} />
              {editingFee &&
                lookups &&
                !lookups.fees.some((o) => o.code === editingFee.feeCode) && (
                  <SelectItem
                    value={editingFee.feeCode}
                    text={coded(editingFee.feeCode, editingFee.feeDesc)}
                  />
                )}
              {options(lookups?.fees)}
            </Select>
            <TextInput
              id="rec-fee-amount"
              labelText="Amount ($)"
              inputMode="decimal"
              value={fee.amount}
              invalid={!!feeErrors.amount}
              invalidText={feeErrors.amount}
              disabled={busy}
              onChange={(e) => setFeeField('amount', e.target.value)}
            />
          </div>
          <div className="detail-dialog__pair">
            {(['startDate', 'endDate'] as const).map((key) => (
              <DatePicker
                key={key}
                className="detail-dialog__date"
                datePickerType="single"
                dateFormat="Y-m-d"
                value={fee[key]}
                invalid={!!feeErrors[key]}
                onChange={(dates: Date[]) => setFeeField(key, dates[0] ? toIsoDate(dates[0]) : '')}
              >
                <DatePickerInput
                  id={`rec-fee-${key}`}
                  labelText={key === 'startDate' ? 'Start Date' : 'End Date'}
                  placeholder="yyyy-mm-dd"
                  invalidText={feeErrors[key]}
                  disabled={busy}
                  onChange={(e) => {
                    if (e.target.value.trim() === '') setFeeField(key, '');
                  }}
                />
              </DatePicker>
            ))}
          </div>
          <fieldset className="detail-dialog__checks">
            <legend className="cds--label">Days</legend>
            {DAYS.map(([key, label]) => (
              <Checkbox
                key={key}
                id={`rec-fee-${key}`}
                labelText={label}
                checked={fee.days[key]}
                disabled={busy}
                onChange={(_, { checked }) => {
                  setFee((f) => ({ ...f, days: { ...f.days, [key]: checked } }));
                  setFeeErrors((e) => ({ ...e, days: undefined }));
                }}
              />
            ))}
          </fieldset>
          {feeErrors.days && (
            <p className="detail-tab__reason" style={{ color: 'var(--cds-text-error)' }}>
              {feeErrors.days}
            </p>
          )}
        </Stack>
        {dialogActions(
          editingFee ? 'Save fee' : 'Add fee',
          () => void submitFee(),
          () => setFeeOpen(false),
        )}
      </Modal>

      {/* Add access type */}
      <Modal
        open={accessOpen}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading="Add access type"
        onRequestClose={() => !busy && setAccessOpen(false)}
        preventCloseOnClickOutside
      >
        <Stack gap={5}>
          <p className="detail-dialog__subtitle">All fields are required unless marked optional.</p>
          <Select
            id="rec-access-type"
            labelText="Access Type"
            value={access.type}
            invalid={!!accessErrors.type}
            invalidText={accessErrors.type}
            disabled={busy || listsLoading}
            onChange={(e) => {
              setAccess({ type: e.target.value, sub: '' });
              setAccessErrors({});
            }}
          >
            <SelectItem value="" text={listsLoading ? 'Loading…' : '— Select access type —'} />
            {options(lookups?.accessTypes)}
          </Select>
          <Select
            id="rec-access-sub"
            labelText="Access Sub Type"
            value={access.sub}
            invalid={!!accessErrors.sub}
            invalidText={accessErrors.sub}
            helperText={access.type ? undefined : 'Choose the access type first.'}
            disabled={busy || listsLoading || !access.type}
            onChange={(e) => {
              setAccess((a) => ({ ...a, sub: e.target.value }));
              setAccessErrors((x) => ({ ...x, sub: undefined }));
            }}
          >
            <SelectItem value="" text="— Select sub type —" />
            {options(subOptions)}
          </Select>
        </Stack>
        {dialogActions(
          'Add access type',
          () => void submitAccess(),
          () => setAccessOpen(false),
        )}
      </Modal>

      {/* Upload establishment order */}
      <Modal
        open={orderOpen}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading="Upload establishment order"
        onRequestClose={() => !busy && setOrderOpen(false)}
        preventCloseOnClickOutside
      >
        <Stack gap={5}>
          <p className="detail-dialog__subtitle">
            A PDF of at most 4 MB. Each order needs its own file name.
          </p>
          {file ? (
            <FileUploaderItem
              name={file.name}
              status="edit"
              invalid={!!fileError}
              errorSubject={fileError}
              iconDescription="Remove file"
              onDelete={() => pick(undefined)}
            />
          ) : (
            <FileUploaderDropContainer
              labelText="Drag and drop a PDF here or click to choose one"
              accept={['.pdf', 'application/pdf']}
              disabled={busy}
              onAddFiles={(_, { addedFiles }) => pick(addedFiles[0])}
            />
          )}
          {!file && fileError && (
            <p className="detail-tab__reason" style={{ color: 'var(--cds-text-error)' }}>
              {fileError}
            </p>
          )}
        </Stack>
        {dialogActions(
          'Upload',
          () => void submitOrder(),
          () => setOrderOpen(false),
        )}
      </Modal>

      {/* Delete / remove confirmation */}
      <Modal
        open={confirm !== null}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading={confirm?.title ?? ''}
        onRequestClose={() => !busy && setConfirm(null)}
        preventCloseOnClickOutside
      >
        <p className="detail-dialog__subtitle">{confirm?.body}</p>
        <div className="detail-dialog__actions">
          <Button kind="tertiary" disabled={busy} onClick={() => setConfirm(null)}>
            Cancel
          </Button>
          <Button kind="danger" disabled={busy} onClick={() => void onConfirm()}>
            {busy ? 'Working…' : (confirm?.action ?? 'Delete')}
          </Button>
        </div>
      </Modal>
    </>
  );
};

export default RecProjectSections;
