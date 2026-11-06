/**
 * Column sorting for the search results tables.
 *
 * A sort is carried as the backend's `sort` request parameter: `<columnKey>,asc` or
 * `<columnKey>,desc`, where the key is the results table's column key (the DTO field).
 * Server-paged searches send it with the criteria, so the order spans every page and the
 * CSV export comes out in the same order; the backend ignores a key it doesn't allow and
 * keeps its default order. Searches that hold every row in the browser sort with
 * {@link sortRows} instead.
 *
 * Clicking a header cycles ascending → descending → off (back to the search's own
 * default order), as Carbon's own sortable tables do.
 */

export type SortDirection = 'ASC' | 'DESC';

export interface ColumnSort {
  key: string;
  direction: SortDirection;
}

/** `clientName,desc` → `{ key: 'clientName', direction: 'DESC' }`; null when unset or malformed. */
export function parseColumnSort(sort: string | null | undefined): ColumnSort | null {
  if (!sort) return null;
  const [key, dir = 'asc'] = sort.split(',');
  if (!key) return null;
  return { key, direction: dir.toLowerCase() === 'desc' ? 'DESC' : 'ASC' };
}

/** The `sort` parameter after clicking `key`'s header: asc → desc → off. */
export function nextColumnSort(
  current: string | null | undefined,
  key: string,
): string | undefined {
  const sort = parseColumnSort(current);
  if (!sort || sort.key !== key) return `${key},asc`;
  if (sort.direction === 'ASC') return `${key},desc`;
  return undefined;
}

/**
 * Props for a Carbon `TableHeader` that is sorted by the page rather than by
 * `DataTable` itself: spread after `getHeaderProps(...)`. Don't put `isSortable` on
 * the `DataTable` — it would re-sort the rows of the current page on its own.
 */
export function sortHeaderProps(
  sort: string | null | undefined,
  key: string,
  onSort: (key: string) => void,
) {
  const current = parseColumnSort(sort);
  const active = current?.key === key;
  return {
    isSortable: true,
    isSortHeader: active,
    sortDirection: active ? current.direction : ('NONE' as const),
    onClick: () => onSort(key),
  };
}

const collator = new Intl.Collator(undefined, { numeric: true, sensitivity: 'base' });

/**
 * Rows sorted in the browser, for a table that holds every result. Blank values sort
 * last in both directions, as the server-side sort puts nulls last. Strings compare
 * case-insensitively with numeric runs as numbers ("TSA 2" before "TSA 10"); ISO dates
 * compare correctly as strings. Stable, so equal values keep their original order.
 */
export function sortRows<T>(
  rows: readonly T[],
  sort: string | null | undefined,
  valueOf: (row: T, key: string) => unknown = (row, key) => (row as Record<string, unknown>)[key],
): T[] {
  const parsed = parseColumnSort(sort);
  if (!parsed) return [...rows];
  const sign = parsed.direction === 'ASC' ? 1 : -1;
  const blank = (v: unknown) => v === null || v === undefined || v === '';
  return [...rows].sort((a, b) => {
    const va = valueOf(a, parsed.key);
    const vb = valueOf(b, parsed.key);
    if (blank(va) || blank(vb)) return blank(va) === blank(vb) ? 0 : blank(va) ? 1 : -1;
    if (typeof va === 'number' && typeof vb === 'number') return sign * (va - vb);
    return sign * collator.compare(String(va), String(vb));
  });
}
