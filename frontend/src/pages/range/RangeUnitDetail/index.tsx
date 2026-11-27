import { Add, ArrowLeft, Edit, Location, Table as TableIcon, TrashCan } from '@carbon/icons-react';
import {
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { useCallback, useEffect, useState, type FC, type ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import ConfirmationModal from '@/components/ConfirmationModal';
import DefinitionGrid from '@/components/DefinitionGrid';
import SectionTile from '@/components/SectionTile';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import { useDefaultOrgUnit } from '@/hooks/useDefaultOrgUnit';
import PageLayout from '@/pages/PageLayout';
import { getAllOrgUnits, type CodeOption } from '@/services/codeLists';
import {
  deleteRangeUnit,
  deleteRangeUnitPasture,
  getRangeUnitDetail,
  type RangeUnitDetail as RangeUnitDetailData,
  type RangeUnitPasture,
} from '@/services/range_unit_detail';

import PastureDialog from './PastureDialog';
import { NEW_RANGE_UNIT, rangeUnitPath } from './paths';
import { useRangeUnitEdit, type RangeUnitEditor } from './useRangeUnitEdit';

const SEARCH_PATH = '/search/range-unit';

const dash = (v: string | null | undefined) => (v && v.trim() ? v : '—');

const backLink = (
  <Link to={SEARCH_PATH} className="back-link">
    <ArrowLeft size={16} /> Back to Range Unit Search
  </Link>
);

/** Legacy's tombstone fields, in fta630MnRgUnPst.jsp's order and with its labels. */
const unitItems = (
  edit: RangeUnitEditor,
  values: {
    rangeUnitId: ReactNode;
    status: string;
    asOf: string;
    region: string;
    name: string;
    district: string;
    zone: string;
  },
) => [
  { label: 'Range Unit', value: values.rangeUnitId },
  { label: 'Status', value: edit.editing ? edit.inputs.status : values.status },
  { label: 'As of', value: edit.editing ? edit.inputs.asOf : values.asOf },
  { label: 'Region', value: values.region },
  { label: 'Range Unit Name', value: edit.editing ? edit.inputs.rangeUnitName : values.name },
  { label: 'District', value: edit.editing ? edit.inputs.district : values.district },
  { label: 'Zone', value: edit.editing ? edit.inputs.zone : values.zone },
];

/**
 * The region legacy's setDefaults showed on Add New: a region user's own org unit, a district
 * user's rollup region, Headquarters' own — described from the org unit list.
 */
function useDefaultRegion(): string {
  const d = useDefaultOrgUnit();
  const [orgUnits, setOrgUnits] = useState<CodeOption[]>([]);
  useEffect(() => {
    let cancelled = false;
    getAllOrgUnits()
      .then((all) => !cancelled && setOrgUnits(all))
      .catch(() => {
        // Only describes the region; without it the field reads a dash.
      });
    return () => {
      cancelled = true;
    };
  }, []);
  const regionNo = d?.orgLevelCode === 'D' ? d.rollupRegionNo : d?.orgUnitNo;
  return orgUnits.find((o) => o.code === regionNo)?.description ?? '—';
}

/** FTA630's Add New, then Save Range: the id is generated and pasture '00' created with it. */
const AddRangeUnit: FC = () => {
  const navigate = useNavigate();
  const defaults = useDefaultOrgUnit();
  const region = useDefaultRegion();
  const edit = useRangeUnitEdit(
    null,
    (result) => navigate(rangeUnitPath(result.rangeUnitId), { replace: true }),
    defaults?.orgLevelCode === 'D' ? (defaults.orgUnitNo ?? undefined) : undefined,
  );

  return (
    <PageLayout
      title="Add Range Unit"
      subtitle="A new range unit and its pastures"
      backLink={backLink}
    >
      <div className="detail-edit">
        <SectionTile title="Range unit" icon={Location}>
          <DefinitionGrid
            items={unitItems(edit, {
              rangeUnitId: '—',
              status: '',
              asOf: '',
              region,
              name: '',
              district: '',
              zone: '',
            })}
          />
          <div className="detail-edit__actions">
            <Button
              kind="tertiary"
              size="md"
              disabled={edit.saving}
              onClick={() => navigate(SEARCH_PATH)}
            >
              Cancel
            </Button>
            <Button
              kind="primary"
              size="md"
              disabled={edit.saving}
              onClick={() => void edit.save()}
            >
              {edit.saving ? 'Saving…' : 'Save range'}
            </Button>
          </div>
        </SectionTile>
      </div>
    </PageLayout>
  );
};

/** FTA630 on an existing unit: Save Range and Delete Range, and its pastures. */
const ExistingRangeUnit: FC<{ unitId: string }> = ({ unitId }) => {
  const navigate = useNavigate();
  const { display } = useNotification();
  const fetcher = useCallback(() => getRangeUnitDetail(unitId), [unitId]);
  const { data: unit, loading, error, reload, refresh } = useApiResource(fetcher, [unitId]);
  const edit = useRangeUnitEdit(unit, () => refresh());
  const canEdit = !!unit?.canEdit;

  const [confirmDelete, setConfirmDelete] = useState(false);
  const [pastureDialog, setPastureDialog] = useState<{ pasture: RangeUnitPasture | null } | null>(
    null,
  );
  const [deletingPasture, setDeletingPasture] = useState<string | null>(null);

  const removeUnit = async () => {
    if (!unit) return;
    await deleteRangeUnit(unit.rangeUnitId, unit.revisionCount);
    display({ kind: 'success', title: 'Delete successful.', timeout: 5000 });
    navigate(SEARCH_PATH);
  };

  // Legacy's pasture Delete had no confirmation.
  const removePasture = async (u: RangeUnitDetailData, p: RangeUnitPasture) => {
    setDeletingPasture(p.pastureId);
    try {
      await deleteRangeUnitPasture(u.rangeUnitId, p.pastureId, p.pastureRevisionCount);
      display({ kind: 'success', title: 'Delete successful.', timeout: 5000 });
      refresh();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not delete the pasture.',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setDeletingPasture(null);
    }
  };

  return (
    <PageLayout
      title={`Range Unit ${unitId}`}
      subtitle="Range unit details and its pastures"
      backLink={backLink}
    >
      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Loading range unit…"
      >
        {unit && (
          <>
            <div className={edit.editing ? 'detail-edit' : undefined}>
              <SectionTile
                title="Range unit"
                icon={Location}
                actions={
                  edit.editing ? undefined : (
                    <>
                      <Button
                        kind="tertiary"
                        size="sm"
                        renderIcon={Edit}
                        disabled={!canEdit}
                        onClick={edit.start}
                      >
                        Edit range unit
                      </Button>
                      <Button
                        kind="danger--tertiary"
                        size="sm"
                        renderIcon={TrashCan}
                        disabled={!canEdit}
                        onClick={() => setConfirmDelete(true)}
                      >
                        Delete range
                      </Button>
                    </>
                  )
                }
              >
                <DefinitionGrid
                  items={unitItems(edit, {
                    rangeUnitId: unit.rangeUnitId,
                    status: dash(unit.statusDescription),
                    asOf: dash(unit.statusDate),
                    region: dash(unit.regionDescription),
                    name: dash(unit.rangeUnitName),
                    district: dash(unit.districtDescription),
                    zone: dash(unit.districtAdminZone),
                  })}
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
                      {edit.saving ? 'Saving…' : 'Save range'}
                    </Button>
                  </div>
                )}
              </SectionTile>
            </div>

            <SectionTile
              title="Pastures"
              icon={TableIcon}
              description={`${unit.pastures.length} rows returned`}
              actions={
                <Button
                  kind="tertiary"
                  size="sm"
                  renderIcon={Add}
                  disabled={!canEdit}
                  onClick={() => setPastureDialog({ pasture: null })}
                >
                  Add pasture
                </Button>
              }
            >
              <div className="bordered-table">
                <TableContainer>
                  <Table aria-label="Pastures">
                    <TableHead>
                      <TableRow>
                        <TableHeader>Pasture ID</TableHeader>
                        <TableHeader>Pasture Description</TableHeader>
                        <TableHeader>Actions</TableHeader>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {unit.pastures.map((p) => (
                        <TableRow key={p.pastureId}>
                          <TableCell>{p.pastureId}</TableCell>
                          <TableCell>{dash(p.pastureName)}</TableCell>
                          <TableCell>
                            <Button
                              kind="ghost"
                              size="sm"
                              renderIcon={Edit}
                              disabled={!canEdit || deletingPasture !== null}
                              onClick={() => setPastureDialog({ pasture: p })}
                            >
                              Update
                            </Button>
                            <Button
                              kind="ghost"
                              size="sm"
                              renderIcon={TrashCan}
                              disabled={!canEdit || deletingPasture !== null}
                              onClick={() => void removePasture(unit, p)}
                            >
                              {deletingPasture === p.pastureId ? 'Deleting…' : 'Delete'}
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </div>
            </SectionTile>

            <ConfirmationModal
              open={confirmDelete}
              onClose={() => setConfirmDelete(false)}
              heading="Delete range unit"
              confirmLabel="Delete range"
              danger
              errorTitle="Could not delete the range unit."
              onConfirm={removeUnit}
            >
              Delete range unit {unit.rangeUnitId} and all its pastures?
            </ConfirmationModal>

            <PastureDialog
              unit={unit}
              open={pastureDialog !== null}
              pasture={pastureDialog?.pasture ?? null}
              onClose={() => setPastureDialog(null)}
              onSaved={() => {
                setPastureDialog(null);
                refresh();
              }}
            />
          </>
        )}
      </AsyncBoundary>
    </PageLayout>
  );
};

/**
 * FTA630 — Maintain Range Unit / Pasture. A unit's fields, edited in place (Save Range) or
 * deleted with its pastures (Delete Range), and its pastures, added, updated and deleted through
 * a dialog. `/range-unit/new` is Add New. The write buttons follow legacy's p_ind — the
 * backend's `canEdit`, whether the user may edit range files of the unit's district — and the
 * backend checks it again on every write.
 */
const RangeUnitDetail: FC = () => {
  const { unitId = '' } = useParams();
  return unitId === NEW_RANGE_UNIT ? (
    <AddRangeUnit />
  ) : (
    <ExistingRangeUnit key={unitId} unitId={unitId} />
  );
};

export default RangeUnitDetail;
