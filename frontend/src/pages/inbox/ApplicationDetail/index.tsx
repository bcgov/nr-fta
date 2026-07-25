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
import { ArrowLeft, Chat, Pause, Checkmark, Close, UserFollow, Map, Upload } from '@carbon/icons-react';
import { useState, type FC } from 'react';
import { Link, useParams } from 'react-router-dom';
import AsyncBoundary from '@/components/AsyncBoundary';
import DefinitionGrid from '@/components/DefinitionGrid';
import Tombstone from '@/components/Tombstone';
import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import { canEdit } from '@/routes/access';
import { useApiResource } from '@/hooks/useApiResource';
import PageLayout from '@/pages/PageLayout';
import { adjudicateApplication, getApplicationDetail } from '@/services/application_detail';
import './ApplicationDetail.scss';

/**
 * FTA952 — Tenure Application detail. Tombstone + tabs (submission details,
 * processing dates, Exhibit A, history) with the adjudication actions
 * (assign, request clarification, hold, clear, reject) gated to FTA_ADMIN.
 * Detail data is read from the backend {@code GET /api/fta/applications/{esfId}}
 * endpoint (which ports THE.FTA_952X_TAMF_DET). The adjudication actions remain
 * placeholder-only until the backend workflow exists — they raise a
 * notification rather than mutating state.
 */
const ApplicationDetail: FC = () => {
  const { esfId = '' } = useParams();
  const { user } = useAuth();
  const notify = useNotification();
  const { data: app, loading, error, reload } = useApiResource(
    () => getApplicationDetail(esfId),
    [esfId],
  );
  const [saving, setSaving] = useState(false);

  // Wire each adjudication button to POST /api/fta/applications/{esfId}/actions
  // (ports THE.FTA_302_ADJUDCOMMENT). `action` selects the mainline branch and
  // `label` is recorded as the adjudication comment / decision note.
  const act = async (label: string, action: string) => {
    const id = app?.tenureAppId ?? esfId;
    setSaving(true);
    try {
      await adjudicateApplication(id, {
        action,
        adjudicationComment: label,
        revisionCount: null,
      });
      notify.display({
        kind: 'success',
        title: `${label} recorded`,
        subtitle: `${label} for ${id}.`,
        timeout: 5000,
      });
      reload();
    } catch (err) {
      notify.display({
        kind: 'error',
        title: `Could not record ${label.toLowerCase()}`,
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 6000,
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <PageLayout title={`Application ${esfId}`}>
      <Link to="/inbox" className="fta-back">
        <ArrowLeft size={16} /> Back to Inbox
      </Link>

      <AsyncBoundary loading={loading} error={error} onRetry={reload} loadingText="Loading application…">
        {app && (
          <>
            <Tombstone
              ariaLabel="Application summary"
              items={[
                { label: 'ESF ID', value: app.tenureAppId },
                {
                  label: 'Forest File',
                  value: app.forestFileId ? (
                    <Link to={`/tenures/${app.forestFileId}`}>{app.forestFileId}</Link>
                  ) : (
                    '—'
                  ),
                },
                { label: 'Type', value: app.tenureAppType ?? '—' },
                {
                  label: 'Status',
                  value: app.status ? <Tag type="blue">{app.status}</Tag> : '—',
                },
                { label: 'Client', value: app.licencee ?? '—' },
                { label: 'Org Unit', value: app.adminOrg ?? '—' },
                { label: 'Awarded', value: app.awardDate ?? '—' },
                { label: 'File Type', value: app.fileTypeDesc ?? app.fileTypeCode ?? '—' },
              ]}
            />

            {canEdit(user) && (
              <div className="app-detail__actions">
                <Button size="sm" renderIcon={UserFollow} disabled={saving} onClick={() => act('Assignment', 'SAVE')}>Assign to me</Button>
                <Button size="sm" kind="tertiary" renderIcon={Chat} disabled={saving} onClick={() => act('Clarification request', 'SAVE')}>Request clarification</Button>
                <Button size="sm" kind="tertiary" renderIcon={Pause} disabled={saving} onClick={() => act('Hold', 'SAVE')}>Place on hold</Button>
                <Button size="sm" kind="tertiary" renderIcon={Checkmark} disabled={saving} onClick={() => act('Clearance', 'ADJUDICATION')}>Clear</Button>
                <Button size="sm" kind="danger--tertiary" renderIcon={Close} disabled={saving} onClick={() => act('Rejection', 'ADJUDICATION')}>Reject</Button>
              </div>
            )}

            <Tabs>
              <TabList aria-label="Application sections" contained>
                <Tab>Submission</Tab>
                <Tab>Processing</Tab>
                <Tab>Exhibit A</Tab>
                <Tab>History</Tab>
              </TabList>
              <TabPanels>
                <TabPanel>
                  <DefinitionGrid
                    items={[
                      { label: 'Application Type', value: app.tenureAppType ?? '—' },
                      { label: 'Harvest Type', value: app.harvestTypeCode ?? '—' },
                      {
                        label: 'Client',
                        value: app.licencee
                          ? `${app.licencee}${app.clientNumber ? ` (${app.clientNumber})` : ''}`
                          : '—',
                      },
                      { label: 'Purpose', value: app.purposeDesc ?? '—' },
                      { label: 'Description', value: app.description ?? '—' },
                    ]}
                  />
                </TabPanel>

                <TabPanel>
                  <DefinitionGrid
                    items={[
                      { label: 'Status Date', value: app.statusDate ?? '—' },
                      { label: 'Award Date', value: app.awardDate ?? '—' },
                      { label: 'Expiry Date', value: app.expiryDate ?? '—' },
                    ]}
                  />
                </TabPanel>

                <TabPanel>
                  <DefinitionGrid
                    items={[
                      { label: 'File Type', value: app.fileTypeDesc ?? app.fileTypeCode ?? '—' },
                      { label: 'Harvest Type', value: app.harvestTypeCode ?? '—' },
                    ]}
                  />
                  <div className="app-detail__actions">
                    <Button size="sm" as={Link} to={`/exhibit-a/${app.tenureAppId}`} renderIcon={Map}>
                      View tenure map
                    </Button>
                    {canEdit(user) && (
                      <Button size="sm" kind="tertiary" as={Link} to={`/exhibit-a/${app.tenureAppId}/upload`} renderIcon={Upload}>
                        Upload Exhibit A
                      </Button>
                    )}
                  </div>
                </TabPanel>

                <TabPanel>
                  <TableContainer title="Application history">
                    <Table>
                      <TableHead>
                        <TableRow>
                          <TableHeader>Date</TableHeader>
                          <TableHeader>Actor</TableHeader>
                          <TableHeader>Action</TableHeader>
                          <TableHeader>Note</TableHeader>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        <TableRow>
                          <TableCell colSpan={4}>No history available.</TableCell>
                        </TableRow>
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

export default ApplicationDetail;
