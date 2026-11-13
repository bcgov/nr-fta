import { DocumentPdf } from '@carbon/icons-react';
import {
  Button,
  Column,
  Grid,
  RadioButton,
  RadioButtonGroup,
  Select,
  SelectItem,
  TextInput,
} from '@carbon/react';
import { useEffect, useState, type FC, type FormEvent } from 'react';

import SectionTile from '@/components/SectionTile';
import { useNotification } from '@/context/notification/useNotification';
import { downloadBlob } from '@/lib/downloadBlob';
import PageLayout from '@/pages/PageLayout';
import { getPostBillingReport, type PostBillingSort } from '@/services/billing';
import { getDistricts, type CodeOption } from '@/services/codeLists';

import '../billing.scss';

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
  const [orgUnit, setOrgUnit] = useState('');
  const [year, setYear] = useState('');
  const [sortBy, setSortBy] = useState<PostBillingSort>('1');
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
      title="Post Billing Report Submission"
      subtitle="List the range invoices issued for a year, by district or for the province."
    >
      <SectionTile title="Report" icon={DocumentPdf}>
        <form onSubmit={(e) => void onSubmit(e)}>
          <Grid narrow>
            <Column sm={4} md={4} lg={6}>
              <Select
                id="post-org"
                labelText="Org Unit"
                value={orgUnit}
                onChange={(e) => setOrgUnit(e.target.value)}
              >
                <SelectItem value="" text="Choose a district" />
                <SelectItem value="*" text="* - All districts" />
                {districts.map((d) => (
                  <SelectItem key={d.code} value={d.code} text={d.description} />
                ))}
              </Select>
            </Column>
            <Column sm={4} md={2} lg={3}>
              <TextInput
                id="post-year"
                labelText="Calendar Year"
                placeholder="e.g. 2025"
                value={year}
                onChange={(e) => setYear(e.target.value.replace(/\D/g, ''))}
                maxLength={4}
                inputMode="numeric"
                autoComplete="off"
              />
            </Column>
            <Column sm={4} md={8} lg={16} className="billing__sort">
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
            </Column>
            <Column sm={4} md={8} lg={16}>
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

export default PostBillingReport;
