import { DocumentPdf } from '@carbon/icons-react';
import { Button, RadioButton, RadioButtonGroup, Select, SelectItem } from '@carbon/react';
import { useEffect, useState, type FC, type FormEvent } from 'react';

import SectionTile from '@/components/SectionTile';
import YearSelect from '@/components/YearSelect';
import { useNotification } from '@/context/notification/useNotification';
import { useDefaultOrgUnitIn } from '@/hooks/useDefaultOrgUnit';
import { downloadBlob } from '@/lib/downloadBlob';
import { FIRST_BILLING_YEAR } from '@/lib/years';
import PageLayout from '@/pages/PageLayout';
import { getPostBillingReport, type PostBillingSort } from '@/services/billing';
import { getBillingReportDistricts, type CodeOption } from '@/services/codeLists';

import { currentYear, useDistrictScope } from '../useDistrictScope';

const SORTS: { value: PostBillingSort; label: string }[] = [
  { value: '1', label: 'Client Name' },
  { value: '2', label: 'File Id' },
  { value: '3', label: 'File Type' },
  { value: '4', label: 'Invoice Number' },
];

/**
 * FTA686 — Post Billing Report Submission: the FTA409 Post-Billing Report, as
 * a PDF — every range invoice for a year, for one district or all ("*"), in
 * the chosen order. Sorted by client it subtotals each client.
 */
const PostBillingReport: FC = () => {
  const notify = useNotification();
  const [districts, setDistricts] = useState<CodeOption[]>([]);
  const [chosen, setChosen] = useState('');
  const [year, setYear] = useState(currentYear);
  const scope = useDistrictScope(districts);
  // A single administered district is preselected and locked, as legacy's protected Org Unit.
  const orgUnit = scope.locked?.code ?? chosen;
  // Legacy defaulted District to the user's org unit when it was a district (Org Unit Maintenance).
  const defaultDistrict = useDefaultOrgUnitIn(scope.options, true);
  useEffect(() => {
    if (defaultDistrict) setChosen((c) => c || defaultDistrict);
  }, [defaultDistrict]);
  const [sortBy, setSortBy] = useState<PostBillingSort>('1');
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

  const canSubmit = orgUnit !== '' && /^\d{4}$/.test(year) && !busy;

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    setBusy(true);
    try {
      downloadBlob(
        await getPostBillingReport(orgUnit, year, sortBy),
        `FTA409-post-billing-${year}.pdf`,
      );
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
      title="Post Billing Report Submission"
      subtitle="List the range invoices issued for a year, by district or for the province."
    >
      <SectionTile title="Report" icon={DocumentPdf}>
        <form className="fsp-search__form" onSubmit={(e) => void onSubmit(e)}>
          <div className="fsp-search__field-grid">
            <div className="fsp-search__wide-cell">
              <Select
                id="post-org"
                labelText="Org Unit"
                value={orgUnit}
                disabled={!!scope.locked}
                onChange={(e) => setChosen(e.target.value)}
              >
                <SelectItem value="" text="Choose a district" />
                {/* Legacy offered "*" only to users who were not district users. */}
                {scope.unscoped && <SelectItem value="*" text="* - All districts" />}
                {scope.options.map((d) => (
                  <SelectItem key={d.code} value={d.code} text={d.description} />
                ))}
              </Select>
            </div>
            <YearSelect
              id="post-year"
              labelText="Calendar Year"
              value={year}
              onChange={setYear}
              first={FIRST_BILLING_YEAR}
            />
            <div className="fsp-search__sort-cell">
              <RadioButtonGroup
                legendText="Sort By"
                name="post-sort"
                valueSelected={sortBy}
                onChange={(v) => setSortBy(v as PostBillingSort)}
              >
                {SORTS.map((s) => (
                  <RadioButton
                    key={s.value}
                    id={`post-sort-${s.value}`}
                    value={s.value}
                    labelText={s.label}
                  />
                ))}
              </RadioButtonGroup>
            </div>
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

export default PostBillingReport;
