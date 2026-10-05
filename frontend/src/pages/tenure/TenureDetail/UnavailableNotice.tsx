import { InlineNotification } from '@carbon/react';

import type { FC } from 'react';

interface Props {
  /** The backend's notices for lists it could not read (a table missing from this database). */
  notices: string[] | null | undefined;
}

/**
 * A warning above a tenure tab when some of its lists could not be read because this
 * database lacks a table they need, so an empty list isn't mistaken for "no records".
 */
const UnavailableNotice: FC<Props> = ({ notices }) =>
  notices && notices.length > 0 ? (
    <InlineNotification
      kind="warning"
      lowContrast
      hideCloseButton
      title="Some data isn't available"
      subtitle={notices.join(' ')}
    />
  ) : null;

export default UnavailableNotice;
