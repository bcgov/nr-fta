import { Button, DatePicker, DatePickerInput, Select, SelectItem, TextInput } from '@carbon/react';
import { useEffect, useState, type FC } from 'react';
import { useNavigate } from 'react-router-dom';

import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import {
  getFileTypes,
  getTenureAdminOrgUnits,
  orgUnitCodeOf,
  type CodeOption,
} from '@/services/codeLists';
import { createTenure } from '@/services/tenure_write';
import { parseTypedDate, TYPED_DATE_PATTERN } from '@/utils/typedDate';
import './AddTenureModal.scss';

interface Form {
  fileId: string;
  fileType: string;
  orgUnit: string;
  issueDate: string;
}

type Errors = Partial<Record<keyof Form, string>>;

const EMPTY: Form = { fileId: '', fileType: '', orgUnit: '', issueDate: '' };

/** yyyy-mm-dd in local time. */
const toIsoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

interface Props {
  open: boolean;
  onClose: () => void;
}

/**
 * FTA010 — Add New Tenure, as a dialog over the current page. Opened from the
 * Tenures menu or the Tenure List; only for a role that may create a tenure
 * (routes/access.ts). POSTs to {@code /api/fta/tenures} and opens the new file.
 */
const AddTenureModal: FC<Props> = ({ open, onClose }) => {
  const notify = useNotification();
  const navigate = useNavigate();
  const [form, setForm] = useState<Form>(EMPTY);
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);
  // THE.FILE_TYPE_CODE and THE.ORG_UNIT: the insert's file type is a foreign key,
  // and the backend resolves the org unit by its code, so both must be real values.
  // The org units are legacy FTA100's Admin Org list: districts, regions and HQ.
  const [fileTypes, setFileTypes] = useState<CodeOption[]>([]);
  const [orgUnits, setOrgUnits] = useState<CodeOption[]>([]);

  useEffect(() => {
    if (!open) return;
    setForm(EMPTY);
    setErrors({});
    let cancelled = false;
    Promise.allSettled([getFileTypes(), getTenureAdminOrgUnits()]).then(([types, units]) => {
      if (cancelled) return;
      if (types.status === 'fulfilled') setFileTypes(types.value);
      if (units.status === 'fulfilled') setOrgUnits(units.value);
      if (types.status === 'rejected' || units.status === 'rejected') {
        notify.display({
          kind: 'error',
          title: 'Could not load the file type and organization unit lists',
          subtitle: 'Close the dialog and open it again to retry.',
          timeout: 6000,
        });
      }
    });
    return () => {
      cancelled = true;
    };
    // notify is stable for the page's life.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const set = (field: keyof Form, value: string) => {
    setForm((f) => ({ ...f, [field]: value }));
    setErrors((e) => ({ ...e, [field]: undefined }));
  };

  const validate = (): Errors => {
    const e: Errors = {};
    if (!form.fileId.trim()) e.fileId = 'Enter the forest file ID.';
    if (!form.fileType) e.fileType = 'Choose a file type.';
    if (!form.orgUnit) e.orgUnit = 'Choose an organization unit.';
    return e;
  };

  const onSubmit = async () => {
    const found = validate();
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    setSaving(true);
    try {
      const { forestFileId } = await createTenure({
        forestFileId: form.fileId.trim(),
        fileTypeCode: form.fileType,
        orgUnitCode: form.orgUnit,
        issueDate: form.issueDate || null,
      });
      notify.display({
        kind: 'success',
        title: 'Tenure created',
        subtitle: `File ${forestFileId} created.`,
        timeout: 5000,
      });
      onClose();
      navigate(`/tenures/${encodeURIComponent(forestFileId)}`);
    } catch (err) {
      notify.display({
        kind: 'error',
        title: 'Could not create tenure',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 6000,
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      // passiveModal + our own action row, as the New Mark Application dialog does.
      passiveModal
      className="add-tenure"
      size="sm"
      modalHeading="Add New Tenure"
      onRequestClose={() => {
        if (!saving) onClose();
      }}
      preventCloseOnClickOutside
    >
      <p className="add-tenure__strap">All fields are required unless marked optional.</p>
      <div className="add-tenure__grid">
        <TextInput
          id="add-tenure-fileId"
          labelText="Forest File ID"
          placeholder="e.g. A19999"
          value={form.fileId}
          maxLength={10}
          invalid={!!errors.fileId}
          invalidText={errors.fileId}
          disabled={saving}
          onChange={(e) => set('fileId', e.target.value.toUpperCase())}
        />
        <Select
          id="add-tenure-fileType"
          labelText="File Type"
          value={form.fileType}
          invalid={!!errors.fileType}
          invalidText={errors.fileType}
          disabled={saving}
          onChange={(e) => set('fileType', e.target.value)}
        >
          <SelectItem value="" text="Choose a type" />
          {fileTypes.map((t) => (
            <SelectItem key={t.code} value={t.code} text={t.description} />
          ))}
        </Select>
        <div className="add-tenure__full">
          <Select
            id="add-tenure-orgUnit"
            labelText="Organization Unit"
            value={form.orgUnit}
            invalid={!!errors.orgUnit}
            invalidText={errors.orgUnit}
            disabled={saving}
            onChange={(e) => set('orgUnit', e.target.value)}
          >
            <SelectItem value="" text="Choose an organization unit" />
            {orgUnits.map((o) => (
              <SelectItem key={o.code} value={orgUnitCodeOf(o)} text={o.description} />
            ))}
          </Select>
        </div>
        <div className="add-tenure__date">
          <DatePicker
            datePickerType="single"
            dateFormat="Y-m-d"
            value={form.issueDate}
            onChange={(dates: Date[]) => set('issueDate', dates[0] ? toIsoDate(dates[0]) : '')}
          >
            <DatePickerInput
              id="add-tenure-issueDate"
              labelText="Issue Date (optional)"
              placeholder="yyyy-mm-dd"
              pattern={TYPED_DATE_PATTERN}
              disabled={saving}
              onChange={(e) => {
                const text = e.target.value;
                if (text.trim() === '') set('issueDate', '');
                else {
                  const typed = parseTypedDate(text);
                  if (typed) set('issueDate', typed);
                }
              }}
            />
          </DatePicker>
        </div>
      </div>

      <div className="add-tenure__actions">
        <Button kind="tertiary" disabled={saving} onClick={onClose}>
          Cancel
        </Button>
        <Button kind="primary" disabled={saving} onClick={() => void onSubmit()}>
          {saving ? 'Creating…' : 'Create tenure'}
        </Button>
      </div>
    </Modal>
  );
};

export default AddTenureModal;
