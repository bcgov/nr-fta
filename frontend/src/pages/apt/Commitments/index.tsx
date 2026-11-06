import { Add, ArrowLeft, Catalog } from '@carbon/icons-react';
import { Button } from '@carbon/react';
import { useCallback, useMemo, useState, type FC } from 'react';
import { Link, useParams } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { SectionTile } from '@/components/SectionTile';
import Tombstone from '@/components/Tombstone';
import { useAuth } from '@/context/auth/useAuth';
import { useApiResource } from '@/hooks/useApiResource';
import PageLayout from '@/pages/PageLayout';
import { canEditApt } from '@/routes/access';
import { aptPaths } from '@/routes/aptPaths';
import { searchAptCommitments, type AptCommitment } from '@/services/apt/commitments';
import {
  getAptManagementUnit,
  getAptManagementUnitHistory,
  getAptPartitionApportionments,
  getAptPartitions,
} from '@/services/apt/managementUnits';
import { formatDate } from '@/utils/formatDate';

import CommitmentEditModal, { type CommitmentContext } from './CommitmentEditModal';
import CommitmentsTable from './CommitmentsTable';

const nf = new Intl.NumberFormat('en-CA');
const num = (v: number | null | undefined) => (v == null ? '—' : nf.format(v));

/**
 * The management unit, determination, partition and apportionment a commitments page is for.
 * Each is read by the id in the path; a missing one fails the page.
 */
async function loadHeader(muId: string, historyId: string, apportionmentId: string) {
  const [mu, histories, apportionments] = await Promise.all([
    getAptManagementUnit(muId),
    getAptManagementUnitHistory(muId, historyId),
    getAptPartitionApportionments({ id: apportionmentId }),
  ]);
  const apportionment = apportionments[0];
  if (!apportionment) throw new Error(`Apportionment ${apportionmentId} was not found.`);
  const partition = (await getAptPartitions({ id: apportionment.partitionId }))[0];
  if (!partition) throw new Error(`Partition ${apportionment.partitionId} was not found.`);
  return { mu, history: histories[0] ?? null, partition, apportionment };
}

/**
 * APT2's APTM6000 Query/Maintain Commitments: the commitments of a partition apportionment's
 * licences, under the management unit, partition and apportionment AACs they share. Reached
 * from a determination's partitions.
 *
 * As in APT2 the list is every commitment of the unit's licences of the apportionment's FOA
 * type, whatever their partition. A new commitment takes this partition and apportionment; an
 * edited one keeps its own (APT2 overwrote them with this page's).
 */
const AptCommitments: FC = () => {
  const { muId = '', historyId = '', apportionmentId = '' } = useParams();
  const { user } = useAuth();
  const canEdit = canEditApt(user);
  const [editing, setEditing] = useState<AptCommitment | null>(null);
  const [editOpen, setEditOpen] = useState(false);

  const headerFetcher = useCallback(
    () => loadHeader(muId, historyId, apportionmentId),
    [muId, historyId, apportionmentId],
  );
  const header = useApiResource(headerFetcher, [headerFetcher]);
  const h = header.data;

  const muType = h?.mu.aptMgmtUnitSt;
  const muNumber = h?.mu.mgmtUnitNumber;
  const foaType = h?.apportionment.foaTypeCode;
  const listFetcher = useCallback(
    () =>
      muType && foaType
        ? searchAptCommitments({
            managementUnitType: muType,
            managementUnitNumber: muNumber,
            foaType,
          })
        : Promise.resolve<AptCommitment[]>([]),
    [muType, muNumber, foaType],
  );
  const list = useApiResource(listFetcher, [listFetcher]);

  const context = useMemo<CommitmentContext | null>(
    () =>
      h
        ? {
            kind: 'apportionment',
            managementUnitId: h.mu.id,
            foaType: h.apportionment.foaTypeCode,
            partitionCode: h.partition.partitionCode,
            partitionLabel: h.partition.partitionDescription ?? h.partition.partitionCode,
            paptId: h.apportionment.id,
          }
        : null,
    [h],
  );

  const openEdit = (c: AptCommitment | null) => {
    setEditing(c);
    setEditOpen(true);
  };

  return (
    <PageLayout
      title="Commitments"
      subtitle="Commitments of the apportionment's licences."
      backLink={
        <Link to={aptPaths.managementUnitDetail(muId, historyId)} className="back-link">
          <ArrowLeft size={16} /> Back to Partitions
        </Link>
      }
    >
      <AsyncBoundary
        loading={header.loading}
        error={header.error}
        onRetry={header.reload}
        loadingText="Loading apportionment…"
      >
        {h && (
          <>
            <Tombstone
              ariaLabel="Apportionment summary"
              items={[
                {
                  label: 'Management Unit',
                  value: (
                    <Link to={aptPaths.managementUnitHistory(h.mu.id)}>
                      {h.mu.mgmtUnitName ?? `${h.mu.aptMgmtUnitSt} ${h.mu.mgmtUnitNumber}`}
                    </Link>
                  ),
                },
                { label: 'Unit AAC (m³)', value: num(h.history?.allowableAnnualCut) },
                {
                  label: 'Determination',
                  value: formatDate(h.history?.determinationDate) || '—',
                },
                {
                  label: 'Partition',
                  value: h.partition.partitionDescription ?? h.partition.partitionCode,
                },
                { label: 'Partition AAC (m³)', value: num(h.partition.allowableAnnualCut) },
                {
                  label: 'Apportionment',
                  value: h.apportionment.foaTypeName ?? h.apportionment.foaTypeCode,
                },
                {
                  label: 'Apportionment AAC (m³)',
                  value: num(h.apportionment.allowableAnnualCut),
                },
              ]}
            />
            <SectionTile
              title="Commitments"
              icon={Catalog}
              actions={
                canEdit ? (
                  <Button kind="tertiary" size="sm" renderIcon={Add} onClick={() => openEdit(null)}>
                    New commitment
                  </Button>
                ) : undefined
              }
            >
              <AsyncBoundary
                loading={list.loading}
                error={list.error}
                onRetry={list.reload}
                loadingText="Loading commitments…"
              >
                {list.data && list.data.length > 0 ? (
                  <CommitmentsTable
                    rows={list.data}
                    variant="apportionment"
                    canEdit={canEdit}
                    onEdit={(c) => openEdit(c)}
                    onDeleted={list.refresh}
                  />
                ) : (
                  <EmptyState
                    icon={<Catalog size={48} />}
                    title="No commitments"
                    body="No licence of this apportionment's FOA type has a commitment."
                  />
                )}
              </AsyncBoundary>
            </SectionTile>
          </>
        )}
      </AsyncBoundary>

      {canEdit && context && (
        <CommitmentEditModal
          open={editOpen}
          commitment={editing}
          context={context}
          onClose={() => setEditOpen(false)}
          onSaved={list.refresh}
        />
      )}
    </PageLayout>
  );
};

export default AptCommitments;
