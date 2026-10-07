import {
  ArrowLeft,
  Copy,
  DocumentTasks,
  Grid,
  RecentlyViewed,
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
import Tombstone from '@/components/Tombstone';
import { useAuth } from '@/context/auth/useAuth';
import { useApiResource } from '@/hooks/useApiResource';
import { useLazyTabs, type LazyTabs } from '@/hooks/useLazyTabs';
import { useNavOrigin } from '@/lib/navOrigin';
import PageLayout from '@/pages/PageLayout';
import { canEdit } from '@/routes/access';
import { markDetailPath } from '@/services/mark_detail';
import { getTenureDetail } from '@/services/tenure_detail';
import { formatDate } from '@/utils/formatDate';

import AacPanel from './AacPanel';
import AssociatedClientsPanel from './AssociatedClientsPanel';
import AssociatedFilesPanel from './AssociatedFilesPanel';
import CopyRotationPanel from './CopyRotationPanel';
import CpCbAmendmentsPanel from './CpCbAmendmentsPanel';
import CutBlocksPanel from './CutBlocksPanel';
import CuttingPermitsPanel from './CuttingPermitsPanel';
import DetailsPanel from './DetailsPanel';
import SaleInfoPanel from './SaleInfoPanel';
import TenureApplicationPanel from './TenureApplicationPanel';
import TenureNotesPanel from './TenureNotesPanel';
import TlBlocksPanel from './TlBlocksPanel';

import type { TenurePanelProps } from './panelProps';
import type { CarbonIconType } from '@carbon/icons-react';
import type { FC, ReactNode } from 'react';

interface TenureTab {
  label: string;
  icon: CarbonIconType;
  render: (props: TenurePanelProps, canEditCp: boolean) => ReactNode;
  /** Only for these file types; every tenure when absent. */
  fileTypes?: readonly string[];
}

/**
 * Timber licence file types: A06 Timber Licence and A30 Consolidated Timber
 * Licence — the pair legacy's TIMBER_LICENCE_SVW selects. TL blocks
 * (FTA980) are blocks within a timber licence's area.
 */
const TIMBER_LICENCE_TYPES = ['A06', 'A30'];

const TABS: TenureTab[] = [
  { label: 'Details', icon: TableOfContents, render: (p) => <DetailsPanel {...p} /> },
  {
    label: 'Cutting permit / mark',
    icon: Stamp,
    render: (p, canEditCp) => (
      <CuttingPermitsPanel
        forestFileId={p.tenure.forestFileId}
        fileTypeCode={p.tenure.fileTypeCode}
        orgUnitCode={p.tenure.orgUnitCode}
        canEdit={canEditCp}
      />
    ),
  },
  { label: 'Cut block', icon: Tree, render: (p) => <CutBlocksPanel {...p} /> },
  { label: 'Associated files', icon: Folders, render: (p) => <AssociatedFilesPanel {...p} /> },
  {
    label: 'Associated clients',
    icon: UserMultiple,
    render: (p) => <AssociatedClientsPanel {...p} />,
  },
  { label: 'AAC', icon: ChartColumn, render: (p) => <AacPanel {...p} /> },
  { label: 'Sale info', icon: Currency, render: (p) => <SaleInfoPanel {...p} /> },
  {
    label: 'Tenure application',
    icon: DocumentTasks,
    render: (p) => <TenureApplicationPanel {...p} />,
  },
  { label: 'Notes', icon: Notebook, render: (p) => <TenureNotesPanel {...p} /> },
  { label: 'Copy rotation', icon: Copy, render: (p) => <CopyRotationPanel {...p} /> },
  {
    label: 'TL blocks',
    icon: Grid,
    render: (p) => <TlBlocksPanel {...p} />,
    fileTypes: TIMBER_LICENCE_TYPES,
  },
  {
    label: 'CP/CB amendments',
    icon: RecentlyViewed,
    render: (p) => <CpCbAmendmentsPanel {...p} />,
  },
];

/** The tenure's tabs — each panel fetches its own data when first opened (useLazyTabs). */
const TenureTabs: FC<{
  panelProps: TenurePanelProps;
  canEditCp: boolean;
  tabs: LazyTabs;
}> = ({ panelProps, canEditCp, tabs }) => {
  // Fixed for the tenure (its file type isn't editable), so tab indices are stable.
  const shown = TABS.filter(
    (t) => !t.fileTypes || t.fileTypes.includes(panelProps.tenure.fileTypeCode ?? ''),
  );
  return (
    <Tabs selectedIndex={tabs.selected} onChange={tabs.onChange}>
      <TabList aria-label="Tenure sections" contained>
        {shown.map((t) => (
          <Tab key={t.label} renderIcon={t.icon}>
            {t.label}
          </Tab>
        ))}
      </TabList>
      <TabPanels>
        {shown.map((t, i) => (
          <TabPanel key={t.label}>{tabs.isOpened(i) && t.render(panelProps, canEditCp)}</TabPanel>
        ))}
      </TabPanels>
    </Tabs>
  );
};

/**
 * FTA100 — Tenure detail, laid out as the private-mark detail: a back link
 * above the title, a status-coloured tombstone of the file's key facts, then Carbon
 * contained Tabs over a full-bleed grey pane — Details first (summary and term
 * side by side), then the tenure's sub-entities. Backed by the backend
 * {@code GET /api/fta/tenures/{id}} endpoint, which ports THE.FTA_100_TENURE
 * (+ FTA_930_AAC, FTA_940_SALE_INFO).
 */
const TenureDetail: FC = () => {
  const { fileId = '' } = useParams();
  const { user } = useAuth();
  // Opened from Timber Mark Search: go back there.
  const back = useNavOrigin() ?? { path: '/search/tenure', label: 'Tenure Search' };
  const {
    data: tenure,
    loading,
    error,
    reload,
  } = useApiResource(() => getTenureDetail(fileId), [fileId]);

  const tabs = useLazyTabs(fileId);

  // What every tab panel gets (panelProps.ts).
  const panelProps: TenurePanelProps | null = tenure
    ? { tenure, canEdit: canEdit(user), onTenureChanged: reload }
    : null;

  const title = 'Tenure';

  return (
    <PageLayout
      title={title}
      subtitle="Tenure record: cutting permits, cut blocks, associated files and clients, AAC and sale details."
      backLink={
        <Link to={back.path} className="back-link">
          <ArrowLeft size={16} /> Back to {back.label}
        </Link>
      }
    >
      <AsyncBoundary loading={loading} error={error} onRetry={reload} loadingText="Loading tenure…">
        {tenure && panelProps && (
          <>
            <Tombstone
              ariaLabel="Tenure summary"
              // The left bar takes the status's colour, as on the private mark.
              className={`bc-status-accent--${statusCodeVariant(tenure.fileStatusCode) ?? 'default'}`}
              items={[
                { label: 'File ID', value: tenure.forestFileId },
                // A private mark's forest file links back to the mark.
                ...(tenure.privateMark || tenure.privateMarkCertificate
                  ? [
                      {
                        label: 'Private Mark',
                        value: (
                          <Link
                            to={
                              markDetailPath(tenure.privateMark, tenure.privateMarkCertificate) ??
                              '/marks'
                            }
                          >
                            {tenure.privateMark ?? tenure.privateMarkCertificate}
                          </Link>
                        ),
                      },
                    ]
                  : []),
                { label: 'Type', value: tenure.fileTypeDesc || tenure.fileTypeCode || '—' },
                {
                  label: 'Admin Organization',
                  value: tenure.orgUnitDesc || tenure.orgUnitCode || '—',
                },
                {
                  label: 'Status',
                  value: tenure.fileStatusCode
                    ? [tenure.fileStatusCode, tenure.fileStatusDesc].filter(Boolean).join(' - ')
                    : '—',
                },
                { label: 'As of', value: formatDate(tenure.fileStatusDate) || '—' },
                { label: 'Effective Date', value: formatDate(tenure.awardDate) || '—' },
                {
                  // Legacy's tombstone: the current expiry once extended, else the initial one.
                  label: 'Expiry Date',
                  value: formatDate(tenure.expiryDate ?? tenure.initialExpiryDate) || '—',
                },
                { label: 'Licensee', value: tenure.licensee || '—' },
              ]}
            />
            {/* Carbon's <Tabs> renders no DOM of its own, so the grey full-bleed
                pane is styled through this wrapper (styles/_detail.scss). */}
            <div className="fsp-info__page-tabs">
              <TenureTabs panelProps={panelProps} canEditCp={canEdit(user)} tabs={tabs} />
            </div>
          </>
        )}
      </AsyncBoundary>
    </PageLayout>
  );
};

export default TenureDetail;
