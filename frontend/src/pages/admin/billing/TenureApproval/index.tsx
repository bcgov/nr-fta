import { SendAlt } from '@carbon/icons-react';
import { Button, Select, SelectItem } from '@carbon/react';
import { useEffect, useState, type FC, type FormEvent } from 'react';

import ConfirmationModal from '@/components/ConfirmationModal';
import SectionTile from '@/components/SectionTile';
import YearSelect from '@/components/YearSelect';
import { useNotification } from '@/context/notification/useNotification';
import { useDefaultOrgUnitIn } from '@/hooks/useDefaultOrgUnit';
import { FIRST_BILLING_YEAR } from '@/lib/years';
import PageLayout from '@/pages/PageLayout';
import { submitBillingApproval } from '@/services/billing';
import { getDistrictOrgUnits, type CodeOption } from '@/services/codeLists';

import { useDistrictScope } from '../useDistrictScope';

/**
 * FTA690 — Tenure Approval Submission, as legacy
 * FTA_690_TENURE_APPROVAL.SUBMIT: queue a range billing run for a district and
 * year. The 690 batch job picks the request up and creates the invoices from
 * the prepared billing instructions; one request per district and year a day.
 */
const TenureApproval: FC = () => {
  const notify = useNotification();
  const [districts, setDistricts] = useState<CodeOption[]>([]);
  const [chosen, setChosen] = useState('');
  const scope = useDistrictScope(districts);
  // A single administered district is preselected and locked, as legacy's protected District.
  const orgUnitNo = scope.locked?.code ?? chosen;
  // Legacy defaulted District to the user's org unit when it was a district (Org Unit Maintenance).
  const defaultDistrict = useDefaultOrgUnitIn(scope.options, true);
  useEffect(() => {
    if (defaultDistrict) setChosen((c) => c || defaultDistrict);
  }, [defaultDistrict]);
  const [year, setYear] = useState('');
  // Queuing a billing run invoices every prepared tenure in the district.
  const [confirmOpen, setConfirmOpen] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getDistrictOrgUnits()
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

  const district = districts.find((d) => d.code === orgUnitNo)?.description ?? '';
  const canSubmit = orgUnitNo !== '' && /^\d{4}$/.test(year);

  // ConfirmationModal shows the error and stays open if this throws.
  const onSubmit = async () => {
    await submitBillingApproval(orgUnitNo, year);
    notify.display({
      kind: 'success',
      title: 'Submit successful.',
      subtitle: `Billing for ${district}, ${year} is queued.`,
      timeout: 5000,
    });
  };

  return (
    <PageLayout
      title="Tenure Approval Submission"
      subtitle="Queue the range billing run that invoices a district's prepared billing instructions."
    >
      <SectionTile title="Billing run" icon={SendAlt}>
        <form
          className="fsp-search__form"
          onSubmit={(e: FormEvent) => {
            e.preventDefault();
            if (canSubmit) setConfirmOpen(true);
          }}
        >
          <div className="fsp-search__field-grid">
            <div className="fsp-search__wide-cell">
              <Select
                id="appr-district"
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
              id="appr-year"
              labelText="Billing Year"
              value={year}
              onChange={setYear}
              first={FIRST_BILLING_YEAR}
              placeholder="Choose a year"
            />
          </div>
          <div className="fsp-search__actions">
            <Button type="submit" size="md" renderIcon={SendAlt} disabled={!canSubmit}>
              Submit
            </Button>
          </div>
        </form>
      </SectionTile>

      <ConfirmationModal
        open={confirmOpen}
        onClose={() => setConfirmOpen(false)}
        heading="Submit for billing?"
        confirmLabel="Submit"
        errorTitle="Could not submit"
        onConfirm={onSubmit}
      >
        The range billing run for {district}, {year} will be queued. It invoices every tenure whose
        billing instructions are prepared.
      </ConfirmationModal>
    </PageLayout>
  );
};

export default TenureApproval;
