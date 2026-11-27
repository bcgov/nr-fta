import {
  ArrowLeft,
  Calendar,
  DocumentTasks,
  Folders,
  Map as MapIcon,
  Notebook,
  TableOfContents,
  UserMultiple,
} from '@carbon/icons-react';
import { InlineNotification, Tab, TabList, TabPanel, TabPanels, Tabs, Tag } from '@carbon/react';
import { Link, useParams } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import RangeTenureExportButton from '@/components/RangeTenureExportButton';
import Tombstone from '@/components/Tombstone';
import { useAuth } from '@/context/auth/useAuth';
import { useApiResource } from '@/hooks/useApiResource';
import { useLazyTabs, type LazyTabs } from '@/hooks/useLazyTabs';
import { useOrgUnitName } from '@/hooks/useOrgUnitName';
import { useNavOrigin } from '@/lib/navOrigin';
import PageLayout from '@/pages/PageLayout';
import AssociatedFilesPanel from '@/pages/tenure/TenureDetail/AssociatedFilesPanel';
import type { SharedTenurePanelProps } from '@/pages/tenure/TenureDetail/panelProps';
import TenureApplicationPanel from '@/pages/tenure/TenureDetail/TenureApplicationPanel';
import TenureNotesPanel from '@/pages/tenure/TenureDetail/TenureNotesPanel';
import { canEdit as canEditTenures } from '@/routes/access';
import { getRangeTenureDetail } from '@/services/range_tenure_detail';
import { exportRangeTenureFile } from '@/services/rangeTenureExport';

import { dash, statusTagType, yesNo } from './format';
import LandBasePanel from './LandBasePanel';
import RangeClientsPanel from './RangeClientsPanel';
import RangeUsagePanel from './RangeUsagePanel';
import TenurePanel from './TenurePanel';

import type { RangePanelProps } from './panelProps';
import type { CarbonIconType } from '@carbon/icons-react';
import type { FC, ReactNode } from 'react';

/** What a tab's render gets: the range panels' props, and the shared tenure panels'. */
interface PanelContext {
  range: RangePanelProps;
  shared: SharedTenurePanelProps;
}

interface RangeTab {
  label: string;
  icon: CarbonIconType;
  render: (ctx: PanelContext) => ReactNode;
}

/**
 * Legacy FtaRangeTabs, in its order and with its labels: 100R, 910, 920R, 950x, 970, 613R, 615R.
 * 910, 950x and 970 are the tenure page's own panels, as legacy reused those screens.
 */
const TABS: RangeTab[] = [
  { label: 'Tenure', icon: TableOfContents, render: ({ range }) => <TenurePanel {...range} /> },
  {
    label: 'Assoc. Files',
    icon: Folders,
    render: ({ shared }) => <AssociatedFilesPanel {...shared} />,
  },
  {
    label: 'Assoc. Client',
    icon: UserMultiple,
    render: ({ range }) => <RangeClientsPanel {...range} />,
  },
  {
    label: 'Tenure Appl.',
    icon: DocumentTasks,
    render: ({ shared }) => <TenureApplicationPanel {...shared} />,
  },
  { label: 'Notes', icon: Notebook, render: ({ shared }) => <TenureNotesPanel {...shared} /> },
  { label: 'Range Usage', icon: Calendar, render: ({ range }) => <RangeUsagePanel {...range} /> },
  { label: 'Assoc. Land Base', icon: MapIcon, render: ({ range }) => <LandBasePanel {...range} /> },
];

/** The range tenure's tabs — each panel mounts when first opened (useLazyTabs). */
const RangeTabs: FC<{ ctx: PanelContext; tabs: LazyTabs }> = ({ ctx, tabs }) => (
  <Tabs selectedIndex={tabs.selected} onChange={tabs.onChange}>
    <TabList aria-label="Range tenure sections" contained>
      {TABS.map((t) => (
        <Tab key={t.label} renderIcon={t.icon}>
          {t.label}
        </Tab>
      ))}
    </TabList>
    <TabPanels>
      {TABS.map((t, i) => (
        <TabPanel key={t.label}>{tabs.isOpened(i) && t.render(ctx)}</TabPanel>
      ))}
    </TabPanels>
  </Tabs>
);

/**
 * FTA100R — Range Tenure detail: the file's summary, then legacy's range tab set (FtaRangeTabs).
 * Backed by {@code GET /api/fta/range-tenures/{agreementId}} (THE.FTA_100RANGE_TENURE with its
 * 613R and 615R tabs); 910, 920R, 950x and 970 load their own lists. The header's `canEdit` says
 * whether the user may edit this file (RangePanelProps.canEdit).
 */
const RangeTenureDetail: FC = () => {
  const orgUnitName = useOrgUnitName();
  const { user } = useAuth();
  const { agreementId = '' } = useParams();
  const back = useNavOrigin() ?? { path: '/search/range-tenure', label: 'Range Tenure Search' };
  const { data, loading, error, reload } = useApiResource(
    () => getRangeTenureDetail(agreementId),
    [agreementId],
  );
  const tabs = useLazyTabs(agreementId);

  const ctx: PanelContext | null = data
    ? {
        range: { tenure: data, canEdit: data.canEdit, onTenureChanged: reload },
        shared: {
          tenure: {
            forestFileId: data.forestFileId,
            awardDate: data.issueDate,
            expiryDate: data.currentExpiryDate,
          },
          // Those tabs' writes are FTA_ADMIN's, as on the tenure page.
          canEdit: canEditTenures(user),
          onTenureChanged: reload,
        },
      }
    : null;

  const mgmtUnit = data
    ? [[data.mgmtUnitType, data.mgmtUnitId].filter(Boolean).join(''), data.mgmtUnitName]
        .filter(Boolean)
        .join(' - ')
    : '';
  const notes = data?.notesLabel?.trim() ?? '';

  return (
    <PageLayout
      title={`Range Tenure ${agreementId}`}
      subtitle="Range tenure, associated files and clients, applications, notes, range usage and land base."
      actions={
        data ? (
          <RangeTenureExportButton
            kind="tertiary"
            size="md"
            onExport={() => exportRangeTenureFile(data.forestFileId)}
          />
        ) : undefined
      }
      backLink={
        <Link to={back.path} className="back-link">
          <ArrowLeft size={16} /> Back to {back.label}
        </Link>
      }
    >
      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Loading range tenure…"
      >
        {data && ctx && (
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

            <Tombstone
              ariaLabel="Range tenure summary"
              items={[
                { label: 'File', value: data.forestFileId },
                { label: 'Type', value: dash(data.fileTypeCode) },
                {
                  label: 'Status',
                  value: (
                    <Tag type={statusTagType(data.fileStatusSt)}>
                      {data.fileStatusDesc ?? dash(data.fileStatusSt)}
                    </Tag>
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

            {/* Carbon's <Tabs> renders no DOM of its own, so the grey full-bleed
                pane is styled through this wrapper (styles/_detail.scss). */}
            <div className="fsp-info__page-tabs">
              <RangeTabs ctx={ctx} tabs={tabs} />
            </div>
          </>
        )}
      </AsyncBoundary>
    </PageLayout>
  );
};

export default RangeTenureDetail;
