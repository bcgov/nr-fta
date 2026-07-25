import {
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { Save } from '@carbon/icons-react';
import { useCallback, useEffect, useState, type FC } from 'react';
import AsyncBoundary from '@/components/AsyncBoundary';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import PageLayout from '@/pages/PageLayout';
import { getRates, saveRates, type RatesMaintenanceRate } from '@/services/rates_maintenance';

/**
 * FTA699 — Rates & Fees Maintenance. Editable rate table backed by the backend
 * {@code GET /api/fta/admin/rates} endpoint (which ports THE.FTA_699_RATEFEE).
 * Save raises a notification; there is no write endpoint yet.
 */
const RatesMaintenance: FC = () => {
  const notify = useNotification();
  const fetcher = useCallback(() => getRates(), []);
  const { data, loading, error, reload } = useApiResource<RatesMaintenanceRate[]>(fetcher, []);

  const [rates, setRates] = useState<RatesMaintenanceRate[]>([]);
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    if (data) setRates(data.map((r) => ({ ...r })));
  }, [data]);

  const setRate = (id: number | null, value: string) =>
    setRates((rs) =>
      rs.map((r) => (r.rangeBillRateId === id ? { ...r, rangeRate: Number(value) || 0 } : r)),
    );

  const onSave = async () => {
    setSaving(true);
    try {
      const { updated } = await saveRates({
        calendarYear: rates[0]?.calendarYear ?? null,
        rates: rates.map((r) => ({
          rangeBillRateId: r.rangeBillRateId,
          rangeFileTypeCode: r.rangeFileTypeCode,
          rangeRateTypeCode: r.rangeRateTypeCode,
          revenueClassnCode: r.revenueClassnCode,
          rangeRate: r.rangeRate,
          rngTenrRateDesc: r.rngTenrRateDesc,
        })),
      });
      notify.display({
        kind: 'success',
        title: 'Rates saved',
        subtitle: `${updated} rate(s) updated.`,
        timeout: 5000,
      });
      reload();
    } catch (err) {
      notify.display({
        kind: 'error',
        title: 'Could not save rates',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 6000,
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <PageLayout title="Rates & Fees Maintenance">
      <AsyncBoundary loading={loading} error={error} onRetry={reload} loadingText="Loading rates…">
        <TableContainer title="Rates & fees" description={`${rates.length} rate(s)`}>
          <Table>
            <TableHead>
              <TableRow>
                <TableHeader>Rate Type</TableHeader>
                <TableHeader>File Type</TableHeader>
                <TableHeader>Revenue Class</TableHeader>
                <TableHeader>Rate</TableHeader>
                <TableHeader>Calendar Year</TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {rates.map((r) => (
                <TableRow key={r.rangeBillRateId ?? `${r.rangeRateTypeCode}-${r.rangeFileTypeCode}`}>
                  <TableCell>{r.rangeRateTypeCode ?? '—'}</TableCell>
                  <TableCell>{r.rangeFileTypeCode ?? '—'}</TableCell>
                  <TableCell>{r.revenueClassnCode ?? '—'}</TableCell>
                  <TableCell>
                    <input
                      type="number"
                      step="0.01"
                      value={r.rangeRate ?? 0}
                      onChange={(e) => setRate(r.rangeBillRateId, e.target.value)}
                      style={{ width: '7rem' }}
                      aria-label={`Rate for ${r.rangeRateTypeCode ?? ''}`}
                    />
                  </TableCell>
                  <TableCell>{r.calendarYear ?? '—'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
        <div style={{ marginTop: '1.5rem' }}>
          <Button renderIcon={Save} onClick={onSave} disabled={saving}>
            {saving ? 'Saving…' : 'Save rates'}
          </Button>
        </div>
      </AsyncBoundary>
    </PageLayout>
  );
};

export default RatesMaintenance;
