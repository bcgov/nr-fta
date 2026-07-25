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
import { useCallback, type FC } from 'react';
import { Link, useParams } from 'react-router-dom';
import AsyncBoundary from '@/components/AsyncBoundary';
import DefinitionGrid from '@/components/DefinitionGrid';
import Tombstone from '@/components/Tombstone';
import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import { canEdit } from '@/routes/access';
import PageLayout from '@/pages/PageLayout';
import { getMarkDetail } from '@/services/mark_detail';

// Carbon Tag colour per private-mark status code (PI/PA/HI/HX/DV/DD/…), with a
// neutral default for anything unmapped.
const STATUS_TAG: Record<string, 'green' | 'blue' | 'purple' | 'gray'> = {
  HI: 'green',
  DD: 'green',
  PA: 'blue',
  PI: 'blue',
  DV: 'purple',
  HX: 'gray',
  HN: 'gray',
};

const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;

/**
 * FTA510/511/513 — Private Mark detail. Tombstone + tabs (Mark Application,
 * Land Index, Associated Clients, Amendments), with an Amend action (FTA512)
 * gated to FTA_ADMIN. Backed by the backend {@code GET /api/fta/marks/{markNumber}}
 * endpoint (which ports THE.FTA_510_PRIVATE_MARK / 511 / 513).
 */
const MarkDetail: FC = () => {
  const { markNumber = '' } = useParams();
  const { user } = useAuth();
  const notify = useNotification();

  const fetcher = useCallback(() => getMarkDetail(markNumber), [markNumber]);
  const { data: mark, loading, error, reload } = useApiResource(fetcher, [markNumber]);

  const onAmend = () =>
    notify.display({
      kind: 'info',
      title: 'Amendment started',
      subtitle: `Amendment for mark ${mark?.timberMark ?? markNumber} (mock — no backend yet).`,
      timeout: 5000,
    });

  return (
    <PageLayout title={`Private Mark ${markNumber}`}>
      <Link to="/marks" className="fta-back">
        <ArrowLeft size={16} /> Back to Private Marks
      </Link>

      <AsyncBoundary loading={loading} error={error} onRetry={reload} loadingText="Loading mark…">
        {mark && (
          <>
            <Tombstone
              ariaLabel="Private mark summary"
              items={[
                { label: 'Mark Number', value: mark.timberMark },
                { label: 'Type', value: dash(mark.fileTypeCode) },
                {
                  label: 'Status',
                  value: mark.markStatusCode ? (
                    <Tag type={STATUS_TAG[mark.markStatusCode] ?? 'gray'}>{mark.markStatusCode}</Tag>
                  ) : (
                    '—'
                  ),
                },
                { label: 'Holder', value: dash(mark.clientName) },
                { label: 'Holder Client #', value: dash(mark.clientNumber) },
                { label: 'Org Unit', value: dash(mark.orgUnitCode ?? mark.forestDistrict) },
                { label: 'Issued', value: dash(mark.markIssueDate) },
                { label: 'Timber Origin', value: dash(mark.crownGrantedAcqDesc) },
              ]}
              action={
                canEdit(user) ? (
                  <Button size="sm" kind="tertiary" renderIcon={Edit} onClick={onAmend}>Amend mark</Button>
                ) : undefined
              }
            />

            <Tabs>
              <TabList aria-label="Mark sections" contained>
                <Tab>Mark Application</Tab>
                <Tab>Land Index</Tab>
                <Tab>Associated Clients</Tab>
                <Tab>Amendments</Tab>
              </TabList>
              <TabPanels>
                <TabPanel>
                  <DefinitionGrid
                    items={[
                      { label: 'Mark Number', value: dash(mark.timberMark) },
                      { label: 'Certificate', value: dash(mark.certificate) },
                      { label: 'File Type', value: dash(mark.fileTypeCode) },
                      { label: 'Timber Origin', value: dash(mark.crownGrantedAcqDesc) },
                      { label: 'Holder', value: `${dash(mark.clientName)} (${dash(mark.clientNumber)})` },
                      { label: 'Issue Date', value: dash(mark.markIssueDate) },
                      { label: 'Expiry Date', value: dash(mark.markExpiryDate) },
                      { label: 'Tenure Term', value: dash(mark.tenureTerm) },
                      { label: 'Status', value: dash(mark.markStatusCode) },
                    ]}
                  />
                </TabPanel>

                <TabPanel>
                  <TableContainer title="Mark Land Index" description={`${mark.landIndex.length} parcel(s)`}>
                    <Table>
                      <TableHead>
                        <TableRow>
                          <TableHeader>Primary Index</TableHeader>
                          <TableHeader>Secondary Index</TableHeader>
                          <TableHeader>Description</TableHeader>
                          <TableHeader>Deactivate Date</TableHeader>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {mark.landIndex.map((p) => (
                          <TableRow key={p.markLandIndexSkey ?? `${p.primaryLandIndexCode}-${p.secondaryLandIndexCode}`}>
                            <TableCell>{dash(p.primaryLandIndexCodeDesc ?? p.primaryLandIndexCode)}</TableCell>
                            <TableCell>{dash(p.secondaryLandIndexCodeDesc ?? p.secondaryLandIndexCode)}</TableCell>
                            <TableCell>{dash(p.markLandIndexDesc)}</TableCell>
                            <TableCell>{dash(p.indexDeactivateDate)}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </TabPanel>

                <TabPanel>
                  <TableContainer title="Associated Clients">
                    <Table>
                      <TableHead>
                        <TableRow>
                          <TableHeader>Client #</TableHeader>
                          <TableHeader>Name</TableHeader>
                          <TableHeader>City</TableHeader>
                          <TableHeader>Role</TableHeader>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {mark.clients.map((c) => (
                          <TableRow key={c.forClientLinkSkey ?? `${c.clientNumber}-${c.fileClientType}`}>
                            <TableCell>{dash(c.clientNumber)}</TableCell>
                            <TableCell>{dash(c.clientName)}</TableCell>
                            <TableCell>{dash(c.clientCity)}</TableCell>
                            <TableCell>{dash(c.fileClientTypeDesc ?? c.fileClientType)}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </TabPanel>

                <TabPanel>
                  <TableContainer title="Amendment history">
                    <Table>
                      <TableHead>
                        <TableRow>
                          <TableHeader>Date</TableHeader>
                          <TableHeader>Status</TableHeader>
                          <TableHeader>Revision</TableHeader>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {mark.amendments.map((a, i) => (
                          <TableRow key={`${a.amendRequestDate ?? 'amd'}-${i}`}>
                            <TableCell>{dash(a.amendRequestDate)}</TableCell>
                            <TableCell>{dash(a.prvMrkAmdStsSt)}</TableCell>
                            <TableCell>{dash(a.revisionCount)}</TableCell>
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

export default MarkDetail;
