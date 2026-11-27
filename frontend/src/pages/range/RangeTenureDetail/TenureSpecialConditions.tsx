import { Add, Edit, ListChecked, TrashCan } from '@carbon/icons-react';
import {
  Button,
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
import { useState, type FC } from 'react';

import ConfirmationModal from '@/components/ConfirmationModal';
import { Modal } from '@/components/Modal';
import SectionTile from '@/components/SectionTile';
import { useNotification } from '@/context/notification/useNotification';
import type { RangeSpecialCondition } from '@/services/range_tenure_detail';
import {
  deleteRangeCondition,
  saveRangeCondition,
  type RangeConditionCode,
} from '@/services/range_tenure_tab';

import { dash } from './format';

/** The code whose title is typed; every other code's title is the code's own. */
const OTHER = 'OTH';
/** RANGE_SPECIAL_CONDITION's limits as the legacy form enforced them. */
const MAX_DESCRIPTION = 500;
const MAX_TITLE = 110;

interface Form {
  code: string;
  title: string;
  description: string;
}

type Errors = Partial<Record<keyof Form, string>>;

/** The SaveSpecCond validators' messages (the backend runs them again). */
const validate = (f: Form): Errors => {
  const e: Errors = {};
  if (!f.code) e.code = 'Condition Code is mandatory.';
  if (!f.title.trim()) e.title = 'Condition Title is mandatory if Condition Type is Other.';
  if (!f.description.trim())
    e.description = 'Condition Description is mandatory if Condition Type is Other.';
  else if (f.description.length > MAX_DESCRIPTION)
    e.description = `Condition Description cannot exceed a maximum of ${MAX_DESCRIPTION} characters.`;
  return e;
};

interface Props {
  forestFileId: string;
  conditions: RangeSpecialCondition[];
  codes: RangeConditionCode[];
  /** Whether the user may add, change and delete conditions on this file. */
  canEdit: boolean;
  /** While the tenure form above is open, the condition buttons wait. */
  locked: boolean;
  onChanged: () => void;
}

/**
 * FTA100R's Special Conditions: the file's conditions, and (for an editor) Save Condition,
 * Update and Delete. Picking a code starts the title and text at the code's own (legacy's
 * GetSpecCond); only an OTH condition's title is typed, and an existing condition keeps its code.
 */
const TenureSpecialConditions: FC<Props> = ({
  forestFileId,
  conditions,
  codes,
  canEdit,
  locked,
  onChanged,
}) => {
  const { display } = useNotification();
  const [editing, setEditing] = useState<RangeSpecialCondition | null>(null);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<Form>({ code: '', title: '', description: '' });
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState<RangeSpecialCondition | null>(null);

  const start = (c: RangeSpecialCondition | null) => {
    setEditing(c);
    setForm({
      code: c?.rangeSpecialConditionCode ?? '',
      title: c?.conditionTitle?.trim() ?? '',
      description: c?.conditionDescription?.trim() ?? '',
    });
    setErrors({});
    setOpen(true);
  };

  const chooseCode = (code: string) => {
    const picked = codes.find((c) => c.code === code);
    setForm({ code, title: picked?.title ?? '', description: picked?.note ?? '' });
    setErrors({});
  };

  const set = (key: keyof Form, value: string) => {
    setForm((f) => ({ ...f, [key]: value }));
    setErrors((e) => ({ ...e, [key]: undefined }));
  };

  const onSave = async () => {
    const found = validate(form);
    if (Object.values(found).some(Boolean)) {
      setErrors(found);
      return;
    }
    setSaving(true);
    try {
      const result = await saveRangeCondition(forestFileId, editing?.specialConditionSkey ?? null, {
        code: form.code,
        title: form.title,
        description: form.description,
        revisionCount: editing?.rscRevisionCount ?? null,
      });
      display({
        kind: result.warnings.length > 0 ? 'warning' : 'success',
        title: result.message,
        subtitle: result.warnings.join(' ') || undefined,
        timeout: result.warnings.length > 0 ? 9000 : 5000,
      });
      setOpen(false);
      onChanged();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not save the special condition.',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 12000,
      });
    } finally {
      setSaving(false);
    }
  };

  const onDelete = async () => {
    if (!deleting || deleting.specialConditionSkey === null) return;
    const result = await deleteRangeCondition(
      forestFileId,
      deleting.specialConditionSkey,
      deleting.rscRevisionCount,
    );
    display({ kind: 'success', title: result.message, timeout: 5000 });
    onChanged();
  };

  // An existing condition's code stays listed even if it has since expired.
  const codeOptions =
    form.code && !codes.some((c) => c.code === form.code)
      ? [{ code: form.code, title: null, note: null }, ...codes]
      : codes;
  const other = form.code === OTHER;

  return (
    <SectionTile
      title="Special Conditions"
      icon={ListChecked}
      actions={
        <Button
          kind="tertiary"
          size="sm"
          renderIcon={Add}
          disabled={!canEdit || locked}
          onClick={() => start(null)}
        >
          Add special condition
        </Button>
      }
    >
      <div className="bordered-table">
        <TableContainer description={`${conditions.length} row(s)`}>
          <Table>
            <TableHead>
              <TableRow>
                <TableHeader>Special Condition Code</TableHeader>
                <TableHeader>Condition Title</TableHeader>
                <TableHeader>Condition Text</TableHeader>
                <TableHeader>Actions</TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {conditions.map((c, i) => (
                <TableRow key={c.specialConditionSkey ?? i}>
                  <TableCell>{dash(c.rangeSpecialConditionCode)}</TableCell>
                  <TableCell>{dash(c.conditionTitle?.trim())}</TableCell>
                  <TableCell>{dash(c.conditionDescription?.trim())}</TableCell>
                  <TableCell>
                    <Button
                      kind="ghost"
                      size="sm"
                      renderIcon={Edit}
                      disabled={!canEdit || locked}
                      onClick={() => start(c)}
                    >
                      Update
                    </Button>
                    <Button
                      kind="danger--ghost"
                      size="sm"
                      renderIcon={TrashCan}
                      disabled={!canEdit || locked}
                      onClick={() => setDeleting(c)}
                    >
                      Delete
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </div>

      {canEdit && (
        <Modal
          open={open}
          passiveModal
          size="md"
          modalHeading={editing ? 'Update special condition' : 'Add special condition'}
          onRequestClose={() => {
            if (!saving) setOpen(false);
          }}
          preventCloseOnClickOutside
        >
          <Stack gap={5}>
            <Select
              id="range-condition-code"
              labelText="Special Condition Code"
              value={form.code}
              invalid={!!errors.code}
              invalidText={errors.code}
              disabled={saving || editing !== null}
              onChange={(e) => chooseCode(e.target.value)}
            >
              <SelectItem value="" text="Choose a code" />
              {codeOptions.map((c) => (
                <SelectItem
                  key={c.code}
                  value={c.code}
                  text={c.title ? `${c.code}-${c.title}` : c.code}
                />
              ))}
            </Select>
            <TextInput
              id="range-condition-title"
              labelText="Condition Title"
              value={form.title}
              maxLength={MAX_TITLE}
              invalid={!!errors.title}
              invalidText={errors.title}
              disabled={saving || !other}
              onChange={(e) => set('title', e.target.value)}
            />
            <TextArea
              id="range-condition-description"
              labelText="Condition Text"
              rows={6}
              maxCount={MAX_DESCRIPTION}
              enableCounter
              value={form.description}
              invalid={!!errors.description}
              invalidText={errors.description}
              disabled={saving}
              onChange={(e) => set('description', e.target.value)}
            />
          </Stack>
          <div className="detail-edit__actions">
            <Button kind="tertiary" disabled={saving} onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button kind="primary" disabled={saving} onClick={() => void onSave()}>
              {saving ? 'Saving…' : 'Save'}
            </Button>
          </div>
        </Modal>
      )}

      <ConfirmationModal
        open={deleting !== null}
        onClose={() => setDeleting(null)}
        heading="Delete special condition"
        confirmLabel="Delete"
        danger
        errorTitle="Could not delete the special condition."
        onConfirm={onDelete}
      >
        {`Are you sure you want to delete special condition ${deleting?.rangeSpecialConditionCode ?? ''}?`}
      </ConfirmationModal>
    </SectionTile>
  );
};

export default TenureSpecialConditions;
