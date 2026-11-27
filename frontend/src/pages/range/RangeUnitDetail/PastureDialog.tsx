import { Button, Stack, TextInput } from '@carbon/react';
import { useEffect, useState, type FC } from 'react';

import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import { safeErrorMessage } from '@/lib/errorMessage';
import {
  addRangeUnitPasture,
  updateRangeUnitPasture,
  type RangeUnitDetail,
  type RangeUnitPasture,
} from '@/services/range_unit_detail';

interface Props {
  unit: RangeUnitDetail;
  open: boolean;
  /** The pasture to update, or null to add one. */
  pasture: RangeUnitPasture | null;
  onClose: () => void;
  onSaved: () => void;
}

interface Errors {
  pastureId?: string;
  pastureName?: string;
}

/** Fta630MnRgUnPstForm's pasture Save validators; the backend runs them again. */
const validate = (pastureId: string, pastureName: string, adding: boolean): Errors => {
  const e: Errors = {};
  if (adding) {
    if (!pastureId.trim()) e.pastureId = 'Pasture ID is mandatory.';
    else if (!/^-?\d+$/.test(pastureId.trim())) e.pastureId = 'Pasture ID must be an integer.';
  }
  if (!pastureName.trim()) e.pastureName = 'Pasture Description is mandatory.';
  return e;
};

/**
 * FTA630's pasture entry row as a dialog: Pasture ID and Pasture Description, then Save. Update
 * locks the id, as legacy disabled it after Update; updating pasture '00' renames the unit.
 */
const PastureDialog: FC<Props> = ({ unit, open, pasture, onClose, onSaved }) => {
  const adding = pasture === null;
  const { display } = useNotification();
  const [pastureId, setPastureId] = useState('');
  const [pastureName, setPastureName] = useState('');
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setPastureId(pasture?.pastureId ?? '');
    setPastureName(pasture?.pastureName ?? '');
    setErrors({});
  }, [open, pasture]);

  const close = () => {
    if (!saving) onClose();
  };

  const submit = async () => {
    const found = validate(pastureId, pastureName, adding);
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    setSaving(true);
    try {
      if (pasture) {
        await updateRangeUnitPasture(unit.rangeUnitId, pasture.pastureId, {
          pastureName,
          pastureRevisionCount: pasture.pastureRevisionCount,
          revisionCount: unit.revisionCount,
        });
      } else {
        await addRangeUnitPasture(unit.rangeUnitId, {
          pastureId,
          pastureName,
          revisionCount: unit.revisionCount,
        });
      }
      display({ kind: 'success', title: 'Save successful.', timeout: 5000 });
      onSaved();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not save the pasture.',
        subtitle: safeErrorMessage(err),
        timeout: 0,
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      passiveModal
      size="sm"
      className="detail-dialog"
      modalHeading={adding ? 'Add pasture' : `Update pasture ${pasture.pastureId}`}
      onRequestClose={close}
      preventCloseOnClickOutside
    >
      {open && (
        <Stack gap={5}>
          <TextInput
            id="ru-pasture-id"
            labelText="Pasture ID"
            maxLength={2}
            value={pastureId}
            invalid={!!errors.pastureId}
            invalidText={errors.pastureId}
            disabled={!adding || saving}
            onChange={(e) => {
              setPastureId(e.target.value);
              setErrors((x) => ({ ...x, pastureId: undefined }));
            }}
          />
          <TextInput
            id="ru-pasture-name"
            labelText="Pasture Description"
            maxLength={30}
            value={pastureName}
            invalid={!!errors.pastureName}
            invalidText={errors.pastureName}
            disabled={saving}
            onChange={(e) => {
              setPastureName(e.target.value);
              setErrors((x) => ({ ...x, pastureName: undefined }));
            }}
          />
        </Stack>
      )}
      <div className="detail-dialog__actions">
        <Button kind="tertiary" disabled={saving} onClick={close}>
          Cancel
        </Button>
        <Button kind="primary" disabled={saving} onClick={() => void submit()}>
          {saving ? 'Saving…' : 'Save'}
        </Button>
      </div>
    </Modal>
  );
};

export default PastureDialog;
