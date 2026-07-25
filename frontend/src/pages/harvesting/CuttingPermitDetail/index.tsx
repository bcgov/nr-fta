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
  UnorderedList,
  ListItem,
} from '@carbon/react';
import { ArrowLeft, Edit, Pause, Tag as TagIcon } from '@carbon/icons-react';
import { useCallback, type FC } from 'react';
import { Link, useParams } from 'react-router-dom';
import AsyncBoundary from '@/components/AsyncBoundary';
import DefinitionGrid from '@/components/DefinitionGrid';
import Tombstone from '@/components/Tombstone';
import { useAuth } from '@/context/auth/useAuth';
import { canEdit } from '@/routes/access';
import PageLayout from '@/pages/PageLayout';
import { useApiResource } from '@/hooks/useApiResource';
import { getCuttingPermitDetail } from '@/services/cutting_permit_detail';
import './CuttingPermitDetail.scss';

const nf = new Intl.NumberFormat('en-CA');

/**
 * FTA902 — Cutting Permit / Timber Mark details. Tombstone + tabs for permit
 * details (legal description, issuance conditions), its cut blocks, and
 * harvest history. Reached from the Harvesting Authority Search or the tenure
 * CP/Mark tab. Backed by {@code GET /api/fta/cutting-permits/{cpId}} (which
 * ports THE.FTA_902_CP_DETAIL).
 */
const CuttingPermitDetail: FC = () => {
  const { cpId = '' } = useParams();
  const { user } = useAuth();

  const fetcher = useCallback(() => getCuttingPermitDetail(cpId), [cpId]);
  const { data: cp, loading, error, reload } = useApiResource(fetcher, [cpId]);

  const issuanceConditions = cp
    ? [
        cp.markingMethodCode ? `Marking method: ${cp.markingMethodCode}` : null,
        cp.markingInstrumentCode ? `Marking instrument: ${cp.markingInstrumentCode}` : null,
        cp.quotaTypeCode ? `Quota type: ${cp.quotaTypeCode}` : null,
        cp.salvageTypeCode ? `Salvage type: ${cp.salvageTypeCode}` : null,
      ].filter((c): c is string => Boolean(c))
    : [];

  const area = cp?.harvestArea != null ? `${nf.format(cp.harvestArea)} ha` : '—';

  return (
    <PageLayout title={cp ? `Cutting Permit ${cp.cuttingPermitId ?? cpId}` : 'Cutting Permit'}>
      <Link to="/search/harvesting-authority" className="fta-back">
        <ArrowLeft size={16} /> Back to Harvesting Authority Search
      </Link>

      <AsyncBoundary loading={loading} error={error} onRetry={reload} loadingText="Loading cutting permit…">
        {cp && (
          <>
            <Tombstone
              ariaLabel="Cutting permit summary"
              items={[
                { label: 'Cutting Permit', value: cp.cuttingPermitId ?? '—' },
                { label: 'Timber Mark', value: cp.timberMark ?? '—' },
                {
                  label: 'Forest File',
                  value: cp.forestFileId ? (
                    <Link to={`/tenures/${cp.forestFileId}`}>{cp.forestFileId}</Link>
                  ) : (
                    '—'
                  ),
                },
                {
                  label: 'Status',
                  value: <Tag type="green">{cp.statusDesc ?? cp.statusCode ?? '—'}</Tag>,
                },
                { label: 'Org Unit', value: cp.adminOrgCode ?? '—' },
                { label: 'Area', value: area },
                { label: 'Issued', value: cp.issueDate ?? '—' },
                { label: 'Expires', value: cp.expiryDate ?? '—' },
              ]}
              action={
                canEdit(user) ? (
                  <Button size="sm" kind="tertiary" renderIcon={Edit}>Edit permit</Button>
                ) : undefined
              }
            />

            {canEdit(user) && (
              <div className="cp-detail__actions">
                <Button size="sm" kind="tertiary" renderIcon={TagIcon} as={Link} to={`/harvesting-authority/${cp.cuttingPermitId ?? cpId}/assign-marks`}>
                  Assign marks to blocks
                </Button>
                <Button size="sm" kind="danger--tertiary" renderIcon={Pause} as={Link} to={`/harvesting-authority/${cp.cuttingPermitId ?? cpId}/suspend-blocks`}>
                  Suspend blocks
                </Button>
              </div>
            )}

            <Tabs>
              <TabList aria-label="Cutting permit sections" contained>
                <Tab>Details</Tab>
                <Tab>Cut Blocks</Tab>
                <Tab>Harvest History</Tab>
              </TabList>
              <TabPanels>
                <TabPanel>
                  <DefinitionGrid
                    items={[
                      { label: 'Legal Description', value: cp.location ?? '—' },
                      { label: 'Timber Mark', value: cp.timberMark ?? '—' },
                      { label: 'File Type', value: cp.fileTypeDescription ?? cp.fileTypeCode ?? '—' },
                      { label: 'Licensee', value: cp.licensee ?? '—' },
                      { label: 'Forest District', value: cp.forestDistrict ?? '—' },
                      { label: 'Authorized Area', value: area },
                    ]}
                  />
                  <h3 style={{ margin: '1rem 0 0.5rem', fontSize: '1rem' }}>Issuance Conditions</h3>
                  {issuanceConditions.length ? (
                    <UnorderedList>
                      {issuanceConditions.map((c, i) => (
                        <ListItem key={i}>{c}</ListItem>
                      ))}
                    </UnorderedList>
                  ) : (
                    <p>No issuance conditions recorded.</p>
                  )}
                </TabPanel>

                <TabPanel>
                  <TableContainer title="Cut Blocks" description="Cut blocks are managed on the Cut Block search screen">
                    <Table>
                      <TableHead>
                        <TableRow>
                          <TableHeader>Block</TableHeader>
                          <TableHeader>Status</TableHeader>
                          <TableHeader>Area (ha)</TableHeader>
                          <TableHeader>Planned Volume (m³)</TableHeader>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        <TableRow>
                          <TableCell colSpan={4}>
                            <Link to={`/search/cut-block?cpId=${encodeURIComponent(cp.cuttingPermitId ?? cpId)}`}>
                              View cut blocks for this permit
                            </Link>
                          </TableCell>
                        </TableRow>
                      </TableBody>
                    </Table>
                  </TableContainer>
                </TabPanel>

                <TabPanel>
                  <DefinitionGrid
                    items={[
                      { label: 'Status', value: cp.statusDesc ?? cp.statusCode ?? '—' },
                      { label: 'Status Date', value: cp.statusDate ?? '—' },
                      {
                        label: 'Tenure Term',
                        value:
                          cp.tenureTermYears != null || cp.tenureTermMonths != null
                            ? `${cp.tenureTermYears ?? 0} yr ${cp.tenureTermMonths ?? 0} mo`
                            : '—',
                      },
                      { label: 'Extend Date', value: cp.extendDate ?? '—' },
                      { label: 'Extend Count', value: cp.extendCount != null ? String(cp.extendCount) : '—' },
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

export default CuttingPermitDetail;
