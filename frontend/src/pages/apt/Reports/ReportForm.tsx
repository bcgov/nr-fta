import { DocumentMultiple_01, DocumentPdf, Download } from '@carbon/icons-react';
import {
  Button,
  ComboBox,
  DatePicker,
  DatePickerInput,
  InlineNotification,
  Select,
  SelectItem,
  Stack,
} from '@carbon/react';
import { useCallback, useState, type FC } from 'react';

import BusyIcon from '@/components/BusyIcon';
import { useApiResource } from '@/hooks/useApiResource';
import { getAptOrgUnits, type AptOrgUnit } from '@/services/apt/lookups';
import { searchAptManagementUnits, type AptManagementUnit } from '@/services/apt/managementUnits';
import type { AptPdfReport, AptReportParams } from '@/services/apt/reports';
import { parseTypedDate, TYPED_DATE_PATTERN } from '@/utils/typedDate';

const toIsoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

/** One entry per management unit name, as APT2's picker listed them. */
function uniqueByName(units: AptManagementUnit[]): AptManagementUnit[] {
  const seen = new Map<string, AptManagementUnit>();
  for (const u of units) {
    const key = u.mgmtUnitName ?? `#${u.id}`;
    if (!seen.has(key)) seen.set(key, u);
  }
  return [...seen.values()].sort((a, b) =>
    (a.mgmtUnitName ?? '').localeCompare(b.mgmtUnitName ?? ''),
  );
}

/** APT2's APTR011 list: only the TSAs, i.e. the units whose name includes "TSA". */
const tsaOnly = (units: AptManagementUnit[]) =>
  units.filter((u) => u.mgmtUnitName?.includes('TSA'));

const muLabel = (u: AptManagementUnit | null | undefined) =>
  u ? (u.mgmtUnitName ?? `Unit ${u.id}`) : '';

const regionLabel = (r: AptOrgUnit) => r.displayName ?? r.name ?? r.code;

const none = <T,>(): Promise<T[]> => Promise.resolve([]);

interface Props {
  report: AptPdfReport;
  /** Whether this report's PDF is being generated; the form is locked meanwhile. */
  running: boolean;
  /** Whether any report or CSV is being produced; the buttons wait meanwhile. */
  busy: boolean;
  /**
   * Runs the report; `chosen` is the picked management unit's or region's name, which the
   * downloaded file is named after.
   */
  onRun: (params: AptReportParams, chosen?: string) => Promise<void>;
  /** The report's data as CSV (APTR041 and APTR013 only); it takes none of the inputs. */
  csv?: { running: boolean; onRun: () => Promise<void> };
  /**
   * The report for every management unit or region at once (APTR011, APTR012, APTR013), as a
   * zip of PDFs; it takes only the report date.
   */
  batch?: { label: string; running: boolean; onRun: (reportDate: string) => Promise<void> };
}

type Errors = Partial<Record<'date' | 'managementUnit' | 'orgUnit', string>>;

/**
 * A PDF report's parameter form (APT2's ReportModal), shown in the report's accordion row: the
 * report date, management unit and region, each only for the reports that take it, and Generate
 * PDF (and Generate CSV where the report has a data extract). The lists it needs are read when the row opens; mounted only while open, so each
 * opening starts fresh.
 */
const ReportForm: FC<Props> = ({ report, running, busy, onRun, csv, batch }) => {
  const needs = (input: AptPdfReport['inputs'][number]) => report.inputs.includes(input);
  const needsMu = needs('managementUnit');
  const needsOrg = needs('orgUnit');
  const tsaUnitsOnly = !!report.tsaUnitsOnly;

  const muFetch = useCallback(
    () =>
      needsMu
        ? searchAptManagementUnits()
            .then(uniqueByName)
            .then((list) => (tsaUnitsOnly ? tsaOnly(list) : list))
        : none<AptManagementUnit>(),
    [needsMu, tsaUnitsOnly],
  );
  const orgFetch = useCallback(
    () => (needsOrg ? getAptOrgUnits('R') : none<AptOrgUnit>()),
    [needsOrg],
  );
  const units = useApiResource(muFetch, [muFetch]);
  const regions = useApiResource(orgFetch, [orgFetch]);

  const [reportDate, setReportDate] = useState(() => toIsoDate(new Date()));
  const [unit, setUnit] = useState<AptManagementUnit | null>(null);
  // Blank means "the list's first entry", APT2's default, until the user picks.
  const [orgUnit, setOrgUnit] = useState('');
  const [errors, setErrors] = useState<Errors>({});

  const regionList = regions.data ?? [];
  const chosenOrg = orgUnit || (regionList[0] ? String(regionList[0].orgUnitNo) : '');

  const loadError = [units.error, regions.error].find(Boolean);
  const listsLoading = units.loading || regions.loading;

  const submit = async () => {
    const found: Errors = {};
    if (needs('date') && !reportDate) found.date = 'Enter the report date.';
    if (needsMu && !unit) found.managementUnit = 'Choose a management unit.';
    if (needsOrg && !chosenOrg) found.orgUnit = 'Choose a region.';
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    const region = regionList.find((r) => String(r.orgUnitNo) === chosenOrg);
    await onRun(
      {
        reportDate: needs('date') ? reportDate : undefined,
        managementUnitId: needsMu ? unit?.id : undefined,
        orgUnitNo: needsOrg ? Number(chosenOrg) : undefined,
      },
      needsMu ? (unit?.mgmtUnitName ?? undefined) : region ? regionLabel(region) : undefined,
    );
  };

  const submitBatch = async () => {
    if (!batch) return;
    if (!reportDate) {
      setErrors({ date: 'Enter the report date.' });
      return;
    }
    setErrors({});
    await batch.onRun(reportDate);
  };

  return (
    <div className="apt-report-form">
      <Stack gap={5}>
        {report.inputs.length === 0 && (
          <p className="apt-report-form__note">This report has no inputs.</p>
        )}
        {loadError && (
          <InlineNotification
            kind="error"
            lowContrast
            hideCloseButton
            title="Couldn’t load the choices."
            subtitle={loadError}
          />
        )}

        {report.inputs.length > 0 && (
          <div className="apt-report-form__fields">
            {needsMu && (
              <ComboBox<AptManagementUnit>
                id="apt-report-mu"
                titleText="Management unit"
                placeholder={units.loading ? 'Loading…' : 'Choose a management unit'}
                items={units.data ?? []}
                itemToString={muLabel}
                selectedItem={unit}
                disabled={running || units.loading}
                invalid={!!errors.managementUnit}
                invalidText={errors.managementUnit}
                onChange={({ selectedItem }) => {
                  setUnit(selectedItem ?? null);
                  setErrors((e) => ({ ...e, managementUnit: undefined }));
                }}
              />
            )}

            {needsOrg && (
              <Select
                id="apt-report-region"
                labelText="Region"
                value={chosenOrg}
                disabled={running || regions.loading}
                invalid={!!errors.orgUnit}
                invalidText={errors.orgUnit}
                onChange={(e) => setOrgUnit(e.target.value)}
              >
                {regions.loading && <SelectItem value="" text="Loading…" />}
                {regionList.map((r) => (
                  <SelectItem key={r.orgUnitNo} value={String(r.orgUnitNo)} text={regionLabel(r)} />
                ))}
              </Select>
            )}

            {needs('date') && (
              <DatePicker
                datePickerType="single"
                dateFormat="Y-m-d"
                className="apt-report-form__date"
                value={reportDate}
                invalid={!!errors.date}
                onChange={(dates: Date[]) => setReportDate(dates[0] ? toIsoDate(dates[0]) : '')}
              >
                <DatePickerInput
                  id="apt-report-date"
                  labelText="Report date"
                  placeholder="yyyy-mm-dd"
                  pattern={TYPED_DATE_PATTERN}
                  invalidText={errors.date}
                  disabled={running}
                  onChange={(e) => {
                    const text = e.target.value;
                    if (text.trim() === '') setReportDate('');
                    else {
                      const typed = parseTypedDate(text);
                      if (typed) setReportDate(typed);
                    }
                  }}
                />
              </DatePicker>
            )}
          </div>
        )}
      </Stack>

      <div className="apt-report-form__actions">
        {csv && (
          <Button
            kind="tertiary"
            renderIcon={csv.running ? BusyIcon : Download}
            disabled={busy}
            onClick={() => void csv.onRun()}
          >
            {csv.running ? 'Generating…' : 'Generate CSV'}
          </Button>
        )}
        {batch && (
          <Button
            kind="secondary"
            renderIcon={batch.running ? BusyIcon : DocumentMultiple_01}
            disabled={busy}
            onClick={() => void submitBatch()}
          >
            {batch.running ? 'Generating…' : batch.label}
          </Button>
        )}
        <Button
          kind="primary"
          renderIcon={running ? BusyIcon : DocumentPdf}
          disabled={busy || listsLoading}
          onClick={() => void submit()}
        >
          {running ? 'Generating…' : 'Generate PDF'}
        </Button>
      </div>
    </div>
  );
};

export default ReportForm;
