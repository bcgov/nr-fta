import { Report } from '@carbon/icons-react';
import { Accordion, AccordionItem } from '@carbon/react';
import { useState, type FC } from 'react';

import SectionTile from '@/components/SectionTile';
import { useNotification } from '@/context/notification/useNotification';
import PageLayout from '@/pages/PageLayout';
import {
  APT_DATA_EXTRACTS,
  APT_PDF_REPORTS,
  aptReportFileName,
  aptReportZipName,
  downloadAptDataExtract,
  getAptReportPdf,
  getAptReportZip,
  type AptPdfReport,
  type AptReportParams,
} from '@/services/apt/reports';
import { triggerBrowserDownload } from '@/utils/download';

import ReportForm from './ReportForm';

import './Reports.css';

/**
 * APT reports (APT2's APTM8000): nine PDF reports rendered in the backend, listed as an
 * accordion (as REPT's Reports page) whose open row holds the inputs the report takes and its
 * Generate PDF button; APTR011, APTR012 and APTR013 can also run for every management unit or
 * region at once, as a zip of PDFs, and APTR041 and APTR013 also offer their data as CSV. Every APT reader may
 * run every report. A generated PDF is downloaded, named as APT2 names it (see
 * {@link aptReportFileName}).
 */
const AptReports: FC = () => {
  const { display } = useNotification();
  // The open report row; one at a time, as in REPT.
  const [openCode, setOpenCode] = useState<string | null>(null);
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
    } catch (err) {
      failed(`Could not generate ${report.code}`, err);
    } finally {
      setRunning(null);
    }
  };

  const runBatch = async (report: AptPdfReport, reportDate: string) => {
    setRunning(`${report.code}-batch`);
    try {
      const zip = await getAptReportZip(report.code, reportDate);
      triggerBrowserDownload(zip, aptReportZipName(report.code));
    } catch (err) {
      failed(`Could not generate the ${report.code} PDFs`, err);
    } finally {
      setRunning(null);
    }
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
    <PageLayout title="APT Reports" subtitle="Apportionment reports.">
      <SectionTile title="Reports" icon={Report}>
        <div className="apt-reports__accordion-table">
          <div className="apt-reports__row apt-reports__row--header" role="presentation">
            <div className="apt-reports__cell">Report</div>
            <div className="apt-reports__cell">Name</div>
          </div>
          <Accordion className="apt-reports__accordion" align="start">
            {APT_PDF_REPORTS.map((r) => (
              <AccordionItem
                key={r.code}
                open={openCode === r.code}
                onHeadingClick={({ isOpen }) => setOpenCode(isOpen ? r.code : null)}
                title={
                  <div className="apt-reports__row">
                    <div className="apt-reports__cell">{r.code}</div>
                    <div className="apt-reports__cell">{r.name}</div>
                  </div>
                }
              >
                {openCode === r.code && (
                  <ReportForm
                    report={r}
                    running={running === r.code}
                    busy={running !== null}
                    onRun={(params, chosen) => runPdf(r, params, chosen)}
                    batch={
                      r.batchLabel
                        ? {
                            label: r.batchLabel,
                            running: running === `${r.code}-batch`,
                            onRun: (reportDate) => runBatch(r, reportDate),
                          }
                        : undefined
                    }
                    csv={
                      APT_DATA_EXTRACTS.some((x) => x.code === r.code)
                        ? {
                            running: running === `${r.code}-csv`,
                            onRun: () => runExtract(r.code),
                          }
                        : undefined
                    }
                  />
                )}
              </AccordionItem>
            ))}
          </Accordion>
        </div>
      </SectionTile>
    </PageLayout>
  );
};

export default AptReports;
