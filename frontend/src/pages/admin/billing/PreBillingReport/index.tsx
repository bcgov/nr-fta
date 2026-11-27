import { DocumentPdf } from '@carbon/icons-react';
import { Button, Select, SelectItem } from '@carbon/react';
import { useEffect, useState, type FC, type FormEvent } from 'react';

import SectionTile from '@/components/SectionTile';
import YearSelect from '@/components/YearSelect';
import { useNotification } from '@/context/notification/useNotification';
import { useDefaultOrgUnitIn } from '@/hooks/useDefaultOrgUnit';
import { downloadBlob } from '@/lib/downloadBlob';
import { FIRST_BILLING_YEAR } from '@/lib/years';
import PageLayout from '@/pages/PageLayout';
import { getPreBillingReport } from '@/services/billing';
import { getBillingReportDistricts, type CodeOption } from '@/services/codeLists';

import { currentYear, useDistrictScope } from '../useDistrictScope';

/**
 * FTA685 — Pre Billing Report Submission: the FTA408 Pre-Billing Report for a
 * district and year, as a PDF. It lists what the next billing run will reverse
 * and charge for each tenure, then the tenures it will refuse and why.
 */
const PreBillingReport: FC = () => {
  const notify = useNotification();
  const [districts, setDistricts] = useState<CodeOption[]>([]);
  const [chosen, setChosen] = useState('');
  const [year, setYear] = useState(currentYear);
  const scope = useDistrictScope(districts);
  // A single administered district is preselected and locked, as legacy's protected District.
  const orgUnitNo = scope.locked?.code ?? chosen;
  // Legacy defaulted District to the user's org unit when it was a district (Org Unit Maintenance).
  const defaultDistrict = useDefaultOrgUnitIn(scope.options, true);
  useEffect(() => {
    if (defaultDistrict) setChosen((c) => c || defaultDistrict);
  }, [defaultDistrict]);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getBillingReportDistricts()
      .then((d) => !cancelled && setDistricts(d))
      .catch(() =>
        notify.display({
          kind: 'error',
          title: 'Could not load the district list.',
          subtitle: 'Reload the page to try again.',
          timeout: 6000,
        }),
      );
    return () => {
      cancelled = true;
    };
    // Load once; notify is stable for the page's life.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const canSubmit = orgUnitNo !== '' && /^\d{4}$/.test(year) && !busy;

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    setBusy(true);
    try {
      downloadBlob(await getPreBillingReport(orgUnitNo, year), `FTA408-pre-billing-${year}.pdf`);
    } catch (err) {
      notify.display({
        kind: 'error',
        title: 'Could not produce the report.',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 6000,
      });
    } finally {
      setBusy(false);
    }
  };

  return (
    <PageLayout
      title="Pre Billing Report Submission"
      subtitle="See what the next range billing run will charge and reverse in a district, and which tenures it will refuse."
    >
      <SectionTile title="Report" icon={DocumentPdf}>
        <form className="fsp-search__form" onSubmit={(e) => void onSubmit(e)}>
          <div className="fsp-search__field-grid">
            <div className="fsp-search__wide-cell">
              <Select
                id="pre-district"
                labelText="District"
                value={orgUnitNo}
                disabled={!!scope.locked}
                onChange={(e) => setChosen(e.target.value)}
              >
                <SelectItem value="" text="Choose a district" />
                {scope.options.map((d) => (
                  <SelectItem key={d.code} value={d.code} text={d.description} />
                ))}
              </Select>
            </div>
            <YearSelect
              id="pre-year"
              labelText="Billing Year"
              value={year}
              onChange={setYear}
              first={FIRST_BILLING_YEAR}
            />
          </div>
          <div className="fsp-search__actions">
            <Button type="submit" size="md" renderIcon={DocumentPdf} disabled={!canSubmit}>
              {busy ? 'Producing…' : 'Submit'}
            </Button>
          </div>
        </form>
      </SectionTile>
    </PageLayout>
  );
};

export default PreBillingReport;
