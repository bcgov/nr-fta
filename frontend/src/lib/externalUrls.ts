import { env } from '@/env';

/**
 * Production GBS (the government billing system), as legacy's configure.prod.properties set
 * `gbs-invoice-url`.
 */
export const GBS_PROD_URL = 'https://a100.gov.bc.ca/int/gbms/GBSMainServlet?GBS04CLINUM=';

/**
 * The GBS page FTA680's G.B.S. button opens: `VITE_GBS_URL` when set, else production. Legacy
 * redirected to `gbs-invoice-url` without the invoice number (GBS could not open one invoice), so
 * every row's button opens GBS's main page.
 */
export function gbsUrl(): string {
  const configured = env.VITE_GBS_URL?.trim();
  return configured || GBS_PROD_URL;
}

/**
 * Production SNC (Status and Clearance) web, as legacy's configure.prod.properties set
 * `sncsc-web.url`.
 */
export const SNC_PROD_URL = 'https://apps.nrs.gov.bc.ca/int/sncsc-web/viewConflicts/';

/**
 * The SNC page for an application's status request, as legacy UrlUtils.generateSncUrl: the SNC
 * web URL (`VITE_SNC_URL` when set, else production) followed by the request's URI.
 */
export function sncUrl(clearanceStatusRequestUri: string): string {
  const base = env.VITE_SNC_URL?.trim() || SNC_PROD_URL;
  return base + clearanceStatusRequestUri;
}
