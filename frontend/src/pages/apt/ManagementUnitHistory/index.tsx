import { Add, ArrowLeft, Calendar, ChartColumn, Edit, TableSplit } from '@carbon/icons-react';
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
import { useCallback, useState, type FC } from 'react';
import { Link, useParams } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import DefinitionGrid from '@/components/DefinitionGrid';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { SectionTile } from '@/components/SectionTile';
import Tombstone from '@/components/Tombstone';
import UserName from '@/components/UserName';
import { useAuth } from '@/context/auth/useAuth';
import { useApiResource } from '@/hooks/useApiResource';
import PageLayout from '@/pages/PageLayout';
import { canEditApt } from '@/routes/access';
import { aptPaths } from '@/routes/aptPaths';
import {
  getAptManagementUnit,
  getAptManagementUnitHistory,
  type AptManagementUnitHistory,
} from '@/services/apt/managementUnits';

import HistoryModal from './HistoryModal';
import { dash, day, num } from './shared';

const COLUMNS = [
  'Effective date',
  'AAC determination date',
  'AAC (m³)',
  'AAC (ha)',
  'Apportionment effective date',
  'Comments',
];

/** The determination's facts, for the detail panel. */
const detailItems = (h: AptManagementUnitHistory) => [
  { label: 'Effective date', value: day(h.effectiveDate) },
  { label: 'AAC determination date', value: day(h.determinationDate) },
  { label: 'AAC (m³)', value: num(h.allowableAnnualCut) },
  { label: 'AAC (ha)', value: num(h.allowableAnnualCutHa) },
  { label: 'Apportionment determination date', value: day(h.apportionmentDeterminationDt) },
  { label: 'Apportionment effective date', value: day(h.apportionmentEffectiveDate) },
  { label: 'Inventory revision date', value: day(h.inventoryRevDate) },
  { label: 'Long term harvest level (m³)', value: num(h.longTermHarvestLevel) },
  { label: 'Total area (ha)', value: num(h.totalArea) },
  { label: 'Productive area (ha)', value: num(h.productiveArea) },
  { label: 'Current net operating area (ha)', value: num(h.currentNetOperatingArea) },
  { label: 'Future harvest landbase area (ha)', value: num(h.futureHarvestLandbaseArea) },
  { label: 'Comments', value: dash(h.comments) },
  { label: 'Added by', value: <UserName userId={h.addUserid} /> },
  { label: 'Added on', value: day(h.addTimestamp) },
];

/**
 * APTM1000 — a management unit and its AAC determinations, newest first (APT2's
 * ManagementUnitsHistory). Selecting a determination shows its detail; from there its
 * partitions and apportionments open. APT administrators edit the unit, add a determination
 * (starting from the selected one) and edit the selected one.
 */
const ManagementUnitHistoryPage: FC = () => {
  const { muId = '' } = useParams();
  const { user } = useAuth();
  const canEdit = canEditApt(user);

  const fetcher = useCallback(
    () => Promise.all([getAptManagementUnit(muId), getAptManagementUnitHistory(muId)]),
    [muId],
  );
  const { data, loading, error, reload, refresh } = useApiResource(fetcher, [muId]);
  const unit = data?.[0];
  const history = data?.[1] ?? [];

  const [picked, setPicked] = useState<number | null>(null);
  const [modal, setModal] = useState<'create' | 'edit' | null>(null);
  // Newest first, as the routine orders them; the newest is shown until one is picked.
  const selected = history.find((h) => h.id === picked) ?? history[0] ?? null;

  const title = unit ? (unit.mgmtUnitName ?? `${unit.aptMgmtUnitSt} ${unit.mgmtUnitNumber}`) : '';

  return (
    <PageLayout
      title={title || 'Management Unit History'}
      subtitle="Management unit and its AAC determinations"
      backLink={
        <Link to={aptPaths.managementUnits()} className="back-link">
          <ArrowLeft size={16} /> Back to Management Units
        </Link>
      }
      actions={
        unit && canEdit ? (
          <Button
            size="md"
            kind="tertiary"
            renderIcon={Edit}
            as={Link}
            to={aptPaths.editManagementUnit(unit.id)}
          >
            Edit management unit
          </Button>
        ) : undefined
      }
    >
      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Loading management unit…"
      >
        {unit && (
          <>
            <Tombstone
              ariaLabel="Management unit summary"
              items={[
                { label: 'Name', value: dash(unit.mgmtUnitName) },
                { label: 'MU type', value: dash(unit.aptMgmtUnitSt) },
                { label: 'MU no.', value: dash(unit.mgmtUnitNumber) },
                { label: 'Status', value: dash(unit.mgmtUnitStatusSt) },
                { label: 'Status effective', value: day(unit.statusEffectiveDate) },
                { label: 'Org unit', value: dash(unit.orgUnitName) },
                {
                  label: 'Source management unit',
                  value: unit.muId ? (
                    <Link to={aptPaths.managementUnitHistory(unit.muId)}>
                      {unit.srcMgmtUnitName ?? unit.muId}
                    </Link>
                  ) : (
                    '—'
                  ),
                },
                { label: 'ORCS file #', value: dash(unit.orcsFileNumber) },
              ]}
            />

            <SectionTile
              title="AAC determinations"
              icon={Calendar}
              actions={
                canEdit ? (
                  <Button
                    size="sm"
                    kind="tertiary"
                    renderIcon={Add}
                    onClick={() => setModal('create')}
                  >
                    New determination
                  </Button>
                ) : undefined
              }
            >
              {history.length === 0 ? (
                <EmptyState
                  title="No AAC determinations"
                  body="This management unit has no AAC determinations yet."
                />
              ) : (
                <TableContainer>
                  <Table size="md" aria-label="AAC determinations">
                    <TableHead>
                      <TableRow>
                        <TableHeader aria-label="Select" />
                        {COLUMNS.map((c) => (
                          <TableHeader key={c}>{c}</TableHeader>
                        ))}
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {history.map((h) => {
                        const isSelected = selected?.id === h.id;
                        return (
                          <TableRow
                            key={h.id}
                            className={isSelected ? 'cds--data-table--selected' : undefined}
                            onClick={() => setPicked(h.id)}
                          >
                            <TableSelectRow
                              radio
                              id={`apt-muh-select-${h.id}`}
                              name="apt-muh-select"
                              aria-label={`Select the determination effective ${day(h.effectiveDate)}`}
                              checked={isSelected}
                              onSelect={() => setPicked(h.id)}
                            />
                            <TableCell>{day(h.effectiveDate)}</TableCell>
                            <TableCell>{day(h.determinationDate)}</TableCell>
                            <TableCell>{num(h.allowableAnnualCut)}</TableCell>
                            <TableCell>{num(h.allowableAnnualCutHa)}</TableCell>
                            <TableCell>{day(h.apportionmentEffectiveDate)}</TableCell>
                            <TableCell>{dash(h.comments)}</TableCell>
                          </TableRow>
                        );
                      })}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </SectionTile>

            {selected && (
              <SectionTile
                title={`Determination effective ${day(selected.effectiveDate)}`}
                icon={ChartColumn}
                actions={
                  <>
                    {canEdit && (
                      <Button
                        size="sm"
                        kind="tertiary"
                        renderIcon={Edit}
                        onClick={() => setModal('edit')}
                      >
                        Edit
                      </Button>
                    )}
                    <Button
                      size="sm"
                      kind="primary"
                      renderIcon={TableSplit}
                      as={Link}
                      to={aptPaths.managementUnitDetail(unit.id, selected.id)}
                    >
                      Partitions and apportionments
                    </Button>
                  </>
                }
              >
                <DefinitionGrid items={detailItems(selected)} />
              </SectionTile>
            )}
          </>
        )}
      </AsyncBoundary>

      {unit && canEdit && (
        <HistoryModal
          muId={unit.id}
          open={modal !== null}
          mode={modal ?? 'create'}
          source={selected}
          onClose={() => setModal(null)}
          onSaved={(saved) => {
            setModal(null);
            setPicked(saved.id);
            refresh();
          }}
        />
      )}
    </PageLayout>
  );
};

export default ManagementUnitHistoryPage;
