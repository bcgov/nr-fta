import { useCallback, useEffect, useState } from 'react';

import { getManagementUnitTypes, type AptCodeOption } from '@/services/apt/lookups';

/**
 * Management unit type descriptions ("Timber Supply Area" for U), which the APT screens show in
 * place of the code. The list is read once and shared; if it can't be read, the code shows.
 */

let loading: Promise<AptCodeOption[]> | null = null;

/** The type list, fetched once for every screen. */
export function loadMuTypes(): Promise<AptCodeOption[]> {
  loading ??= getManagementUnitTypes().catch((e: unknown) => {
    loading = null;
    throw e;
  });
  return loading;
}

/** A type's description from the list, else the code; null for no code. */
export const muTypeName = (types: AptCodeOption[], code: string | null | undefined) =>
  code ? types.find((t) => t.code === code)?.description || code : null;

/** A function giving a type code's description, once the list has loaded (the code until then). */
export function useMuTypeName(): (code: string | null | undefined) => string | null {
  const [types, setTypes] = useState<AptCodeOption[]>([]);
  useEffect(() => {
    let cancelled = false;
    loadMuTypes()
      .then((t) => {
        if (!cancelled) setTypes(t);
      })
      .catch(() => {
        // The code stays on show.
      });
    return () => {
      cancelled = true;
    };
  }, []);
  return useCallback((code) => muTypeName(types, code), [types]);
}
