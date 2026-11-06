import { ArrowLeft, RecentlyViewed } from '@carbon/icons-react';
import { useCallback, useState, type FC } from 'react';
import { Link, useParams } from 'react-router-dom';

import { SectionTile } from '@/components/SectionTile';
import PageLayout from '@/pages/PageLayout';
import { aptPaths } from '@/routes/aptPaths';
import type { AptCommitmentHistory as Entry } from '@/services/apt/commitments';

import CommitmentHistoryView from './CommitmentHistoryView';

/**
 * APT2's APTM6200 Apportionment Commitment History, as a page. Goes back to the commitment's
 * licence (its journal says which), or to the licence search before that is known. A deleted
 * commitment keeps its history, so this still opens after a delete.
 */
const AptCommitmentHistory: FC = () => {
  const { commitmentId = '' } = useParams();
  const [forestFileId, setForestFileId] = useState<string | null>(null);
  const onLoaded = useCallback(
    (rows: Entry[]) => setForestFileId(rows.find((r) => r.forestFileId)?.forestFileId ?? null),
    [],
  );

  const back = forestFileId
    ? { path: aptPaths.licenceDetail(forestFileId), label: `Licence ${forestFileId}` }
    : { path: aptPaths.licences(), label: 'Licence Search' };

  return (
    <PageLayout
      title="Commitment History"
      subtitle={`Every change to commitment ${commitmentId}, newest first.`}
      backLink={
        <Link to={back.path} className="back-link">
          <ArrowLeft size={16} /> Back to {back.label}
        </Link>
      }
    >
      <SectionTile title="Journal" icon={RecentlyViewed}>
        <CommitmentHistoryView commitmentId={commitmentId} onLoaded={onLoaded} />
      </SectionTile>
    </PageLayout>
  );
};

export default AptCommitmentHistory;
