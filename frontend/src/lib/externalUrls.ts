import { env } from '@/env';

/** A configured URL, or null when the setting is blank. */
const configured = (value: string | undefined): string | null => value?.trim() || null;

/**
 * The GBS (government billing system) page FTA680's G.B.S. button opens: `VITE_GBS_URL`, set per
 * environment (legacy's `gbs-invoice-url`). Legacy redirected there without the invoice number
 * (GBS could not open one invoice), so every row's button opens GBS's main page. Null when the
 * environment has no GBS URL; the button is then disabled.
 */
export function gbsUrl(): string | null {
  return configured(env.VITE_GBS_URL);
}

/**
 * The SNC (Status and Clearance) page for an application's status request, as legacy
 * UrlUtils.generateSncUrl: `VITE_SNC_URL` (legacy's `sncsc-web.url`, set per environment)
 * followed by the request's URI. Null when the environment has no SNC URL.
 */
export function sncUrl(clearanceStatusRequestUri: string): string | null {
  const base = configured(env.VITE_SNC_URL);
  return base ? base + clearanceStatusRequestUri : null;
}
