import { Loading } from '@carbon/react';

import { useUserName } from '@/hooks/useUserName';

import type { FC } from 'react';


import './UserName.css';

interface Props {
  /** A user id as the records hold it (`IDIR\JSMITH`, or bare). */
  userId: string | null | undefined;
  /** Shown when {@code userId} is blank. Defaults to an em dash. */
  fallback?: string;
  className?: string;
}

/**
 * Renders a user id as a resolved display name. Shows the raw id with a small
 * spinner to its right while the (batched, session-cached) lookup is in
 * flight, then swaps to the name and hides the spinner. Falls back to the raw
 * id if it can't be resolved. Drop-in for any place a raw login is shown —
 * safe even on fields that already hold a real name (the lookup just misses
 * and the original value is displayed).
 */
const UserName: FC<Props> = ({ userId, fallback = '—', className }) => {
  const { text, loading } = useUserName(userId);
  if (!(userId ?? '').trim()) return <>{fallback}</>;
  return (
    // Once the name shows, the IDIR id is still a hover away.
    <span
      className={`user-name${className ? ` ${className}` : ''}`}
      title={text !== userId ? (userId ?? undefined) : undefined}
    >
      <span className="user-name__text">{text}</span>
      {loading && (
        <Loading
          small
          withOverlay={false}
          className="user-name__spinner"
          description="Looking up name"
        />
      )}
    </span>
  );
};

export default UserName;
