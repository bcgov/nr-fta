import { Download } from '@carbon/icons-react';
import { Button, RadioButton, RadioButtonGroup, Select, SelectItem, Tile } from '@carbon/react';
import { useEffect, useState, type FC, type FormEvent } from 'react';

import DateField from '@/components/DateField';
import { useNotification } from '@/context/notification/useNotification';
import { safeErrorMessage } from '@/lib/errorMessage';
import PageLayout from '@/pages/PageLayout';
import {
  exportApplicationMetrics,
  type ApplicationMetricsCriteria,
} from '@/services/applicationMetrics';
import { getTenureAdminOrgUnits, type CodeOption } from '@/services/codeLists';

const BLANK: ApplicationMetricsCriteria = {
  orgUnitNo: '',
  submissionDateFrom: '',
  submissionDateTo: '',
  emailRecdDateFrom: '',
  emailRecdDateTo: '',
  acceptanceDateFrom: '',
  acceptanceDateTo: '',
  decisionDateFrom: '',
  decisionDateTo: '',
  issuanceDateFrom: '',
  issuanceDateTo: '',
};

type DateKey = Exclude<keyof ApplicationMetricsCriteria, 'orgUnitNo'>;

/** The five date ranges, labelled as legacy's form named them in its messages. */
const RANGES: { id: string; label: string; from: DateKey; to: DateKey }[] = [
  {
    id: 'submission',
    label: 'Submission Date',
    from: 'submissionDateFrom',
    to: 'submissionDateTo',
  },
  { id: 'email', label: "Email Rec'd Date", from: 'emailRecdDateFrom', to: 'emailRecdDateTo' },
  {
    id: 'acceptance',
    label: 'Acceptance Date',
    from: 'acceptanceDateFrom',
    to: 'acceptanceDateTo',
  },
  { id: 'decision', label: 'Decision Date', from: 'decisionDateFrom', to: 'decisionDateTo' },
  { id: 'issuance', label: 'Issuance Date', from: 'issuanceDateFrom', to: 'issuanceDateTo' },
];

/**
 * FTA008 — Application Metrics Export. Legacy had no on-screen list: Export wrote an Excel
 * workbook of cutting permit application metrics (a Metrics sheet and a Holds sheet) from
 * FTA_008_METRICS_EXTRACT, for one District or all of them and any of five date ranges. Nothing
 * is mandatory; each From must be on or before its To. Clear empties the form.
 */
const ApplicationMetrics: FC = () => {
  const notify = useNotification();
  const [districts, setDistricts] = useState<CodeOption[]>([]);
  const [form, setForm] = useState<ApplicationMetricsCriteria>(BLANK);
  const [exporting, setExporting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getTenureAdminOrgUnits()
      .then((d) => !cancelled && setDistricts(d))
      .catch(() => {
        if (cancelled) return;
        notify.display({
          kind: 'error',
          title: 'Could not load the districts.',
          subtitle: 'Reload the page to try again.',
          timeout: 6000,
        });
      });
    return () => {
      cancelled = true;
    };
    // Load once; notify is stable for the page's life.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const set = (key: keyof ApplicationMetricsCriteria, value: string) =>
    setForm((f) => ({ ...f, [key]: value }));

  const fromAfterTo = (r: (typeof RANGES)[number]) =>
    form[r.from] !== '' && form[r.to] !== '' && form[r.from] > form[r.to];
  const canExport = !exporting && !RANGES.some(fromAfterTo);

  const onExport = async (e: FormEvent) => {
    e.preventDefault();
    if (!canExport) return;
    setExporting(true);
    try {
      await exportApplicationMetrics(form);
    } catch (err) {
      notify.display({
        kind: 'error',
        title: 'Export failed.',
        subtitle: safeErrorMessage(err, 'The application metrics export could not be produced.'),
        timeout: 7000,
      });
    } finally {
      setExporting(false);
    }
  };

  return (
    <PageLayout
      title="Application Metrics Export"
      subtitle="Export cutting permit application metrics and holds to Excel"
    >
      <Tile className="fsp-search__tile">
        <form className="fsp-search__form" onSubmit={(e) => void onExport(e)}>
          <div className="fsp-search__field-grid">
            <div className="fsp-search__wide-cell">
              <Select
                id="am-district"
                labelText="District"
                helperText="If no District is selected, records for all Districts will be returned"
                value={form.orgUnitNo}
                onChange={(e) => set('orgUnitNo', e.target.value)}
              >
                <SelectItem value="" text="" />
                {districts.map((o) => (
                  <SelectItem key={o.code} value={o.code} text={o.description} />
                ))}
              </Select>
            </div>

            <h2 className="fsp-search__group-heading">Date ranges</h2>

            {RANGES.flatMap((r) => [
              <DateField
                key={`${r.id}-from`}
                id={`am-${r.id}-from`}
                labelText={`${r.label} From`}
                value={form[r.from]}
                onChange={(v) => set(r.from, v)}
                invalidText={
                  fromAfterTo(r)
                    ? `${r.label} From must be less than or equal to ${r.label} To.`
                    : null
                }
              />,
              <DateField
                key={`${r.id}-to`}
                id={`am-${r.id}-to`}
                labelText={`${r.label} To`}
                value={form[r.to]}
                onChange={(v) => set(r.to, v)}
              />,
            ])}

            <div className="fsp-search__sort-cell">
              <RadioButtonGroup legendText="Sort by" name="am-sort-by" valueSelected="onlyCP">
                <RadioButton id="am-sort-only-cp" labelText="Only CPs" value="onlyCP" />
              </RadioButtonGroup>
            </div>
          </div>

          <div className="fsp-search__actions">
            <Button
              kind="tertiary"
              size="md"
              type="button"
              onClick={() => setForm(BLANK)}
              disabled={exporting}
            >
              Clear
            </Button>
            <Button type="submit" size="md" renderIcon={Download} disabled={!canExport}>
              {exporting ? 'Exporting…' : 'Export'}
            </Button>
          </div>
        </form>
      </Tile>
    </PageLayout>
  );
};

export default ApplicationMetrics;
