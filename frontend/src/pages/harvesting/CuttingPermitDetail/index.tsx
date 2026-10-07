import {
  ArrowLeft,
  Calendar,
  Edit,
  Pause,
  RecentlyViewed,
  Report,
  Stamp,
  TableOfContents,
  Tag as TagIcon,
  Tree,
} from '@carbon/icons-react';
import { Button, Tab, TabList, TabPanel, TabPanels, Tabs } from '@carbon/react';
import { useCallback, useEffect, useState, type FC } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import DetailTile, { type DetailField } from '@/components/DetailTile';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { statusCodeVariant } from '@/components/StatusTag/statusCodes';
import Tombstone from '@/components/Tombstone';
import { useAuth } from '@/context/auth/useAuth';
import { useApiResource } from '@/hooks/useApiResource';
import { useLazyTabs } from '@/hooks/useLazyTabs';
import PageLayout from '@/pages/PageLayout';
import { canEdit, isPathAllowedForUser } from '@/routes/access';
import {
  getDistricts,
  getMarkingInstruments,
  getMarkingMethods,
  getSalvageTypes,
  type CodeOption,
} from '@/services/codeLists';
import { getCuttingPermitDetail } from '@/services/cutting_permit_detail';
import { formatDate } from '@/utils/formatDate';

import PermitCutBlocks from './PermitCutBlocks';

const nf = new Intl.NumberFormat('en-CA');

const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;

const date = (v: string | null | undefined) => formatDate(v) || '—';

/** "CODE - description" when both are known, else whichever is. */
const codeDesc = (code: string | null | undefined, desc: string | null | undefined) =>
  code && desc ? `${code} - ${desc}` : dash(desc || code);

interface Lookups {
  districts: CodeOption[];
  markingMethods: CodeOption[];
  markingInstruments: CodeOption[];
  salvageTypes: CodeOption[];
}

const NO_LOOKUPS: Lookups = {
  districts: [],
  markingMethods: [],
  markingInstruments: [],
  salvageTypes: [],
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
 * ports THE.FTA_902_CP_DETAIL).
 */
const CuttingPermitDetail: FC = () => {
  const { cpId = '' } = useParams();
  // A CP id is unique only within its tenure, so links from a tenure say which.
  const [searchParams] = useSearchParams();
  const forestFileId = searchParams.get('forestFileId') ?? undefined;
  const { user } = useAuth();

  const fetcher = useCallback(
    () => getCuttingPermitDetail(cpId, { forestFileId }),
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
    ]).then(([d, mm, mi, st]) => {
      if (cancelled) return;
      const ok = (r: PromiseSettledResult<CodeOption[]>) =>
        r.status === 'fulfilled' ? r.value : [];
      setLookups({
        districts: ok(d),
        markingMethods: ok(mm),
        markingInstruments: ok(mi),
        salvageTypes: ok(st),
      });
    });
    return () => {
      cancelled = true;
    };
  }, []);

  // Each tab is rendered only once opened.
  const tabs = useLazyTabs(`${cpId}|${forestFileId ?? ''}`);

  const area = cp?.harvestArea != null ? `${nf.format(cp.harvestArea)} ha` : '—';
  const permitId = cp?.cuttingPermitId ?? cpId;

  const actions =
    cp && canEdit(user) ? (
      <>
        <Button size="md" kind="tertiary" renderIcon={Edit}>
          Edit permit
        </Button>
        <Button
          size="md"
          kind="tertiary"
          renderIcon={TagIcon}
          as={Link}
          to={`/harvesting-authority/${permitId}/assign-marks`}
        >
          Assign marks to blocks
        </Button>
        <Button
          size="md"
          kind="danger"
          renderIcon={Pause}
          as={Link}
          to={`/harvesting-authority/${permitId}/suspend-blocks`}
        >
          Suspend blocks
        </Button>
      </>
    ) : undefined;

  // A Timber Mark Headquarters Administrator reaches this page from Timber Mark
  // Search and may not open Harvesting Authority Search, so goes back there.
  const back = isPathAllowedForUser(user, '/search/harvesting-authority')
    ? { path: '/search/harvesting-authority', label: 'Harvesting Authority Search' }
    : { path: '/search/timber-mark', label: 'Timber Mark Search' };

  const permitFields: DetailField[] = cp
    ? [
        { label: 'File Type', value: codeDesc(cp.fileTypeCode, cp.fileTypeDescription) },
        { label: 'Licensee', value: dash(cp.licensee) },
        { label: 'Forest District', value: label(lookups.districts, cp.forestDistrict) },
        { label: 'Authorized Area', value: area },
        {
          label: 'Management Unit',
          value: cp.mgmtUnitTypeCode
            ? [cp.mgmtUnitTypeCode, cp.mgmtUnitId].filter(Boolean).join(' ')
            : '—',
        },
        { label: 'Legal Description', value: dash(cp.location), wide: true },
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
        { label: 'Quota Type', value: dash(cp.quotaTypeCode) },
        { label: 'Salvage Type', value: label(lookups.salvageTypes, cp.salvageTypeCode) },
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
                { label: 'Admin Organization', value: dash(cp.adminOrgCode) },
                { label: 'Status', value: codeDesc(cp.statusCode, cp.statusDesc) },
                { label: 'Area', value: area },
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
                </TabList>
                <TabPanels>
                  <TabPanel>
                    {tabs.isOpened(0) && (
                      <div className="fsp-info__tab-panel">
                        <div className="fsp-info__tile-row">
                          <DetailTile title="Permit details" icon={Report} fields={permitFields} />
                          <DetailTile
                            title="Issuance conditions"
                            icon={Stamp}
                            fields={conditionFields}
                          />
                        </div>
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
                    {tabs.isOpened(2) && (
                      <div className="fsp-info__tab-panel">
                        <DetailTile title="Term and status" icon={Calendar} fields={termFields} />
                      </div>
                    )}
                  </TabPanel>
                </TabPanels>
              </Tabs>
            </div>
          </>
        )}
      </AsyncBoundary>
    </PageLayout>
  );
};

export default CuttingPermitDetail;
