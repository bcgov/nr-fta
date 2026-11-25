/**
 * Saves rows the page already holds as a CSV file, written as the backend's CsvWriter writes
 * its exports: RFC 4180 quoting, CRLF line ends, a UTF-8 byte order mark (so Excel reads
 * accented names), and an apostrophe before a value starting `=`, `+`, `@` or a tab, so a
 * spreadsheet shows it rather than running it as a formula.
 */

export type CsvValue = string | number | boolean | null | undefined;

/** One CSV field: the value as text, quoted when it has to be. */
export function csvField(value: CsvValue): string {
  if (value === null || value === undefined) return '';
  let text = String(value);
  if (text === '') return '';
  if ('=+@\t'.includes(text[0])) text = `'${text}`;
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

/** The file's text: the header row, then one line per row. */
export function toCsv(header: string[], rows: CsvValue[][]): string {
  return [header, ...rows].map((r) => r.map(csvField).join(',')).join('\r\n') + '\r\n';
}

/** Saves the rows as `<baseName>-<yyyy-mm-dd>.csv`, as the server names its exports. */
export function downloadRowsAsCsv(
  baseName: string,
  header: string[],
  rows: CsvValue[][],
  today = new Date(),
): void {
  const day = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(
    today.getDate(),
  ).padStart(2, '0')}`;
  const blob = new Blob(['﻿', toCsv(header, rows)], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  try {
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `${baseName}-${day}.csv`;
    // Firefox only honours a click on an element that is in the document.
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
  } finally {
    URL.revokeObjectURL(url);
  }
}
