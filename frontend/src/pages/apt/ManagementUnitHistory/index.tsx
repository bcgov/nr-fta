import {
  Add,
  ArrowLeft,
  Calendar,
  ChartColumn,
  Edit,
  Enterprise,
  TableSplit,
} from '@carbon/icons-react';
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
import UserName from '@/components/UserName';
import { useAuth } from '@/context/auth/useAuth';
import { useApiResource } from '@/hooks/useApiResource';
import { useMuTypeName } from '@/pages/apt/muTypes';
import PageLayout from '@/pages/PageLayout';
import { canEditApt } from '@/routes/access';
import { aptPaths } from '@/routes/aptPaths';
import { getManagementUnitStatuses, type AptCodeOption } from '@/services/apt/lookups';
import {
  getAptManagementUnit,
  getAptManagementUnitHistory,
  type AptManagementUnitHistory,
} from '@/services/apt/managementUnits';

import HistoryModal from './HistoryModal';
import { dash, day, num } from './shared';
import { useUnitEdit } from './useUnitEdit';
import './ManagementUnitHistory.css';

const COLUMNS = ['Effective date', 'AAC (m³)', 'AAC (ha)', 'Comment'];

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
 * partitions and apportionments open. APT administrators edit the unit in place, add a
 * determination (starting from the selected one) and edit the selected one.
 */
const ManagementUnitHistoryPage: FC = () => {
  const muType = useMuTypeName();
  const { muId = '' } = useParams();
  const { user } = useAuth();
  const canEdit = canEditApt(user);

  const fetcher = useCallback(
    () =>
      Promise.all([
        getAptManagementUnit(muId),
        getAptManagementUnitHistory(muId),
        // Only to describe the status; if it fails the code shows instead.
        getManagementUnitStatuses().catch(() => [] as AptCodeOption[]),
      ]),
    [muId],
  );
  const { data, loading, error, reload, refresh } = useApiResource(fetcher, [muId]);
  const unit = data?.[0];
  const history = data?.[1] ?? [];
  const statuses = data?.[2] ?? [];
  const statusName = unit?.mgmtUnitStatusSt
    ? statuses.find((st) => st.code === unit.mgmtUnitStatusSt)?.description || unit.mgmtUnitStatusSt
    : null;

  const [picked, setPicked] = useState<number | null>(null);
  const [modal, setModal] = useState<'create' | 'edit' | null>(null);
  // The Management unit section's fields, edited in place.
  const edit = useUnitEdit(unit, () => refresh());
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
    >
      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Loading management unit…"
      >
        {unit && (
          <>
            <div className={`apt-mu__top${edit.editing ? ' detail-edit' : ''}`}>
              <SectionTile
                title="Management unit"
                icon={Enterprise}
                actions={
                  canEdit && !edit.editing ? (
                    <Button kind="tertiary" size="sm" renderIcon={Edit} onClick={edit.start}>
                      Edit management unit
                    </Button>
                  ) : undefined
                }
              >
                <DefinitionGrid
                  items={[
                    { label: 'Name', value: dash(unit.mgmtUnitName) },
                    { label: 'Management unit type', value: dash(muType(unit.aptMgmtUnitSt)) },
                    { label: 'Management unit no.', value: dash(unit.mgmtUnitNumber) },
                    {
                      label: 'Status',
                      value: edit.editing ? edit.inputs.status : dash(statusName),
                    },
                    {
                      label: 'Status effective',
                      value: edit.editing
                        ? edit.inputs.effectiveDate
                        : day(unit.statusEffectiveDate),
                    },
                    {
                      label: 'Org unit',
                      value: edit.editing ? edit.inputs.orgUnit : dash(unit.orgUnitName),
                    },
                    {
                      label: edit.editing
                        ? 'Source management unit (optional)'
                        : 'Source management unit',
                      value: edit.editing ? (
                        edit.inputs.source
                      ) : unit.muId ? (
                        <Link to={aptPaths.managementUnitHistory(unit.muId)}>
                          {unit.srcMgmtUnitName ?? unit.muId}
                        </Link>
                      ) : (
                        '—'
                      ),
                    },
                    {
                      label: edit.editing ? 'Comments (optional)' : 'Comments',
                      value: edit.editing ? edit.inputs.comments : dash(unit.comments),
                      wide: edit.editing,
                    },
                  ]}
                />
                {edit.editing && (
                  <div className="detail-edit__actions">
                    <Button kind="tertiary" size="md" disabled={edit.saving} onClick={edit.cancel}>
                      Cancel
                    </Button>
                    <Button
                      kind="primary"
                      size="md"
                      disabled={edit.saving}
                      onClick={() => void edit.save()}
                    >
                      {edit.saving ? 'Saving…' : 'Save changes'}
                    </Button>
                  </div>
                )}
              </SectionTile>
            </div>

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
                <div className="bordered-table">
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
                              <TableCell>{num(h.allowableAnnualCut)}</TableCell>
                              <TableCell>{num(h.allowableAnnualCutHa)}</TableCell>
                              <TableCell>{dash(h.comments)}</TableCell>
                            </TableRow>
                          );
                        })}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </div>
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
