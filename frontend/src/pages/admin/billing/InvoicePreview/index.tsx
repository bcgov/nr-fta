import { Receipt, Search as SearchIcon } from '@carbon/icons-react';
import { Button, Column, Grid, TextInput } from '@carbon/react';
import { useEffect, useState, type FC, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import SectionTile from '@/components/SectionTile';
import PageLayout from '@/pages/PageLayout';
import { getInvoicePreview, type InvoicePreview as Invoice } from '@/services/billing';

import InvoiceView from '../InvoiceView';
import '../billing.scss';

const thisYear = () => String(new Date().getFullYear());

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
  const [year, setYear] = useState(params.get('year') || thisYear());
  const [invoice, setInvoice] = useState<Invoice | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);

  const load = async (f: string, y: string) => {
    setLoading(true);
    setError(undefined);
    try {
      setInvoice(await getInvoicePreview(f, y));
    } catch (e) {
      setInvoice(null);
      setError(e instanceof Error ? e.message : 'Could not preview the invoice');
    } finally {
      setLoading(false);
    }
  };

  // Open the tenure named in the URL, once.
  useEffect(() => {
    const f = params.get('file');
    if (f) void load(f, params.get('year') || thisYear());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const onGo = (e: FormEvent) => {
    e.preventDefault();
    const f = file.trim().toUpperCase();
    setFile(f);
    setParams({ file: f, year }, { replace: true });
    void load(f, year);
  };

  const canGo = file.trim() !== '' && /^\d{4}$/.test(year);

  return (
    <PageLayout
      title="Invoice Preview"
      subtitle="See the range invoice a grazing or hay tenure would get for a billing year."
    >
      <SectionTile title="Tenure" icon={SearchIcon}>
        <form onSubmit={onGo}>
          <Grid narrow>
            <Column sm={4} md={3} lg={5}>
              <TextInput
                id="inv-file"
                labelText="Tenure Number"
                placeholder="e.g. RAN076543"
                value={file}
                onChange={(e) => setFile(e.target.value)}
                maxLength={10}
                autoComplete="off"
              />
            </Column>
            <Column sm={4} md={2} lg={3}>
              <TextInput
                id="inv-year"
                labelText="Year"
                value={year}
                onChange={(e) => setYear(e.target.value.replace(/\D/g, ''))}
                maxLength={4}
                inputMode="numeric"
                autoComplete="off"
              />
            </Column>
            <Column sm={4} md={3} lg={3} className="billing__go">
              <Button type="submit" size="md" renderIcon={SearchIcon} disabled={!canGo || loading}>
                Go
              </Button>
            </Column>
          </Grid>
        </form>
      </SectionTile>

      {(invoice || loading || error) && (
        <SectionTile title="Invoice" icon={Receipt}>
          <AsyncBoundary loading={loading} error={error} loadingText="Pricing the invoice…">
            {invoice && <InvoiceView invoice={invoice} />}
          </AsyncBoundary>
        </SectionTile>
      )}
    </PageLayout>
  );
};

export default InvoicePreview;
