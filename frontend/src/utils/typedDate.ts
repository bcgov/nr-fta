/**
 * A date typed into a date field, as `yyyy-mm-dd`, or null while it isn't a
 * whole real date yet. The dashes are optional: `20261006` reads as
 * `2026-10-06`.
 *
 * Carbon's date picker only takes a date picked from its calendar; text typed
 * into the box never reaches the form. Date fields pass their input's text
 * through this so typing works too.
 */
export function parseTypedDate(text: string): string | null {
  const m = /^(\d{4})-?(\d{2})-?(\d{2})$/.exec(text.trim());
  if (!m) return null;
  const [, y, mo, d] = m;
  const date = new Date(Number(y), Number(mo) - 1, Number(d));
  // Rejects 2026-02-30 and the like, which Date would roll into March.
  if (date.getMonth() !== Number(mo) - 1 || date.getDate() !== Number(d)) return null;
  return `${y}-${mo}-${d}`;
}

/** The input pattern for a date typed with or without dashes. */
export const TYPED_DATE_PATTERN = '\\d{4}-?\\d{2}-?\\d{2}';
