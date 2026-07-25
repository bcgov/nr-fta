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
import { useApiResource } from '@/hooks/useApiResource';
import { canEdit } from '@/routes/access';
import PageLayout from '@/pages/PageLayout';
import { getRangeTenureDetail } from '@/services/range_tenure_detail';

const nf = new Intl.NumberFormat('en-CA');

/** Map a range file status code to a Carbon Tag colour. */
function statusTagType(status: string | null): 'green' | 'blue' | 'gray' | 'red' {
  switch (status) {
    case 'PA':
    case 'PI':
    case 'PP':
    case 'PL':
      return 'blue';
    case 'CA':
    case 'RRS':
      return 'red';
    case 'EE':
    case 'D':
      return 'gray';
    default:
      return 'green';
  }
}

const dash = (v: string | number | null | undefined) => (v === null || v === undefined || v === '' ? '—' : v);
const num = (v: number | null | undefined) => (v === null || v === undefined ? '—' : nf.format(v));

/**
 * FTA100Range — Range Tenure detail. Tombstone + tabs for range usage
 * (FTA613R), rotations (FTA611/612), associated land base (FTA615R), and
 * usage history. Backed by the backend {@code GET
 * /api/fta/range-tenures/{agreementId}} endpoint, which ports
 * THE.FTA_100RANGE_TENURE plus its range usage and land base sub-tabs.
 */
const RangeTenureDetail: FC = () => {
  const { agreementId = '' } = useParams();
  const { user } = useAuth();
  const { data, loading, error, reload } = useApiResource(
    () => getRangeTenureDetail(agreementId),
    [agreementId],
  );

  return (
    <PageLayout title={`Range Tenure ${agreementId}`}>
      <Link to="/search/range-tenure" className="fta-back">
        <ArrowLeft size={16} /> Back to Range Tenure Search
      </Link>

      <AsyncBoundary loading={loading} error={error} onRetry={reload} loadingText="Loading range tenure…">
        {data && (
          <>
            <Tombstone
              ariaLabel="Range agreement summary"
              items={[
                { label: 'Agreement', value: data.forestFileId },
                { label: 'Type', value: dash(data.fileTypeCode) },
                {
                  label: 'Status',
                  value: <Tag type={statusTagType(data.fileStatusSt)}>{dash(data.fileStatusSt)}</Tag>,
                },
                { label: 'Holder', value: dash(data.licensee) },
                { label: 'Org Unit', value: dash(data.adminOrgUnitNo) },
                { label: 'Authorized AUMs', value: num(data.rangeUsage[0]?.authorizedUse ?? null) },
                { label: 'Issued', value: dash(data.issueDate) },
                { label: 'Expires', value: dash(data.expiryDate) },
              ]}
              action={
                canEdit(user) ? (
                  <Button size="sm" kind="tertiary" renderIcon={Edit}>Edit agreement</Button>
                ) : undefined
              }
            />

            <Tabs>
              <TabList aria-label="Range tenure sections" contained>
                <Tab>Range Usage</Tab>
                <Tab>Rotations</Tab>
                <Tab>Land Base</Tab>
                <Tab>Usage History</Tab>
              </TabList>
              <TabPanels>
                <TabPanel>
                  <DefinitionGrid
                    items={[
                      { label: 'Management Unit', value: dash(data.mgmtUnitId ?? data.fileName) },
                      { label: 'Authorized AUMs', value: num(data.rangeUsage[0]?.authorizedUse ?? null) },
                      { label: 'Agreement Type', value: dash(data.fileTypeCode) },
                      { label: 'Term', value: `${dash(data.issueDate)} — ${dash(data.expiryDate)}` },
                    ]}
                  />
                </TabPanel>

                <TabPanel>
                  <TableContainer
                    title="Grazing / Hay Cutting Rotations"
                    description={`${data.rangeUsage.length} rotation(s)`}
                  >
                    <Table>
                      <TableHead>
                        <TableRow>
                          <TableHeader>Year</TableHeader>
                          <TableHeader>Unit</TableHeader>
                          <TableHeader>Kind</TableHeader>
                          <TableHeader>Start</TableHeader>
                          <TableHeader>End</TableHeader>
                          <TableHeader>AUMs</TableHeader>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {data.rangeUsage.map((r, i) => (
                          <TableRow key={i}>
                            <TableCell>{dash(r.calendarYear)}</TableCell>
                            <TableCell>{dash(data.mgmtUnitId)}</TableCell>
                            <TableCell>{dash(data.fileTypeCode)}</TableCell>
                            <TableCell>{dash(data.issueDate)}</TableCell>
                            <TableCell>{dash(data.expiryDate)}</TableCell>
                            <TableCell>{num(r.totalAnnualUse)}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </TabPanel>

                <TabPanel>
                  <TableContainer title="Associated Land Base" description={`${data.landBase.length} parcel(s)`}>
                    <Table>
                      <TableHead>
                        <TableRow>
                          <TableHeader>Parcel</TableHeader>
                          <TableHeader>Description</TableHeader>
                          <TableHeader>Area (ha)</TableHeader>
                          <TableHeader>Tenure Type</TableHeader>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {data.landBase.map((p) => (
                          <TableRow key={p.landBaseSkey ?? p.rangeLandBaseId}>
                            <TableCell>{dash(p.rangeLandBaseId ?? p.rangeLandBasePid)}</TableCell>
                            <TableCell>{dash(p.description)}</TableCell>
                            <TableCell>{num(p.forageProduction)}</TableCell>
                            <TableCell>{dash(p.rangeLandOwnershipTypeDesc ?? p.rangeLandOwnershipTypeCode)}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </TabPanel>

                <TabPanel>
                  <TableContainer title="Usage History">
                    <Table>
                      <TableHead>
                        <TableRow>
                          <TableHeader>Year</TableHeader>
                          <TableHeader>Authorized AUMs</TableHeader>
                          <TableHeader>Actual AUMs</TableHeader>
                          <TableHeader>Utilization</TableHeader>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {data.rangeUsage.map((u, i) => (
                          <TableRow key={i}>
                            <TableCell>{dash(u.calendarYear)}</TableCell>
                            <TableCell>{num(u.authorizedUse)}</TableCell>
                            <TableCell>{num(u.totalAnnualUse)}</TableCell>
                            <TableCell>
                              {u.authorizedUse && u.totalAnnualUse
                                ? `${Math.round((u.totalAnnualUse / u.authorizedUse) * 100)}%`
                                : '—'}
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </TabPanel>
              </TabPanels>
            </Tabs>
          </>
        )}
      </AsyncBoundary>
    </PageLayout>
  );
};

export default RangeTenureDetail;
