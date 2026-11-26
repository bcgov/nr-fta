import { Select, SelectItem, TextArea } from '@carbon/react';
import { useEffect, useState, type ReactNode } from 'react';

import DateField from '@/components/DateField';
import { useNotification } from '@/context/notification/useNotification';
import {
  getAptOrgUnits,
  getManagementUnitStatuses,
  type AptCodeOption,
  type AptOrgUnit,
} from '@/services/apt/lookups';
import {
  searchAptManagementUnits,
  updateAptManagementUnit,
  type AptManagementUnit,
} from '@/services/apt/managementUnits';

import { MAX_COMMENTS, unitLabel } from './shared';

interface Form {
  orgUnitNo: string;
  sourceId: string;
  status: string;
  effectiveDate: string;
  comments: string;
}

type Errors = Partial<Record<keyof Form, string>>;

const formOf = (u: AptManagementUnit): Form => ({
  orgUnitNo: u.orgUnitNo == null ? '' : String(u.orgUnitNo),
  sourceId: u.muId == null ? '' : String(u.muId),
  status: u.mgmtUnitStatusSt ?? '',
  effectiveDate: u.statusEffectiveDate ?? '',
  comments: u.comments ?? '',
});

/** APT2's checks (APTM1200). */
const validate = (f: Form): Errors => {
  const e: Errors = {};
  if (!f.orgUnitNo) e.orgUnitNo = 'Org Unit is required.';
  if (!f.status) e.status = 'Status is required.';
  if (!f.effectiveDate) e.effectiveDate = 'Status Effective Date is required.';
  if (f.comments.length > MAX_COMMENTS) e.comments = 'Comments cannot exceed 2000 characters.';
  return e;
};

interface Lists {
  orgUnits: AptOrgUnit[];
  statuses: AptCodeOption[];
  sources: AptManagementUnit[];
}

export interface UnitEditor {
  editing: boolean;
  saving: boolean;
  start: () => void;
  cancel: () => void;
  save: () => Promise<void>;
  /** The editable fields' inputs, shown in place of their values while editing. */
  inputs: {
    orgUnit: ReactNode;
    source: ReactNode;
    status: ReactNode;
    effectiveDate: ReactNode;
    comments: ReactNode;
  };
}

/**
 * APT2's APTM1200 edit of a management unit, in place on the unit's page as the app's other
 * sections edit: org unit, source management unit, status and its effective date, and comments.
 * Type and number identify the unit, so they stay fixed.
 */
export function useUnitEdit(
  unit: AptManagementUnit | null | undefined,
  onSaved: (saved: AptManagementUnit) => void,
): UnitEditor {
  const { display } = useNotification();
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<Form | null>(null);
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);
  const [lists, setLists] = useState<Lists | null>(null);

  // The pick lists, read each time editing starts.
  useEffect(() => {
    if (!editing) return;
    let cancelled = false;
    setLists(null);
    Promise.all([getAptOrgUnits(), getManagementUnitStatuses(), searchAptManagementUnits()])
      .then(([orgUnits, statuses, sources]) => {
        if (!cancelled) setLists({ orgUnits, statuses, sources });
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setLists({ orgUnits: [], statuses: [], sources: [] });
        display({
          kind: 'error',
          title: 'Could not load the management unit lists',
          subtitle: err instanceof Error ? err.message : 'Request failed',
          timeout: 9000,
        });
      });
    return () => {
      cancelled = true;
    };
  }, [editing, display]);

  const start = () => {
    if (!unit) return;
    setForm(formOf(unit));
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
    if (!unit || !form) return;
    const found = validate(form);
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    setSaving(true);
    try {
      const saved = await updateAptManagementUnit(unit.id, {
        orgUnitNo: Number(form.orgUnitNo),
        muType: unit.aptMgmtUnitSt,
        muNo: unit.mgmtUnitNumber,
        status: form.status,
        effectiveDate: form.effectiveDate,
        muId: form.sourceId ? Number(form.sourceId) : null,
        // Not shown; sent back unchanged so a save keeps it.
        orcsFileNumber: unit.orcsFileNumber?.trim() || null,
        comments: form.comments.trim() ? form.comments : null,
      });
      display({
        kind: 'success',
        title: `Management unit ${saved.mgmtUnitName ?? ''} saved`,
        timeout: 5000,
      });
      setEditing(false);
      onSaved(saved);
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not save the management unit',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  const f = form ?? { orgUnitNo: '', sourceId: '', status: '', effectiveDate: '', comments: '' };
  const loading = editing && lists === null;
  const sources = (lists?.sources ?? []).filter((u) => u.id !== unit?.id);

  const inputs = {
    orgUnit: (
      <div className="detail-edit__input detail-edit__input--cell">
        <Select
          id="apt-mu-edit-org"
          labelText="Org unit"
          hideLabel
          value={f.orgUnitNo}
          invalid={!!errors.orgUnitNo}
          invalidText={errors.orgUnitNo}
          disabled={saving || loading}
          onChange={(e) => set('orgUnitNo', e.target.value)}
        >
          {/* The unit's own org unit shows while the list loads. */}
          {loading && f.orgUnitNo ? (
            <SelectItem value={f.orgUnitNo} text={unit?.orgUnitName ?? f.orgUnitNo} />
          ) : (
            <SelectItem value="" text={loading ? 'Loading…' : '— Select an org unit —'} />
          )}
          {(lists?.orgUnits ?? []).map((o) => (
            <SelectItem
              key={o.orgUnitNo}
              value={String(o.orgUnitNo)}
              text={o.displayName ?? o.code}
            />
          ))}
        </Select>
      </div>
    ),
    source: (
      <div className="detail-edit__input detail-edit__input--cell">
        <Select
          id="apt-mu-edit-source"
          labelText="Source management unit"
          hideLabel
          value={f.sourceId}
          disabled={saving || loading}
          onChange={(e) => set('sourceId', e.target.value)}
        >
          {loading && f.sourceId ? (
            <SelectItem value={f.sourceId} text={unit?.srcMgmtUnitName ?? f.sourceId} />
          ) : (
            <SelectItem value="" text={loading ? 'Loading…' : '— None —'} />
          )}
          {sources.map((u) => (
            <SelectItem key={u.id} value={String(u.id)} text={unitLabel(u)} />
          ))}
        </Select>
      </div>
    ),
    status: (
      <div className="detail-edit__input detail-edit__input--cell">
        <Select
          id="apt-mu-edit-status"
          labelText="Status"
          hideLabel
          value={f.status}
          invalid={!!errors.status}
          invalidText={errors.status}
          disabled={saving || loading}
          onChange={(e) => set('status', e.target.value)}
        >
          {loading && f.status ? (
            <SelectItem value={f.status} text={f.status} />
          ) : (
            <SelectItem value="" text={loading ? 'Loading…' : '— Select a status —'} />
          )}
          {(lists?.statuses ?? []).map((s) => (
            <SelectItem key={s.code} value={s.code} text={s.description || s.code} />
          ))}
        </Select>
      </div>
    ),
    effectiveDate: (
      <div className="detail-edit__input detail-edit__input--cell">
        <DateField
          id="apt-mu-edit-effective"
          labelText="Status effective date"
          hideLabel
          value={f.effectiveDate}
          invalidText={errors.effectiveDate}
          disabled={saving}
          onChange={(v) => set('effectiveDate', v)}
        />
      </div>
    ),
    comments: (
      <TextArea
        id="apt-mu-edit-comments"
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
