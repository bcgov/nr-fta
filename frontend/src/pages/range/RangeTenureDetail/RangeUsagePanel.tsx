import { Add, RecentlyViewed } from '@carbon/icons-react';
import {
  Button,
  Checkbox,
  FormGroup,
  InlineNotification,
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
import { useCallback, useState, type FC, type ReactNode } from 'react';

import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import { safeErrorMessage } from '@/lib/errorMessage';
import type { CodeOption } from '@/services/codeLists';
import type { RangeUsage } from '@/services/range_tenure_detail';
import {
  copyRangeUsage,
  getRangeUsageOptions,
  saveRangeUsage,
  type RangeUsageSave,
} from '@/services/range_usage';

import { codeDesc, dash, num } from './format';

import type { RangePanelProps } from './panelProps';

/** Legacy's line under the edit row (range.legal.text.info). */
const LEGAL_TEXT = '** Any change requires new legal documentation to be produced. **';

/**
 * The FTA613R usage columns. Grazing (E) files show non-use (billable,
 * non-billable and its reason); hay cutting (H) files show a temporary
 * reduction (amount and reason). The tab shows a grazing non-use reason with
 * its description, the history window its code alone, as legacy did.
 */
const UsageTable: FC<{
  title: string;
  rows: RangeUsage[];
  hay: boolean;
  history?: boolean;
  actions?: (row: RangeUsage) => ReactNode;
}> = ({ title, rows, hay, history = false, actions }) => {
  const reduction = hay ? 'Reduction' : 'Non-Use';
  return (
    <div className="bordered-table">
      <TableContainer title={title} description={`${rows.length} row(s)`}>
        <Table>
          <TableHead>
            <TableRow>
              <TableHeader>Year</TableHeader>
              <TableHeader>Authorized Use</TableHeader>
              <TableHeader>Increase Amount</TableHeader>
              <TableHeader>Increase Reason</TableHeader>
              <TableHeader>{reduction} Agreement No.</TableHeader>
              {!hay && <TableHeader>Non-Use Billable</TableHeader>}
              <TableHeader>{hay ? 'Reduction Amount' : 'Non-Use Non-Billable'}</TableHeader>
              <TableHeader>{hay ? 'Reduction Reason' : 'Non-Billable Reason'}</TableHeader>
              <TableHeader>Total Annual Use</TableHeader>
              {actions && <TableHeader>Actions</TableHeader>}
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((u) => (
              <TableRow key={u.calendarYear ?? undefined}>
                <TableCell>{dash(u.calendarYear)}</TableCell>
                <TableCell>{num(u.authorizedUse)}</TableCell>
                <TableCell>{num(u.tempIncrease)}</TableCell>
                <TableCell>
                  {codeDesc(u.rangeIncreaseReasonCode, u.rangeIncreaseReasonDesc)}
                </TableCell>
                <TableCell>{dash(u.nonUseAgreementNo)}</TableCell>
                {!hay && <TableCell>{num(u.nonUseBillable)}</TableCell>}
                <TableCell>{num(u.nonUseNonbillable)}</TableCell>
                <TableCell>
                  {hay || history
                    ? dash(u.rangeNonUseReasonCode)
                    : codeDesc(u.rangeNonUseReasonCode, u.rangeNonUseReasonDesc)}
                </TableCell>
                <TableCell>{num(u.totalAnnualUse)}</TableCell>
                {actions && <TableCell>{actions(u)}</TableCell>}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </div>
  );
};

type UsageForm = Omit<RangeUsageSave, 'revisionCount'>;

const EMPTY_FORM: UsageForm = {
  authorizedUse: '',
  tempIncrease: '',
  rangeIncreaseReasonCode: '',
  nonUseAgreementNo: '',
  nonUseBillable: '',
  nonUseNonbillable: '',
  rangeNonUseReasonCode: '',
};

const text = (v: number | null) => (v === null ? '' : String(v));

/** Legacy's Select: the row's values into the edit row. */
const formOf = (u: RangeUsage): UsageForm => ({
  authorizedUse: text(u.authorizedUse),
  tempIncrease: text(u.tempIncrease),
  rangeIncreaseReasonCode: u.rangeIncreaseReasonCode ?? '',
  nonUseAgreementNo: text(u.nonUseAgreementNo),
  nonUseBillable: text(u.nonUseBillable),
  nonUseNonbillable: text(u.nonUseNonbillable),
  rangeNonUseReasonCode: u.rangeNonUseReasonCode ?? '',
});

/** Total Annual Use: Auth.Use + Increase - Non_Use, blanks as 0; null when one is not a number. */
const totalOf = (f: UsageForm): number | null => {
  const n = (s: string) => (s.trim() === '' ? 0 : Number(s));
  const total =
    n(f.authorizedUse) + n(f.tempIncrease) - (n(f.nonUseBillable) + n(f.nonUseNonbillable));
  return Number.isFinite(total) ? total : null;
};

/** The list, plus the stored value when the list no longer carries it (an expired code). */
const withCurrent = (options: CodeOption[], code: string) =>
  code && !options.some((o) => o.code === code)
    ? [{ code, description: code }, ...options]
    : options;

const options = (items: CodeOption[]) =>
  items.map((o) => <SelectItem key={o.code} value={o.code} text={o.description || o.code} />);

/**
 * The Range Usage tab — legacy FTA613R: the usage of each year within the tenure term, with
 * Add year and, on each row, Edit (legacy's Select then Save) and Copy (legacy's Copy of the
 * saved year to every remaining year, or to From - To), and the View History window (every
 * year, newest first). Legacy hid its Delete, so there is none.
 *
 * Editing needs the backend's canEdit, and no application in the Inbox (legacy warned and
 * disabled Save and Copy). The backend runs legacy's checks and answers with its messages.
 */
const RangeUsagePanel: FC<RangePanelProps> = ({ tenure, canEdit, onTenureChanged }) => {
  const forestFileId = tenure.forestFileId;
  const { display } = useNotification();
  const [historyOpen, setHistoryOpen] = useState(false);
  const hay = (tenure.fileTypeCode ?? '').includes('H');
  const nonUse = hay ? 'Temporary Reduction' : 'Non-Use';

  const fetcher = useCallback(() => getRangeUsageOptions(forestFileId), [forestFileId]);
  const { data: opts } = useApiResource(fetcher, [forestFileId]);
  const allowed = canEdit && !!opts && !opts.applicationPending;

  // ─── Save (add or edit a year) ────────────────────────────────────────────
  const [dialog, setDialog] = useState<{ editing: RangeUsage | null } | null>(null);
  const [year, setYear] = useState('');
  const [form, setForm] = useState<UsageForm>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);

  const usedYears = new Set(tenure.rangeUsage.map((u) => u.calendarYear));
  const freeYears = (opts?.years ?? []).filter((y) => !usedYears.has(y));

  const openDialog = (editing: RangeUsage | null) => {
    setForm(editing ? formOf(editing) : EMPTY_FORM);
    setYear(editing ? String(editing.calendarYear) : '');
    setDialog({ editing });
  };

  const closeDialog = () => {
    if (!saving) setDialog(null);
  };

  const set = (key: keyof UsageForm, value: string) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const submit = async () => {
    if (!dialog) return;
    if (!year) {
      display({
        kind: 'error',
        title: 'Could not save the range usage.',
        subtitle: 'Year is mandatory.',
        timeout: 0,
      });
      return;
    }
    setSaving(true);
    try {
      const res = await saveRangeUsage(forestFileId, Number(year), {
        ...form,
        // Hay files have no billable non-use.
        nonUseBillable: hay ? '' : form.nonUseBillable,
        revisionCount: dialog.editing?.revisionCount ?? null,
      });
      display({ kind: 'success', title: res.message, timeout: 5000 });
      setDialog(null);
      onTenureChanged();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not save the range usage.',
        subtitle: safeErrorMessage(err),
        timeout: 0,
      });
    } finally {
      setSaving(false);
    }
  };

  // ─── Copy ─────────────────────────────────────────────────────────────────
  const [copying, setCopying] = useState<RangeUsage | null>(null);
  const [copy, setCopy] = useState({ all: false, from: '', to: '' });
  const [copyBusy, setCopyBusy] = useState(false);

  const openCopy = (row: RangeUsage) => {
    setCopy({ all: false, from: '', to: '' });
    setCopying(row);
  };

  const submitCopy = async () => {
    if (!copying || copying.calendarYear === null) return;
    setCopyBusy(true);
    try {
      const res = await copyRangeUsage(forestFileId, copying.calendarYear, {
        allRemainingYears: copy.all,
        copyFromYear: copy.all ? '' : copy.from,
        copyToYear: copy.all ? '' : copy.to,
      });
      display({ kind: 'success', title: res.message, timeout: 5000 });
      setCopying(null);
      onTenureChanged();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not copy the range usage.',
        subtitle: safeErrorMessage(err),
        timeout: 0,
      });
    } finally {
      setCopyBusy(false);
    }
  };

  // ─── View ─────────────────────────────────────────────────────────────────
  const editing = dialog?.editing ?? null;
  const total = totalOf(form);
  const yearOptions = (opts?.years ?? []).map((y) => (
    <SelectItem key={y} value={String(y)} text={String(y)} />
  ));

  const amount = (key: keyof UsageForm, id: string, label: string) => (
    <TextInput
      id={`range-usage-${id}`}
      labelText={label}
      inputMode="numeric"
      maxLength={5}
      value={form[key]}
      disabled={saving}
      onChange={(e) => set(key, e.target.value)}
    />
  );

  return (
    <>
      {opts?.applicationPending && (
        <InlineNotification
          kind="warning"
          lowContrast
          hideCloseButton
          title="You may not update while application is in the Inbox."
        />
      )}
      <header className="detail-tab__actions">
        <Button
          kind="tertiary"
          size="md"
          renderIcon={Add}
          disabled={!allowed || freeYears.length === 0}
          onClick={() => openDialog(null)}
        >
          Add year
        </Button>
        <Button
          kind="tertiary"
          size="md"
          renderIcon={RecentlyViewed}
          onClick={() => setHistoryOpen(true)}
        >
          View history
        </Button>
      </header>
      <UsageTable
        title={hay ? 'Hay Cutting' : 'Grazing'}
        rows={tenure.rangeUsage}
        hay={hay}
        actions={(u) => (
          <OverflowMenu
            size="sm"
            flipped
            aria-label="Row actions"
            iconDescription="Row actions"
            disabled={!allowed}
          >
            <OverflowMenuItem itemText="Edit" onClick={() => openDialog(u)} />
            <OverflowMenuItem itemText="Copy" onClick={() => openCopy(u)} />
          </OverflowMenu>
        )}
      />

      <Modal
        open={historyOpen}
        passiveModal
        size="lg"
        modalHeading="Usage History"
        onRequestClose={() => setHistoryOpen(false)}
      >
        {historyOpen && (
          <UsageTable title="Usage History" rows={tenure.usageHistory} hay={hay} history />
        )}
      </Modal>

      {/* Save — legacy's edit row. */}
      <Modal
        open={!!dialog}
        passiveModal
        size="md"
        className="detail-dialog"
        modalHeading={editing ? `Edit ${editing.calendarYear} range usage` : 'Add year'}
        onRequestClose={closeDialog}
        preventCloseOnClickOutside
      >
        {dialog && (
          <Stack gap={5}>
            <p className="detail-dialog__subtitle">{LEGAL_TEXT}</p>
            <div className="detail-dialog__pair">
              <Select
                id="range-usage-year"
                labelText="Year"
                value={year}
                disabled={saving || !!editing}
                onChange={(e) => setYear(e.target.value)}
              >
                <SelectItem value="" text="— Select year —" />
                {editing
                  ? yearOptions
                  : freeYears.map((y) => <SelectItem key={y} value={String(y)} text={String(y)} />)}
              </Select>
              {amount('authorizedUse', 'authorized', 'Authorized Use')}
            </div>
            <FormGroup legendText="Temporary Increase">
              <div className="detail-dialog__pair">
                {amount('tempIncrease', 'increase', 'Amount')}
                <Select
                  id="range-usage-increase-reason"
                  labelText="Reason"
                  value={form.rangeIncreaseReasonCode}
                  disabled={saving}
                  onChange={(e) => set('rangeIncreaseReasonCode', e.target.value)}
                >
                  <SelectItem value="" text="" />
                  {options(
                    withCurrent(
                      opts?.increaseReasons ?? [],
                      editing?.rangeIncreaseReasonCode ?? '',
                    ),
                  )}
                </Select>
              </div>
            </FormGroup>
            <FormGroup legendText={nonUse}>
              <Stack gap={5}>
                <div className="detail-dialog__pair">
                  {amount('nonUseAgreementNo', 'agreement', 'Agreement No.')}
                  {hay
                    ? amount('nonUseNonbillable', 'nonbillable', 'Amount')
                    : amount('nonUseBillable', 'billable', 'Billable')}
                </div>
                <div className="detail-dialog__pair">
                  {!hay && amount('nonUseNonbillable', 'nonbillable', 'Non-Billable')}
                  <Select
                    id="range-usage-non-use-reason"
                    labelText={hay ? 'Reason' : 'Non-Billable Reason'}
                    value={form.rangeNonUseReasonCode}
                    disabled={saving}
                    onChange={(e) => set('rangeNonUseReasonCode', e.target.value)}
                  >
                    <SelectItem value="" text="" />
                    {options(
                      withCurrent(opts?.nonUseReasons ?? [], editing?.rangeNonUseReasonCode ?? ''),
                    )}
                  </Select>
                </div>
              </Stack>
            </FormGroup>
            <TextInput
              id="range-usage-total"
              labelText="Total Annual Use (Auth.Use + Increase - Non_Use)"
              value={total === null ? '' : String(total)}
              readOnly
            />
          </Stack>
        )}
        <div className="detail-dialog__actions">
          <Button kind="tertiary" disabled={saving} onClick={closeDialog}>
            Cancel
          </Button>
          <Button kind="primary" disabled={saving} onClick={() => void submit()}>
            {saving ? 'Saving…' : 'Save'}
          </Button>
        </div>
      </Modal>

      {/* Copy — legacy's All Remaining Years --or-- From Year / To. */}
      <Modal
        open={!!copying}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading={copying ? `Copy ${copying.calendarYear} range usage` : 'Copy range usage'}
        onRequestClose={() => {
          if (!copyBusy) setCopying(null);
        }}
        preventCloseOnClickOutside
      >
        {copying && (
          <Stack gap={5}>
            <p className="detail-dialog__subtitle">
              Range Usage may exist in the years copied to. It is replaced.
            </p>
            <Checkbox
              id="range-usage-copy-all"
              labelText="All Remaining Years"
              checked={copy.all}
              disabled={copyBusy}
              onChange={(_, { checked }) => setCopy((prev) => ({ ...prev, all: checked }))}
            />
            <div className="detail-dialog__pair">
              <Select
                id="range-usage-copy-from"
                labelText="From Year"
                value={copy.from}
                disabled={copyBusy || copy.all}
                onChange={(e) => setCopy((prev) => ({ ...prev, from: e.target.value }))}
              >
                <SelectItem value="" text="" />
                {yearOptions}
              </Select>
              <Select
                id="range-usage-copy-to"
                labelText="To"
                value={copy.to}
                disabled={copyBusy || copy.all}
                onChange={(e) => setCopy((prev) => ({ ...prev, to: e.target.value }))}
              >
                <SelectItem value="" text="" />
                {yearOptions}
              </Select>
            </div>
          </Stack>
        )}
        <div className="detail-dialog__actions">
          <Button kind="tertiary" disabled={copyBusy} onClick={() => setCopying(null)}>
            Cancel
          </Button>
          <Button kind="primary" disabled={copyBusy} onClick={() => void submitCopy()}>
            {copyBusy ? 'Copying…' : 'Copy'}
          </Button>
        </div>
      </Modal>
    </>
  );
};

export default RangeUsagePanel;
