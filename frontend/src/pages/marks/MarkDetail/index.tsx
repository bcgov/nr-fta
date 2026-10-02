import {
  ArrowLeft,
  Document,
  Map as MapIcon,
  Notebook,
  RecentlyViewed,
  UserMultiple,
} from '@carbon/icons-react';
import { Tab, TabList, TabPanel, TabPanels, Tabs } from '@carbon/react';
import { useCallback, useState, type FC } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import { statusCodeVariant } from '@/components/StatusTag/statusCodes';
import StatusTag from '@/components/StatusTag/StatusTag';
import { useAuth } from '@/context/auth/useAuth';
import { useApiResource } from '@/hooks/useApiResource';
import PageLayout from '@/pages/PageLayout';
import { canEditMarks } from '@/routes/access';
import { getMarkDetail } from '@/services/mark_detail';

import AmendmentsPanel from './AmendmentsPanel';
import ClientsPanel from './ClientsPanel';
import LandIndexPanel from './LandIndexPanel';
import MarkApplicationPanel from './MarkApplicationPanel';
import NotesPanel from './NotesPanel';
import './MarkDetail.scss';

/**
 * FTA510/511/513 — Private Mark detail. Title and actions, then Carbon
 * contained Tabs over a full-bleed grey pane — the TenureDetail layout:
 * Mark Application (FTA510), Land Index (FTA511), Associated Clients (FTA513),
 * Amendments and Notes (FTA970). The Mark application tab edits in place
 * (MarkApplicationPanel) and Notes takes new notes, both for FTA_ADMIN and the
 * two timber mark roles. Backed by
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

  // Controlled, because saving a note reloads the mark: the loading state
  // unmounts the tabs, and an uncontrolled Tabs would come back on the first.
  const [selectedTab, setSelectedTab] = useState(0);
  // While the Mark application tab is being edited, nothing else on the page
  // can be used — the FSP Information page's one-editor-at-a-time rule.
  const [editing, setEditing] = useState(false);

  // The mark (or an application's certificate) in a large pill beside the words,
  // coloured as its status is everywhere else (grey until the mark has loaded).
  const title = (
    <span className="mark-title">
      {byCertificate ? 'Private Mark Application' : 'Private Mark'}
      <StatusTag
        status={markNumber}
        variant={statusCodeVariant(mark?.markStatusCode) ?? 'default'}
        className="mark-title__pill"
      />
    </span>
  );

  return (
    <PageLayout
      title={title}
      subtitle="Mark application, land index, associated clients and amendment history."
      backLink={
        editing ? (
          // Leaving mid-edit would drop the changes without asking.
          <span className="back-link back-link--disabled" aria-disabled="true">
            <ArrowLeft size={16} /> Back to Private Mark Applications
          </span>
        ) : (
          <Link to="/marks" className="back-link">
            <ArrowLeft size={16} /> Back to Private Mark Applications
          </Link>
        )
      }
    >
      <AsyncBoundary loading={loading} error={error} onRetry={reload} loadingText="Loading mark…">
        {mark && (
          // Carbon's <Tabs> renders no DOM of its own, so the grey full-bleed
          // pane is styled through this wrapper (styles/_detail.scss).
          <div className="fsp-info__page-tabs">
            <Tabs
              selectedIndex={selectedTab}
              onChange={({ selectedIndex }) => setSelectedTab(selectedIndex)}
            >
              <TabList aria-label="Private mark sections" contained>
                <Tab renderIcon={Document}>Mark application</Tab>
                <Tab renderIcon={MapIcon} disabled={editing}>
                  Land index
                </Tab>
                <Tab renderIcon={UserMultiple} disabled={editing}>
                  Associated clients
                </Tab>
                <Tab renderIcon={RecentlyViewed} disabled={editing}>
                  Amendments
                </Tab>
                <Tab renderIcon={Notebook} disabled={editing}>
                  Notes
                </Tab>
              </TabList>
              <TabPanels>
                <TabPanel>
                  <MarkApplicationPanel
                    mark={mark}
                    id={markNumber}
                    byCertificate={byCertificate}
                    canEdit={canEditMarks(user)}
                    onEditingChange={setEditing}
                    onSaved={reload}
                  />
                </TabPanel>

                <TabPanel>
                  <LandIndexPanel
                    id={markNumber}
                    byCertificate={byCertificate}
                    rows={mark.landIndex}
                    canAdd={!!mark.editRules?.landIndex}
                    disabledReason={mark.editRules?.landIndexReason ?? null}
                    onAdded={reload}
                  />
                </TabPanel>

                <TabPanel>
                  <ClientsPanel
                    id={markNumber}
                    byCertificate={byCertificate}
                    rows={mark.clients}
                    canAdd={!!mark.editRules?.clients}
                    disabledReason={mark.editRules?.clientsReason ?? null}
                    onAdded={reload}
                  />
                </TabPanel>

                <TabPanel>
                  <AmendmentsPanel
                    id={markNumber}
                    byCertificate={byCertificate}
                    rows={mark.amendments}
                    canAdd={!!mark.editRules?.amendments}
                    disabledReason={mark.editRules?.amendmentsReason ?? null}
                    onAdded={reload}
                  />
                </TabPanel>
                <TabPanel>
                  <NotesPanel
                    id={markNumber}
                    byCertificate={byCertificate}
                    forestFileId={mark.forestFileId}
                    notes={mark.notes}
                    canAdd={canEditMarks(user)}
                    onAdded={reload}
                  />
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
