import { ArrowLeft, Document, Edit } from '@carbon/icons-react';
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
import { Link, useParams } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import DefinitionGrid from '@/components/DefinitionGrid';
import SectionTile from '@/components/SectionTile';
import Tombstone from '@/components/Tombstone';
import { useAuth } from '@/context/auth/useAuth';
import { useApiResource } from '@/hooks/useApiResource';
import PageLayout from '@/pages/PageLayout';
import { canEdit } from '@/routes/access';
import { getTenureDetail } from '@/services/tenure_detail';

import type { FC } from 'react';

const nf = new Intl.NumberFormat('en-CA');

// Sub-collection tabs (CP/Mark, Cut Block, Roads, Assoc Files, Assoc Clients,
// Notes) are served by separate endpoints not yet ported in this vertical
// slice; the columns/cross-links are kept intact, driven by empty lists for now.
type CuttingPermit = {
  cpId: string;
  timberMark: string;
  status: string;
  issueDate: string;
  volume: number;
};
type CutBlock = { blockId: string; cpId: string; status: string; areaHa: number };
type Road = { roadId: string; name: string; status: string; lengthKm: number; tenureType: string };
type AssociatedFile = { fileId: string; relationship: string; fileType: string; status: string };
type AssociatedClient = {
  clientNumber: string;
  name: string;
  relationship: string;
  location: string;
};
type Note = { date: string; author: string; text: string };

/**
 * FTA100 — Tenure detail. Persistent "tombstone" header (key identifiers) +
 * Carbon Tabs across the tenure's sub-entities (CP/Mark, Cut Block, Assoc
 * Clients, AAC, Notes) — the tabbed-detail pattern the other FTA record
 * screens follow. Backed by the backend {@code GET /api/fta/tenures/{id}}
 * endpoint, which ports THE.FTA_100_TENURE (+ FTA_930_AAC, FTA_940_SALE_INFO).
 */
const TenureDetail: FC = () => {
  const { fileId = '' } = useParams();
  const { user } = useAuth();
  const {
    data: tenure,
    loading,
    error,
    reload,
  } = useApiResource(() => getTenureDetail(fileId), [fileId]);

  const cuttingPermits: CuttingPermit[] = [];
  const cutBlocks: CutBlock[] = [];
  const roads: Road[] = [];
  const associatedFiles: AssociatedFile[] = [];
  const associatedClients: AssociatedClient[] = [];
  const notes: Note[] = [];

  return (
    <PageLayout
      title={`Tenure ${fileId}`}
      subtitle="Tenure record: cutting permits, cut blocks, roads, associated files and clients, AAC and sale details."
      actions={
        tenure && canEdit(user) ? (
          <Button size="md" kind="tertiary" renderIcon={Edit}>
            Edit tenure
          </Button>
        ) : undefined
      }
    >
      <Link to="/search/tenure" className="back-link">
        <ArrowLeft size={16} /> Back to Tenure Search
      </Link>

      <AsyncBoundary loading={loading} error={error} onRetry={reload} loadingText="Loading tenure…">
        {tenure && (
          <>
            <SectionTile title="Tenure summary" icon={Document}>
              <Tombstone
                ariaLabel="Tenure summary"
                items={[
                  { label: 'File ID', value: tenure.forestFileId },
                  { label: 'File Type', value: tenure.fileTypeCode ?? '—' },
                  {
                    label: 'Status',
                    value:
                      (tenure.fileStatusDesc ?? tenure.fileStatusCode) ? (
                        <Tag type="green">{tenure.fileStatusDesc ?? tenure.fileStatusCode}</Tag>
                      ) : (
                        '—'
                      ),
                  },
                  { label: 'Org Unit', value: tenure.orgUnitCode ?? '—' },
                  { label: 'Licensee', value: tenure.licensee ?? '—' },
                  { label: 'Client #', value: tenure.clientNumber ?? '—' },
                  { label: 'Issued', value: tenure.awardDate ?? '—' },
                  { label: 'Expires', value: tenure.expiryDate ?? '—' },
                ]}
              />
            </SectionTile>

            <Tabs>
              <TabList aria-label="Tenure sections" contained>
                <Tab>Tenure</Tab>
                <Tab>CP / Mark</Tab>
                <Tab>Cut Block</Tab>
                <Tab>Roads</Tab>
                <Tab>Assoc Files</Tab>
                <Tab>Assoc Clients</Tab>
                <Tab>AAC</Tab>
                <Tab>Sale Info</Tab>
                <Tab>Notes</Tab>
              </TabList>
              <TabPanels>
                <TabPanel>
                  <DefinitionGrid
                    items={[
                      { label: 'Management Unit', value: tenure.managementUnit ?? '—' },
                      {
                        label: 'Allowable Annual Cut',
                        value:
                          tenure.allowableAnnualCut != null
                            ? `${nf.format(tenure.allowableAnnualCut)} m³/yr`
                            : '—',
                      },
                      { label: 'Issue Date', value: tenure.awardDate ?? '—' },
                      { label: 'Expiry Date', value: tenure.expiryDate ?? '—' },
                    ]}
                  />
                </TabPanel>

                <TabPanel>
                  <div className="bordered-table">
                    <TableContainer title="Cutting Permits & Timber Marks">
                      <Table>
                        <TableHead>
                          <TableRow>
                            <TableHeader>CP</TableHeader>
                            <TableHeader>Timber Mark</TableHeader>
                            <TableHeader>Status</TableHeader>
                            <TableHeader>Issue Date</TableHeader>
                            <TableHeader>Volume (m³)</TableHeader>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {cuttingPermits.map((cp) => (
                            <TableRow key={cp.cpId}>
                              <TableCell>
                                <Link to={`/harvesting-authority/${cp.cpId}`}>{cp.cpId}</Link>
                              </TableCell>
                              <TableCell>{cp.timberMark}</TableCell>
                              <TableCell>{cp.status}</TableCell>
                              <TableCell>{cp.issueDate}</TableCell>
                              <TableCell>{nf.format(cp.volume)}</TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  </div>
                </TabPanel>

                <TabPanel>
                  <div className="bordered-table">
                    <TableContainer title="Cut Blocks">
                      <Table>
                        <TableHead>
                          <TableRow>
                            <TableHeader>Block</TableHeader>
                            <TableHeader>CP</TableHeader>
                            <TableHeader>Status</TableHeader>
                            <TableHeader>Area (ha)</TableHeader>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {cutBlocks.map((b) => (
                            <TableRow key={b.blockId}>
                              <TableCell>
                                <Link to={`/cut-block/${b.blockId}`}>{b.blockId}</Link>
                              </TableCell>
                              <TableCell>
                                <Link to={`/harvesting-authority/${b.cpId}`}>{b.cpId}</Link>
                              </TableCell>
                              <TableCell>{b.status}</TableCell>
                              <TableCell>{b.areaHa.toFixed(1)}</TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  </div>
                </TabPanel>

                <TabPanel>
                  <div className="bordered-table">
                    <TableContainer title="Road Sections">
                      <Table>
                        <TableHead>
                          <TableRow>
                            <TableHeader>Road</TableHeader>
                            <TableHeader>Name</TableHeader>
                            <TableHeader>Status</TableHeader>
                            <TableHeader>Length (km)</TableHeader>
                            <TableHeader>Tenure Type</TableHeader>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {roads.map((r) => (
                            <TableRow key={r.roadId}>
                              <TableCell>
                                <Link to={`/road/${r.roadId}`}>{r.roadId}</Link>
                              </TableCell>
                              <TableCell>{r.name}</TableCell>
                              <TableCell>{r.status}</TableCell>
                              <TableCell>{r.lengthKm.toFixed(1)}</TableCell>
                              <TableCell>{r.tenureType}</TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  </div>
                </TabPanel>

                <TabPanel>
                  <div className="bordered-table">
                    <TableContainer title="Associated Files">
                      <Table>
                        <TableHead>
                          <TableRow>
                            <TableHeader>File ID</TableHeader>
                            <TableHeader>Relationship</TableHeader>
                            <TableHeader>File Type</TableHeader>
                            <TableHeader>Status</TableHeader>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {associatedFiles.map((f) => (
                            <TableRow key={f.fileId}>
                              <TableCell>
                                <Link to={`/tenures/${f.fileId}`}>{f.fileId}</Link>
                              </TableCell>
                              <TableCell>{f.relationship}</TableCell>
                              <TableCell>{f.fileType}</TableCell>
                              <TableCell>{f.status}</TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  </div>
                </TabPanel>

                <TabPanel>
                  <div className="bordered-table">
                    <TableContainer title="Associated Clients">
                      <Table>
                        <TableHead>
                          <TableRow>
                            <TableHeader>Client #</TableHeader>
                            <TableHeader>Name</TableHeader>
                            <TableHeader>Relationship</TableHeader>
                            <TableHeader>Location</TableHeader>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {associatedClients.map((c) => (
                            <TableRow key={c.clientNumber + c.location}>
                              <TableCell>{c.clientNumber}</TableCell>
                              <TableCell>{c.name}</TableCell>
                              <TableCell>{c.relationship}</TableCell>
                              <TableCell>{c.location}</TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  </div>
                </TabPanel>

                <TabPanel>
                  <DefinitionGrid
                    items={[
                      {
                        label: 'Allowable Annual Cut',
                        value:
                          tenure.allowableAnnualCut != null
                            ? `${nf.format(tenure.allowableAnnualCut)} m³/yr`
                            : '—',
                      },
                      {
                        label: 'Schedule A Area',
                        value:
                          tenure.scheduleAArea != null
                            ? `${nf.format(tenure.scheduleAArea)} ha`
                            : '—',
                      },
                      {
                        label: 'Schedule B Area',
                        value:
                          tenure.scheduleBArea != null
                            ? `${nf.format(tenure.scheduleBArea)} ha`
                            : '—',
                      },
                      { label: 'Management Unit', value: tenure.managementUnit ?? '—' },
                    ]}
                  />
                </TabPanel>

                <TabPanel>
                  <DefinitionGrid
                    items={[
                      { label: 'Sale Method', value: tenure.saleMethodCode ?? '—' },
                      { label: 'Sale Type', value: tenure.saleTypeCode ?? '—' },
                      { label: 'Payment Method', value: tenure.paymentMethodCode ?? '—' },
                      {
                        label: 'Bonus Bid',
                        value:
                          tenure.ftaBonusBid != null ? `$${nf.format(tenure.ftaBonusBid)}` : '—',
                      },
                      {
                        label: 'Cash Sale Total',
                        value:
                          tenure.cashSaleTotDol != null
                            ? `$${nf.format(tenure.cashSaleTotDol)}`
                            : '—',
                      },
                    ]}
                  />
                </TabPanel>

                <TabPanel>
                  <div className="bordered-table">
                    <TableContainer title="Forest / Range Notes">
                      <Table>
                        <TableHead>
                          <TableRow>
                            <TableHeader>Date</TableHeader>
                            <TableHeader>Author</TableHeader>
                            <TableHeader>Note</TableHeader>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {notes.map((n, i) => (
                            <TableRow key={i}>
                              <TableCell>{n.date}</TableCell>
                              <TableCell>{n.author}</TableCell>
                              <TableCell>{n.text}</TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  </div>
                </TabPanel>
              </TabPanels>
            </Tabs>
          </>
        )}
      </AsyncBoundary>
    </PageLayout>
  );
};

export default TenureDetail;
