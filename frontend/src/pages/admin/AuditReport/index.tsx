import { Search as SearchIcon, Reset, Download, Report } from '@carbon/icons-react';
import {
  Button,
  Column,
  Grid,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  Tag,
  TextInput,
} from '@carbon/react';
import { useState, type FC, type FormEvent } from 'react';

import AsyncBoundary from '@/components/AsyncBoundary';
import SectionTile from '@/components/SectionTile';
import { useNotification } from '@/context/notification/useNotification';
import PageLayout from '@/pages/PageLayout';
import {
  fetchAuditReport,
  type AuditReport,
  type AuditReportParams,
} from '@/services/audit_report';

import './AuditReport.scss';

/**
 * FTA402 — Private Mark Certificate report. Filterable timber-mark certificate
 * list with CSV export, backed by the backend {@code GET /api/fta/admin/audit}
 * endpoint (which ports THE.FTA_402_PKG).
 */
const AuditReport: FC = () => {
  const notify = useNotification();
  const [criteria, setCriteria] = useState<AuditReportParams>({});
  const [rows, setRows] = useState<AuditReport[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);

  const onField = (field: keyof AuditReportParams) => (value: string) =>
    setCriteria((c) => ({ ...c, [field]: value }));

  const runReport = async (params: AuditReportParams) => {
    setLoading(true);
    setError(undefined);
    try {
      setRows(await fetchAuditReport(params));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Report failed');
      setRows(null);
    } finally {
      setLoading(false);
    }
  };

  const onSearch = (e: FormEvent) => {
    e.preventDefault();
    void runReport(criteria);
  };
  const onReset = () => {
    setCriteria({});
    setRows(null);
    setError(undefined);
  };
  const onExport = () =>
    notify.display({
      kind: 'success',
      title: 'Export started',
      subtitle: `${rows?.length ?? 0} certificate row(s) exported (mock — no backend yet).`,
      timeout: 5000,
    });

  return (
    <PageLayout
      title="Audit Report"
      subtitle="Search private mark certificates by timber mark or licensee, and export the results."
    >
      <SectionTile title="Search criteria" icon={SearchIcon}>
        <form className="audit-report__form" onSubmit={onSearch}>
          <Grid narrow>
            <Column sm={4} md={4} lg={4}>
              <TextInput
                id="au-mark"
                labelText="Timber Mark"
                placeholder="e.g. AB1234"
                value={criteria.timberMark ?? ''}
                onChange={(e) => onField('timberMark')(e.target.value)}
              />
            </Column>
            <Column sm={4} md={4} lg={4}>
              <TextInput
                id="au-licensee"
                labelText="Licensee"
                placeholder="e.g. West Fraser"
                value={criteria.mainLicensee ?? ''}
                onChange={(e) => onField('mainLicensee')(e.target.value)}
              />
            </Column>
          </Grid>
          <div className="audit-report__actions">
            <Button type="submit" size="md" renderIcon={SearchIcon}>
              Run report
            </Button>
            <Button type="button" size="md" kind="tertiary" renderIcon={Reset} onClick={onReset}>
              Reset
            </Button>
          </div>
        </form>
      </SectionTile>

      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={() => void runReport(criteria)}
        loadingText="Running report…"
      >
        {rows !== null && (
          <SectionTile
            title="Certificates"
            icon={Report}
            actions={
              rows.length > 0 ? (
                <Button
                  type="button"
                  size="md"
                  kind="tertiary"
                  renderIcon={Download}
                  onClick={onExport}
                >
                  Export
                </Button>
              ) : undefined
            }
          >
            <div className="bordered-table">
              <TableContainer description={`${rows.length} entr${rows.length === 1 ? 'y' : 'ies'}`}>
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableHeader>Timber Mark</TableHeader>
                      <TableHeader>Licensee</TableHeader>
                      <TableHeader>District</TableHeader>
                      <TableHeader>File Type</TableHeader>
                      <TableHeader>Issue Date</TableHeader>
                      <TableHeader>Expiry Date</TableHeader>
                      <TableHeader>Secondary Clients</TableHeader>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {rows.map((a, i) => (
                      <TableRow key={i}>
                        <TableCell>
                          <Tag type="blue">{a.timberMark}</Tag>
                        </TableCell>
                        <TableCell>{a.mainLicensee ?? '—'}</TableCell>
                        <TableCell>{a.district ?? '—'}</TableCell>
                        <TableCell>{a.fileTypeDesc ?? '—'}</TableCell>
                        <TableCell>{a.markIssueDate ?? '—'}</TableCell>
                        <TableCell>{a.markExpiryDate ?? '—'}</TableCell>
                        <TableCell>{a.secondaryClientCount ?? 0}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </div>
          </SectionTile>
        )}
      </AsyncBoundary>
    </PageLayout>
  );
};

export default AuditReport;
