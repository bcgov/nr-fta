import { Button, Select, SelectItem, Stack, TextArea, TextInput } from '@carbon/react';
import { useEffect, useState, type FC } from 'react';

import DateField from '@/components/DateField';
import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import { updateAptLicence, type AptLicence } from '@/services/apt/licences';
import { getFoaTypes, type AptCodeOption } from '@/services/apt/lookups';

/** APT_LICENCES.COMMENTS is VARCHAR2(2000). */
const MAX_COMMENTS = 2000;

interface Form {
  foaType: string;
  replacementDate: string;
  replacementTerm: string;
  comments: string;
}

type Errors = Partial<Record<keyof Form, string>>;

const formOf = (l: AptLicence): Form => ({
  foaType: l.aptFoaSt ?? '',
  replacementDate: l.replacementDate ?? '',
  replacementTerm: l.replacementTerm != null ? String(l.replacementTerm) : '',
  comments: l.comments ?? '',
});

/** APT2's checks: an FOA type, a 0–999 month term, comments within the column. */
const validate = (f: Form): Errors => {
  const e: Errors = {};
  if (!f.foaType) e.foaType = 'FOA Type is required.';
  if (f.replacementTerm.trim() && !/^\d{1,3}$/.test(f.replacementTerm.trim()))
    e.replacementTerm = 'Must be a number between 0 and 999.';
  if (f.comments.length > MAX_COMMENTS)
    e.comments = `Comments cannot exceed ${MAX_COMMENTS} characters.`;
  return e;
};

interface Props {
  licence: AptLicence;
  open: boolean;
  onClose: () => void;
  /** Called with the licence as stored, after the dialog has closed. */
  onSaved: (saved: AptLicence) => void;
}

/**
 * APT2's APTM4000 licence fields: the FOA type, picked from those valid for the licence's
 * management unit type, the replacement date and term, and comments. Saving creates APT's
 * record of the licence if it has none yet.
 */
const LicenceEditModal: FC<Props> = ({ licence, open, onClose, onSaved }) => {
  const { display } = useNotification();
  const [form, setForm] = useState<Form>(() => formOf(licence));
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);
  const [foaTypes, setFoaTypes] = useState<AptCodeOption[] | null>(null);

  useEffect(() => {
    if (open) {
      setForm(formOf(licence));
      setErrors({});
    }
  }, [open, licence]);

  const muType = licence.aptMgmtUnitSt?.trim() || undefined;
  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setFoaTypes(null);
    getFoaTypes(muType)
      .then((t) => {
        if (!cancelled) setFoaTypes(t);
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setFoaTypes([]);
        display({
          kind: 'error',
          title: 'Could not load the FOA types',
          subtitle: err instanceof Error ? err.message : 'Request failed',
          timeout: 9000,
        });
      });
    return () => {
      cancelled = true;
    };
  }, [open, muType, display]);

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
    setSaving(true);
    try {
      const saved = await updateAptLicence(licence.forestFileId, {
        foaType: form.foaType,
        replacementDate: form.replacementDate || null,
        replacementTerm: form.replacementTerm.trim() ? Number(form.replacementTerm.trim()) : null,
        comments: form.comments.trim() ? form.comments : null,
      });
      display({ kind: 'success', title: `Licence ${licence.forestFileId} saved`, timeout: 5000 });
      onClose();
      onSaved(saved);
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not save the licence',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  const loading = open && foaTypes === null;
  // The licence's own type stays selectable even if its unit's list no longer offers it.
  const options =
    form.foaType && foaTypes && !foaTypes.some((t) => t.code === form.foaType)
      ? [{ code: form.foaType, description: null }, ...foaTypes]
      : (foaTypes ?? []);

  return (
    <Modal
      open={open}
      passiveModal
      size="md"
      className="detail-dialog"
      modalHeading={`Edit licence ${licence.forestFileId}`}
      onRequestClose={close}
      preventCloseOnClickOutside
    >
      <Stack gap={5}>
        <Select
          id="licence-foa"
          labelText="FOA Type"
          value={form.foaType}
          invalid={!!errors.foaType}
          invalidText={errors.foaType}
          disabled={saving || loading}
          onChange={(e) => set('foaType', e.target.value)}
        >
          <SelectItem value="" text={loading ? 'Loading…' : '— Select a type —'} />
          {options.map((t) => (
            <SelectItem
              key={t.code}
              value={t.code}
              text={t.description ? `${t.code} - ${t.description}` : t.code}
            />
          ))}
        </Select>
        <div className="detail-dialog__pair">
          <DateField
            id="licence-replacement-date"
            labelText="Replacement Date (optional)"
            value={form.replacementDate}
            disabled={saving}
            onChange={(v) => set('replacementDate', v)}
          />
          <TextInput
            id="licence-replacement-term"
            labelText="Replacement Term (months, optional)"
            inputMode="numeric"
            value={form.replacementTerm}
            invalid={!!errors.replacementTerm}
            invalidText={errors.replacementTerm}
            disabled={saving}
            onChange={(e) => set('replacementTerm', e.target.value)}
          />
        </div>
        <TextArea
          id="licence-comments"
          labelText="Comments (optional)"
          rows={4}
          maxCount={MAX_COMMENTS}
          enableCounter
          value={form.comments}
          invalid={!!errors.comments}
          invalidText={errors.comments}
          disabled={saving}
          onChange={(e) => set('comments', e.target.value)}
        />
      </Stack>
      <div className="detail-dialog__actions">
        <Button kind="tertiary" disabled={saving} onClick={close}>
          Cancel
        </Button>
        <Button kind="primary" disabled={saving || loading} onClick={() => void submit()}>
          {saving ? 'Saving…' : 'Save'}
        </Button>
      </div>
    </Modal>
  );
};

export default LicenceEditModal;
