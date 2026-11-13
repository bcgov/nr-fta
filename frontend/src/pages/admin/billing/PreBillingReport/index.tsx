import { DocumentPdf } from '@carbon/icons-react';
import { Button, Column, Grid, Select, SelectItem, TextInput } from '@carbon/react';
import { useEffect, useState, type FC, type FormEvent } from 'react';

import SectionTile from '@/components/SectionTile';
import { useNotification } from '@/context/notification/useNotification';
import { downloadBlob } from '@/lib/downloadBlob';
import PageLayout from '@/pages/PageLayout';
import { getPreBillingReport } from '@/services/billing';
import { getDistricts, type CodeOption } from '@/services/codeLists';

import '../billing.scss';

/**
 * FTA685 — Pre Billing Report Submission: the FTA408 Pre-Billing Report for a
 * district and year, as a PDF. It lists what the next billing run will reverse
 * and charge for each tenure, then the tenures it will refuse and why.
 */
const PreBillingReport: FC = () => {
  const notify = useNotification();
  const [districts, setDistricts] = useState<CodeOption[]>([]);
  const [orgUnitNo, setOrgUnitNo] = useState('');
  const [year, setYear] = useState('');
  const [busy, setBusy] = useState(false);

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
      title="Pre Billing Report Submission"
      subtitle="See what the next range billing run will charge and reverse in a district, and which tenures it will refuse."
    >
      <SectionTile title="Report" icon={DocumentPdf}>
        <form onSubmit={(e) => void onSubmit(e)}>
          <Grid narrow>
            <Column sm={4} md={4} lg={6}>
              <Select
                id="pre-district"
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
                id="pre-year"
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
              <Button type="submit" size="md" renderIcon={DocumentPdf} disabled={!canSubmit}>
                {busy ? 'Producing…' : 'Submit'}
              </Button>
            </Column>
          </Grid>
        </form>
      </SectionTile>
    </PageLayout>
  );
};

export default PreBillingReport;
