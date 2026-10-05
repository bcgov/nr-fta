import {
  ArrowLeft,
  Campsite,
  Copy,
  DocumentTasks,
  Grid,
  RecentlyViewed,
  Sprout,
  Wheat,
  ChartColumn,
  Currency,
  Folders,
  Notebook,
  Stamp,
  TableOfContents,
  Tree,
  UserMultiple,
} from '@carbon/icons-react';
import { Tab, TabList, TabPanel, TabPanels, Tabs } from '@carbon/react';
import { Link, useParams } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import { statusCodeVariant } from '@/components/StatusTag/statusCodes';
import StatusTag from '@/components/StatusTag/StatusTag';
import { useAuth } from '@/context/auth/useAuth';
import { useApiResource } from '@/hooks/useApiResource';
import PageLayout from '@/pages/PageLayout';
import { canEdit } from '@/routes/access';
import { getTenureDetail } from '@/services/tenure_detail';

import AacPanel from './AacPanel';
import AssociatedClientsPanel from './AssociatedClientsPanel';
import AssociatedFilesPanel from './AssociatedFilesPanel';
import CopyRotationPanel from './CopyRotationPanel';
import CpCbAmendmentsPanel from './CpCbAmendmentsPanel';
import CutBlocksPanel from './CutBlocksPanel';
import CuttingPermitsPanel from './CuttingPermitsPanel';
import DetailsPanel from './DetailsPanel';
import GrazingRotationPanel from './GrazingRotationPanel';
import HayCuttingRotationPanel from './HayCuttingRotationPanel';
import RecProjectPanel from './RecProjectPanel';
import SaleInfoPanel from './SaleInfoPanel';
import TenureApplicationPanel from './TenureApplicationPanel';
import TenureNotesPanel from './TenureNotesPanel';
import TlBlocksPanel from './TlBlocksPanel';

import type { TenurePanelProps } from './panelProps';
import type { FC } from 'react';

/**
 * FTA100 — Tenure detail, laid out as the private-mark detail: a back link
 * above the title, the file id in a status-coloured pill, then Carbon
 * contained Tabs over a full-bleed grey pane — Details first (summary and term
 * side by side), then the tenure's sub-entities. Backed by the backend
 * {@code GET /api/fta/tenures/{id}} endpoint, which ports THE.FTA_100_TENURE
 * (+ FTA_930_AAC, FTA_940_SALE_INFO).
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

  const statusVariant = statusCodeVariant(tenure?.fileStatusCode);

  // What every tab panel gets (panelProps.ts).
  const panelProps: TenurePanelProps | null = tenure
    ? { tenure, canEdit: canEdit(user), onTenureChanged: reload }
    : null;

  // The file id in a large pill beside the words, coloured as its status is in
  // Tenure Search (grey until the tenure has loaded).
  const title = (
    <span className="detail-title">
      Tenure
      <StatusTag
        status={fileId}
        variant={statusVariant ?? 'default'}
        className="detail-title__pill"
      />
    </span>
  );

  return (
    <PageLayout
      title={title}
      subtitle="Tenure record: cutting permits, cut blocks, associated files and clients, AAC and sale details."
      backLink={
        <Link to="/search/tenure" className="back-link">
          <ArrowLeft size={16} /> Back to Tenure Search
        </Link>
      }
    >
      <AsyncBoundary loading={loading} error={error} onRetry={reload} loadingText="Loading tenure…">
        {tenure && panelProps && (
          // Carbon's <Tabs> renders no DOM of its own, so the grey full-bleed
          // pane is styled through this wrapper (styles/_detail.scss).
          <div className="fsp-info__page-tabs">
            <Tabs>
              <TabList aria-label="Tenure sections" contained>
                <Tab renderIcon={TableOfContents}>Details</Tab>
                <Tab renderIcon={Stamp}>Cutting permit / mark</Tab>
                <Tab renderIcon={Tree}>Cut block</Tab>
                <Tab renderIcon={Folders}>Associated files</Tab>
                <Tab renderIcon={UserMultiple}>Associated clients</Tab>
                <Tab renderIcon={ChartColumn}>AAC</Tab>
                <Tab renderIcon={Currency}>Sale info</Tab>
                <Tab renderIcon={DocumentTasks}>Tenure application</Tab>
                <Tab renderIcon={Notebook}>Notes</Tab>
                <Tab renderIcon={Sprout}>Grazing rotation</Tab>
                <Tab renderIcon={Wheat}>Hay cutting rotation</Tab>
                <Tab renderIcon={Copy}>Copy rotation</Tab>
                <Tab renderIcon={Grid}>TL blocks</Tab>
                <Tab renderIcon={RecentlyViewed}>CP/CB amendments</Tab>
                <Tab renderIcon={Campsite}>Rec project</Tab>
              </TabList>
              <TabPanels>
                <TabPanel>
                  <DetailsPanel {...panelProps} />
                </TabPanel>

                <TabPanel>
                  <CuttingPermitsPanel
                    forestFileId={tenure.forestFileId}
                    fileTypeCode={tenure.fileTypeCode}
                    orgUnitCode={tenure.orgUnitCode}
                    canEdit={canEdit(user)}
                  />
                </TabPanel>

                <TabPanel>
                  <CutBlocksPanel {...panelProps} />
                </TabPanel>

                <TabPanel>
                  <AssociatedFilesPanel {...panelProps} />
                </TabPanel>

                <TabPanel>
                  <AssociatedClientsPanel {...panelProps} />
                </TabPanel>

                <TabPanel>
                  <AacPanel {...panelProps} />
                </TabPanel>

                <TabPanel>
                  <SaleInfoPanel {...panelProps} />
                </TabPanel>

                <TabPanel>
                  <TenureApplicationPanel {...panelProps} />
                </TabPanel>

                <TabPanel>
                  <TenureNotesPanel {...panelProps} />
                </TabPanel>

                <TabPanel>
                  <GrazingRotationPanel {...panelProps} />
                </TabPanel>

                <TabPanel>
                  <HayCuttingRotationPanel {...panelProps} />
                </TabPanel>

                <TabPanel>
                  <CopyRotationPanel {...panelProps} />
                </TabPanel>

                <TabPanel>
                  <TlBlocksPanel {...panelProps} />
                </TabPanel>

                <TabPanel>
                  <CpCbAmendmentsPanel {...panelProps} />
                </TabPanel>

                <TabPanel>
                  <RecProjectPanel {...panelProps} />
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
