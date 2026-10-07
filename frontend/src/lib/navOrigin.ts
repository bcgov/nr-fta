import { useLocation } from 'react-router-dom';

/**
 * Where a detail page was reached from, carried in the router's location
 * state, so its back link returns there rather than to its usual list — e.g.
 * a private mark opened from Timber Mark Search goes back to that search, not
 * to Private Mark Applications.
 *
 * A search sets it with `navigate(to, { state: originState(...) })`.
 */
export interface NavOrigin {
  /** Path to return to. */
  path: string;
  /** Says where — the back link reads "Back to {label}". */
  label: string;
}

export const TIMBER_MARK_SEARCH_ORIGIN: NavOrigin = {
  path: '/search/timber-mark',
  label: 'Timber Mark Search',
};

export const originState = (origin: NavOrigin) => ({ origin });

/** The origin this page was opened from, or null when reached any other way. */
export function useNavOrigin(): NavOrigin | null {
  const { state } = useLocation();
  const origin = (state as { origin?: Partial<NavOrigin> } | null)?.origin;
  return typeof origin?.path === 'string' && typeof origin.label === 'string'
    ? { path: origin.path, label: origin.label }
    : null;
}
