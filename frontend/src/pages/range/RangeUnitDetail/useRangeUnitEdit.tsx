import { Select, SelectItem, TextInput } from '@carbon/react';
import { useEffect, useMemo, useState, type ReactNode } from 'react';

import DateField from '@/components/DateField';
import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import { mayEditRangeDistrict } from '@/routes/access';
import {
  getDistrictOrgUnits,
  getRangeUnitStatuses,
  orgUnitCodeOf,
  type CodeOption,
} from '@/services/codeLists';
import {
  addRangeUnit,
  saveRangeUnit,
  type RangeUnitDetail,
  type RangeUnitWriteResult,
} from '@/services/range_unit_detail';

/** A new range unit's status: legacy preset P and disabled the list. */
export const NEW_STATUS = 'P';

interface Form {
  rangeUnitName: string;
  status: string;
  asOf: string;
  district: string;
  districtAdminZone: string;
}

type Errors = Partial<Record<keyof Form, string>>;

const EMPTY: Form = {
  rangeUnitName: '',
  status: NEW_STATUS,
  asOf: '',
  district: '',
  districtAdminZone: '',
};

const formOf = (u: RangeUnitDetail): Form => ({
  rangeUnitName: u.rangeUnitName ?? '',
  status: u.statusCode ?? '',
  asOf: u.statusDate ?? '',
  district: u.district ?? '',
  districtAdminZone: u.districtAdminZone ?? '',
});

/** Fta630MnRgUnPstForm's Save Range validators; the backend runs them again, with the rest. */
const validate = (f: Form): Errors => {
  const e: Errors = {};
  const zone = f.districtAdminZone.trim().toUpperCase();
  if (!f.rangeUnitName.trim()) e.rangeUnitName = 'Range Unit Name is mandatory.';
  if (!zone) e.districtAdminZone = 'Zone is mandatory.';
  else if (!/^[A-Z0-9]*$/.test(zone)) e.districtAdminZone = 'Zone must be alpha numeric [A-Z, 0-9]';
  if (!f.status) e.status = 'Status is mandatory.';
  if (!f.district) e.district = 'District is mandatory.';
  return e;
};

/** The list, plus the stored value when the list no longer carries it (an expired code). */
const withCurrent = (options: CodeOption[], code: string, description: string | null) =>
  code && !options.some((o) => o.code === code)
    ? [{ code, description: description ?? code }, ...options]
    : options;

interface Lists {
  statuses: CodeOption[];
  districts: CodeOption[];
}

export interface RangeUnitEditor {
  editing: boolean;
  saving: boolean;
  start: () => void;
  cancel: () => void;
  save: () => Promise<void>;
  /** The district picked (ORG_UNIT_NO), for the page to describe its region. */
  district: string;
  /** The editable fields' inputs, shown in place of their values while editing. */
  inputs: {
    status: ReactNode;
    asOf: ReactNode;
    rangeUnitName: ReactNode;
    district: ReactNode;
    zone: ReactNode;
  };
}

/**
 * FTA630's range unit fields, edited in place: Status and As of, Range Unit Name, District and
 * Zone. With no unit it is Add New — always editing, Status preset P and disabled, and the
 * District limited to those the user may edit (locked when there is only one, as legacy locked a
 * district user's). Saving runs Save Range on the backend, which answers with legacy's messages.
 *
 * @param defaultDistrict the district (ORG_UNIT_NO) an Add New starts at: the user's default
 */
export function useRangeUnitEdit(
  unit: RangeUnitDetail | null | undefined,
  onSaved: (result: RangeUnitWriteResult) => void,
  defaultDistrict?: string,
): RangeUnitEditor {
  const adding = unit === null;
  const { user } = useAuth();
  const { display } = useNotification();
  const [editing, setEditing] = useState(adding);
  const [form, setForm] = useState<Form>(EMPTY);
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);
  const [lists, setLists] = useState<Lists | null>(null);

  useEffect(() => {
    if (!editing) return;
    let cancelled = false;
    Promise.all([getRangeUnitStatuses(), getDistrictOrgUnits()])
      .then(([statuses, districts]) => {
        if (!cancelled) setLists({ statuses, districts });
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setLists({ statuses: [], districts: [] });
        display({
          kind: 'error',
          title: 'Could not load the range unit lists.',
          subtitle: err instanceof Error ? err.message : 'Request failed',
          timeout: 9000,
        });
      });
    return () => {
      cancelled = true;
    };
  }, [editing, display]);

  // The districts the user may put a unit in, plus the unit's own.
  const districts = useMemo(() => {
    const editable = (lists?.districts ?? []).filter((o) =>
      mayEditRangeDistrict(user, orgUnitCodeOf(o)),
    );
    return unit ? withCurrent(editable, unit.district ?? '', unit.districtDescription) : editable;
  }, [lists, user, unit]);
  const districtLocked = !!lists && districts.length <= 1;

  // Add New starts at the user's default district, or the only one they may pick.
  useEffect(() => {
    if (!adding || !lists) return;
    setForm((f) => {
      if (f.district) return f;
      const start =
        defaultDistrict && districts.some((o) => o.code === defaultDistrict)
          ? defaultDistrict
          : districts.length === 1
            ? districts[0].code
            : '';
      return start ? { ...f, district: start } : f;
    });
  }, [adding, lists, districts, defaultDistrict]);

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
    setForm((f) => ({ ...f, [key]: value }));
    setErrors((e) => {
      if (!e[key]) return e;
      const next = { ...e };
      delete next[key];
      return next;
    });
  };

  const save = async () => {
    const found = validate(form);
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    setSaving(true);
    try {
      const body = {
        rangeUnitName: form.rangeUnitName,
        status: form.status,
        asOf: form.asOf,
        district: form.district,
        districtAdminZone: form.districtAdminZone,
      };
      const result = unit
        ? await saveRangeUnit(unit.rangeUnitId, { ...body, revisionCount: unit.revisionCount })
        : await addRangeUnit(body);
      display({
        kind: result.warnings.length > 0 ? 'warning' : 'success',
        title: 'Save successful.',
        subtitle: result.warnings.join(' ') || undefined,
        timeout: 5000,
      });
      if (unit) setEditing(false);
      onSaved(result);
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not save the range unit.',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  const loading = editing && lists === null;
  const statuses = withCurrent(
    lists?.statuses ?? [],
    unit?.statusCode ?? '',
    unit?.statusDescription ?? null,
  );

  const inputs = {
    status: (
      <div className="detail-edit__input detail-edit__input--cell">
        <Select
          id="ru-edit-status"
          labelText="Status"
          hideLabel
          value={form.status}
          invalid={!!errors.status}
          invalidText={errors.status}
          disabled={adding || saving || loading}
          onChange={(e) => set('status', e.target.value)}
        >
          <SelectItem value="" text={loading ? 'Loading…' : ''} />
          {(loading ? withCurrent([], form.status, form.status) : statuses).map((o) => (
            <SelectItem key={o.code} value={o.code} text={o.description || o.code} />
          ))}
        </Select>
      </div>
    ),
    asOf: (
      <div className="detail-edit__input detail-edit__input--cell">
        <DateField
          id="ru-edit-as-of"
          labelText="As of"
          hideLabel
          value={form.asOf}
          disabled={saving}
          onChange={(v) => set('asOf', v)}
        />
      </div>
    ),
    rangeUnitName: (
      <div className="detail-edit__input detail-edit__input--cell">
        <TextInput
          id="ru-edit-name"
          labelText="Range Unit Name"
          hideLabel
          maxLength={30}
          value={form.rangeUnitName}
          invalid={!!errors.rangeUnitName}
          invalidText={errors.rangeUnitName}
          disabled={saving}
          onChange={(e) => set('rangeUnitName', e.target.value)}
        />
      </div>
    ),
    district: (
      <div className="detail-edit__input detail-edit__input--cell">
        <Select
          id="ru-edit-district"
          labelText="District"
          hideLabel
          value={form.district}
          invalid={!!errors.district}
          invalidText={errors.district}
          disabled={saving || loading || districtLocked}
          onChange={(e) => set('district', e.target.value)}
        >
          <SelectItem value="" text={loading ? 'Loading…' : ''} />
          {districts.map((o) => (
            <SelectItem key={o.code} value={o.code} text={o.description || o.code} />
          ))}
        </Select>
      </div>
    ),
    zone: (
      <div className="detail-edit__input detail-edit__input--cell">
        <TextInput
          id="ru-edit-zone"
          labelText="Zone"
          hideLabel
          maxLength={4}
          value={form.districtAdminZone}
          invalid={!!errors.districtAdminZone}
          invalidText={errors.districtAdminZone}
          disabled={saving}
          onChange={(e) => set('districtAdminZone', e.target.value.toUpperCase())}
        />
      </div>
    ),
  };

  return { editing, saving, start, cancel, save, district: form.district, inputs };
}
