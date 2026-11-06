import { Add, ArrowLeft, Catalog, Document, Edit } from '@carbon/icons-react';
import { Button } from '@carbon/react';
import { useCallback, useMemo, useState, type FC } from 'react';
import { Link, useParams } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import DefinitionGrid from '@/components/DefinitionGrid';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { SectionTile } from '@/components/SectionTile';
import Tombstone from '@/components/Tombstone';
import UserName from '@/components/UserName';
import { useAuth } from '@/context/auth/useAuth';
import { useApiResource } from '@/hooks/useApiResource';
import CommitmentEditModal, {
  type CommitmentContext,
} from '@/pages/apt/Commitments/CommitmentEditModal';
import CommitmentsTable from '@/pages/apt/Commitments/CommitmentsTable';
import PageLayout from '@/pages/PageLayout';
import { canEditApt } from '@/routes/access';
import { aptPaths } from '@/routes/aptPaths';
import type { AptCommitment } from '@/services/apt/commitments';
import { getAptLicence, getAptLicenceCommitments } from '@/services/apt/licences';
import { formatDate } from '@/utils/formatDate';

import LicenceEditModal from './LicenceEditModal';

const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;
const date = (v: string | null | undefined) => formatDate(v) || '—';

/**
 * APT2's APTM4000 Licence: the tenure's facts, APT's FOA type, replacement and comments for it,
 * and its commitments. An APT administrator edits the licence fields and adds, edits and deletes
 * its commitments here; anyone else reads.
 */
const AptLicenceDetail: FC = () => {
  const { forestFileId = '' } = useParams();
  const { user } = useAuth();
  const canEdit = canEditApt(user);
  const [licenceOpen, setLicenceOpen] = useState(false);
  const [editing, setEditing] = useState<AptCommitment | null>(null);
  const [commitmentOpen, setCommitmentOpen] = useState(false);

  const licenceFetcher = useCallback(() => getAptLicence(forestFileId), [forestFileId]);
  const licence = useApiResource(licenceFetcher, [licenceFetcher]);
  const commitmentsFetcher = useCallback(
    () => getAptLicenceCommitments(forestFileId),
    [forestFileId],
  );
  const commitments = useApiResource(commitmentsFetcher, [commitmentsFetcher]);
  const l = licence.data;

  const context = useMemo<CommitmentContext | null>(
    () =>
      l
        ? {
            kind: 'licence',
            forestFileId: l.forestFileId,
            managementUnitType: l.aptMgmtUnitSt,
            managementUnitNumber: l.mgmtUnitNumber,
          }
        : null,
    [l],
  );

  const openCommitment = (c: AptCommitment | null) => {
    setEditing(c);
    setCommitmentOpen(true);
  };

  const mu = l ? [l.aptMgmtUnitSt, l.mgmtUnitNumber].filter(Boolean).join(' ') || null : null;

  return (
    <PageLayout
      title={`Licence ${l?.forestFileId ?? forestFileId}`}
      subtitle="The licence's apportionment details and commitments."
      backLink={
        <Link to={aptPaths.licences()} className="back-link">
          <ArrowLeft size={16} /> Back to Licence Search
        </Link>
      }
    >
      <AsyncBoundary
        loading={licence.loading}
        error={licence.error}
        onRetry={licence.reload}
        loadingText="Loading licence…"
      >
        {l && (
          <>
            <Tombstone
              ariaLabel="Licence summary"
              items={[
                { label: 'Forest File ID', value: l.forestFileId },
                { label: 'Licensee', value: dash(l.clientName) },
                { label: 'Controlling Co.', value: dash(l.controlCompany) },
                { label: 'Org. Unit', value: dash(l.orgUnitName) },
                { label: 'Management Unit', value: dash(l.mgmtUnitName ?? mu) },
                { label: 'File Status', value: dash(l.fileStatus) },
                { label: 'FOA Type', value: dash(l.aptFoaSt) },
              ]}
            />
            <SectionTile
              title="Licence"
              icon={Document}
              actions={
                canEdit ? (
                  <Button
                    kind="tertiary"
                    size="sm"
                    renderIcon={Edit}
                    onClick={() => setLicenceOpen(true)}
                  >
                    Edit licence
                  </Button>
                ) : undefined
              }
            >
              <DefinitionGrid
                items={[
                  { label: 'Management Unit Type', value: dash(l.aptMgmtUnitSt) },
                  { label: 'Management Unit No.', value: dash(l.mgmtUnitNumber) },
                  { label: 'File Status Date', value: date(l.fileStatusDate) },
                  { label: 'Legal Effective Date', value: date(l.legalEffectiveDt) },
                  { label: 'Initial Expiry Date', value: date(l.initialExpiryDt) },
                  { label: 'Current Expiry Date', value: date(l.currentExpiryDt) },
                  { label: 'Tenure Term', value: dash(l.tenureTerm) },
                  { label: 'FOA Type', value: dash(l.aptFoaSt) },
                  { label: 'Replacement Date', value: date(l.replacementDate) },
                  {
                    label: 'Replacement Term',
                    value: l.replacementTerm != null ? `${l.replacementTerm} months` : '—',
                  },
                  { label: 'Comments', value: dash(l.comments) },
                  {
                    label: 'Added',
                    value: l.addTimestamp ? (
                      <>
                        {date(l.addTimestamp)} by <UserName userId={l.addUserid} />
                      </>
                    ) : (
                      '—'
                    ),
                  },
                  {
                    label: 'Last Updated',
                    value: l.updateTimestamp ? (
                      <>
                        {date(l.updateTimestamp)} by <UserName userId={l.updateUserid} />
                      </>
                    ) : (
                      '—'
                    ),
                  },
                ]}
              />
            </SectionTile>
          </>
        )}
      </AsyncBoundary>

      {l && (
        <SectionTile
          title="Commitments"
          icon={Catalog}
          actions={
            canEdit ? (
              <Button
                kind="tertiary"
                size="sm"
                renderIcon={Add}
                onClick={() => openCommitment(null)}
              >
                New commitment
              </Button>
            ) : undefined
          }
        >
          <AsyncBoundary
            loading={commitments.loading}
            error={commitments.error}
            onRetry={commitments.reload}
            loadingText="Loading commitments…"
          >
            {commitments.data && commitments.data.length > 0 ? (
              <CommitmentsTable
                rows={commitments.data}
                variant="licence"
                canEdit={canEdit}
                onEdit={(c) => openCommitment(c)}
                onDeleted={commitments.refresh}
              />
            ) : (
              <EmptyState
                icon={<Catalog size={48} />}
                title="No commitments"
                body="This licence has no commitments."
              />
            )}
          </AsyncBoundary>
        </SectionTile>
      )}

      {canEdit && l && context && (
        <>
          <LicenceEditModal
            open={licenceOpen}
            onClose={() => setLicenceOpen(false)}
            licence={l}
            onSaved={licence.refresh}
          />
          <CommitmentEditModal
            open={commitmentOpen}
            commitment={editing}
            context={context}
            onClose={() => setCommitmentOpen(false)}
            onSaved={commitments.refresh}
          />
        </>
      )}
    </PageLayout>
  );
};

export default AptLicenceDetail;
