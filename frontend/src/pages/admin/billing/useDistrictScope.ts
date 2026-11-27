import { useMemo } from 'react';

import { useAuth } from '@/context/auth/useAuth';
import { isRangeHeadquartersAdmin } from '@/routes/access';
import { orgUnitCodeOf, type CodeOption } from '@/services/codeLists';

export interface DistrictScope {
  /**
   * False for a district-scoped administrator (FTA_ADMIN_DISTRICT-xxx) — unless, on a range
   * screen, they also hold FTA_RANGE_HEADQUARTERS_ADMIN.
   */
  unscoped: boolean;
  /** The district options the user may pick: all of them, or only the ones they administer. */
  options: CodeOption[];
  /** The district to preselect and lock: set when the user administers exactly one. */
  locked: CodeOption | undefined;
}

/**
 * Which districts a billing or audit screen offers. Legacy defaulted a district user's org unit
 * and protected it; here a district-scoped administrator sees only their districts, and one with
 * a single district gets it preselected and locked. The backend refuses any other.
 *
 * On the range billing screens (`kind` 'range', the default) FTA_RANGE_HEADQUARTERS_ADMIN acts for every
 * district, as the backend's BillingDistrictScope; the Audit Report ('admin') goes by the
 * FTA_ADMIN grant alone.
 */
export function useDistrictScope(
  all: CodeOption[],
  kind: 'range' | 'admin' = 'range',
): DistrictScope {
  const { user } = useAuth();
  const everyDistrict = kind === 'range' && isRangeHeadquartersAdmin(user);
  const scoped = everyDistrict ? null : user?.privileges?.FTA_ADMIN;
  return useMemo(() => {
    if (!scoped) return { unscoped: true, options: all, locked: undefined };
    const options = all.filter((o) => scoped.includes(orgUnitCodeOf(o)));
    return {
      unscoped: false,
      options,
      locked: scoped.length === 1 && options.length === 1 ? options[0] : undefined,
    };
  }, [all, scoped]);
}

export { currentYear } from '@/lib/years';
