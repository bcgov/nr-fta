import { Select, SelectItem, TextArea, TextInput } from '@carbon/react';
import { useEffect, useState, type ReactNode } from 'react';

import DateField from '@/components/DateField';
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

export interface LicenceEditor {
  editing: boolean;
  saving: boolean;
  start: () => void;
  cancel: () => void;
  save: () => Promise<void>;
  /** The editable fields' inputs, shown in place of their values while editing. */
  inputs: {
    foaType: ReactNode;
    replacementDate: ReactNode;
    replacementTerm: ReactNode;
    comments: ReactNode;
  };
}

/**
 * APT2's APTM4000 licence fields, edited in place on the licence page as FSP edits its
 * Information tab: the FOA type, picked from those valid for the licence's management unit
 * type, the replacement date and term, and comments. Saving creates APT's record of the
 * licence if it has none yet.
 */
export function useLicenceEdit(
  licence: AptLicence | null | undefined,
  onSaved: (saved: AptLicence) => void,
): LicenceEditor {
  const { display } = useNotification();
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<Form | null>(null);
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);
  const [foaTypes, setFoaTypes] = useState<AptCodeOption[] | null>(null);

  const muType = licence?.aptMgmtUnitSt?.trim() || undefined;
  useEffect(() => {
    if (!editing) return;
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
          title: 'Could not load the FOA types.',
          subtitle: err instanceof Error ? err.message : 'Request failed',
          timeout: 9000,
        });
      });
    return () => {
      cancelled = true;
    };
  }, [editing, muType, display]);

  const start = () => {
    if (!licence) return;
    setForm(formOf(licence));
    setErrors({});
    setEditing(true);
  };

  const cancel = () => {
    if (saving) return;
    setEditing(false);
    setErrors({});
  };

  const set = <K extends keyof Form>(key: K, value: Form[K]) => {
    setForm((f) => (f ? { ...f, [key]: value } : f));
    setErrors((e) => {
      if (!e[key]) return e;
      const next = { ...e };
      delete next[key];
      return next;
    });
  };

  const save = async () => {
    if (!licence || !form) return;
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
      display({ kind: 'success', title: `Licence ${licence.forestFileId} saved.`, timeout: 5000 });
      setEditing(false);
      onSaved(saved);
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not save the licence.',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  const f = form ?? { foaType: '', replacementDate: '', replacementTerm: '', comments: '' };
  const loading = editing && foaTypes === null;
  // The licence's own type stays selectable even if its unit's list no longer offers it.
  const options =
    f.foaType && foaTypes && !foaTypes.some((t) => t.code === f.foaType)
      ? [{ code: f.foaType, description: null }, ...foaTypes]
      : (foaTypes ?? []);

  const inputs = {
    foaType: (
      <div className="detail-edit__input detail-edit__input--cell">
        <Select
          id="licence-foa"
          labelText="FOA Type"
          hideLabel
          value={f.foaType}
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
      </div>
    ),
    replacementDate: (
      <div className="detail-edit__input detail-edit__input--cell">
        <DateField
          id="licence-replacement-date"
          labelText="Replacement Date"
          hideLabel
          value={f.replacementDate}
          disabled={saving}
          onChange={(v) => set('replacementDate', v)}
        />
      </div>
    ),
    replacementTerm: (
      <div className="detail-edit__input detail-edit__input--cell">
        <TextInput
          id="licence-replacement-term"
          labelText="Replacement Term (months)"
          hideLabel
          inputMode="numeric"
          value={f.replacementTerm}
          invalid={!!errors.replacementTerm}
          invalidText={errors.replacementTerm}
          disabled={saving}
          onChange={(e) => set('replacementTerm', e.target.value)}
        />
      </div>
    ),
    comments: (
      <TextArea
        id="licence-comments"
        labelText="Comments"
        hideLabel
        className="detail-edit__textarea"
        rows={4}
        maxCount={MAX_COMMENTS}
        enableCounter
        value={f.comments}
        invalid={!!errors.comments}
        invalidText={errors.comments}
        disabled={saving}
        onChange={(e) => set('comments', e.target.value)}
      />
    ),
  };

  return { editing, saving, start, cancel, save, inputs };
}
