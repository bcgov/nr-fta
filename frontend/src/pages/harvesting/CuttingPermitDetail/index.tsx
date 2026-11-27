import {
  Archive,
  ArrowLeft,
  Calendar,
  Edit,
  Pause,
  RecentlyViewed,
  Report,
  Stamp,
  TableOfContents,
  Tag as TagIcon,
  TrashCan,
  Tree,
} from '@carbon/icons-react';
import { Button, InlineNotification, Tab, TabList, TabPanel, TabPanels, Tabs } from '@carbon/react';
import { useCallback, useEffect, useState, type FC } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import ConfirmationModal from '@/components/ConfirmationModal';
import DetailTile, { type DetailField } from '@/components/DetailTile';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { statusCodeVariant } from '@/components/StatusTag/statusCodes';
import Tombstone from '@/components/Tombstone';
import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import { useLazyTabs } from '@/hooks/useLazyTabs';
import { useOrgUnitName } from '@/hooks/useOrgUnitName';
import PageLayout from '@/pages/PageLayout';
import { TimberMarkHarvestHistory } from '@/pages/tenure/TenureDetail/HarvestHistoryPanel';
import { canEditHarvest, isPathAllowedForUser } from '@/routes/access';
import {
  getDistricts,
  getMarkingInstruments,
  getMarkingMethods,
  getSalvageTypes,
  getQuotaTypes,
  getCrownLandsRegions,
  type CodeOption,
} from '@/services/codeLists';
import {
  areaLabel,
  deletePermit,
  getCuttingPermitDetail,
  getPermitLookups,
  hasScheduleB,
  retirePermit,
} from '@/services/cutting_permit_detail';
import { formatDate } from '@/utils/formatDate';

import PermitConditions from './PermitConditions';
import PermitCutBlocks from './PermitCutBlocks';
import PermitEditModal from './PermitEditModal';
import PermitPostponements from './PermitPostponements';

const nf = new Intl.NumberFormat('en-CA');

const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;

const date = (v: string | null | undefined) => formatDate(v) || '—';

/** FTA902's yes/no answers; U (and nothing) is its blank. */
const yesNo = (v: string | null | undefined) => (v === 'Y' ? 'Yes' : v === 'N' ? 'No' : '—');

/** "CODE - description" when both are known, else whichever is. */
const codeDesc = (code: string | null | undefined, desc: string | null | undefined) =>
  code && desc ? `${code} - ${desc}` : dash(desc || code);

interface Lookups {
  districts: CodeOption[];
  markingMethods: CodeOption[];
  markingInstruments: CodeOption[];
  salvageTypes: CodeOption[];
  quotaTypes: CodeOption[];
  landRegions: CodeOption[];
  extendReasons: CodeOption[];
}

const NO_LOOKUPS: Lookups = {
  districts: [],
  markingMethods: [],
  markingInstruments: [],
  salvageTypes: [],
  quotaTypes: [],
  landRegions: [],
  extendReasons: [],
};

/** A code's "CODE - description" label from its list, or the bare code. */
const label = (options: CodeOption[], code: string | null | undefined) =>
  code ? (options.find((o) => o.code === code)?.description ?? code) : '—';

/**
 * FTA902 — Cutting Permit / Timber Mark details, laid out as the private mark
 * and tenure details: a back link above the title, a status-coloured tombstone
 * of the permit's key facts, then contained Tabs over a full-bleed grey pane of
 * section cards. Reached from the Harvesting Authority Search or the tenure
 * CP/Mark tab. Backed by {@code GET /api/fta/cutting-permits/{cpId}} (which
 * ports THE.FTA_902_CP_DETAIL), whose rules decide which actions are enabled.
 * Legacy's Print CP Approval is not here: FTA902 disabled it permanently.
 */
const CuttingPermitDetail: FC = () => {
  const orgUnitName = useOrgUnitName();
  const { cpId = '' } = useParams();
  // A CP id is unique only within its tenure, so links from a tenure say which.
  const [searchParams] = useSearchParams();
  const forestFileId = searchParams.get('forestFileId') ?? undefined;
  const { user } = useAuth();

  const navigate = useNavigate();
  const { display } = useNotification();
  const [editOpen, setEditOpen] = useState(false);
  const [confirming, setConfirming] = useState<'retire' | 'delete' | null>(null);

  const fetcher = useCallback(
    () =>
      forestFileId
        ? getCuttingPermitDetail(cpId, forestFileId)
        : Promise.reject(new Error('Open this permit from its tenure or a search result.')),
    [cpId, forestFileId],
  );
  const { data: cp, loading, error, reload } = useApiResource(fetcher, [cpId, forestFileId]);

  // Descriptions for the codes the permit holds (cached app-wide). Settled
  // individually: a list that fails just leaves its codes bare.
  const [lookups, setLookups] = useState<Lookups>(NO_LOOKUPS);
  useEffect(() => {
    let cancelled = false;
    Promise.allSettled([
      getDistricts(),
      getMarkingMethods(),
      getMarkingInstruments(),
      getSalvageTypes(),
      getQuotaTypes(),
      getCrownLandsRegions(),
      getPermitLookups(),
    ]).then(([d, mm, mi, st, qt, lr, pl]) => {
      if (cancelled) return;
      const ok = (r: PromiseSettledResult<CodeOption[]>) =>
        r.status === 'fulfilled' ? r.value : [];
      setLookups({
        districts: ok(d),
        markingMethods: ok(mm),
        markingInstruments: ok(mi),
        salvageTypes: ok(st),
        quotaTypes: ok(qt),
        landRegions: ok(lr),
        extendReasons: pl.status === 'fulfilled' ? pl.value.extendReasons : [],
      });
    });
    return () => {
      cancelled = true;
    };
  }, []);

  // Each tab is rendered only once opened.
  const tabs = useLazyTabs(`${cpId}|${forestFileId ?? ''}`);

  const hectares = (v: number | null | undefined) => (v != null ? nf.format(v) : '—');
  const permitId = cp?.cuttingPermitId ?? cpId;

  const permitQuery = forestFileId ? `?forestFileId=${encodeURIComponent(forestFileId)}` : '';
  const rules = cp?.rules;

  const actions =
    cp && rules && canEditHarvest(user) ? (
      <>
        <Button
          size="md"
          kind="tertiary"
          renderIcon={Edit}
          disabled={!rules.canSave}
          onClick={() => setEditOpen(true)}
        >
          Edit permit
        </Button>
        {rules.canAssignMarks && (
          <Button
            size="md"
            kind="tertiary"
            renderIcon={TagIcon}
            as={Link}
            to={`/harvesting-authority/${encodeURIComponent(permitId)}/assign-marks${permitQuery}`}
          >
            Assign marks to blocks
          </Button>
        )}
        {rules.canSuspendBlocks && (
          <Button
            size="md"
            kind="tertiary"
            renderIcon={Pause}
            as={Link}
            to={`/harvesting-authority/${encodeURIComponent(permitId)}/suspend-blocks${permitQuery}`}
          >
            Suspend blocks
          </Button>
        )}
        <Button
          size="md"
          kind="tertiary"
          renderIcon={Archive}
          disabled={!rules.canRetire}
          onClick={() => setConfirming('retire')}
        >
          Retire
        </Button>
        <Button
          size="md"
          kind="danger"
          renderIcon={TrashCan}
          disabled={!rules.canDelete}
          onClick={() => setConfirming('delete')}
        >
          Delete
        </Button>
      </>
    ) : undefined;

  // A Timber Mark Headquarters Administrator reaches this page from Timber Mark
  // Search and may not open Harvesting Authority Search, so goes back there.
  const readsSections = isPathAllowedForUser(user, '/search/harvesting-authority');
  const back = readsSections
    ? { path: '/search/harvesting-authority', label: 'Harvesting Authority Search' }
    : { path: '/search/timber-mark', label: 'Timber Mark Search' };

  const permitFields: DetailField[] = cp
    ? [
        { label: 'File Type', value: codeDesc(cp.fileTypeCode, cp.fileTypeDescription) },
        { label: 'Licensee', value: dash(cp.licensee) },
        { label: 'Forest District', value: label(lookups.districts, cp.forestDistrict) },
        { label: areaLabel(cp.fileTypeCode), value: hectares(cp.harvestArea) },
        ...(hasScheduleB(cp.fileTypeCode)
          ? [{ label: 'Schedule A (ha)', value: hectares(cp.scheduleAArea) }]
          : []),
        { label: 'Zone', value: dash(cp.districtAdmnZone) },
        {
          label: 'Management Unit',
          value: cp.mgmtUnitTypeCode
            ? [cp.mgmtUnitTypeCode, cp.mgmtUnitId].filter(Boolean).join(' ')
            : '—',
        },
        { label: 'Land Region', value: label(lookups.landRegions, cp.crownLandsRegionCode) },
        { label: 'Geographic Location', value: dash(cp.location), wide: true },
        { label: 'Retired', value: date(cp.retirementDate) },
      ]
    : [];

  const conditionFields: DetailField[] = cp
    ? [
        {
          label: 'Marking Method',
          value: label(lookups.markingMethods, cp.markingMethodCode),
        },
        {
          label: 'Marking Instrument',
          value: label(lookups.markingInstruments, cp.markingInstrumentCode),
        },
        { label: 'Quota Type', value: label(lookups.quotaTypes, cp.quotaTypeCode) },
        { label: 'Salvage Type', value: label(lookups.salvageTypes, cp.salvageTypeCode) },
        {
          label: 'CP Extension Application Fee Waived',
          value: yesNo(cp.extAppFeeWaived),
        },
        { label: 'CP Extension Application Fee Paid', value: yesNo(cp.extAppFeePaid) },
        { label: 'Within Fibre Recovery Zone', value: yesNo(cp.withinFibreRecoveryZone) },
        { label: 'Deciduous', value: yesNo(cp.deciduousInd) },
        { label: 'Catastrophic', value: yesNo(cp.catastrophicInd) },
        { label: 'Cruise Based', value: yesNo(cp.cruiseBasedInd) },
      ]
    : [];

  const termFields: DetailField[] = cp
    ? [
        { label: 'Status', value: codeDesc(cp.statusCode, cp.statusDesc) },
        { label: 'Status Date', value: date(cp.statusDate) },
        {
          label: 'Tenure Term',
          value:
            cp.tenureTermYears != null || cp.tenureTermMonths != null
              ? `${cp.tenureTermYears ?? 0} yr ${cp.tenureTermMonths ?? 0} mo`
              : '—',
        },
        { label: 'Issued', value: date(cp.issueDate) },
        { label: 'Expires', value: date(cp.expiryDate) },
        { label: 'Extended', value: date(cp.extendDate) },
        {
          label: 'Extension Reason',
          value: label(lookups.extendReasons, cp.extendReasonCode),
        },
        { label: 'Extension Count', value: dash(cp.extendCount) },
      ]
    : [];

  return (
    <PageLayout
      title="Cutting Permit"
      subtitle="Permit details, cut blocks, and harvest history."
      actions={actions}
      backLink={
        <Link to={back.path} className="back-link">
          <ArrowLeft size={16} /> Back to {back.label}
        </Link>
      }
    >
      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Loading cutting permit…"
      >
        {cp && (
          <>
            <Tombstone
              ariaLabel="Cutting permit summary"
              // The left bar takes the status's colour, as on the private mark and tenure.
              className={`bc-status-accent--${statusCodeVariant(cp.statusCode) ?? 'default'}`}
              items={[
                { label: 'Cutting Permit', value: dash(cp.cuttingPermitId) },
                { label: 'Timber Mark', value: dash(cp.timberMark) },
                {
                  label: 'Forest File',
                  value: cp.forestFileId ? (
                    <Link to={`/tenures/${encodeURIComponent(cp.forestFileId)}`}>
                      {cp.forestFileId}
                    </Link>
                  ) : (
                    '—'
                  ),
                },
                { label: 'Admin Organization', value: orgUnitName(cp.adminOrgCode) },
                { label: 'Status', value: codeDesc(cp.statusCode, cp.statusDesc) },
                { label: areaLabel(cp.fileTypeCode), value: hectares(cp.harvestArea) },
                { label: 'Issued', value: date(cp.issueDate) },
                { label: 'Expires', value: date(cp.expiryDate) },
              ]}
            />
            {/* Carbon's <Tabs> renders no DOM of its own, so the grey full-bleed
                pane is styled through this wrapper (styles/_detail.scss). */}
            <div className="fsp-info__page-tabs">
              <Tabs selectedIndex={tabs.selected} onChange={tabs.onChange}>
                <TabList aria-label="Cutting permit sections" contained>
                  <Tab renderIcon={TableOfContents}>Details</Tab>
                  <Tab renderIcon={Tree}>Cut blocks</Tab>
                  <Tab renderIcon={RecentlyViewed}>Harvest history</Tab>
                  <Tab renderIcon={Stamp} disabled={!readsSections}>
                    Conditions
                  </Tab>
                  <Tab renderIcon={Calendar} disabled={!readsSections}>
                    Postponements
                  </Tab>
                </TabList>
                <TabPanels>
                  <TabPanel>
                    {tabs.isOpened(0) && (
                      <div className="fsp-info__tab-panel">
                        {/* Fta902CpDetailAction.handleGet's messages, in its words. */}
                        {cp.blockSuspensionsExist && (
                          <InlineNotification
                            kind="info"
                            lowContrast
                            hideCloseButton
                            title="There have been suspended blocks in this cutting permit. Ensure that the Expiry Date is correct."
                          />
                        )}
                        {cp.postponementExpired && (
                          <InlineNotification
                            kind="info"
                            lowContrast
                            hideCloseButton
                            title="The Postponement Period has expired."
                          />
                        )}
                        <div className="fsp-info__tile-row">
                          <DetailTile title="Permit details" icon={Report} fields={permitFields} />
                          <DetailTile
                            title="Issuance conditions"
                            icon={Stamp}
                            fields={conditionFields}
                          />
                        </div>
                        <DetailTile title="Term and status" icon={Calendar} fields={termFields} />
                      </div>
                    )}
                  </TabPanel>

                  <TabPanel>
                    {tabs.isOpened(1) && (
                      <div className="fsp-info__tab-panel">
                        {cp.forestFileId ? (
                          <PermitCutBlocks
                            forestFileId={cp.forestFileId}
                            cuttingPermitId={cp.cuttingPermitId}
                          />
                        ) : (
                          <EmptyState
                            icon={<Tree size={48} />}
                            title="No cut blocks for this permit"
                            body="This permit has no tenure to list blocks from."
                          />
                        )}
                      </div>
                    )}
                  </TabPanel>

                  <TabPanel>
                    {tabs.isOpened(2) &&
                      (cp.timberMark ? (
                        <TimberMarkHarvestHistory timberMark={cp.timberMark} />
                      ) : (
                        <div className="fsp-info__tab-panel">
                          <EmptyState
                            icon={<RecentlyViewed size={48} />}
                            title="No harvest history for this permit"
                            body="This permit has no timber mark."
                          />
                        </div>
                      ))}
                  </TabPanel>

                  <TabPanel>
                    {tabs.isOpened(3) && (
                      <PermitConditions
                        hvaSkey={cp.hvaSkey}
                        canManage={cp.rules.canManageConditions}
                      />
                    )}
                  </TabPanel>

                  <TabPanel>
                    {tabs.isOpened(4) && <PermitPostponements cp={cp} onChanged={reload} />}
                  </TabPanel>
                </TabPanels>
              </Tabs>
            </div>
          </>
        )}
      </AsyncBoundary>

      {cp && (
        <>
          <PermitEditModal
            cp={cp}
            open={editOpen}
            onClose={() => setEditOpen(false)}
            onSaved={reload}
          />
          <ConfirmationModal
            open={confirming === 'retire'}
            onClose={() => setConfirming(null)}
            heading="Retire cutting permit"
            confirmLabel="Retire"
            errorTitle="Could not retire the cutting permit"
            onConfirm={async () => {
              await retirePermit(cp.hvaSkey, cp.revisionCount);
              display({ kind: 'success', title: 'Retirement successful.', timeout: 5000 });
              reload();
            }}
          >
            Retire cutting permit {cp.cuttingPermitId}?
          </ConfirmationModal>
          <ConfirmationModal
            open={confirming === 'delete'}
            onClose={() => setConfirming(null)}
            heading="Delete cutting permit"
            confirmLabel="Delete"
            danger
            errorTitle="Could not delete the cutting permit"
            onConfirm={async () => {
              await deletePermit(cp.hvaSkey, cp.revisionCount);
              display({ kind: 'success', title: 'Cutting permit deleted.', timeout: 5000 });
              navigate(`/tenures/${encodeURIComponent(cp.forestFileId ?? '')}`);
            }}
          >
            Delete cutting permit {cp.cuttingPermitId}?
          </ConfirmationModal>
        </>
      )}
    </PageLayout>
  );
};

export default CuttingPermitDetail;
