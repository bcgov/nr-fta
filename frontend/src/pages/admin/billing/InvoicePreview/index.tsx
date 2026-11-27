import { Receipt, Search as SearchIcon } from '@carbon/icons-react';
import { Button, TextInput } from '@carbon/react';
import { useEffect, useState, type FC, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import SectionTile from '@/components/SectionTile';
import YearSelect from '@/components/YearSelect';
import { useNotification } from '@/context/notification/useNotification';
import { safeErrorMessage } from '@/lib/errorMessage';
import { currentYear, FIRST_BILLING_YEAR } from '@/lib/years';
import PageLayout from '@/pages/PageLayout';
import { getInvoicePreview, type InvoicePreview as Invoice } from '@/services/billing';

import InvoiceView from '../InvoiceView';
import '../billing.scss';

/**
 * FTA695 — Invoice Preview, as legacy THE.FTA_695_INVOICEPREVIEW: the range
 * invoice a grazing or hay tenure would get for a year, priced from its range
 * provision and that year's rates. Read-only.
 *
 * `?file=&year=` opens a tenure directly.
 */
const InvoicePreview: FC = () => {
  const [params, setParams] = useSearchParams();
  const [file, setFile] = useState(params.get('file') ?? '');
  const [year, setYear] = useState(params.get('year') || currentYear());
  const [invoice, setInvoice] = useState<Invoice | null>(null);
  const [loading, setLoading] = useState(false);
  const notify = useNotification();

  const load = async (f: string, y: string) => {
    setLoading(true);
    try {
      setInvoice(await getInvoicePreview(f, y));
    } catch (e) {
      setInvoice(null);
      // FTA695 showed its errors, one per line, and no invoice.
      notify.display({
        kind: 'error',
        title: 'Could not preview the invoice.',
        subtitle: safeErrorMessage(e),
        timeout: 0,
      });
    } finally {
      setLoading(false);
    }
  };

  // Open the tenure named in the URL, once.
  useEffect(() => {
    const f = params.get('file');
    if (f) void load(f, params.get('year') || currentYear());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const onGo = (e: FormEvent) => {
    e.preventDefault();
    const f = file.trim().toUpperCase();
    setFile(f);
    setParams({ file: f, year }, { replace: true });
    void load(f, year);
  };

  // Legacy's calendarYearRange (no year after this one) is the list's upper end, and the server's.
  const canGo = file.trim() !== '' && /^\d{4}$/.test(year);

  return (
    <PageLayout
      title="Invoice Preview"
      subtitle="See the range invoice a grazing or hay tenure would get for a billing year."
    >
      <SectionTile title="Tenure" icon={SearchIcon}>
        <form className="fsp-search__form" onSubmit={onGo}>
          <div className="fsp-search__field-grid">
            <TextInput
              id="inv-file"
              labelText="Tenure Number"
              placeholder="e.g. RAN076543"
              value={file}
              onChange={(e) => setFile(e.target.value)}
              maxLength={10}
              autoComplete="off"
            />
            <YearSelect
              id="inv-year"
              labelText="Year"
              value={year}
              onChange={setYear}
              first={FIRST_BILLING_YEAR}
            />
          </div>
          <div className="fsp-search__actions">
            <Button type="submit" size="md" renderIcon={SearchIcon} disabled={!canGo || loading}>
              Go
            </Button>
          </div>
        </form>
      </SectionTile>

      {(invoice || loading) && (
        <SectionTile title="Invoice" icon={Receipt}>
          <AsyncBoundary loading={loading} loadingText="Pricing the invoice…">
            {invoice && <InvoiceView invoice={invoice} />}
          </AsyncBoundary>
        </SectionTile>
      )}
    </PageLayout>
  );
};

export default InvoicePreview;
