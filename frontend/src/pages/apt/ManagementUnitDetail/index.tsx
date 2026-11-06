import { Add, ArrowLeft, Edit, Partnership, TableSplit, WarningFilled } from '@carbon/icons-react';
import {
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TableSelectRow,
} from '@carbon/react';
import { useCallback, useEffect, useState, type FC } from 'react';
import { Link, useParams } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { SectionTile } from '@/components/SectionTile';
import Tombstone from '@/components/Tombstone';
import { useAuth } from '@/context/auth/useAuth';
import { useApiResource } from '@/hooks/useApiResource';
import { dash, day, num } from '@/pages/apt/ManagementUnitHistory/shared';
import PageLayout from '@/pages/PageLayout';
import { canEditApt } from '@/routes/access';
import { aptPaths } from '@/routes/aptPaths';
import { getFoaTypes, getPartitionTypes, type AptCodeOption } from '@/services/apt/lookups';
import {
  getAptManagementUnit,
  getAptManagementUnitHistory,
  getAptPartitionApportionments,
  getAptPartitions,
  type AptPartition,
  type AptPartitionApportionment,
} from '@/services/apt/managementUnits';

import ApportionmentModal from './ApportionmentModal';
import PartitionModal from './PartitionModal';

/** Sums a column, treating a blank as nothing (APT2's totals). */
const sum = <T,>(rows: T[], pick: (r: T) => number | null) =>
  rows.reduce((total, r) => total + (pick(r) ?? 0), 0);

/** A total that exceeds what it is apportioned from: shown in the error colour, flagged. */
const Total: FC<{ value: number; over?: boolean; label: string }> = ({ value, over, label }) => (
  <strong
    style={over ? { color: 'var(--cds-text-error)' } : undefined}
    title={over ? `More than the partition's ${label}` : undefined}
  >
    {over && (
      <WarningFilled
        size={16}
        aria-label={`More than the partition's ${label}`}
        style={{ verticalAlign: 'text-bottom', marginInlineEnd: '0.25rem' }}
      />
    )}
    {num(value)}
  </strong>
);

type Editing<T> = { item: T | null } | null;

/**
 * APTM2000 — one AAC determination's partitions and, for the partition selected, its
 * apportionments by form of agreement (APT2's ManagementUnitDetails). Each table has its totals;
 * the apportionment totals are flagged when they exceed the partition's AAC. An apportionment
 * opens its commitments. APT administrators add, edit and delete partitions and apportionments.
 *
 * <p>APT2's "Apportionment Distributions (BCTS)" panel was an empty table with a button that did
 * nothing, so it is not here.
 */
const ManagementUnitDetail: FC = () => {
  const { muId = '', historyId = '' } = useParams();
  const { user } = useAuth();
  const canEdit = canEditApt(user);

  const fetcher = useCallback(async () => {
    const [unit, history, partitions] = await Promise.all([
      getAptManagementUnit(muId),
      getAptManagementUnitHistory(muId, historyId),
      getAptPartitions({ managementUnitHistoryId: historyId }),
    ]);
    const determination = history[0];
    if (!determination) throw new Error(`Determination ${historyId} not found.`);
    const apportionments = (
      await Promise.all(partitions.map((p) => getAptPartitionApportionments({ partitionId: p.id })))
    ).flat();
    return { unit, determination, partitions, apportionments };
  }, [muId, historyId]);
  const { data, loading, error, reload, refresh } = useApiResource(fetcher, [muId, historyId]);

  // The code lists the dialogs need, for editors only. A failure leaves a list empty.
  const [partitionTypes, setPartitionTypes] = useState<AptCodeOption[]>([]);
  const [foaTypes, setFoaTypes] = useState<AptCodeOption[]>([]);
  const muType = data?.unit.aptMgmtUnitSt;
  useEffect(() => {
    if (!canEdit || !muType) return;
    let cancelled = false;
    Promise.allSettled([getPartitionTypes(), getFoaTypes(muType)]).then(([pt, foa]) => {
      if (cancelled) return;
      setPartitionTypes(pt.status === 'fulfilled' ? pt.value : []);
      setFoaTypes(foa.status === 'fulfilled' ? foa.value : []);
    });
    return () => {
      cancelled = true;
    };
  }, [canEdit, muType]);

  const [picked, setPicked] = useState<number | null>(null);
  const [partitionEdit, setPartitionEdit] = useState<Editing<AptPartition>>(null);
  const [apportionmentEdit, setApportionmentEdit] =
    useState<Editing<AptPartitionApportionment>>(null);

  const partitions = data?.partitions ?? [];
  const active = partitions.find((p) => p.id === picked) ?? partitions[0] ?? null;
  const apportionments = active
    ? (data?.apportionments ?? []).filter((a) => a.partitionId === active.id)
    : [];

  const partitionAac = sum(partitions, (p) => p.allowableAnnualCut);
  const partitionAacHa = sum(partitions, (p) => p.allowableAnnualCutHa);
  const apportionedAac = sum(apportionments, (a) => a.allowableAnnualCut);
  const apportionedAacHa = sum(apportionments, (a) => a.allowableAnnualCutHa);

  const unit = data?.unit;
  const determination = data?.determination;

  return (
    <PageLayout
      title={
        unit ? (unit.mgmtUnitName ?? `${unit.aptMgmtUnitSt} ${unit.mgmtUnitNumber}`) : 'Partitions'
      }
      subtitle="Partitions and apportionments of an AAC determination"
      backLink={
        <Link to={aptPaths.managementUnitHistory(muId)} className="back-link">
          <ArrowLeft size={16} /> Back to Management Unit History
        </Link>
      }
    >
      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Loading partitions…"
      >
        {unit && determination && (
          <>
            <Tombstone
              ariaLabel="AAC determination summary"
              items={[
                { label: 'Name', value: dash(unit.mgmtUnitName) },
                { label: 'MU type', value: dash(unit.aptMgmtUnitSt) },
                { label: 'MU no.', value: dash(unit.mgmtUnitNumber) },
                { label: 'AAC determination date', value: day(determination.determinationDate) },
                { label: 'Effective date', value: day(determination.effectiveDate) },
                { label: 'AAC (m³)', value: num(determination.allowableAnnualCut) },
                { label: 'AAC (ha)', value: num(determination.allowableAnnualCutHa) },
                {
                  label: 'Apportionment determination',
                  value: day(determination.apportionmentDeterminationDt),
                },
                {
                  label: 'Apportionment effective',
                  value: day(determination.apportionmentEffectiveDate),
                },
              ]}
            />

            <SectionTile
              title="Partitions"
              icon={TableSplit}
              actions={
                canEdit ? (
                  <Button
                    size="sm"
                    kind="tertiary"
                    renderIcon={Add}
                    onClick={() => setPartitionEdit({ item: null })}
                  >
                    New partition
                  </Button>
                ) : undefined
              }
            >
              {partitions.length === 0 ? (
                <EmptyState
                  title="No partitions"
                  body="This AAC determination has not been partitioned."
                />
              ) : (
                <TableContainer>
                  <Table size="md" aria-label="Partitions">
                    <TableHead>
                      <TableRow>
                        <TableHeader aria-label="Select" />
                        <TableHeader>Type</TableHeader>
                        <TableHeader>AAC (m³)</TableHeader>
                        <TableHeader>AAC (ha)</TableHeader>
                        <TableHeader>Apportioned (m³)</TableHeader>
                        <TableHeader>Comments</TableHeader>
                        {canEdit && <TableHeader aria-label="Actions" />}
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {partitions.map((p) => {
                        const isActive = active?.id === p.id;
                        return (
                          <TableRow
                            key={p.id}
                            className={isActive ? 'cds--data-table--selected' : undefined}
                            onClick={() => setPicked(p.id)}
                          >
                            <TableSelectRow
                              radio
                              id={`apt-part-select-${p.id}`}
                              name="apt-part-select"
                              aria-label={`Show the apportionments of ${p.partitionDescription ?? p.partitionCode}`}
                              checked={isActive}
                              onSelect={() => setPicked(p.id)}
                            />
                            <TableCell>{dash(p.partitionDescription ?? p.partitionCode)}</TableCell>
                            <TableCell>{num(p.allowableAnnualCut)}</TableCell>
                            <TableCell>{num(p.allowableAnnualCutHa)}</TableCell>
                            <TableCell>{num(p.totalApportionmentAac)}</TableCell>
                            <TableCell>{dash(p.comments)}</TableCell>
                            {canEdit && (
                              <TableCell>
                                <Button
                                  size="sm"
                                  kind="ghost"
                                  renderIcon={Edit}
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    setPartitionEdit({ item: p });
                                  }}
                                >
                                  Edit
                                </Button>
                              </TableCell>
                            )}
                          </TableRow>
                        );
                      })}
                      <TableRow>
                        <TableCell />
                        <TableCell>
                          <strong>Total</strong>
                        </TableCell>
                        <TableCell>
                          <strong>{num(partitionAac)}</strong>
                        </TableCell>
                        <TableCell>
                          <strong>{num(partitionAacHa)}</strong>
                        </TableCell>
                        <TableCell colSpan={canEdit ? 3 : 2} />
                      </TableRow>
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </SectionTile>

            <SectionTile
              title={
                active
                  ? `Apportionments — ${active.partitionDescription ?? active.partitionCode}`
                  : 'Apportionments'
              }
              icon={Partnership}
              actions={
                canEdit ? (
                  <Button
                    size="sm"
                    kind="tertiary"
                    renderIcon={Add}
                    disabled={!active}
                    onClick={() => setApportionmentEdit({ item: null })}
                  >
                    New apportionment
                  </Button>
                ) : undefined
              }
            >
              {!active ? (
                <EmptyState
                  title="No partition"
                  body="Apportionments belong to a partition; this determination has none."
                />
              ) : apportionments.length === 0 ? (
                <EmptyState
                  title="No apportionments"
                  body="This partition has not been apportioned."
                />
              ) : (
                <TableContainer>
                  <Table size="md" aria-label="Apportionments">
                    <TableHead>
                      <TableRow>
                        <TableHeader>Form of agreement / rights held</TableHeader>
                        <TableHeader>AAC (m³)</TableHeader>
                        <TableHeader>AAC (ha)</TableHeader>
                        <TableHeader>Distributed (m³)</TableHeader>
                        <TableHeader>Comments</TableHeader>
                        <TableHeader aria-label="Actions" />
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {apportionments.map((a) => (
                        <TableRow key={a.id}>
                          <TableCell>{dash(a.foaTypeName ?? a.foaTypeCode)}</TableCell>
                          <TableCell>{num(a.allowableAnnualCut)}</TableCell>
                          <TableCell>{num(a.allowableAnnualCutHa)}</TableCell>
                          <TableCell>{num(a.totalDistributionVolume)}</TableCell>
                          <TableCell>{dash(a.comments)}</TableCell>
                          <TableCell className="fsp-search__cell--nowrap">
                            {canEdit && (
                              <Button
                                size="sm"
                                kind="ghost"
                                renderIcon={Edit}
                                onClick={() => setApportionmentEdit({ item: a })}
                              >
                                Edit
                              </Button>
                            )}
                            <Button
                              size="sm"
                              kind="ghost"
                              as={Link}
                              to={aptPaths.commitments(muId, historyId, a.id)}
                            >
                              Commitments
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))}
                      <TableRow>
                        <TableCell>
                          <strong>Total</strong>
                        </TableCell>
                        <TableCell>
                          <Total
                            value={apportionedAac}
                            over={apportionedAac > (active.allowableAnnualCut ?? 0)}
                            label="AAC (m³)"
                          />
                        </TableCell>
                        <TableCell>
                          <Total
                            value={apportionedAacHa}
                            over={apportionedAacHa > (active.allowableAnnualCutHa ?? 0)}
                            label="AAC (ha)"
                          />
                        </TableCell>
                        <TableCell colSpan={3} />
                      </TableRow>
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </SectionTile>
          </>
        )}
      </AsyncBoundary>

      {canEdit && determination && (
        <PartitionModal
          open={partitionEdit !== null}
          managementUnitHistoryId={determination.id}
          partition={partitionEdit?.item ?? null}
          partitionTypes={partitionTypes}
          onClose={() => setPartitionEdit(null)}
          onSaved={(id) => {
            setPartitionEdit(null);
            setPicked(id);
            refresh();
          }}
          onDeleted={() => {
            setPartitionEdit(null);
            setPicked(null);
            refresh();
          }}
        />
      )}
      {canEdit && active && (
        <ApportionmentModal
          open={apportionmentEdit !== null}
          partition={active}
          apportionment={apportionmentEdit?.item ?? null}
          foaTypes={foaTypes}
          onClose={() => setApportionmentEdit(null)}
          onSaved={() => {
            setApportionmentEdit(null);
            refresh();
          }}
          onDeleted={() => {
            setApportionmentEdit(null);
            refresh();
          }}
        />
      )}
    </PageLayout>
  );
};

export default ManagementUnitDetail;
