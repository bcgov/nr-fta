import { SendAlt } from '@carbon/icons-react';
import { Button, Column, Grid, Select, SelectItem, TextInput } from '@carbon/react';
import { useEffect, useState, type FC, type FormEvent } from 'react';

import ConfirmationModal from '@/components/ConfirmationModal';
import SectionTile from '@/components/SectionTile';
import { useNotification } from '@/context/notification/useNotification';
import PageLayout from '@/pages/PageLayout';
import { submitBillingApproval } from '@/services/billing';
import { getDistricts, type CodeOption } from '@/services/codeLists';

import '../billing.scss';

/**
 * FTA690 — Tenure Approval Submission, as legacy
 * FTA_690_TENURE_APPROVAL.SUBMIT: queue a range billing run for a district and
 * year. The 690 batch job picks the request up and creates the invoices from
 * the prepared billing instructions; one request per district and year a day.
 */
const TenureApproval: FC = () => {
  const notify = useNotification();
  const [districts, setDistricts] = useState<CodeOption[]>([]);
  const [orgUnitNo, setOrgUnitNo] = useState('');
  const [year, setYear] = useState('');
  // Queuing a billing run invoices every prepared tenure in the district.
  const [confirmOpen, setConfirmOpen] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getDistricts()
      .then((d) => !cancelled && setDistricts(d))
      .catch(() =>
        notify.display({
          kind: 'error',
          title: 'Could not load the district list',
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
      title: 'Submit successful',
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
          onSubmit={(e: FormEvent) => {
            e.preventDefault();
            if (canSubmit) setConfirmOpen(true);
          }}
        >
          <Grid narrow>
            <Column sm={4} md={4} lg={6}>
              <Select
                id="appr-district"
                labelText="District"
                value={orgUnitNo}
                onChange={(e) => setOrgUnitNo(e.target.value)}
              >
                <SelectItem value="" text="Choose a district" />
                {districts.map((d) => (
                  <SelectItem key={d.code} value={d.code} text={d.description} />
                ))}
              </Select>
            </Column>
            <Column sm={4} md={2} lg={3}>
              <TextInput
                id="appr-year"
                labelText="Billing Year"
                placeholder="e.g. 2025"
                value={year}
                onChange={(e) => setYear(e.target.value.replace(/\D/g, ''))}
                maxLength={4}
                inputMode="numeric"
                autoComplete="off"
              />
            </Column>
            <Column sm={4} md={2} lg={3} className="billing__go">
              <Button type="submit" size="md" renderIcon={SendAlt} disabled={!canSubmit}>
                Submit
              </Button>
            </Column>
          </Grid>
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
