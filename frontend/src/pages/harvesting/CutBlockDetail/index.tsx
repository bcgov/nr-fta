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
  Tag,
} from '@carbon/react';
import { ArrowLeft, Edit, Pause, Tag as TagIcon } from '@carbon/icons-react';
import type { FC } from 'react';
import { Link, useParams } from 'react-router-dom';
import AsyncBoundary from '@/components/AsyncBoundary';
import DefinitionGrid from '@/components/DefinitionGrid';
import Tombstone from '@/components/Tombstone';
import { useAuth } from '@/context/auth/useAuth';
import { canEdit } from '@/routes/access';
import { useApiResource } from '@/hooks/useApiResource';
import PageLayout from '@/pages/PageLayout';
import { getCutblockDetail } from '@/services/cutblock_detail';
import './CutBlockDetail.scss';

// Local amendment history for the cut block sub-tab (FTA905 is a separate port).
const MOCK_AMENDMENTS = [
  { date: '2022-03-11', type: 'Area', description: 'Boundary adjustment −2.4 ha (riparian).' },
  { date: '2023-09-01', type: 'Volume', description: 'Planned volume revised after cruise.' },
];

const fmtArea = (n: number | null): string => (n == null ? '—' : `${n.toFixed(1)} ha`);

/**
 * FTA904 — Cut Block detail. Tombstone + tabs for block details, amendments
 * (FTA905), and suspensions (FTA914). Reached from Cut Block Search or a
 * cutting permit's Cut Blocks tab. Data is loaded from the backend
 * {@code GET /api/fta/cut-blocks/{blockId}} endpoint (which ports
 * THE.FTA_904_CUTBLKDETAIL).
 */
const CutBlockDetail: FC = () => {
  const { blockId = '' } = useParams();
  const { user } = useAuth();
  const { data, loading, error, reload } = useApiResource(
    () => getCutblockDetail(blockId),
    [blockId],
  );

  const id = data?.cutBlockId ?? blockId;
  const isSuspended = (data?.blockStatus ?? '').toUpperCase().startsWith('S');

  return (
    <PageLayout title={`Cut Block ${id}`}>
      <Link to="/search/cut-block" className="fta-back">
        <ArrowLeft size={16} /> Back to Cut Block Search
      </Link>

      <AsyncBoundary loading={loading} error={error} onRetry={reload} loadingText="Loading cut block…">
        {data && (
          <>
            <Tombstone
              ariaLabel="Cut block summary"
              items={[
                { label: 'Block', value: data.cutBlockId ?? id },
                {
                  label: 'Cutting Permit',
                  value: data.cuttingPermitId ? (
                    <Link to={`/harvesting-authority/${data.cuttingPermitId}`}>{data.cuttingPermitId}</Link>
                  ) : (
                    '—'
                  ),
                },
                {
                  label: 'Forest File',
                  value: data.forestFileId ? (
                    <Link to={`/tenures/${data.forestFileId}`}>{data.forestFileId}</Link>
                  ) : (
                    '—'
                  ),
                },
                { label: 'Timber Mark', value: data.timberMark ?? '—' },
                {
                  label: 'Status',
                  value: data.blockStatus ? <Tag type="green">{data.blockStatus}</Tag> : '—',
                },
                { label: 'Org Unit', value: data.forestDistrict ?? '—' },
                { label: 'Gross Area', value: fmtArea(data.plannedGrossBlockArea) },
                { label: 'Net Area', value: fmtArea(data.plannedNetBlockArea) },
              ]}
              action={
                canEdit(user) ? (
                  <Button size="sm" kind="tertiary" renderIcon={Edit}>Edit block</Button>
                ) : undefined
              }
            />

            {canEdit(user) && (
              <div className="cb-detail__actions">
                <Button size="sm" kind="tertiary" renderIcon={Edit} as={Link} to={`/cut-block/${id}/amend`}>
                  Amend
                </Button>
                <Button size="sm" kind="tertiary" renderIcon={TagIcon} as={Link} to={`/cut-block/${id}/relabel`}>
                  Re-label
                </Button>
                <Button size="sm" kind="danger--tertiary" renderIcon={Pause} as={Link} to={`/cut-block/${id}/suspend`}>
                  Suspend
                </Button>
              </div>
            )}

            <Tabs>
              <TabList aria-label="Cut block sections" contained>
                <Tab>Details</Tab>
                <Tab>Amendments</Tab>
                <Tab>Suspensions</Tab>
              </TabList>
              <TabPanels>
                <TabPanel>
                  <DefinitionGrid
                    items={[
                      { label: 'Gross Area', value: fmtArea(data.plannedGrossBlockArea) },
                      { label: 'Net Area', value: fmtArea(data.plannedNetBlockArea) },
                      { label: 'Disturbance Gross Area', value: fmtArea(data.disturbanceGrossArea) },
                      { label: 'Disturbance Start', value: data.disturbanceStartDate ?? 'Not started' },
                    ]}
                  />
                </TabPanel>

                <TabPanel>
                  <TableContainer title="Amendments" description={`${MOCK_AMENDMENTS.length} amendment(s)`}>
                    <Table>
                      <TableHead>
                        <TableRow>
                          <TableHeader>Date</TableHeader>
                          <TableHeader>Type</TableHeader>
                          <TableHeader>Description</TableHeader>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {MOCK_AMENDMENTS.map((a, i) => (
                          <TableRow key={i}>
                            <TableCell>{a.date}</TableCell>
                            <TableCell>{a.type}</TableCell>
                            <TableCell>{a.description}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </TabPanel>

                <TabPanel>
                  {isSuspended ? (
                    <DefinitionGrid
                      items={[
                        { label: 'Suspension Status', value: <Tag type="red">Suspended</Tag> },
                        { label: 'Reason', value: 'Pending cutblock re-survey' },
                        { label: 'Effective', value: data.blockStatusDate ?? '—' },
                      ]}
                    />
                  ) : (
                    <p style={{ padding: '1rem 0' }}>This cut block has no active suspensions.</p>
                  )}
                </TabPanel>
              </TabPanels>
            </Tabs>
          </>
        )}
      </AsyncBoundary>
    </PageLayout>
  );
};

export default CutBlockDetail;
