import { ArrowLeft, Calendar, Document, ListChecked } from '@carbon/icons-react';
import {
  InlineNotification,
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
import { useApiResource } from '@/hooks/useApiResource';
import { useOrgUnitName } from '@/hooks/useOrgUnitName';
import PageLayout from '@/pages/PageLayout';
import { getRangeTenureDetail } from '@/services/range_tenure_detail';
import type { RangeUsage } from '@/services/range_tenure_detail';

import type { FC } from 'react';

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

const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;
const num = (v: number | null | undefined) => (v === null || v === undefined ? '—' : nf.format(v));
const yesNo = (v: string | null | undefined) => (v === 'Y' ? 'Yes' : v === 'N' ? 'No' : '—');
/** "CODE - Description", or the code alone when the description is unknown. */
const codeDesc = (code: string | null, desc: string | null) =>
  code ? (desc ? `${code} - ${desc}` : code) : '—';

/**
 * The FTA613R usage columns. Grazing (E) files show non-use (billable,
 * non-billable and its reason); hay cutting (H) files show a temporary
 * reduction (amount and reason). The tab shows a grazing non-use reason with
 * its description, the history window its code alone, as legacy did.
 */
const UsageTable: FC<{
  title: string;
  rows: RangeUsage[];
  hay: boolean;
  history?: boolean;
}> = ({ title, rows, hay, history = false }) => {
  const reduction = hay ? 'Reduction' : 'Non-Use';
  return (
    <div className="bordered-table">
      <TableContainer title={title} description={`${rows.length} row(s)`}>
        <Table>
          <TableHead>
            <TableRow>
              <TableHeader>Year</TableHeader>
              <TableHeader>Authorized Use</TableHeader>
              <TableHeader>Increase Amount</TableHeader>
              <TableHeader>Increase Reason</TableHeader>
              <TableHeader>{reduction} Agreement No.</TableHeader>
              {!hay && <TableHeader>Non-Use Billable</TableHeader>}
              <TableHeader>{hay ? 'Reduction Amount' : 'Non-Use Non-Billable'}</TableHeader>
              <TableHeader>{hay ? 'Reduction Reason' : 'Non-Billable Reason'}</TableHeader>
              <TableHeader>Total Annual Use</TableHeader>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((u) => (
              <TableRow key={u.calendarYear ?? undefined}>
                <TableCell>{dash(u.calendarYear)}</TableCell>
                <TableCell>{num(u.authorizedUse)}</TableCell>
                <TableCell>{num(u.tempIncrease)}</TableCell>
                <TableCell>
                  {codeDesc(u.rangeIncreaseReasonCode, u.rangeIncreaseReasonDesc)}
                </TableCell>
                <TableCell>{dash(u.nonUseAgreementNo)}</TableCell>
                {!hay && <TableCell>{num(u.nonUseBillable)}</TableCell>}
                <TableCell>{num(u.nonUseNonbillable)}</TableCell>
                <TableCell>
                  {hay || history
                    ? dash(u.rangeNonUseReasonCode)
                    : codeDesc(u.rangeNonUseReasonCode, u.rangeNonUseReasonDesc)}
                </TableCell>
                <TableCell>{num(u.totalAnnualUse)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </div>
  );
};

/**
 * FTA100Range — Range Tenure detail. The tenure header, its term, and its
 * special conditions, with tabs for range usage (FTA613R, within the term),
 * the associated land base (FTA615R), and the usage history (FTA613R's View
 * History). Backed by the backend {@code GET /api/fta/range-tenures/{agreementId}}
 * endpoint, which ports THE.FTA_100RANGE_TENURE plus those tabs.
 */
const RangeTenureDetail: FC = () => {
  const orgUnitName = useOrgUnitName();
  const { agreementId = '' } = useParams();
  const { data, loading, error, reload } = useApiResource(
    () => getRangeTenureDetail(agreementId),
    [agreementId],
  );

  const hay = (data?.fileTypeCode ?? '').includes('H');
  const replaceable = data?.licenceReplaceableInd === 'Y';
  const mgmtUnit = data
    ? [[data.mgmtUnitType, data.mgmtUnitId].filter(Boolean).join(''), data.mgmtUnitName]
        .filter(Boolean)
        .join(' - ')
    : '';
  const notes = data?.notesLabel?.trim() ?? '';

  return (
    <PageLayout
      title={`Range Tenure ${agreementId}`}
      subtitle="Range tenure, range usage, land base, and usage history."
    >
      <Link to="/search/range-tenure" className="back-link">
        <ArrowLeft size={16} /> Back to Range Tenure Search
      </Link>

      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Loading range tenure…"
      >
        {data && (
          <>
            {data.termWarning && (
              <InlineNotification
                kind="warning"
                lowContrast
                hideCloseButton
                title="Warning:"
                subtitle={data.termWarning}
              />
            )}

            <SectionTile title="Range tenure summary" icon={Document}>
              <Tombstone
                ariaLabel="Range tenure summary"
                items={[
                  { label: 'File', value: data.forestFileId },
                  { label: 'Type', value: dash(data.fileTypeCode) },
                  {
                    label: 'Status',
                    value: (
                      <Tag type={statusTagType(data.fileStatusSt)}>{dash(data.fileStatusSt)}</Tag>
                    ),
                  },
                  { label: 'Status Date', value: dash(data.fileStatusDate) },
                  { label: 'Admin Org', value: orgUnitName(data.adminOrgUnitNo) },
                  { label: 'District', value: orgUnitName(data.forestDistrictNo) },
                  { label: 'Client', value: dash(data.licensee) },
                  { label: 'Sec. Licensee', value: yesNo(data.secLicenseeInd) },
                  { label: 'Mgmt Unit', value: dash(mgmtUnit) },
                  { label: 'Zone', value: dash(data.districtAdminZone) },
                  { label: 'File Name', value: dash(data.fileName) },
                  ...(notes ? [{ label: 'Notes', value: <Tag type="red">{notes}</Tag> }] : []),
                ]}
              />
            </SectionTile>

            {replaceable && (
              <SectionTile title="Historical" icon={Calendar}>
                <DefinitionGrid
                  items={[{ label: 'Original Start Date', value: dash(data.originalIssueDate) }]}
                />
              </SectionTile>
            )}

            <SectionTile title="Current" icon={Calendar}>
              <DefinitionGrid
                items={[
                  { label: 'Start Date', value: dash(data.issueDate) },
                  {
                    label: 'Term',
                    value: data.tenureTermYears === null ? '—' : `${data.tenureTermYears} yr`,
                  },
                  { label: 'Initial Expiry Date', value: dash(data.expiryDate) },
                  { label: 'Replacement Count', value: dash(data.replacementCount) },
                ]}
              />
            </SectionTile>

            {replaceable && (
              <SectionTile title="Replacement" icon={Calendar}>
                <DefinitionGrid
                  items={[
                    {
                      label: 'Replacement Term',
                      value:
                        data.replacementTermYears === null
                          ? '—'
                          : `${data.replacementTermYears} yr`,
                    },
                    ...(data.replacementLabel
                      ? [{ label: 'Replacement', value: data.replacementLabel, wide: true }]
                      : []),
                  ]}
                />
              </SectionTile>
            )}

            <SectionTile title="Special Conditions" icon={ListChecked}>
              <div className="bordered-table">
                <TableContainer description={`${data.specialConditions.length} row(s)`}>
                  <Table>
                    <TableHead>
                      <TableRow>
                        <TableHeader>Special Condition Code</TableHeader>
                        <TableHeader>Condition Title</TableHeader>
                        <TableHeader>Condition Text</TableHeader>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {data.specialConditions.map((c, i) => (
                        <TableRow key={c.specialConditionSkey ?? i}>
                          <TableCell>{dash(c.rangeSpecialConditionCode)}</TableCell>
                          <TableCell>{dash(c.conditionTitle)}</TableCell>
                          <TableCell>{dash(c.conditionDescription)}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </div>
            </SectionTile>

            <Tabs>
              <TabList aria-label="Range tenure sections" contained>
                <Tab>Range Usage</Tab>
                <Tab>Land Base</Tab>
                <Tab>Usage History</Tab>
              </TabList>
              <TabPanels>
                <TabPanel>
                  <UsageTable
                    title={hay ? 'Hay Cutting' : 'Grazing'}
                    rows={data.rangeUsage}
                    hay={hay}
                  />
                </TabPanel>

                <TabPanel>
                  <div className="bordered-table">
                    <TableContainer
                      title="Associated Land Base"
                      description={`${data.landBase.length} row(s)`}
                    >
                      <Table>
                        <TableHead>
                          <TableRow>
                            <TableHeader>Legal Description</TableHeader>
                            <TableHeader>PID</TableHeader>
                            <TableHeader>ID #</TableHeader>
                            <TableHeader>Type</TableHeader>
                            <TableHeader>Purpose</TableHeader>
                            <TableHeader>Fenced Pasture (ha)</TableHeader>
                            <TableHeader>Unfenced Pasture (ha)</TableHeader>
                            <TableHeader>Hay Field (ha)</TableHeader>
                            <TableHeader>Comment</TableHeader>
                            <TableHeader>Lease Start Date</TableHeader>
                            <TableHeader>Lease End Date</TableHeader>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {data.landBase.map((p, i) => (
                            <TableRow key={p.landBaseSkey ?? i}>
                              <TableCell>{dash(p.description)}</TableCell>
                              <TableCell>{dash(p.rangeLandBasePid)}</TableCell>
                              <TableCell>{dash(p.rangeLandBaseId)}</TableCell>
                              <TableCell>
                                {codeDesc(
                                  p.rangeLandOwnershipTypeCode,
                                  p.rangeLandOwnershipTypeDesc,
                                )}
                              </TableCell>
                              <TableCell>
                                {codeDesc(p.rangeLandPurposeCode, p.rangeLandPurposeDesc)}
                              </TableCell>
                              <TableCell>{num(p.fencedPasture)}</TableCell>
                              <TableCell>{num(p.unfencedPasture)}</TableCell>
                              <TableCell>{num(p.forageProduction)}</TableCell>
                              <TableCell>{dash(p.landBaseComment)}</TableCell>
                              <TableCell>{dash(p.leaseStartDate)}</TableCell>
                              <TableCell>{dash(p.leaseEndDate)}</TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  </div>
                </TabPanel>

                <TabPanel>
                  <UsageTable title="Usage History" rows={data.usageHistory} hay={hay} history />
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
