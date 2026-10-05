import { apiPost } from './http';

/**
 * Display names for user ids as FTA's records hold them (`IDIR\JSMITH`), from
 * the backend's nr-user-lookup-api resolver. Keyed by each id exactly as sent;
 * ids it couldn't resolve are absent. Callers go through `lib/userNameStore`,
 * which batches every id on screen into one request.
 */
export const resolveUserNames = (userIds: string[]): Promise<Record<string, string>> =>
  apiPost<Record<string, string>>('/api/fta/users/resolve', { userIds });
