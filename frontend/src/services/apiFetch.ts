import { env } from '@/env';

import { ensureSessionFresh } from '@/context/auth/refreshSession';
import { safeErrorMessage } from '@/lib/errorMessage';
import { ensureFreshUser, getUserManager } from '@/services/keycloak';

const API_BASE_URL = env.VITE_API_BASE_URL ?? '';

/**
 * Reads the current access token from the oidc-client-ts user store.
 *
 * `ensureFreshUser` returns the stored user, renewing only if the token is at
 * or near expiry, so this is the same call `ensureSessionFresh` just made and
 * is a no-op the second time. Reading the token off the returned user — rather
 * than fetching it separately — is what guarantees the header carries the token
 * that was just renewed, not the one it replaced.
 */
const getAccessToken = async (): Promise<string | undefined> => {
  try {
    const user = await ensureFreshUser(getUserManager());
    return user?.access_token;
  } catch {
    return undefined;
  }
};

/**
 * Fetch wrapper that refreshes the session if its access token is near
 * expiry, attaches the current access token as a Bearer header, and prefixes
 * paths with the configured API base URL.
 *
 * Pair every service-layer call with this helper rather than calling
 * fetch() directly — the ensureSessionFresh pre-check is what lets idle
 * tabs survive past the 5-minute access token TTL.
 */
export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  await ensureSessionFresh();

  const token = await getAccessToken();
  const headers = new Headers(init.headers);
  if (token) headers.set('Authorization', `Bearer ${token}`);
  if (!headers.has('Accept')) headers.set('Accept', 'application/json');

  const url = /^https?:/.test(path) ? path : `${API_BASE_URL}${path}`;
  return fetch(url, { ...init, headers });
}

/**
 * Pulls the user-facing message out of an error response body. Handles
 * both shapes the backend emits:
 * <ul>
 *   <li>Spring's RFC7807 {@code ProblemDetail} —
 *       {@code {type, title, status, detail, instance}} — where the
 *       human-readable text is in {@code detail} (falling back to
 *       {@code title}).</li>
 *   <li>The legacy {@code RestExceptionHandler} shape —
 *       {@code {status, timestamp, message, ...}}.</li>
 * </ul>
 * We try {@code detail}, then {@code message}, then {@code title} so the
 * toast subtitle gets a clean sentence rather than the raw JSON. Falls
 * back to the raw text for non-JSON bodies (e.g. a plain 502 from a
 * proxy or an unhandled exception that surfaces as text).
 */
export async function readErrorMessage(res: Response): Promise<string> {
  const raw = await res.text().catch(() => '');
  if (!raw) return '';
  let candidate = '';
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (parsed && typeof parsed === 'object') {
      const fields = parsed as Record<string, unknown>;
      for (const key of ['detail', 'message', 'title'] as const) {
        const val = fields[key];
        if (typeof val === 'string' && val.trim()) {
          candidate = val.trim();
          break;
        }
      }
      // A JSON body with no message field (e.g. Spring's default
      // {timestamp,status,error,path}) has nothing human-readable —
      // leave `candidate` empty so the caller uses its own fallback.
    } else if (typeof parsed === 'string') {
      candidate = parsed;
    }
  } catch {
    // Body wasn't JSON — the raw text is the only candidate.
    candidate = raw;
  }
  // Guard the choke point: never hand a caller raw SQL/ORA/stack-trace or
  // an error-JSON dump. Technical text collapses to '' so the caller's
  // clean fallback message wins.
  return safeErrorMessage(candidate, '');
}
