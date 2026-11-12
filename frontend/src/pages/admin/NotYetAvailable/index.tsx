
import EmptyState from '@/components/EmptyState/EmptyState';
import SectionTile from '@/components/SectionTile';
import PageLayout from '@/pages/PageLayout';

import type { FC } from 'react';

/**
 * A legacy FTA screen whose menu entry is kept but which has not been ported
 * yet. It says so, rather than showing invented data.
 */
const NotYetAvailable: FC<{ title: string }> = ({ title }) => (
  <PageLayout title={title}>
    <SectionTile title={title}>
      <EmptyState
        title="Not available yet"
        body="This screen has not been moved to the new FTA yet."
      />
    </SectionTile>
  </PageLayout>
);

export default NotYetAvailable;
