import { TrashCan } from '@carbon/icons-react';
import { Button, Select, SelectItem, Stack, TextArea, TextInput } from '@carbon/react';
import { useEffect, useState, type FC } from 'react';

import ConfirmationModal from '@/components/ConfirmationModal';
import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import {
  MAX_COMMENTS,
  numText,
  toNumber,
  wholeProblem,
} from '@/pages/apt/ManagementUnitHistory/shared';
import type { AptCodeOption } from '@/services/apt/lookups';
import {
  createAptPartition,
  deleteAptPartition,
  updateAptPartition,
  type AptPartition,
} from '@/services/apt/managementUnits';

interface Form {
  code: string;
  aac: string;
  aacHa: string;
  comments: string;
}

type Errors = Partial<Record<keyof Form, string>>;

const EMPTY: Form = { code: '', aac: '', aacHa: '', comments: '' };

/** APT2's PartitionEdit checks. */
const validate = (f: Form): Errors => {
  const e: Errors = {};
  if (!f.code) e.code = 'Partition Type is required.';
  const aac = wholeProblem('AAC (m³)', f.aac, true);
  if (aac) e.aac = aac;
  const aacHa = wholeProblem('AAC (ha)', f.aacHa);
  if (aacHa) e.aacHa = aacHa;
  if (f.comments.length > MAX_COMMENTS) e.comments = 'Comments cannot exceed 2000 characters.';
  return e;
};

interface Props {
  open: boolean;
  /** The determination a new partition is added to. */
  managementUnitHistoryId: number;
  /** The partition to edit, or null for a new one. */
  partition: AptPartition | null;
  partitionTypes: AptCodeOption[];
  onClose: () => void;
  /** After a save, with the partition's id. */
  onSaved: (id: number) => void;
  /** After a delete. */
  onDeleted: () => void;
}

/**
 * APTM2100 — a partition of the determination, new or edited, and its delete (APT2's
 * PartitionEdit). Rendered for APT administrators only, so every action here, the delete
 * included, is theirs.
 */
const PartitionModal: FC<Props> = ({
  open,
  managementUnitHistoryId,
  partition,
  partitionTypes,
  onClose,
  onSaved,
  onDeleted,
}) => {
  const { display } = useNotification();
  const [form, setForm] = useState<Form>(EMPTY);
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);
  const [confirming, setConfirming] = useState(false);

  useEffect(() => {
    if (open) {
      setForm(
        partition
          ? {
              code: partition.partitionCode,
              aac: numText(partition.allowableAnnualCut),
              aacHa: numText(partition.allowableAnnualCutHa),
              comments: partition.comments ?? '',
            }
          : EMPTY,
      );
      setErrors({});
    }
  }, [open, partition]);

  const set = <K extends keyof Form>(key: K, value: Form[K]) => {
    setForm((f) => ({ ...f, [key]: value }));
    setErrors((e) => {
      if (!e[key]) return e;
      const next = { ...e };
      delete next[key];
      return next;
    });
  };

  const close = () => {
    if (!saving) onClose();
  };

  const submit = async () => {
    const found = validate(form);
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    const body = {
      partitionCode: form.code,
      allowableAnnualCut: toNumber(form.aac) ?? 0,
      allowableAnnualCutHa: toNumber(form.aacHa),
      comments: form.comments.trim() ? form.comments : null,
    };
    setSaving(true);
    try {
      const saved = partition
        ? await updateAptPartition(partition.id, body)
        : await createAptPartition({ ...body, muhId: managementUnitHistoryId });
      display({
        kind: 'success',
        title: partition ? 'Partition saved' : 'Partition added',
        timeout: 5000,
      });
      onSaved(saved.id);
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not save the partition',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  // A partition type no longer in the list still shows as itself.
  const types =
    partition && !partitionTypes.some((t) => t.code === partition.partitionCode)
      ? [
          { code: partition.partitionCode, description: partition.partitionDescription },
          ...partitionTypes,
        ]
      : partitionTypes;

  return (
    <>
      <Modal
        open={open && !confirming}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading={partition ? 'Edit partition' : 'New partition'}
        onRequestClose={close}
        preventCloseOnClickOutside
      >
        <Stack gap={5}>
          <Select
            id="apt-part-type"
            labelText="Partition type"
            value={form.code}
            invalid={!!errors.code}
            invalidText={errors.code}
            disabled={saving}
            onChange={(e) => set('code', e.target.value)}
          >
            <SelectItem value="" text="— Select a type —" />
            {types.map((t) => (
              <SelectItem key={t.code} value={t.code} text={t.description ?? t.code} />
            ))}
          </Select>
          <div className="detail-dialog__pair">
            <TextInput
              id="apt-part-aac"
              labelText="AAC (m³)"
              inputMode="numeric"
              value={form.aac}
              invalid={!!errors.aac}
              invalidText={errors.aac}
              disabled={saving}
              onChange={(e) => set('aac', e.target.value)}
            />
            <TextInput
              id="apt-part-aac-ha"
              labelText="AAC (ha)"
              inputMode="numeric"
              value={form.aacHa}
              invalid={!!errors.aacHa}
              invalidText={errors.aacHa}
              disabled={saving}
              onChange={(e) => set('aacHa', e.target.value)}
            />
          </div>
          <TextArea
            id="apt-part-comments"
            labelText="Comments"
            value={form.comments}
            maxCount={MAX_COMMENTS}
            enableCounter
            invalid={!!errors.comments}
            invalidText={errors.comments}
            disabled={saving}
            onChange={(e) => set('comments', e.target.value)}
          />
        </Stack>
        <div className="detail-dialog__actions">
          {partition && (
            <Button
              kind="danger--tertiary"
              renderIcon={TrashCan}
              disabled={saving}
              onClick={() => setConfirming(true)}
            >
              Delete
            </Button>
          )}
          <Button kind="tertiary" disabled={saving} onClick={close}>
            Cancel
          </Button>
          <Button kind="primary" disabled={saving} onClick={() => void submit()}>
            {saving ? 'Saving…' : 'Save'}
          </Button>
        </div>
      </Modal>

      {partition && (
        <ConfirmationModal
          open={confirming}
          onClose={() => setConfirming(false)}
          heading="Delete partition"
          confirmLabel="Delete"
          danger
          errorTitle="Could not delete the partition"
          onConfirm={async () => {
            await deleteAptPartition(partition.id);
            display({ kind: 'success', title: 'Partition deleted', timeout: 5000 });
            setConfirming(false);
            onDeleted();
          }}
        >
          Delete the {partition.partitionDescription ?? partition.partitionCode} partition? A
          partition with apportionments cannot be deleted.
        </ConfirmationModal>
      )}
    </>
  );
};

export default PartitionModal;
