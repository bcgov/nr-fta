import {
  ArrowLeft,
  ChartColumn,
  Currency,
  Document,
  Edit,
  Folders,
  Notebook,
  Report,
  Road as RoadIcon,
  Stamp,
  TableOfContents,
  Tree,
  UserMultiple,
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
import { Link, useParams } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import StatusTag from '@/components/StatusTag/StatusTag';
import { useAuth } from '@/context/auth/useAuth';
import { useApiResource } from '@/hooks/useApiResource';
import PageLayout from '@/pages/PageLayout';
import { canEdit } from '@/routes/access';
import { getTenureDetail } from '@/services/tenure_detail';

import type { CarbonIconType } from '@carbon/icons-react';
import type { FC, ReactNode } from 'react';

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

interface Field {
  label: string;
  value: ReactNode;
}

/**
 * A white section card on the grey tab canvas: an icon heading over a
 * label/value list — the section treatment of nr-fsp-new's FSP Information tab.
 */
const DetailTile: FC<{
  title: string;
  icon: CarbonIconType;
  fields: Field[];
  /** Right-aligned in the card header — where FSP puts its "Edit …" buttons. */
  action?: ReactNode;
}> = ({ title, icon: Icon, fields, action }) => (
  <section className="fsp-info__tile">
    <header className="fsp-info__tile-header">
      <h2 className="fsp-info__section-title">
        <Icon size={20} />
        <span>{title}</span>
      </h2>
      {action}
    </header>
    <dl className="fsp-info__field-list">
      {fields.map((f) => (
        <div key={f.label} className="fsp-info__field">
          <dt>{f.label}</dt>
          <dd>{f.value}</dd>
        </div>
      ))}
    </dl>
  </section>
);

/**
 * FTA100 — Tenure detail. Title and actions, then Carbon contained Tabs over a
 * full-bleed grey pane (the nr-fsp-new FSP information layout): Details first,
 * holding the tenure summary, then the tenure's sub-entities. Backed by the
 * backend {@code GET /api/fta/tenures/{id}} endpoint, which ports
 * THE.FTA_100_TENURE (+ FTA_930_AAC, FTA_940_SALE_INFO).
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

  // FSP places each section's edit action in that section's card header,
  // shown only to users who may edit. Not wired to an edit mode yet.
  const editButton = (label: string) =>
    canEdit(user) ? (
      <Button kind="tertiary" size="sm" renderIcon={Edit}>
        {label}
      </Button>
    ) : undefined;

  const status = tenure?.fileStatusDesc ?? tenure?.fileStatusCode;
  const allowableAnnualCut =
    tenure?.allowableAnnualCut != null ? `${nf.format(tenure.allowableAnnualCut)} m³/yr` : '—';

  return (
    <PageLayout
      title={`Tenure ${fileId}`}
      subtitle="Tenure record: cutting permits, cut blocks, roads, associated files and clients, AAC and sale details."
    >
      <Link to="/search/tenure" className="back-link">
        <ArrowLeft size={16} /> Back to Tenure Search
      </Link>

      <AsyncBoundary loading={loading} error={error} onRetry={reload} loadingText="Loading tenure…">
        {tenure && (
          // Carbon's <Tabs> renders no DOM of its own, so the grey full-bleed
          // pane is styled through this wrapper (styles/_detail.scss).
          <div className="fsp-info__page-tabs">
            <Tabs>
              <TabList aria-label="Tenure sections" contained>
                <Tab renderIcon={TableOfContents}>Details</Tab>
                <Tab renderIcon={Document}>Tenure</Tab>
                <Tab renderIcon={Stamp}>CP / mark</Tab>
                <Tab renderIcon={Tree}>Cut block</Tab>
                <Tab renderIcon={RoadIcon}>Roads</Tab>
                <Tab renderIcon={Folders}>Assoc files</Tab>
                <Tab renderIcon={UserMultiple}>Assoc clients</Tab>
                <Tab renderIcon={ChartColumn}>AAC</Tab>
                <Tab renderIcon={Currency}>Sale info</Tab>
                <Tab renderIcon={Notebook}>Notes</Tab>
              </TabList>
              <TabPanels>
                <TabPanel>
                  <div className="fsp-info__tab-panel">
                    <DetailTile
                      title="Tenure summary"
                      action={editButton('Edit tenure details')}
                      icon={Report}
                      fields={[
                        { label: 'File ID', value: tenure.forestFileId },
                        { label: 'File Type', value: tenure.fileTypeCode ?? '—' },
                        { label: 'Status', value: status ? <StatusTag status={status} /> : '—' },
                        { label: 'Org Unit', value: tenure.orgUnitCode ?? '—' },
                        { label: 'Licensee', value: tenure.licensee ?? '—' },
                        { label: 'Client #', value: tenure.clientNumber ?? '—' },
                        { label: 'Issued', value: tenure.awardDate ?? '—' },
                        { label: 'Expires', value: tenure.expiryDate ?? '—' },
                      ]}
                    />
                  </div>
                </TabPanel>

                <TabPanel>
                  <div className="fsp-info__tab-panel">
                    <DetailTile
                      title="Tenure"
                      action={editButton('Edit tenure')}
                      icon={Document}
                      fields={[
                        { label: 'Management Unit', value: tenure.managementUnit ?? '—' },
                        { label: 'Allowable Annual Cut', value: allowableAnnualCut },
                        { label: 'Issue Date', value: tenure.awardDate ?? '—' },
                        { label: 'Expiry Date', value: tenure.expiryDate ?? '—' },
                      ]}
                    />
                  </div>
                </TabPanel>

                <TabPanel>
                  <div className="fsp-info__tab-panel bordered-table">
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
                  <div className="fsp-info__tab-panel bordered-table">
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
                  <div className="fsp-info__tab-panel bordered-table">
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
                  <div className="fsp-info__tab-panel bordered-table">
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
                  <div className="fsp-info__tab-panel bordered-table">
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
                  <div className="fsp-info__tab-panel">
                    <DetailTile
                      title="Allowable annual cut"
                      action={editButton('Edit AAC')}
                      icon={ChartColumn}
                      fields={[
                        { label: 'Allowable Annual Cut', value: allowableAnnualCut },
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
                  </div>
                </TabPanel>

                <TabPanel>
                  <div className="fsp-info__tab-panel">
                    <DetailTile
                      title="Sale information"
                      action={editButton('Edit sale information')}
                      icon={Currency}
                      fields={[
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
                  </div>
                </TabPanel>

                <TabPanel>
                  <div className="fsp-info__tab-panel bordered-table">
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
          </div>
        )}
      </AsyncBoundary>
    </PageLayout>
  );
};

export default TenureDetail;
