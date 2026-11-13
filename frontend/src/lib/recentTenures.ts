import type { TenureDetail } from '@/services/tenure_detail';

/**
 * The tenures a user last opened, for the Tenures landing page. Kept in
 * localStorage (per browser, per user): nothing on the backend records tenure
 * access, and legacy FTA had no such list. Each entry is a snapshot of the
 * tombstone at the time of the visit, so it can be shown without a request;
 * reopening the tenure refreshes it.
 */
export interface RecentTenure {
  fileId: string;
  fileType: string | null;
  statusCode: string | null;
  statusDesc: string | null;
  licensee: string | null;
  orgUnit: string | null;
  /** ISO timestamp of the visit. */
  viewedAt: string;
}

export const MAX_RECENT_TENURES = 10;

// Scoped by user so a shared machine doesn't show one person's files to another.
const storageKey = (userName: string | undefined) =>
  `fta.recentTenures.${(userName ?? 'anonymous').toUpperCase()}`;

const isRecentTenure = (v: unknown): v is RecentTenure =>
  typeof v === 'object' &&
  v !== null &&
  typeof (v as RecentTenure).fileId === 'string' &&
  typeof (v as RecentTenure).viewedAt === 'string';

/** Most recent first; empty when storage is unavailable or holds junk. */
export function loadRecentTenures(userName: string | undefined): RecentTenure[] {
  try {
    const raw = localStorage.getItem(storageKey(userName));
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter(isRecentTenure).slice(0, MAX_RECENT_TENURES) : [];
  } catch {
    return [];
  }
}

/** Moves (or adds) the tenure to the top of the user's list. */
export function recordRecentTenure(userName: string | undefined, tenure: TenureDetail): void {
  const entry: RecentTenure = {
    fileId: tenure.forestFileId,
    fileType: tenure.fileTypeDesc || tenure.fileTypeCode,
    statusCode: tenure.fileStatusCode,
    statusDesc: tenure.fileStatusDesc,
    licensee: tenure.licensee,
    orgUnit: tenure.orgUnitDesc || tenure.orgUnitCode,
    viewedAt: new Date().toISOString(),
  };
  const others = loadRecentTenures(userName).filter(
    (t) => t.fileId.toUpperCase() !== entry.fileId.toUpperCase(),
  );
  try {
    localStorage.setItem(
      storageKey(userName),
      JSON.stringify([entry, ...others].slice(0, MAX_RECENT_TENURES)),
    );
  } catch {
    // Storage full or disabled: the list just won't remember this visit.
  }
}
