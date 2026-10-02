import {
  ArrowLeft,
  Document,
  Edit,
  Map as MapIcon,
  RecentlyViewed,
  Report,
  Stamp,
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
import { useCallback, useEffect, useState, type FC } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import DetailTile from '@/components/DetailTile';
import StatusTag from '@/components/StatusTag/StatusTag';
import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import PageLayout from '@/pages/PageLayout';
import { canEditMarks } from '@/routes/access';
import { getPrivateMarkStatuses } from '@/services/codeLists';
import { getMarkDetail } from '@/services/mark_detail';
import { formatDate } from '@/utils/formatDate';

const nf = new Intl.NumberFormat('en-CA');

const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;
const date = (v: string | null | undefined) => dash(formatDate(v));

/** A table's single full-width row when it has nothing to list. */
const EmptyRow: FC<{ colSpan: number; text: string }> = ({ colSpan, text }) => (
  <TableRow>
    <TableCell colSpan={colSpan} className="fsp-info__empty-cell">
      {text}
    </TableCell>
  </TableRow>
);

/**
 * FTA510/511/513 — Private Mark detail. Title and actions, then Carbon
 * contained Tabs over a full-bleed grey pane — the TenureDetail layout:
 * Mark Application (FTA510), Land Index (FTA511), Associated Clients (FTA513)
 * and Amendments, with an Amend action (FTA512) gated to FTA_ADMIN. Backed by
 * the backend {@code GET /api/fta/marks/{id}} endpoint, which ports
 * THE.FTA_510_PRIVATE_MARK / 511 / 513.
 *
 * <p>The route id is the timber mark, or — with `?by=certificate` — the
 * certificate of an application that has not been issued a mark yet; the
 * FTA500 list links those rows that way.
 */
const MarkDetail: FC = () => {
  const { markNumber = '' } = useParams();
  const [searchParams] = useSearchParams();
  const byCertificate = searchParams.get('by') === 'certificate';
  const { user } = useAuth();
  const notify = useNotification();

  const fetcher = useCallback(
    () => getMarkDetail(markNumber, byCertificate),
    [markNumber, byCertificate],
  );
  const {
    data: mark,
    loading,
    error,
    reload,
  } = useApiResource(fetcher, [markNumber, byCertificate]);

  // Status codes (HN, PA, PI, DV, HI, HX) spelled out. Cosmetic: on failure the
  // codes are shown as they are.
  const [statusNames, setStatusNames] = useState<Record<string, string>>({});
  useEffect(() => {
    let cancelled = false;
    getPrivateMarkStatuses()
      .then((options) => {
        if (!cancelled) {
          setStatusNames(Object.fromEntries(options.map((o) => [o.code, o.description])));
        }
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);
  const statusTag = (code: string | null) =>
    code ? <StatusTag status={statusNames[code] || code} /> : '—';

  const onAmend = () =>
    notify.display({
      kind: 'info',
      title: 'Amendment started',
      subtitle: `Amendment for mark ${mark?.timberMark ?? markNumber} (mock — no backend yet).`,
      timeout: 5000,
    });

  const amendButton =
    mark?.timberMark && canEditMarks(user) ? (
      <Button kind="tertiary" size="sm" renderIcon={Edit} onClick={onAmend}>
        Amend mark
      </Button>
    ) : undefined;

  const title = byCertificate
    ? `Private Mark Application ${markNumber}`
    : `Private Mark ${markNumber}`;

  return (
    <PageLayout
      title={title}
      subtitle="Mark application, land index, associated clients and amendment history."
    >
      <Link to="/marks" className="back-link">
        <ArrowLeft size={16} /> Back to Private Marks
      </Link>

      <AsyncBoundary loading={loading} error={error} onRetry={reload} loadingText="Loading mark…">
        {mark && (
          // Carbon's <Tabs> renders no DOM of its own, so the grey full-bleed
          // pane is styled through this wrapper (styles/_detail.scss).
          <div className="fsp-info__page-tabs">
            <Tabs>
              <TabList aria-label="Private mark sections" contained>
                <Tab renderIcon={Document}>Mark application</Tab>
                <Tab renderIcon={MapIcon}>Land index</Tab>
                <Tab renderIcon={UserMultiple}>Associated clients</Tab>
                <Tab renderIcon={RecentlyViewed}>Amendments</Tab>
              </TabList>
              <TabPanels>
                <TabPanel>
                  <div className="fsp-info__tab-panel">
                    <DetailTile
                      title="Mark summary"
                      icon={Report}
                      action={amendButton}
                      fields={[
                        { label: 'Timber Mark', value: dash(mark.timberMark) },
                        { label: 'Certificate', value: dash(mark.certificate) },
                        { label: 'Status', value: statusTag(mark.markStatusCode) },
                        { label: 'Status Date', value: date(mark.markStatusDate) },
                        { label: 'File Type', value: dash(mark.fileTypeCode) },
                        {
                          label: 'Organization Unit',
                          value: dash(mark.orgUnitCode ?? mark.forestDistrict),
                        },
                        { label: 'Holder', value: dash(mark.clientName) },
                        {
                          label: 'Holder Client #',
                          value: mark.clientNumber
                            ? `${mark.clientNumber}${mark.clientLocnCode ? ` / ${mark.clientLocnCode}` : ''}`
                            : '—',
                        },
                      ]}
                    />
                    <DetailTile
                      title="Application"
                      icon={Document}
                      fields={[
                        { label: 'Application Date', value: date(mark.markApplicationDate) },
                        { label: 'Issue Date', value: date(mark.markIssueDate) },
                        { label: 'Expiry Date', value: date(mark.markExpiryDate) },
                        { label: 'Cancel Date', value: date(mark.markCancelDate) },
                        {
                          label: 'Tenure Term',
                          value: mark.tenureTerm != null ? `${mark.tenureTerm} yr` : '—',
                        },
                        { label: 'Timber Origin', value: dash(mark.crownGrantedAcqDesc) },
                        { label: 'Granted / Acquired', value: date(mark.grantedAcqrdDate) },
                        { label: 'Proof of Crown Grant', value: dash(mark.proofOfCrownOrLegal) },
                      ]}
                    />
                    <DetailTile
                      title="Marking and location"
                      icon={Stamp}
                      fields={[
                        { label: 'Marking Method', value: dash(mark.markingMethodCode) },
                        { label: 'Marking Instrument', value: dash(mark.markingInstrumentCode) },
                        { label: 'Block Location', value: dash(mark.permitBlockLocn) },
                        {
                          label: 'Block Area',
                          value:
                            mark.permitBlockArea != null
                              ? `${nf.format(mark.permitBlockArea)} ha`
                              : '—',
                        },
                      ]}
                    />
                  </div>
                </TabPanel>

                <TabPanel>
                  <div className="fsp-info__tab-panel bordered-table">
                    <TableContainer
                      title="Mark Land Index"
                      description={`${mark.landIndex.length} parcel(s)`}
                    >
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
                          {mark.landIndex.length === 0 ? (
                            <EmptyRow colSpan={4} text="No land index recorded for this mark." />
                          ) : (
                            mark.landIndex.map((p) => (
                              <TableRow
                                key={
                                  p.markLandIndexSkey ??
                                  `${p.primaryLandIndexCode}-${p.secondaryLandIndexCode}`
                                }
                              >
                                <TableCell>
                                  {dash(p.primaryLandIndexCodeDesc ?? p.primaryLandIndexCode)}
                                </TableCell>
                                <TableCell>
                                  {dash(p.secondaryLandIndexCodeDesc ?? p.secondaryLandIndexCode)}
                                </TableCell>
                                <TableCell>{dash(p.markLandIndexDesc)}</TableCell>
                                <TableCell>{date(p.indexDeactivateDate)}</TableCell>
                              </TableRow>
                            ))
                          )}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  </div>
                </TabPanel>

                <TabPanel>
                  <div className="fsp-info__tab-panel bordered-table">
                    <TableContainer
                      title="Associated Clients"
                      description={`${mark.clients.length} client(s)`}
                    >
                      <Table>
                        <TableHead>
                          <TableRow>
                            <TableHeader>Client #</TableHeader>
                            <TableHeader>Location</TableHeader>
                            <TableHeader>Name</TableHeader>
                            <TableHeader>City</TableHeader>
                            <TableHeader>Role</TableHeader>
                            <TableHeader>Start Date</TableHeader>
                            <TableHeader>End Date</TableHeader>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {mark.clients.length === 0 ? (
                            <EmptyRow colSpan={7} text="No clients associated with this mark." />
                          ) : (
                            mark.clients.map((c) => (
                              <TableRow
                                key={
                                  c.forClientLinkSkey ??
                                  `${c.clientNumber}-${c.clientLocnCode}-${c.fileClientType}`
                                }
                              >
                                <TableCell>{dash(c.clientNumber)}</TableCell>
                                <TableCell>{dash(c.clientLocnCode)}</TableCell>
                                <TableCell>{dash(c.clientName)}</TableCell>
                                <TableCell>{dash(c.clientCity)}</TableCell>
                                <TableCell>
                                  {dash(c.fileClientTypeDesc ?? c.fileClientType)}
                                </TableCell>
                                <TableCell>{date(c.licenseeStartDt)}</TableCell>
                                <TableCell>{date(c.licenseeEndDate)}</TableCell>
                              </TableRow>
                            ))
                          )}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  </div>
                </TabPanel>

                <TabPanel>
                  <div className="fsp-info__tab-panel bordered-table">
                    <TableContainer
                      title="Amendment History"
                      description={`${mark.amendments.length} amendment(s)`}
                    >
                      <Table>
                        <TableHead>
                          <TableRow>
                            <TableHeader>Request Date</TableHeader>
                            <TableHeader>Status</TableHeader>
                            <TableHeader>Revision</TableHeader>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {mark.amendments.length === 0 ? (
                            <EmptyRow colSpan={3} text="No amendments on record for this mark." />
                          ) : (
                            mark.amendments.map((a, i) => (
                              <TableRow key={`${a.amendRequestDate ?? 'amd'}-${i}`}>
                                <TableCell>{date(a.amendRequestDate)}</TableCell>
                                <TableCell>{statusTag(a.prvMrkAmdStsSt)}</TableCell>
                                <TableCell>{dash(a.revisionCount)}</TableCell>
                              </TableRow>
                            ))
                          )}
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

export default MarkDetail;
