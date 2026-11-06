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
  createAptPartitionApportionment,
  deleteAptPartitionApportionment,
  updateAptPartitionApportionment,
  type AptPartition,
  type AptPartitionApportionment,
} from '@/services/apt/managementUnits';

interface Form {
  foa: string;
  aac: string;
  aacHa: string;
  comments: string;
}

type Errors = Partial<Record<keyof Form, string>>;

const EMPTY: Form = { foa: '', aac: '', aacHa: '', comments: '' };

/** APT2's PartitionApportionmentEdit checks. */
const validate = (f: Form): Errors => {
  const e: Errors = {};
  if (!f.foa) e.foa = 'Form of Agreement is required.';
  const aac = wholeProblem('AAC (m³)', f.aac, true);
  if (aac) e.aac = aac;
  const aacHa = wholeProblem('AAC (ha)', f.aacHa);
  if (aacHa) e.aacHa = aacHa;
  if (f.comments.length > MAX_COMMENTS) e.comments = 'Comments cannot exceed 2000 characters.';
  return e;
};

interface Props {
  open: boolean;
  /** The partition a new apportionment is added to. */
  partition: AptPartition;
  /** The apportionment to edit, or null for a new one. */
  apportionment: AptPartitionApportionment | null;
  /** The FOA types valid for the management unit's type. */
  foaTypes: AptCodeOption[];
  onClose: () => void;
  onSaved: () => void;
  onDeleted: () => void;
}

/**
 * APTM2200 — a partition's apportionment to a form of agreement, new or edited, and its delete
 * (APT2's PartitionApportionmentEdit). Rendered for APT administrators only.
 */
const ApportionmentModal: FC<Props> = ({
  open,
  partition,
  apportionment,
  foaTypes,
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
        apportionment
          ? {
              foa: apportionment.foaTypeCode,
              aac: numText(apportionment.allowableAnnualCut),
              aacHa: numText(apportionment.allowableAnnualCutHa),
              comments: apportionment.comments ?? '',
            }
          : EMPTY,
      );
      setErrors({});
    }
  }, [open, apportionment]);

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
      foaTypeCode: form.foa,
      allowableAnnualCut: toNumber(form.aac) ?? 0,
      allowableAnnualCutHa: toNumber(form.aacHa),
      comments: form.comments.trim() ? form.comments : null,
    };
    setSaving(true);
    try {
      if (apportionment) await updateAptPartitionApportionment(apportionment.id, body);
      else await createAptPartitionApportionment({ ...body, partitionId: partition.id });
      display({
        kind: 'success',
        title: apportionment ? 'Apportionment saved' : 'Apportionment added',
        timeout: 5000,
      });
      onSaved();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not save the apportionment',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  // An FOA type no longer in the list still shows as itself.
  const types =
    apportionment && !foaTypes.some((t) => t.code === apportionment.foaTypeCode)
      ? [{ code: apportionment.foaTypeCode, description: apportionment.foaTypeName }, ...foaTypes]
      : foaTypes;

  return (
    <>
      <Modal
        open={open && !confirming}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading={apportionment ? 'Edit apportionment' : 'New apportionment'}
        onRequestClose={close}
        preventCloseOnClickOutside
      >
        <Stack gap={5}>
          <p className="detail-dialog__subtitle">
            Partition: {partition.partitionDescription ?? partition.partitionCode}
          </p>
          <Select
            id="apt-apt-foa"
            labelText="Form of agreement / rights held"
            value={form.foa}
            invalid={!!errors.foa}
            invalidText={errors.foa}
            disabled={saving}
            onChange={(e) => set('foa', e.target.value)}
          >
            <SelectItem value="" text="— Select a form of agreement —" />
            {types.map((t) => (
              <SelectItem key={t.code} value={t.code} text={t.description ?? t.code} />
            ))}
          </Select>
          <div className="detail-dialog__pair">
            <TextInput
              id="apt-apt-aac"
              labelText="AAC (m³)"
              inputMode="numeric"
              value={form.aac}
              invalid={!!errors.aac}
              invalidText={errors.aac}
              disabled={saving}
              onChange={(e) => set('aac', e.target.value)}
            />
            <TextInput
              id="apt-apt-aac-ha"
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
            id="apt-apt-comments"
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
          {apportionment && (
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

      {apportionment && (
        <ConfirmationModal
          open={confirming}
          onClose={() => setConfirming(false)}
          heading="Delete apportionment"
          confirmLabel="Delete"
          danger
          errorTitle="Could not delete the apportionment"
          onConfirm={async () => {
            await deleteAptPartitionApportionment(apportionment.id);
            display({ kind: 'success', title: 'Apportionment deleted', timeout: 5000 });
            setConfirming(false);
            onDeleted();
          }}
        >
          Delete the {apportionment.foaTypeName ?? apportionment.foaTypeCode} apportionment?
          Commitments recorded against it are not deleted with it.
        </ConfirmationModal>
      )}
    </>
  );
};

export default ApportionmentModal;
