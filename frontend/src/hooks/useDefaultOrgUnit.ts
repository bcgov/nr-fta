import { useEffect, useMemo, useState, type SetStateAction } from 'react';

import type { CodeOption } from '@/services/codeLists';
import { getUserOrgUnitDefault, type UserOrgUnitDefault } from '@/services/org_unit_maint';

/**
 * The signed-in user's default org unit (Org Unit Maintenance), or undefined until it has loaded
 * or when it could not be read — a screen then simply starts without it, as nothing depends on
 * it but a preselected district.
 */
export function useDefaultOrgUnit(): UserOrgUnitDefault | undefined {
  const [value, setValue] = useState<UserOrgUnitDefault | undefined>(undefined);
  useEffect(() => {
    let cancelled = false;
    getUserOrgUnitDefault()
      .then((d) => !cancelled && setValue(d))
      .catch(() => {
        // Not worth a toast: the screen works without a default.
      });
    return () => {
      cancelled = true;
    };
  }, []);
  return value;
}

/**
 * The user's default org unit when it is one of `options`, else undefined. With `districtOnly`,
 * only a district-level default counts (legacy's "if user's org unit is a district").
 */
export function useDefaultOrgUnitIn(
  options: CodeOption[],
  districtOnly = false,
): string | undefined {
  const d = useDefaultOrgUnit();
  const no = d?.orgUnitNo ?? undefined;
  if (!no || (districtOnly && d?.orgLevelCode !== 'D')) return undefined;
  return options.some((o) => o.code === no) ? no : undefined;
}

/**
 * A search form's district field started at the user's default org unit, as legacy's
 * setDefaults put the user's org unit in a blank district on a new or cleared form.
 *
 * Fills the field once the default and the options have loaded, if the form is still untouched
 * (equal to `empty`). Returns `empty` with the default in it, for the screen's Clear to reset to.
 */
export function useFormDistrictDefault<F extends object>(
  form: F,
  setForm: (value: SetStateAction<F>) => void,
  empty: F,
  field: keyof F,
  options: CodeOption[],
  districtOnly = false,
): F {
  const orgUnitNo = useDefaultOrgUnitIn(options, districtOnly);
  const start = useMemo(
    () => (orgUnitNo ? { ...empty, [field]: orgUnitNo } : empty),
    [empty, field, orgUnitNo],
  );
  const untouched = JSON.stringify(form) === JSON.stringify(empty);
  useEffect(() => {
    if (orgUnitNo && untouched) setForm((prev) => (prev[field] ? prev : start));
    // Only when the default arrives: a field the user empties afterwards stays empty.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [orgUnitNo]);
  return start;
}
