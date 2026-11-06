import { DocumentPdf, Download, Report } from '@carbon/icons-react';
import {
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { useState, type FC } from 'react';

import SectionTile from '@/components/SectionTile';
import { useNotification } from '@/context/notification/useNotification';
import PageLayout from '@/pages/PageLayout';
import {
  APT_DATA_EXTRACTS,
  APT_PDF_REPORTS,
  aptReportFileName,
  downloadAptDataExtract,
  getAptReportPdf,
  type AptPdfReport,
  type AptReportParams,
} from '@/services/apt/reports';
import { triggerBrowserDownload } from '@/utils/download';

import ReportModal from './ReportModal';

import './Reports.css';

/**
 * APT reports (APT2's APTM8000): nine PDF reports rendered in the backend, each with the
 * inputs it takes, and two CSV data extracts. Every APT reader may run every report. A
 * generated PDF is downloaded, named as APT2 names it (see {@link aptReportFileName}).
 */
const AptReports: FC = () => {
  const { display } = useNotification();
  const [asking, setAsking] = useState<AptPdfReport | null>(null);
  // The report or extract being produced; one at a time, as in APT2.
  const [running, setRunning] = useState<string | null>(null);

  const failed = (title: string, err: unknown) =>
    display({
      kind: 'error',
      title,
      subtitle: err instanceof Error ? err.message : 'Request failed',
      timeout: 9000,
    });

  const runPdf = async (report: AptPdfReport, params: AptReportParams = {}, chosen?: string) => {
    setRunning(report.code);
    try {
      const pdf = await getAptReportPdf(report.code, params);
      triggerBrowserDownload(pdf, aptReportFileName(report.code, chosen));
      setAsking(null);
    } catch (err) {
      failed(`Could not generate ${report.code}`, err);
    } finally {
      setRunning(null);
    }
  };

  const choose = (report: AptPdfReport) => {
    if (report.inputs.length === 0) void runPdf(report);
    else setAsking(report);
  };

  const runExtract = async (code: string) => {
    const key = `${code}-csv`;
    setRunning(key);
    try {
      await downloadAptDataExtract(code);
    } catch (err) {
      failed(`Could not download the ${code} data`, err);
    } finally {
      setRunning(null);
    }
  };

  return (
    <PageLayout title="APT Reports" subtitle="Apportionment reports and data extracts.">
      <SectionTile title="Reports" icon={Report}>
        <TableContainer>
          <Table size="md" aria-label="APT reports">
            <TableHead>
              <TableRow>
                <TableHeader>Report</TableHeader>
                <TableHeader>Name</TableHeader>
                <TableHeader>
                  <span className="cds--visually-hidden">Actions</span>
                </TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {APT_PDF_REPORTS.map((r) => (
                <TableRow key={r.code}>
                  <TableCell>{r.code}</TableCell>
                  <TableCell>{r.name}</TableCell>
                  <TableCell className="apt-reports__action-cell">
                    <Button
                      kind="ghost"
                      size="sm"
                      renderIcon={DocumentPdf}
                      disabled={running !== null}
                      onClick={() => choose(r)}
                    >
                      {running === r.code && !asking ? 'Generating…' : 'Generate'}
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </SectionTile>

      <SectionTile title="Data extracts" icon={Download}>
        <div className="apt-reports__extracts">
          {APT_DATA_EXTRACTS.map((x) => (
            <Button
              key={x.code}
              kind="tertiary"
              size="md"
              renderIcon={Download}
              disabled={running !== null}
              onClick={() => void runExtract(x.code)}
            >
              {running === `${x.code}-csv` ? 'Downloading…' : `${x.label} (${x.code})`}
            </Button>
          ))}
        </div>
      </SectionTile>

      {asking && (
        <ReportModal
          key={asking.code}
          report={asking}
          running={running === asking.code}
          onClose={() => setAsking(null)}
          onRun={(params, chosen) => runPdf(asking, params, chosen)}
        />
      )}
    </PageLayout>
  );
};

export default AptReports;
