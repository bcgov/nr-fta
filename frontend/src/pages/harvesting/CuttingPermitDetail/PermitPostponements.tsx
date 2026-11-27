import { Add, Edit, TrashCan } from '@carbon/icons-react';
import {
  Button,
  Checkbox,
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
import ConfirmationModal from '@/components/ConfirmationModal';
import DateField from '@/components/DateField';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import {
  addPermitPostponement,
  deletePermitPostponement,
  getPermitPostponements,
  updatePermitPostponement,
  type CuttingPermitDetail,
  type PermitPostponement,
  type PermitPostponementRequest,
} from '@/services/cutting_permit_detail';
import { formatDate } from '@/utils/formatDate';
import { parseTypedDate } from '@/utils/typedDate';

const date = (v: string | null | undefined) => formatDate(v) || '—';
const WHOLE_PATTERN = /^\d{1,2}$/;

interface Form {
  start: string;
  expiry: string;
  years: string;
  months: string;
  rescinded: boolean;
}

type Errors = Partial<Record<keyof Form, string>>;

const today = () => new Date().toISOString().slice(0, 10);

/** The day after a yyyy-mm-dd date, as yyyy-mm-dd. */
const dayAfter = (iso: string) => {
  const d = new Date(`${iso}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + 1);
  return d.toISOString().slice(0, 10);
};

/** {@code getEnableRescind}: within [start, expiry), unless the user may always rescind. */
const mayRescind = (row: PermitPostponement | null, anytime: boolean) =>
  anytime ||
  (!!row?.startDate && !!row.expiryDate && today() >= row.startDate && today() < row.expiryDate);

interface Props {
  cp: CuttingPermitDetail;
  /** Call after a write: a postponement moves the permit's status and expiry. */
  onChanged: () => void;
}

/**
 * FTA902's Postponements. Adding one puts the permit in HP and pushes its expiry out; the package
 * (SAVE_HARVESTING_POSTPONE) checks them against each other and against the tenure's expiry.
 */
const PermitPostponements: FC<Props> = ({ cp, onChanged }) => {
  const { display } = useNotification();
  const { canPostpone, canChangePostponements, canRescindAnytime } = cp.rules;
  const fetcher = useCallback(() => getPermitPostponements(cp.hvaSkey), [cp.hvaSkey]);
  const { data: rows, loading, error, reload } = useApiResource(fetcher, [cp.hvaSkey]);

  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<PermitPostponement | null>(null);
  const [deleting, setDeleting] = useState<PermitPostponement | null>(null);
  const [errors, setErrors] = useState<Errors>({});
  // The validated request awaiting FTA902's "You are about to…" confirmation.
  const [pending, setPending] = useState<PermitPostponementRequest | null>(null);
  // fta902CpDetail.jsp: the start date is locked once there is more than one postponement.
  const startLocked = (rows?.length ?? 0) > 1;
  const [form, setForm] = useState<Form>({
    start: '',
    expiry: '',
    years: '',
    months: '',
    rescinded: false,
  });

  const set = <K extends keyof Form>(key: K, value: Form[K]) => {
    setForm((f) => ({ ...f, [key]: value }));
    setErrors((e) => ({ ...e, [key]: undefined }));
  };

  const openDialog = (row: PermitPostponement | null) => {
    setEditing(row);
    // Fta902CpDetailAction.handleGet: a new one starts the day after the latest one expires.
    const latestExpiry = rows?.[0]?.expiryDate;
    setForm({
      start: row ? (row.startDate ?? '') : latestExpiry ? dayAfter(latestExpiry) : '',
      expiry: row?.expiryDate ?? '',
      years: row?.termYears != null ? `${row.termYears}` : '',
      months: row?.termMonths != null ? `${row.termMonths}` : '',
      rescinded: row?.rescinded === 'Y',
    });
    setErrors({});
    setOpen(true);
  };

  const refreshAll = () => {
    reload();
    onChanged();
  };

  const submit = () => {
    const found: Errors = {};
    if (!parseTypedDate(form.start)) found.start = 'Enter a date as yyyy-mm-dd.';
    if (form.expiry.trim() && !parseTypedDate(form.expiry))
      found.expiry = 'Enter a date as yyyy-mm-dd.';
    if (!form.expiry.trim() && !form.years.trim() && !form.months.trim())
      found.expiry = 'Enter an expiry date or a term.';
    if (form.years.trim() && !WHOLE_PATTERN.test(form.years.trim()))
      found.years = 'Enter a whole number.';
    if (form.months.trim() && !WHOLE_PATTERN.test(form.months.trim()))
      found.months = 'Enter a whole number.';
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    setPending({
      revisionCount: cp.revisionCount,
      startDate: parseTypedDate(form.start),
      expiryDate: form.expiry.trim() ? parseTypedDate(form.expiry) : null,
      termYears: form.years.trim() || null,
      termMonths: form.months.trim() || null,
      rescinded: form.rescinded ? 'Y' : 'N',
    });
  };

  /** Throws on failure, so the confirmation stays open and reports it. */
  const save = async (req: PermitPostponementRequest) => {
    const { warnings } = editing
      ? await updatePermitPostponement(cp.hvaSkey, editing.id, req)
      : await addPermitPostponement(cp.hvaSkey, req);
    display({
      kind: warnings.length > 0 ? 'warning' : 'success',
      title: editing ? 'Postponement updated' : 'Postponement added',
      subtitle: warnings.join(' '),
      timeout: 7000,
    });
    setOpen(false);
    refreshAll();
  };

  const addButton = (
    <Button
      size="md"
      kind="primary"
      renderIcon={Add}
      disabled={!canPostpone}
      onClick={() => openDialog(null)}
    >
      Add postponement
    </Button>
  );

  return (
    <div className="fsp-info__tab-panel">
      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Loading postponements…"
      >
        {rows &&
          (rows.length === 0 ? (
            <EmptyState
              icon={<Add size={48} />}
              title="No postponements"
              body="Postponements of this permit appear here."
              action={<div className="detail-tab__empty-action">{addButton}</div>}
            />
          ) : (
            <div>
              <header className="detail-tab__actions">{addButton}</header>
              <div className="bordered-table">
                <TableContainer>
                  <Table size="md" useZebraStyles>
                    <TableHead>
                      <TableRow>
                        <TableHeader>Previous CP Expiry</TableHeader>
                        <TableHeader>Start</TableHeader>
                        <TableHeader>Expiry</TableHeader>
                        <TableHeader>Term</TableHeader>
                        <TableHeader>Rescinded</TableHeader>
                        <TableHeader>Actions</TableHeader>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {rows.map((p) => (
                        <TableRow key={p.id}>
                          <TableCell>{date(p.previousExpiryDate)}</TableCell>
                          <TableCell>{date(p.startDate)}</TableCell>
                          <TableCell>{date(p.expiryDate)}</TableCell>
                          <TableCell>
                            {p.termYears ?? 0} yr {p.termMonths ?? 0} mo
                          </TableCell>
                          <TableCell>{p.rescinded === 'Y' ? 'Yes' : 'No'}</TableCell>
                          <TableCell>
                            <Button
                              size="sm"
                              kind="ghost"
                              hasIconOnly
                              renderIcon={Edit}
                              iconDescription="Update postponement"
                              disabled={!canChangePostponements}
                              onClick={() => openDialog(p)}
                            />
                            <Button
                              size="sm"
                              kind="ghost"
                              hasIconOnly
                              renderIcon={TrashCan}
                              className="icon-button--delete"
                              iconDescription="Delete postponement"
                              disabled={!canChangePostponements}
                              onClick={() => setDeleting(p)}
                            />
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </div>
            </div>
          ))}
      </AsyncBoundary>

      <Modal
        open={open && pending === null}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading={editing ? 'Update postponement' : 'Add postponement'}
        onRequestClose={() => setOpen(false)}
        preventCloseOnClickOutside
      >
        <Stack gap={5}>
          <div className="detail-dialog__pair">
            <DateField
              id="cp-pp-start"
              labelText="Start"
              value={form.start}
              invalidText={errors.start}
              disabled={startLocked}
              onChange={(v) => set('start', v)}
            />
            <DateField
              id="cp-pp-expiry"
              labelText="Expiry"
              value={form.expiry}
              invalidText={errors.expiry}
              onChange={(v) => set('expiry', v)}
            />
          </div>
          <div className="detail-dialog__pair">
            <TextInput
              id="cp-pp-years"
              labelText="Term (years)"
              inputMode="numeric"
              value={form.years}
              invalid={!!errors.years}
              invalidText={errors.years}
              onChange={(e) => set('years', e.target.value)}
            />
            <TextInput
              id="cp-pp-months"
              labelText="Term (months)"
              inputMode="numeric"
              value={form.months}
              invalid={!!errors.months}
              invalidText={errors.months}
              onChange={(e) => set('months', e.target.value)}
            />
          </div>
          {editing && (
            <Checkbox
              id="cp-pp-rescinded"
              labelText="Rescinded"
              checked={form.rescinded}
              disabled={!mayRescind(editing, canRescindAnytime)}
              onChange={(_, { checked }) => set('rescinded', checked)}
            />
          )}
        </Stack>
        <div className="detail-dialog__actions">
          <Button kind="tertiary" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button kind="primary" onClick={submit}>
            Save
          </Button>
        </div>
      </Modal>

      <ConfirmationModal
        open={pending !== null}
        onClose={() => setPending(null)}
        heading={editing ? 'Update postponement' : 'Add postponement'}
        confirmLabel="Save"
        errorTitle="Could not save the postponement"
        onConfirm={async () => {
          if (pending) await save(pending);
        }}
      >
        {editing
          ? 'You are about to UPDATE a postponement period. Are you sure?'
          : 'You are about to CREATE a NEW postponement period. Are you sure?'}
      </ConfirmationModal>

      <ConfirmationModal
        open={deleting !== null}
        onClose={() => setDeleting(null)}
        heading="Delete postponement"
        confirmLabel="Delete"
        danger
        errorTitle="Could not delete the postponement"
        onConfirm={async () => {
          if (!deleting) return;
          await deletePermitPostponement(cp.hvaSkey, deleting.id, cp.revisionCount);
          display({ kind: 'success', title: 'Postponement deleted.', timeout: 5000 });
          refreshAll();
        }}
      >
        Delete the postponement starting {date(deleting?.startDate)}?
      </ConfirmationModal>
    </div>
  );
};

export default PermitPostponements;
