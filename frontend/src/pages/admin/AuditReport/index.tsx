import { DocumentPdf } from '@carbon/icons-react';
import { Button, Column, Grid, Select, SelectItem } from '@carbon/react';
import { useEffect, useState, type FC, type FormEvent } from 'react';

import DateField from '@/components/DateField';
import SectionTile from '@/components/SectionTile';
import { useNotification } from '@/context/notification/useNotification';
import { useDefaultOrgUnit } from '@/hooks/useDefaultOrgUnit';
import { downloadBlob } from '@/lib/downloadBlob';
import { useDistrictScope } from '@/pages/admin/billing/useDistrictScope';
import PageLayout from '@/pages/PageLayout';
import { getAuditReport, getDistrictRegions, type AuditReportName } from '@/services/audit_report';
import {
  getDistrictOrgUnits,
  getFileTypes,
  getRegions,
  type CodeOption,
} from '@/services/codeLists';

import './AuditReport.scss';

const REPORTS: { value: AuditReportName; label: string; filename: string }[] = [
  {
    value: 'FTA403',
    label: 'District Override Audit Report',
    filename: 'FTA403-district-override-audit.pdf',
  },
  { value: 'FTA405', label: 'Delete EE Audit Report', filename: 'FTA405-delete-ee-audit.pdf' },
];

/** yyyy-mm-dd in local time. */
const iso = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

/** Today less three months, the day clamped to the month's end as Java's Calendar does. */
const threeMonthsAgo = () => {
  const now = new Date();
  const first = new Date(now.getFullYear(), now.getMonth() - 3, 1);
  const lastDay = new Date(first.getFullYear(), first.getMonth() + 1, 0).getDate();
  return iso(new Date(first.getFullYear(), first.getMonth(), Math.min(now.getDate(), lastDay)));
};

const FILE_TYPE_SLOTS = 5;

/**
 * FTA675 — Audit Report: the FTA403 District Override Audit (FDC_AUDIT_LOG) or the FTA405
 * Delete EE Audit (FTAEE_AUDIT_LOG) for a region and district ("*" for all), an entry-date
 * range (three months to today by default) and up to five file types ("*" or none for all), as a
 * PDF. As legacy's validators: Region and District are required, From is on or before To, no
 * file type twice, and the district falls within the region.
 */
const AuditReport: FC = () => {
  const notify = useNotification();
  const [regions, setRegions] = useState<CodeOption[]>([]);
  const [districts, setDistricts] = useState<CodeOption[]>([]);
  const [fileTypeOptions, setFileTypeOptions] = useState<CodeOption[]>([]);
  const [districtRegions, setDistrictRegions] = useState<Record<string, string>>({});
  const [report, setReport] = useState<AuditReportName | ''>('');
  const [regionChoice, setRegionChoice] = useState('');
  const [districtChoice, setDistrictChoice] = useState('');
  const [fromDate, setFromDate] = useState(threeMonthsAgo);
  const [toDate, setToDate] = useState(() => iso(new Date()));
  const [fileTypes, setFileTypes] = useState<string[]>(() => Array(FILE_TYPE_SLOTS).fill(''));
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    Promise.allSettled([
      getRegions(),
      getDistrictOrgUnits(),
      getFileTypes(),
      getDistrictRegions(),
    ]).then(([r, d, t, dr]) => {
      if (cancelled) return;
      if (r.status === 'fulfilled') setRegions(r.value);
      if (d.status === 'fulfilled') setDistricts(d.value);
      if (t.status === 'fulfilled') setFileTypeOptions(t.value);
      if (dr.status === 'fulfilled') setDistrictRegions(dr.value);
      if ([r, d, t, dr].some((x) => x.status === 'rejected')) {
        notify.display({
          kind: 'error',
          title: 'Could not load the dropdown lists',
          subtitle: 'Reload the page to try again.',
          timeout: 6000,
        });
      }
    });
    return () => {
      cancelled = true;
    };
    // Load once; notify is stable for the page's life.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Legacy protected Region and District for a district user; a single administered district
  // is preselected and locked here, with its region.
  const scope = useDistrictScope(districts);
  const district = scope.locked?.code ?? districtChoice;
  const region = scope.locked ? (districtRegions[scope.locked.code] ?? '') : regionChoice;

  // Legacy defaulted Region and District to a district user's org unit and its region, and Region
  // to a region user's (Org Unit Maintenance).
  const userDefault = useDefaultOrgUnit();
  useEffect(() => {
    const no = userDefault?.orgUnitNo;
    if (!no) return;
    if (userDefault.orgLevelCode === 'D' && scope.options.some((o) => o.code === no)) {
      setDistrictChoice((c) => c || no);
      const regionNo = userDefault.rollupRegionNo;
      if (regionNo && regions.some((o) => o.code === regionNo)) {
        setRegionChoice((c) => c || regionNo);
      }
    } else if (userDefault.orgLevelCode === 'R' && regions.some((o) => o.code === no)) {
      setRegionChoice((c) => c || no);
    }
  }, [userDefault, scope.options, regions]);

  const notInRegion =
    region !== '' &&
    district !== '' &&
    region !== '*' &&
    district !== '*' &&
    districtRegions[district] !== undefined &&
    districtRegions[district] !== region;
  const fromAfterTo = fromDate !== '' && toDate !== '' && fromDate > toDate;
  const duplicateAt = fileTypes.map(
    (t, i) => t !== '' && fileTypes.slice(0, i).some((u) => u === t),
  );

  const canSubmit =
    report !== '' &&
    region !== '' &&
    district !== '' &&
    !notInRegion &&
    !fromAfterTo &&
    !duplicateAt.some(Boolean) &&
    !busy;

  const setFileType = (i: number, v: string) =>
    setFileTypes((all) => all.map((t, j) => (j === i ? v : t)));

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    setBusy(true);
    try {
      const pdf = await getAuditReport({ report, region, district, fromDate, toDate, fileTypes });
      downloadBlob(pdf, REPORTS.find((r) => r.value === report)?.filename ?? 'audit-report.pdf');
    } catch (err) {
      notify.display({
        kind: 'error',
        title: 'Could not produce the report',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 6000,
      });
    } finally {
      setBusy(false);
    }
  };

  return (
    <PageLayout
      title="Audit Report"
      subtitle="Produce the District Override Audit or Delete EE Audit report for a region, district and date range."
    >
      <SectionTile title="Report" icon={DocumentPdf}>
        <form className="audit-report__form" onSubmit={(e) => void onSubmit(e)}>
          <Grid narrow>
            <Column sm={4} md={8} lg={8}>
              <Select
                id="audit-report"
                labelText="Report"
                value={report}
                onChange={(e) => setReport(e.target.value as AuditReportName | '')}
              >
                <SelectItem value="" text="Choose a report" />
                {REPORTS.map((r) => (
                  <SelectItem key={r.value} value={r.value} text={r.label} />
                ))}
              </Select>
            </Column>
            <Column sm={4} md={8} lg={8} />
            <Column sm={4} md={4} lg={8}>
              <Select
                id="audit-region"
                labelText="Region Code"
                value={region}
                disabled={!!scope.locked}
                onChange={(e) => setRegionChoice(e.target.value)}
              >
                <SelectItem value="" text="Choose a region" />
                {scope.unscoped && <SelectItem value="*" text="* - All regions" />}
                {regions.map((r) => (
                  <SelectItem key={r.code} value={r.code} text={r.description} />
                ))}
              </Select>
            </Column>
            <Column sm={4} md={4} lg={8}>
              <Select
                id="audit-district"
                labelText="District Code"
                value={district}
                disabled={!!scope.locked}
                invalid={notInRegion}
                invalidText="District does not fall within the region."
                onChange={(e) => setDistrictChoice(e.target.value)}
              >
                <SelectItem value="" text="Choose a district" />
                {scope.unscoped && <SelectItem value="*" text="* - All districts" />}
                {scope.options.map((d) => (
                  <SelectItem key={d.code} value={d.code} text={d.description} />
                ))}
              </Select>
            </Column>
            <Column sm={4} md={4} lg={8}>
              <DateField
                id="audit-from"
                labelText="Date Range From"
                value={fromDate}
                onChange={setFromDate}
                invalidText={
                  fromAfterTo
                    ? 'Date Range From must be less than or equal to Date Range To.'
                    : null
                }
              />
            </Column>
            <Column sm={4} md={4} lg={8}>
              <DateField
                id="audit-to"
                labelText="Date Range To"
                value={toDate}
                onChange={setToDate}
              />
            </Column>
            {fileTypes.map((t, i) => (
              <Column key={i} sm={4} md={4} lg={5}>
                <Select
                  id={`audit-file-type-${i + 1}`}
                  labelText={`File Type ${i + 1}`}
                  value={t}
                  invalid={duplicateAt[i]}
                  invalidText="File Type must have unique entries."
                  onChange={(e) => setFileType(i, e.target.value)}
                >
                  <SelectItem value="" text={i === 0 ? 'All file types' : ''} />
                  {i === 0 && <SelectItem value="*" text="* - All file types" />}
                  {fileTypeOptions.map((o) => (
                    <SelectItem key={o.code} value={o.code} text={o.description} />
                  ))}
                </Select>
              </Column>
            ))}
          </Grid>
          <div className="audit-report__actions">
            <Button type="submit" size="md" renderIcon={DocumentPdf} disabled={!canSubmit}>
              {busy ? 'Producing…' : 'Submit'}
            </Button>
          </div>
        </form>
      </SectionTile>
    </PageLayout>
  );
};

export default AuditReport;
