import { Add } from '@carbon/icons-react';
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
  TextArea,
  TextInput,
} from '@carbon/react';
import { useCallback, useState, type FC } from 'react';

import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import { safeErrorMessage } from '@/lib/errorMessage';
import type { CodeOption } from '@/services/codeLists';
import {
  addRangeLandBase,
  getRangeLandBaseOptions,
  removeRangeLandBase,
  updateRangeLandBase,
  type RangeLandBaseSave,
} from '@/services/range_land_base';
import type { RangeLandBase } from '@/services/range_tenure_detail';
import { parseTypedDate, TYPED_DATE_PATTERN } from '@/utils/typedDate';

import { codeDesc, dash, num } from './format';

import type { RangePanelProps } from './panelProps';

/** Legacy's line under the File header (range.legal.text.info). */
const LEGAL_TEXT = '** Any change requires new legal documentation to be produced. **';

type LandForm = Omit<RangeLandBaseSave, 'revisionCount'>;

const EMPTY_FORM: LandForm = {
  description: '',
  rangeLandBasePid: '',
  rangeLandBaseId: '',
  rangeLandOwnershipTypeCode: '',
  rangeLandPurposeCode: '',
  fencedPasture: '',
  unfencedPasture: '',
  forageProduction: '',
  landBaseComment: '',
  leaseStartDate: '',
  leaseEndDate: '',
};

const text = (v: string | number | null) => (v === null ? '' : String(v));

/** Legacy's Update: the record's values into the edit row. */
const formOf = (p: RangeLandBase): LandForm => ({
  description: text(p.description),
  rangeLandBasePid: text(p.rangeLandBasePid),
  rangeLandBaseId: text(p.rangeLandBaseId),
  rangeLandOwnershipTypeCode: text(p.rangeLandOwnershipTypeCode),
  rangeLandPurposeCode: text(p.rangeLandPurposeCode),
  fencedPasture: text(p.fencedPasture),
  unfencedPasture: text(p.unfencedPasture),
  forageProduction: text(p.forageProduction),
  landBaseComment: text(p.landBaseComment),
  leaseStartDate: text(p.leaseStartDate),
  leaseEndDate: text(p.leaseEndDate),
});

/** yyyy-mm-dd in local time. */
const toIsoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

/** The list, plus the stored value when the list no longer carries it (an expired code). */
const withCurrent = (options: CodeOption[], code: string, desc: string | null) =>
  code && !options.some((o) => o.code === code)
    ? [{ code, description: desc ? `${code} - ${desc}` : code }, ...options]
    : options;

const options = (items: CodeOption[]) =>
  items.map((o) => <SelectItem key={o.code} value={o.code} text={o.description || o.code} />);

/**
 * The Assoc. Land Base tab — legacy FTA615R: the private land parcels associated with the
 * tenure, with Add and, on each record, Edit (legacy's Update then Save) and Remove (a soft
 * delete, which needs a comment).
 *
 * Legacy checked no authority on 615R; here editing needs the backend's canEdit, as on the other
 * range tabs. The backend runs legacy's checks and answers with its messages.
 */
const LandBasePanel: FC<RangePanelProps> = ({ tenure, canEdit, onTenureChanged }) => {
  const forestFileId = tenure.forestFileId;
  const { display } = useNotification();
  const fetcher = useCallback(() => getRangeLandBaseOptions(forestFileId), [forestFileId]);
  const { data: opts } = useApiResource(fetcher, [forestFileId]);
  const allowed = canEdit && !!opts;

  // ─── Save (add or update) ─────────────────────────────────────────────────
  const [dialog, setDialog] = useState<{ editing: RangeLandBase | null } | null>(null);
  const [form, setForm] = useState<LandForm>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);

  const openDialog = (editing: RangeLandBase | null) => {
    setForm(editing ? formOf(editing) : EMPTY_FORM);
    setDialog({ editing });
  };

  const closeDialog = () => {
    if (!saving) setDialog(null);
  };

  const set = (key: keyof LandForm, value: string) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const submit = async () => {
    if (!dialog) return;
    setSaving(true);
    try {
      const { editing } = dialog;
      const res =
        editing && editing.landBaseSkey !== null
          ? await updateRangeLandBase(forestFileId, editing.landBaseSkey, {
              ...form,
              revisionCount: editing.revisionCount,
            })
          : await addRangeLandBase(forestFileId, { ...form, revisionCount: null });
      display({ kind: 'success', title: res.message, timeout: 5000 });
      setDialog(null);
      onTenureChanged();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not save the land base record.',
        subtitle: safeErrorMessage(err),
        timeout: 0,
      });
    } finally {
      setSaving(false);
    }
  };

  // ─── Remove ───────────────────────────────────────────────────────────────
  const [removing, setRemoving] = useState<RangeLandBase | null>(null);
  const [comment, setComment] = useState('');
  const [removeBusy, setRemoveBusy] = useState(false);

  const openRemove = (p: RangeLandBase) => {
    setComment(p.landBaseComment ?? '');
    setRemoving(p);
  };

  const submitRemove = async () => {
    if (!removing || removing.landBaseSkey === null) return;
    setRemoveBusy(true);
    try {
      const res = await removeRangeLandBase(
        forestFileId,
        removing.landBaseSkey,
        comment,
        removing.revisionCount,
      );
      display({ kind: 'success', title: res.message, timeout: 5000 });
      setRemoving(null);
      onTenureChanged();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not remove the land base record.',
        subtitle: safeErrorMessage(err),
        timeout: 0,
      });
    } finally {
      setRemoveBusy(false);
    }
  };

  // ─── View ─────────────────────────────────────────────────────────────────
  const editing = dialog?.editing ?? null;

  const field = (key: keyof LandForm, id: string, label: string, maxLength: number) => (
    <TextInput
      id={`land-base-${id}`}
      labelText={label}
      maxLength={maxLength}
      value={form[key]}
      disabled={saving}
      onChange={(e) => set(key, e.target.value)}
    />
  );

  const date = (key: 'leaseStartDate' | 'leaseEndDate', id: string, label: string) => (
    <DatePicker
      datePickerType="single"
      dateFormat="Y-m-d"
      className="detail-dialog__date"
      value={form[key]}
      onChange={(dates: Date[]) => set(key, dates[0] ? toIsoDate(dates[0]) : '')}
    >
      <DatePickerInput
        id={`land-base-${id}`}
        labelText={label}
        placeholder="yyyy-mm-dd"
        disabled={saving}
        pattern={TYPED_DATE_PATTERN}
        onChange={(e) => {
          const typed = e.target.value;
          if (typed.trim() === '') set(key, '');
          else {
            const parsed = parseTypedDate(typed);
            if (parsed) set(key, parsed);
          }
        }}
      />
    </DatePicker>
  );

  return (
    <>
      <header className="detail-tab__actions">
        <Button
          kind="tertiary"
          size="md"
          renderIcon={Add}
          disabled={!allowed}
          onClick={() => openDialog(null)}
        >
          Add land base
        </Button>
      </header>
      <div className="bordered-table">
        <TableContainer
          title="Associated Land Base"
          description={`${tenure.landBase.length} row(s)`}
        >
          <Table>
            <TableHead>
              <TableRow>
                <TableHeader>Legal Description</TableHeader>
                <TableHeader>PID</TableHeader>
                <TableHeader>ID #</TableHeader>
                <TableHeader>Type</TableHeader>
                <TableHeader>Purpose</TableHeader>
                <TableHeader>Fenced Pasture (ha)</TableHeader>
                <TableHeader>Unfenced Pasture (ha)</TableHeader>
                <TableHeader>Hay Field (ha)</TableHeader>
                <TableHeader>Comment</TableHeader>
                <TableHeader>Lease Start Date</TableHeader>
                <TableHeader>Lease End Date</TableHeader>
                <TableHeader>Actions</TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {tenure.landBase.map((p, i) => (
                <TableRow key={p.landBaseSkey ?? i}>
                  <TableCell>{dash(p.description)}</TableCell>
                  <TableCell>{dash(p.rangeLandBasePid)}</TableCell>
                  <TableCell>{dash(p.rangeLandBaseId)}</TableCell>
                  <TableCell>
                    {codeDesc(p.rangeLandOwnershipTypeCode, p.rangeLandOwnershipTypeDesc)}
                  </TableCell>
                  <TableCell>{codeDesc(p.rangeLandPurposeCode, p.rangeLandPurposeDesc)}</TableCell>
                  <TableCell>{num(p.fencedPasture)}</TableCell>
                  <TableCell>{num(p.unfencedPasture)}</TableCell>
                  <TableCell>{num(p.forageProduction)}</TableCell>
                  <TableCell>{dash(p.landBaseComment)}</TableCell>
                  <TableCell>{dash(p.leaseStartDate)}</TableCell>
                  <TableCell>{dash(p.leaseEndDate)}</TableCell>
                  <TableCell>
                    <OverflowMenu
                      size="sm"
                      flipped
                      aria-label="Row actions"
                      iconDescription="Row actions"
                      disabled={!allowed || p.landBaseSkey === null}
                    >
                      <OverflowMenuItem itemText="Edit" onClick={() => openDialog(p)} />
                      <OverflowMenuItem
                        itemText="Remove"
                        isDelete
                        hasDivider
                        onClick={() => openRemove(p)}
                      />
                    </OverflowMenu>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </div>

      {/* Save — legacy's edit row. */}
      <Modal
        open={!!dialog}
        passiveModal
        size="md"
        className="detail-dialog"
        modalHeading={editing ? 'Edit land base' : 'Add land base'}
        onRequestClose={closeDialog}
        preventCloseOnClickOutside
      >
        {dialog && (
          <Stack gap={5}>
            <p className="detail-dialog__subtitle">{LEGAL_TEXT}</p>
            <div className="detail-dialog__grid">
              <div className="detail-dialog__full">
                {field('description', 'description', 'Legal Description', 255)}
              </div>
              {field('rangeLandBasePid', 'pid', 'PID (optional)', 9)}
              {field('rangeLandBaseId', 'id', 'ID # (optional)', 50)}
              <Select
                id="land-base-type"
                labelText="Type"
                value={form.rangeLandOwnershipTypeCode}
                disabled={saving}
                onChange={(e) => set('rangeLandOwnershipTypeCode', e.target.value)}
              >
                <SelectItem value="" text="— Select type —" />
                {options(
                  withCurrent(
                    opts?.ownershipTypes ?? [],
                    editing?.rangeLandOwnershipTypeCode ?? '',
                    editing?.rangeLandOwnershipTypeDesc ?? null,
                  ),
                )}
              </Select>
              <Select
                id="land-base-purpose"
                labelText="Purpose (optional)"
                value={form.rangeLandPurposeCode}
                disabled={saving}
                onChange={(e) => set('rangeLandPurposeCode', e.target.value)}
              >
                <SelectItem value="" text="" />
                {options(
                  withCurrent(
                    opts?.purposes ?? [],
                    editing?.rangeLandPurposeCode ?? '',
                    editing?.rangeLandPurposeDesc ?? null,
                  ),
                )}
              </Select>
              {field('fencedPasture', 'fenced', 'Fenced Pasture (ha) (optional)', 8)}
              {field('unfencedPasture', 'unfenced', 'Unfenced Pasture (ha) (optional)', 8)}
              {field('forageProduction', 'hay', 'Hay Field (ha) (optional)', 8)}
              <div />
              {date('leaseStartDate', 'lease-start', 'Lease Start Date (optional)')}
              {date('leaseEndDate', 'lease-end', 'Lease End Date (optional)')}
              <div className="detail-dialog__full">
                <TextArea
                  id="land-base-comment"
                  labelText="Comment (optional)"
                  rows={3}
                  maxCount={4000}
                  value={form.landBaseComment}
                  disabled={saving}
                  onChange={(e) => set('landBaseComment', e.target.value)}
                />
              </div>
            </div>
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

      {/* Remove — legacy's confirm, with the comment it required. */}
      <Modal
        open={!!removing}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading="Remove land base"
        onRequestClose={() => {
          if (!removeBusy) setRemoving(null);
        }}
        preventCloseOnClickOutside
      >
        {removing && (
          <Stack gap={5}>
            <p className="detail-dialog__subtitle">
              Are you sure you want to remove the Land Base record
              {removing.description ? ` ${removing.description}` : ''}?
            </p>
            <TextArea
              id="land-base-remove-comment"
              labelText="Comment"
              rows={3}
              maxCount={4000}
              value={comment}
              disabled={removeBusy}
              onChange={(e) => setComment(e.target.value)}
            />
          </Stack>
        )}
        <div className="detail-dialog__actions">
          <Button kind="tertiary" disabled={removeBusy} onClick={() => setRemoving(null)}>
            Cancel
          </Button>
          <Button kind="danger" disabled={removeBusy} onClick={() => void submitRemove()}>
            {removeBusy ? 'Removing…' : 'Remove'}
          </Button>
        </div>
      </Modal>
    </>
  );
};

export default LandBasePanel;
