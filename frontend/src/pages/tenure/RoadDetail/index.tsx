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
import { ArrowLeft, Edit } from '@carbon/icons-react';
import type { FC } from 'react';
import { Link, useParams } from 'react-router-dom';
import AsyncBoundary from '@/components/AsyncBoundary';
import DefinitionGrid from '@/components/DefinitionGrid';
import Tombstone from '@/components/Tombstone';
import { useAuth } from '@/context/auth/useAuth';
import { canEdit } from '@/routes/access';
import { useApiResource } from '@/hooks/useApiResource';
import PageLayout from '@/pages/PageLayout';
import { getRoadDetail } from '@/services/road_detail';

// Mock road segments (FTA907) for the road's Segments tab.
const MOCK_SEGMENTS = [
  { segId: 'SEG-1', fromKm: 0.0, toKm: 4.2, surface: 'Gravel', status: 'Active' },
  { segId: 'SEG-2', fromKm: 4.2, toKm: 9.6, surface: 'Native', status: 'Active' },
  { segId: 'SEG-3', fromKm: 9.6, toKm: 12.4, surface: 'Native', status: 'Wintering' },
];

/**
 * FTA131 — Road Section detail. Tombstone + tabs for section details, road
 * segments (FTA907), and tenure history (FTA906). Reached from a tenure's
 * Roads tab. Data comes from the backend {@code GET /api/fta/roads/{roadId}}
 * endpoint (which ports THE.FTA_131_ROADSECTION).
 */
const RoadDetail: FC = () => {
  const { roadId = '' } = useParams();
  const { user } = useAuth();
  const { data: road, loading, error, reload } = useApiResource(
    () => getRoadDetail(roadId),
    [roadId],
  );

  return (
    <PageLayout title={`Road Section ${roadId}`}>
      <AsyncBoundary loading={loading} error={error} onRetry={reload} loadingText="Loading road…">
        {road && (
          <>
            <Link to={`/tenures/${road.forestFileId}`} className="fta-back">
              <ArrowLeft size={16} /> Back to Tenure {road.forestFileId}
            </Link>

            <Tombstone
              ariaLabel="Road section summary"
              items={[
                { label: 'Road', value: road.roadSectionId },
                { label: 'Name', value: road.roadSectName ?? '—' },
                {
                  label: 'Status',
                  value: (
                    <Tag type={road.retirementDate ? 'gray' : 'green'}>
                      {road.roadSectionStatusCode ?? '—'}
                    </Tag>
                  ),
                },
                { label: 'Length', value: `${road.roadSectLength ?? '—'} km` },
                { label: 'Amendment', value: road.sectionCurrentAmendId ?? '—' },
                {
                  label: 'Forest File',
                  value: <Link to={`/tenures/${road.forestFileId}`}>{road.forestFileId}</Link>,
                },
              ]}
              action={
                canEdit(user) ? (
                  <Button size="sm" kind="tertiary" renderIcon={Edit}>Edit road</Button>
                ) : undefined
              }
            />

            <Tabs>
              <TabList aria-label="Road sections" contained>
                <Tab>Details</Tab>
                <Tab>Segments</Tab>
                <Tab>Tenure History</Tab>
              </TabList>
              <TabPanels>
                <TabPanel>
                  <DefinitionGrid
                    items={[
                      { label: 'Road Name', value: road.roadSectName ?? '—' },
                      { label: 'Total Length', value: `${road.roadSectLength ?? '—'} km` },
                      { label: 'Original Length', value: `${road.roadOrigLength ?? '—'} km` },
                      { label: 'Status', value: road.roadSectionStatusCode ?? '—' },
                    ]}
                  />
                </TabPanel>

                <TabPanel>
                  <TableContainer title="Road Segments" description={`${MOCK_SEGMENTS.length} segment(s)`}>
                    <Table>
                      <TableHead>
                        <TableRow>
                          <TableHeader>Segment</TableHeader>
                          <TableHeader>From (km)</TableHeader>
                          <TableHeader>To (km)</TableHeader>
                          <TableHeader>Surface</TableHeader>
                          <TableHeader>Status</TableHeader>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {MOCK_SEGMENTS.map((s) => (
                          <TableRow key={s.segId}>
                            <TableCell>{s.segId}</TableCell>
                            <TableCell>{s.fromKm.toFixed(1)}</TableCell>
                            <TableCell>{s.toKm.toFixed(1)}</TableCell>
                            <TableCell>{s.surface}</TableCell>
                            <TableCell>{s.status}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </TabPanel>

                <TabPanel>
                  <DefinitionGrid
                    items={[
                      { label: 'Marks', value: road.markList ?? '—' },
                      { label: 'Current Amendment', value: road.sectionCurrentAmendId ?? '—' },
                      { label: 'District Zone', value: road.districtAdmnZone ?? '—' },
                    ]}
                  />
                </TabPanel>
              </TabPanels>
            </Tabs>
          </>
        )}
      </AsyncBoundary>
    </PageLayout>
  );
};

export default RoadDetail;
