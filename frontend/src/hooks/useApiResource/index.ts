import { useCallback, useEffect, useState } from 'react';

export interface ApiResource<T> {
  data: T | undefined;
  loading: boolean;
  error: string | undefined;
  /** Re-run the fetch (e.g. a Retry button or after a mutation). */
  reload: () => void;
}

/**
 * Runs an async fetch on mount (and whenever `deps` change) and exposes
 * {data, loading, error, reload}. The shared loading/error contract every FTA
 * screen uses when reading from the backend, so pages don't each re-implement
 * it. `fetcher` must be stable or memoized by the caller via `deps`.
 */
export function useApiResource<T>(
  fetcher: () => Promise<T>,
  deps: unknown[] = [],
): ApiResource<T> {
  const [data, setData] = useState<T | undefined>(undefined);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | undefined>(undefined);
  const [nonce, setNonce] = useState(0);

  const reload = useCallback(() => setNonce((n) => n + 1), []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(undefined);
    fetcher()
      .then((result) => {
        if (!cancelled) setData(result);
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Something went wrong');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, nonce]);

  return { data, loading, error, reload };
}
