import type { AptManagementUnit } from '@/services/apt/managementUnits';
import { formatDate } from '@/utils/formatDate';

/**
 * Bits the management-unit, history and partition screens share: display formatting, the
 * number checks APT2 made (against the NUMBER(9) and NUMBER(9,2) columns). The date field is
 * DateField.tsx.
 */

const nf = new Intl.NumberFormat('en-CA', { maximumFractionDigits: 2 });

/** An em dash for nothing, else the value. */
export const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;

/** A number with thousands separators, or an em dash. */
export const num = (v: number | null | undefined) =>
  v === null || v === undefined ? '—' : nf.format(v);

/** A date as the app shows dates, or an em dash. */
export const day = (v: string | null | undefined) => formatDate(v) || '—';

/** A number's text for an input: '' for none. */
export const numText = (v: number | null | undefined) =>
  v === null || v === undefined ? '' : String(v);

/** NUMBER(9): AAC, AAC (ha), long term harvest level. */
const WHOLE = /^\d{1,9}$/;
/** NUMBER(9,2): the areas. */
const AREA = /^\d{1,7}(\.\d{1,2})?$/;

const clean = (text: string) => text.trim().replace(/,/g, '');

/** A typed number, or null when blank. Call after the field passed its check. */
export const toNumber = (text: string): number | null => {
  const t = clean(text);
  return t === '' ? null : Number(t);
};

/**
 * Null when `text` is a valid whole number from 0 to 999,999,999 (or blank and optional),
 * else the message.
 */
export function wholeProblem(label: string, text: string, required = false): string | null {
  const t = clean(text);
  if (t === '') return required ? `${label} is required.` : null;
  return WHOLE.test(t) ? null : `${label} must be a whole number from 0 to 999,999,999.`;
}

/** Null when `text` is an area from 0 to 9,999,999.99 (or blank), else the message. */
export function areaProblem(label: string, text: string): string | null {
  const t = clean(text);
  if (t === '') return null;
  return AREA.test(t) ? null : `${label} must be from 0 to 9,999,999.99, with up to 2 decimals.`;
}

/** The comments columns are VARCHAR2(2000). */
export const MAX_COMMENTS = 2000;

/** How a unit reads in a list: `U 01 - Arrow TSA`. */
export const unitLabel = (u: AptManagementUnit) =>
  `${u.aptMgmtUnitSt} ${u.mgmtUnitNumber}${u.mgmtUnitName ? ` - ${u.mgmtUnitName}` : ''}`;
