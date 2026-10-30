import {
  ArrowLeft,
  Calendar,
  Edit,
  Pause,
  Report,
  TableOfContents,
  Tag as TagIcon,
  Tree,
  WarningAlt,
} from '@carbon/icons-react';
import {
  Button,
  Tab,
  TabList,
  TabPanel,
  TabPanels,
  Tabs,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { useState, type FC } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import DetailTile, { type DetailField } from '@/components/DetailTile';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { statusCodeVariant } from '@/components/StatusTag/statusCodes';
import StatusTag from '@/components/StatusTag/StatusTag';
import Tombstone from '@/components/Tombstone';
import { useAuth } from '@/context/auth/useAuth';
import { useApiResource } from '@/hooks/useApiResource';
import { useLazyTabs } from '@/hooks/useLazyTabs';
import PageLayout from '@/pages/PageLayout';
import { canManageHarvest } from '@/routes/access';
import { blockQuery, getCutblockDetail } from '@/services/cutblock_detail';
import { formatDate } from '@/utils/formatDate';

import BlockAmendments from './BlockAmendments';
import EditCutBlockModal from './EditCutBlockModal';

const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;
const date = (v: string | null | undefined) => formatDate(v) || '—';
const area = (n: number | null) =>
  n == null
    ? '—'
    : `${n.toLocaleString('en-CA', { minimumFractionDigits: 1, maximumFractionDigits: 4 })} ha`;
const yesNo = (v: string | null) => (v === 'Y' ? 'Yes' : v === 'N' ? 'No' : '—');

/**
 * FTA904 — Cut Block detail. Tombstone + tabs for block details, amendments
 * (FTA905), and suspensions (FTA914). Reached from Cut Block Search or a
 * cutting permit's Cut Blocks tab. Data is loaded from the backend
 * {@code GET /api/fta/cut-blocks/{blockId}} endpoint, which also says what the
 * block allows; its writes enforce the same rules.
 */
const CutBlockDetail: FC = () => {
  const { blockId = '' } = useParams();
  // A block id repeats across tenures and permits, so links say which.
  const [searchParams] = useSearchParams();
  const forestFileId = searchParams.get('forestFileId') ?? '';
  const cuttingPermitId = searchParams.get('cuttingPermitId') ?? undefined;
  const { user } = useAuth();
  const { data, loading, error, reload } = useApiResource(
    () => getCutblockDetail(blockId, { forestFileId, cuttingPermitId }),
    [blockId, forestFileId, cuttingPermitId],
  );
  const [editing, setEditing] = useState(false);

  // Each tab is rendered only once opened.
  const tabs = useLazyTabs(`${blockId}|${forestFileId}|${cuttingPermitId ?? ''}`);

  const id = data?.cutBlockId ?? blockId;
  const query = data?.forestFileId ? blockQuery(data.forestFileId, data.cuttingPermitId) : '';
  const rules = data?.rules;
  // The suspension page is keyed by cb_skey, as a label is unique only within its permit.
  const suspensionPath = `/cut-block/${data?.cbSkey ?? ''}/suspension`;

  const actions = rules && (
    <>
      {rules.canEdit && (
        <Button size="md" kind="tertiary" renderIcon={Edit} onClick={() => setEditing(true)}>
          Edit block
        </Button>
      )}
      {rules.canRelabel && canManageHarvest(user) && (
        <Button
          size="md"
          kind="tertiary"
          renderIcon={TagIcon}
          as={Link}
          to={`/cut-block/${encodeURIComponent(id)}/relabel${query}`}
        >
          Re-label
        </Button>
      )}
      {rules.canSuspend && (
        <Button size="md" kind="danger" renderIcon={Pause} as={Link} to={suspensionPath}>
          Suspend
        </Button>
      )}
      {rules.canEditSuspension && (
        <Button size="md" kind="tertiary" renderIcon={Pause} as={Link} to={suspensionPath}>
          Edit suspension
        </Button>
      )}
    </>
  );

  const blockFields: DetailField[] = data
    ? [
        {
          label: 'Status',
          value: data.blockStatusDescription
            ? `${data.blockStatus} - ${data.blockStatusDescription}`
            : dash(data.blockStatus),
        },
        { label: 'As of', value: date(data.blockStatusDate) },
        { label: 'Opening', value: dash(data.opening) },
        { label: 'SP Exempt', value: yesNo(data.spExemptInd) },
        { label: 'Reference', value: dash(data.referenceName) },
        { label: 'Cut Management', value: dash(data.cutRegulationCode) },
        { label: 'Reforest Declare Type', value: dash(data.reforestDeclareTypeCode) },
        { label: "Waste Assessment Req'd", value: yesNo(data.wasteAssessmentRequired) },
        { label: 'Waste Status', value: dash(data.wasteDescription) },
        { label: 'Under Partition Order', value: yesNo(data.underPartitionOrder) },
        { label: 'Harvesting Reason', value: dash(data.fireHarvestingReasonCode) },
        { label: 'Reported Fire Date', value: date(data.reportedFireDate) },
        { label: 'Approved', value: date(data.decisionDate) },
        { label: 'Issued', value: date(data.issuanceDate) },
        { label: 'Block Description', value: dash(data.cutBlockDescription), wide: true },
      ]
    : [];

  const areaFields: DetailField[] = data
    ? [
        { label: 'Planned Gross', value: area(data.plannedGrossBlockArea) },
        { label: 'Planned Net', value: area(data.plannedNetBlockArea) },
        { label: 'Planned Start', value: date(data.plannedHarvestDate) },
        { label: 'Logged Area', value: area(data.disturbanceGrossArea) },
        { label: 'Logged Start', value: date(data.disturbanceStartDate) },
        { label: 'Logged Complete', value: date(data.disturbanceEndDate) },
        { label: 'Total Logged', value: area(data.totalLoggedArea) },
      ]
    : [];

  const markFields: DetailField[] = data
    ? [
        { label: 'Mark', value: dash(data.timberMark) },
        { label: 'District', value: dash(data.forestDistrict) },
        { label: 'Mark Status', value: dash(data.markStatus) },
        { label: 'Issue Date', value: date(data.markIssueDate) },
        { label: 'Mark Term', value: dash(data.markTerm) },
        { label: 'Expires', value: date(data.markExpiryDate) },
        { label: 'Salvage Type', value: dash(data.salvageTypeCode) },
      ]
    : [];

  return (
    <PageLayout
      title={`Cut Block ${id}`}
      subtitle="Block details, amendments, and suspensions"
      actions={actions}
      backLink={
        <Link to="/search/cut-block" className="back-link">
          <ArrowLeft size={16} /> Back to Cut Block Search
        </Link>
      }
    >
      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Loading cut block…"
      >
        {data && (
          <>
            <Tombstone
              ariaLabel="Cut block summary"
              // The left bar takes the status's colour, as on the permit and tenure.
              className={`bc-status-accent--${statusCodeVariant(data.blockStatus) ?? 'default'}`}
              items={[
                { label: 'Block', value: data.cutBlockId ?? id },
                {
                  label: 'Cutting Permit',
                  value:
                    data.cuttingPermitId && data.forestFileId ? (
                      <Link
                        to={`/harvesting-authority/${encodeURIComponent(
                          data.cuttingPermitId,
                        )}?forestFileId=${encodeURIComponent(data.forestFileId)}`}
                      >
                        {data.cuttingPermitId}
                      </Link>
                    ) : (
                      dash(data.cuttingPermitId)
                    ),
                },
                {
                  label: 'Forest File',
                  value: data.forestFileId ? (
                    <Link to={`/tenures/${encodeURIComponent(data.forestFileId)}`}>
                      {data.forestFileId}
                    </Link>
                  ) : (
                    '—'
                  ),
                },
                { label: 'Timber Mark', value: dash(data.timberMark) },
                {
                  label: 'Status',
                  value: data.blockStatus ? (
                    <StatusTag
                      status={data.blockStatusDescription ?? data.blockStatus}
                      variant={statusCodeVariant(data.blockStatus)}
                    />
                  ) : (
                    '—'
                  ),
                },
                { label: 'Organization Unit', value: dash(data.forestDistrict) },
                { label: 'Gross Area', value: area(data.plannedGrossBlockArea) },
                { label: 'Net Area', value: area(data.plannedNetBlockArea) },
              ]}
            />

            {/* Carbon's <Tabs> renders no DOM of its own, so the grey full-bleed
                pane is styled through this wrapper (styles/_detail.scss). */}
            <div className="fsp-info__page-tabs">
              <Tabs selectedIndex={tabs.selected} onChange={tabs.onChange}>
                <TabList aria-label="Cut block sections" contained>
                  <Tab renderIcon={TableOfContents}>Details</Tab>
                  <Tab renderIcon={Calendar}>Amendments</Tab>
                  <Tab renderIcon={WarningAlt}>Suspensions</Tab>
                </TabList>
                <TabPanels>
                  <TabPanel>
                    {tabs.isOpened(0) && (
                      <div className="fsp-info__tab-panel">
                        <div className="fsp-info__tile-row">
                          <DetailTile title="Block" icon={Tree} fields={blockFields} />
                          <DetailTile title="Areas and dates" icon={Calendar} fields={areaFields} />
                        </div>
                        <DetailTile title="Timber mark" icon={Report} fields={markFields} />
                      </div>
                    )}
                  </TabPanel>

                  <TabPanel>
                    {tabs.isOpened(1) && (
                      <div className="fsp-info__tab-panel">
                        {data.forestFileId && (
                          <BlockAmendments forestFileId={data.forestFileId} cbSkey={data.cbSkey} />
                        )}
                      </div>
                    )}
                  </TabPanel>

                  <TabPanel>
                    {tabs.isOpened(2) && (
                      <div className="fsp-info__tab-panel">
                        {data.suspensions.length === 0 ? (
                          <EmptyState
                            icon={<WarningAlt size={48} />}
                            title="No suspensions for this cut block"
                            body="Partition order suspensions of this block appear here."
                          />
                        ) : (
                          <div className="bordered-table">
                            <TableContainer
                              title="Suspensions"
                              description={`${data.suspensions.length} suspension(s)`}
                            >
                              <Table>
                                <TableHead>
                                  <TableRow>
                                    <TableHeader>Order No</TableHeader>
                                    <TableHeader>Under Partition</TableHeader>
                                    <TableHeader>Start</TableHeader>
                                    <TableHeader>End</TableHeader>
                                  </TableRow>
                                </TableHead>
                                <TableBody>
                                  {data.suspensions.map((s) => (
                                    <TableRow key={s.suspensionId ?? s.orderNumber}>
                                      <TableCell>{dash(s.orderNumber)}</TableCell>
                                      <TableCell>{dash(s.underPartition)}</TableCell>
                                      <TableCell>{date(s.startDate)}</TableCell>
                                      <TableCell>{date(s.endDate)}</TableCell>
                                    </TableRow>
                                  ))}
                                </TableBody>
                              </Table>
                            </TableContainer>
                          </div>
                        )}
                      </div>
                    )}
                  </TabPanel>
                </TabPanels>
              </Tabs>
            </div>

            <EditCutBlockModal
              block={data}
              open={editing}
              onClose={() => setEditing(false)}
              onSaved={reload}
            />
          </>
        )}
      </AsyncBoundary>
    </PageLayout>
  );
};

export default CutBlockDetail;
