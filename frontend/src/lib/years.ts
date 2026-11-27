/**
 * The year lists the admin and report screens offer in place of a typed year.
 */

/** The current calendar year, as legacy's setDefaults put in the Year fields. */
export const currentYear = (): string => String(new Date().getFullYear());

/**
 * The earliest billing year offered. Range billing requests exist from 2001; the billing
 * screens (680, 685, 686, 690, 695) refuse a future year, so they offer this year back to here.
 */
export const FIRST_BILLING_YEAR = 2000;

/**
 * The earliest expiry year Archive Tenures (640) offers. Legacy accepted any four-digit year, and
 * a long-expired tenure still active is exactly what it is for, so the list reaches back further.
 */
export const FIRST_ARCHIVE_YEAR = 1980;

/** Every year from `last` down to `first`, newest first, as strings. */
export function yearOptions(first: number, last: number = Number(currentYear())): string[] {
  const years: string[] = [];
  for (let y = last; y >= first; y--) years.push(String(y));
  return years;
}
