import type { AptRelatedClient } from '@/services/apt/linkages';

/** A controlling corporation and the relationships under it. */
export interface ParentGroup {
  clientNumber: string;
  clientName: string | null;
  children: AptRelatedClient[];
}

/**
 * Groups relationships by controlling corporation, as APT2 listed them. The backend's order
 * (corporation name, then licensee name) is kept, both for the groups and within them.
 */
export function groupByParent(rows: AptRelatedClient[]): ParentGroup[] {
  const groups = new Map<string, ParentGroup>();
  for (const row of rows) {
    let group = groups.get(row.parentClientNumber);
    if (!group) {
      group = {
        clientNumber: row.parentClientNumber,
        clientName: row.parentClientName,
        children: [],
      };
      groups.set(row.parentClientNumber, group);
    }
    group.children.push(row);
  }
  return [...groups.values()];
}

/** Whether either client's number or name contains the filter text (case-insensitive). */
export function matchesFilter(row: AptRelatedClient, filter: string): boolean {
  const f = filter.trim().toUpperCase();
  if (!f) return true;
  return [row.clientNumber, row.clientName, row.parentClientNumber, row.parentClientName].some(
    (v) => (v ?? '').toUpperCase().includes(f),
  );
}
