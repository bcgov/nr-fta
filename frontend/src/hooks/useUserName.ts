import { useEffect, useSyncExternalStore } from 'react';

import { peek, request, subscribe } from '@/lib/userNameStore';

export interface UserNameState {
  /** Text to display — `Display Name (USERNAME)`, or just `USERNAME` if unresolved. */
  text: string;
  /** True while the lookup is in flight (caller shows a spinner). */
  loading: boolean;
}

/**
 * The IDIR username without its domain: `IDIR\jsmith` gives `JSMITH`. Any other
 * value (a bare id, or a field that already holds a name) is shown as stored.
 */
export function idirUsername(id: string): string {
  const match = /^idir\\(.+)$/i.exec(id);
  return match ? match[1].trim().toUpperCase() : id;
}

/**
 * Resolves a single user id (`IDIR\JSMITH`) to `Jane Smith (JSMITH)` via the
 * shared session cache. Shows the username immediately (with
 * {@code loading: true}) and swaps to the full text once the batched lookup
 * lands. Cache hits return synchronously with no loading flash. Ids that
 * can't be resolved stay as the username, with {@code loading: false}.
 */
export function useUserName(rawId: string | null | undefined): UserNameState {
  const id = (rawId ?? '').trim();

  const cached = useSyncExternalStore(
    subscribe,
    () => (id ? peek(id) : ''),
    () => (id ? peek(id) : ''),
  );

  useEffect(() => {
    if (id) request(id);
  }, [id]);

  if (!id) return { text: '', loading: false };
  const username = idirUsername(id);
  if (cached === undefined) return { text: username, loading: true };
  // '' → looked up but unresolved: the username alone.
  return { text: cached === '' ? username : `${cached} (${username})`, loading: false };
}
