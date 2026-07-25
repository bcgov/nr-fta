import { apiFetch, readErrorMessage } from './apiFetch';

/**
 * Typed JSON helpers over {@link apiFetch}. Every FTA service module calls
 * these rather than fetch() directly — they attach the bearer token + refresh
 * (via apiFetch), the CSRF header on writes, parse JSON, and turn non-2xx
 * responses into thrown Errors carrying the backend's message.
 */

function readXsrfToken(): string {
  const match = document.cookie.split(';').find((c) => c.trim().startsWith('XSRF-TOKEN='));
  return match ? decodeURIComponent(match.split('=')[1] ?? '') : '';
}

async function parse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const msg = await readErrorMessage(res);
    throw new Error(msg || `Request failed (${res.status})`);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

/** Build a querystring from a params object, dropping empty/undefined values. */
export function toQuery(params: Record<string, string | number | boolean | undefined | null>): string {
  const entries = Object.entries(params).filter(
    ([, v]) => v !== undefined && v !== null && v !== '',
  );
  if (entries.length === 0) return '';
  const qs = new URLSearchParams(entries.map(([k, v]) => [k, String(v)]));
  return `?${qs.toString()}`;
}

export async function apiGet<T>(path: string): Promise<T> {
  return parse<T>(await apiFetch(path, { method: 'GET' }));
}

async function writeJson<T>(method: 'POST' | 'PUT' | 'DELETE', path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { 'X-XSRF-TOKEN': readXsrfToken() };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  return parse<T>(
    await apiFetch(path, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    }),
  );
}

export const apiPost = <T>(path: string, body?: unknown) => writeJson<T>('POST', path, body);
export const apiPut = <T>(path: string, body?: unknown) => writeJson<T>('PUT', path, body);
export const apiDelete = <T>(path: string, body?: unknown) => writeJson<T>('DELETE', path, body);
