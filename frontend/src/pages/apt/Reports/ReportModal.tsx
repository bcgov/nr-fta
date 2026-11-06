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

import { Modal } from '@/components/Modal';
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
  /** Whether a report is being generated; the form is locked meanwhile. */
  running: boolean;
  onClose: () => void;
  /**
   * Runs the report; `chosen` is the picked management unit's or region's name, which the
   * downloaded file is named after.
   */
  onRun: (params: AptReportParams, chosen?: string) => Promise<void>;
}

type Errors = Partial<Record<'date' | 'managementUnit' | 'orgUnit', string>>;

/**
 * A PDF report's parameter form (APT2's ReportModal): the report date, management unit and
 * region, each shown only for the reports that take it. The lists it needs are read when it
 * opens. Mounted per report, so each opening starts fresh.
 */
const ReportModal: FC<Props> = ({ report, running, onClose, onRun }) => {
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

  return (
    <Modal
      open
      passiveModal
      size="sm"
      className="detail-dialog"
      modalHeading={`${report.code} ${report.name}`}
      onRequestClose={() => !running && onClose()}
      preventCloseOnClickOutside
    >
      <Stack gap={5}>
        {loadError && (
          <InlineNotification
            kind="error"
            lowContrast
            hideCloseButton
            title="Couldn’t load the choices"
            subtitle={loadError}
          />
        )}

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
            className="detail-dialog__date"
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
      </Stack>

      <div className="detail-dialog__actions">
        <Button kind="tertiary" disabled={running} onClick={onClose}>
          Cancel
        </Button>
        <Button kind="primary" disabled={running || listsLoading} onClick={() => void submit()}>
          {running ? 'Generating…' : 'Generate report'}
        </Button>
      </div>
    </Modal>
  );
};

export default ReportModal;
