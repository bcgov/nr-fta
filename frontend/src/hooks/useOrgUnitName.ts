import { useCallback, useEffect, useState } from 'react';

import { getAllOrgUnits } from '@/services/codeLists';

/**
 * Names an org unit in full ("DCC - Cariboo-Chilcotin Natural Resource District") from what a
 * record stores: its ORG_UNIT_NO or its ORG_UNIT_CODE. Until the list loads, or for a unit it
 * doesn't know, the stored value is shown as is; an empty value is an em dash.
 */
export function useOrgUnitName(): (value: string | number | null | undefined) => string {
  const [names, setNames] = useState<Map<string, string>>(new Map());

  useEffect(() => {
    let cancelled = false;
    getAllOrgUnits()
      .then((units) => {
        if (cancelled) return;
        const next = new Map<string, string>();
        for (const u of units) {
          next.set(u.code, u.description);
          // The description starts "CODE - ", so it is found by the code as well.
          const code = u.description.split(' - ')[0];
          if (code && !next.has(code)) next.set(code, u.description);
        }
        setNames(next);
      })
      .catch(() => {
        // Not worth a toast: the codes still show.
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return useCallback(
    (value) => {
      if (value == null || value === '') return '—';
      const key = String(value);
      return names.get(key) ?? key;
    },
    [names],
  );
}
