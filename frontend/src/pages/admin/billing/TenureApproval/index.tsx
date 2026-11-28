import { SendAlt } from '@carbon/icons-react';
import { Button, Select, SelectItem } from '@carbon/react';
import { useEffect, useState, type FC, type FormEvent } from 'react';

import ConfirmationModal from '@/components/ConfirmationModal';
import SectionTile from '@/components/SectionTile';
import YearSelect from '@/components/YearSelect';
import { useNotification } from '@/context/notification/useNotification';
import { useDefaultOrgUnitIn } from '@/hooks/useDefaultOrgUnit';
import { downloadBlob } from '@/lib/downloadBlob';
import { safeErrorMessage } from '@/lib/errorMessage';
import { FIRST_BILLING_YEAR } from '@/lib/years';
import PageLayout from '@/pages/PageLayout';
import { getBillingRequestSummary, submitBillingApproval } from '@/services/billing';
import { getDistrictOrgUnits, type CodeOption } from '@/services/codeLists';

import { useDistrictScope } from '../useDistrictScope';

const money = new Intl.NumberFormat('en-CA', {
  style: 'currency',
  currency: 'CAD',
  currencyDisplay: 'narrowSymbol',
});

/**
 * FTA690 — Tenure Approval Submission. Submit records a range billing request for a district
 * and year (legacy FTA_690_TENURE_APPROVAL.SUBMIT) and the server bills it there and then — a
 * few seconds — where legacy waited for the nightly 690 batch. On success the FTA411 Approval
 * Summary downloads, which legacy e-mailed to the submitter; on failure the package's message is
 * shown.
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
  // Billing invoices every prepared tenure in the district.
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

  // ConfirmationModal shows a busy button while this runs, and a toast if it throws.
  const onSubmit = async () => {
    notify.clear();
    const r = await submitBillingApproval(orgUnitNo, year);
    const what = `${district}, ${year}`;
    if (r.status !== 'COMPLETE') {
      notify.display({
        kind: 'error',
        title: 'Billing failed.',
        subtitle: `${what}: ${r.messageText || 'no invoices were created.'}`,
        timeout: 10000,
      });
      return;
    }
    const total = r.totalInvoiceValue == null ? '' : `, ${money.format(r.totalInvoiceValue)}`;
    const numbers = r.firstInvoiceNumber
      ? ` (${r.firstInvoiceNumber}${r.lastInvoiceNumber && r.lastInvoiceNumber !== r.firstInvoiceNumber ? ` – ${r.lastInvoiceNumber}` : ''})`
      : '';
    try {
      downloadBlob(
        await getBillingRequestSummary(r.rangeBillRequestId),
        `FTA411-approval-summary-${r.rangeBillRequestId}.pdf`,
      );
      notify.display({
        kind: 'success',
        title: 'Billing complete.',
        subtitle: `${what}: ${r.totalInvoices ?? 0} invoice(s)${numbers}${total}.`,
        timeout: 8000,
      });
    } catch (e) {
      notify.display({
        kind: 'warning',
        title: 'Billing complete, but the approval summary could not be downloaded.',
        subtitle: `${what}: ${r.totalInvoices ?? 0} invoice(s)${numbers}${total}. ${safeErrorMessage(e)}`,
        timeout: 10000,
      });
    }
  };

  return (
    <PageLayout
      title="Tenure Approval Submission"
      subtitle="Run the range billing that invoices a district's prepared billing instructions."
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
        Billing for {district}, {year} runs now. It invoices every tenure whose billing instructions
        are prepared, then downloads the approval summary.
      </ConfirmationModal>
    </PageLayout>
  );
};

export default TenureApproval;
