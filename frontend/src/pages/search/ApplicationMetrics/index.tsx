import { Button, Column, Grid, Select, SelectItem, TextInput } from '@carbon/react';
import { Search as SearchIcon, Reset, Download } from '@carbon/icons-react';
import { useState, type FC, type FormEvent } from 'react';
import SearchResultsTable, { type ColumnDef } from '@/components/SearchResultsTable';
import { useNotification } from '@/context/notification/useNotification';
import PageLayout from '@/pages/PageLayout';
import { MOCK_APPLICATIONS, findApplication } from '@/mocks/applications';
import { ORG_UNITS } from '@/mocks/reference';

interface MetricRow {
  id: string;
  esfId: string;
  fileId: string;
  applicationType: string;
  orgUnit: string;
  status: string;
  processingDays: number;
  holdDays: number;
  netDays: number;
}

const HEADERS: ColumnDef[] = [
  { key: 'esfId', header: 'ESF ID' },
  { key: 'fileId', header: 'File ID' },
  { key: 'applicationType', header: 'Type' },
  { key: 'orgUnit', header: 'Org Unit' },
  { key: 'status', header: 'Status' },
  { key: 'processingDays', header: 'Processing Days' },
  { key: 'holdDays', header: 'Hold Days' },
  { key: 'netDays', header: 'Net Days' },
];

function buildMetrics(orgUnit?: string): MetricRow[] {
  return MOCK_APPLICATIONS.filter((a) => !orgUnit || a.orgUnit === orgUnit).map((a) => {
    const d = findApplication(a.esfId)!;
    return {
      id: a.esfId,
      esfId: a.esfId,
      fileId: a.fileId,
      applicationType: a.applicationType,
      orgUnit: a.orgUnit,
      status: a.status,
      processingDays: d.processingDays,
      holdDays: d.holdDays,
      netDays: d.processingDays - d.holdDays,
    };
  });
}

/**
 * FTA008 — Application Metrics Export. Search application processing metrics by
 * org unit / date range and export. Export is mock-only (raises a notification).
 */
const ApplicationMetrics: FC = () => {
  const notify = useNotification();
  const [orgUnit, setOrgUnit] = useState('');
  const [rows, setRows] = useState<MetricRow[] | null>(null);

  const onSearch = (e: FormEvent) => {
    e.preventDefault();
    setRows(buildMetrics(orgUnit || undefined));
  };
  const onReset = () => {
    setOrgUnit('');
    setRows(null);
  };
  const onExport = () =>
    notify.display({
      kind: 'success',
      title: 'Export started',
      subtitle: `${rows?.length ?? 0} metric row(s) exported to CSV (mock — no backend yet).`,
      timeout: 5000,
    });

  return (
    <PageLayout title="Application Metrics Export">
      <form style={{ maxWidth: '64rem', marginBottom: '2rem' }} onSubmit={onSearch}>
        <Grid narrow>
          <Column sm={4} md={4} lg={5}>
            <Select id="am-org" labelText="Org Unit" value={orgUnit} onChange={(e) => setOrgUnit(e.target.value)}>
              <SelectItem value="" text="All org units" />
              {ORG_UNITS.map((o) => <SelectItem key={o} value={o} text={o} />)}
            </Select>
          </Column>
          <Column sm={4} md={2} lg={3}>
            <TextInput id="am-from" labelText="Submitted from" placeholder="yyyy-mm-dd" />
          </Column>
          <Column sm={4} md={2} lg={3}>
            <TextInput id="am-to" labelText="Submitted to" placeholder="yyyy-mm-dd" />
          </Column>
        </Grid>
        <div style={{ display: 'flex', gap: '0.5rem', marginTop: '1.5rem' }}>
          <Button type="submit" renderIcon={SearchIcon}>Search</Button>
          <Button type="button" kind="ghost" renderIcon={Reset} onClick={onReset}>Reset</Button>
          {rows !== null && rows.length > 0 && (
            <Button type="button" kind="tertiary" renderIcon={Download} onClick={onExport}>Export CSV</Button>
          )}
        </div>
      </form>

      {rows !== null && (
        <SearchResultsTable
          title="Processing metrics"
          rows={rows}
          headers={HEADERS}
          emptyTitle="No metrics found"
          emptyBody="No applications match the selected org unit."
        />
      )}
    </PageLayout>
  );
};

export default ApplicationMetrics;
