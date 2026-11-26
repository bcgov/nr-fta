import { Currency, Save, Calendar } from '@carbon/icons-react';
import {
  Button,
  Column,
  Grid,
  InlineNotification,
  Select,
  SelectItem,
  Stack,
  TextInput,
} from '@carbon/react';
import { useEffect, useState, type FC } from 'react';

import AsyncBoundary from '@/components/AsyncBoundary';
import SectionTile from '@/components/SectionTile';
import UserName from '@/components/UserName';
import { useNotification } from '@/context/notification/useNotification';
import PageLayout from '@/pages/PageLayout';
import {
  getRates,
  getRateYears,
  saveRates,
  type RateValue,
  type RatesYear,
} from '@/services/rates_maintenance';

import './RatesMaintenance.scss';

/** fta699Ratefee.jsp's left column; the other six are the licence and permit fees. */
const RATE_CODES = ['GGR', 'GUF', 'GNF', 'HGR', 'HUF', 'HNF'];

const CORRUPT =
  'Corrupt data has been found in Range Billing Rates table. Please contact system support.';

const NUMBER = /^\+?(\d+\.?\d*|\.\d+)$/;

/**
 * Fta699RatefeeForm's chain for one value — the server runs the same checks. First failure only.
 */
const check = (label: string, raw: string): string | undefined => {
  const v = raw.trim();
  if (!v) return `${label} is mandatory.`;
  if (v.includes('-')) return `Negative values are not allowed for ${label}.`;
  if (!NUMBER.test(v)) return `${label} must be numeric.`;
  if (Number(v) > 999.99) return `${label} field must be between 0.00 and 999.99.`;
  const dot = v.indexOf('.');
  if (dot >= 0 && v.length - dot - 1 > 2)
    return `${label} cannot contain more than 2 places of decimal.`;
  return undefined;
};

const shown = (r: RateValue) => (r.rate == null ? '' : r.rate.toFixed(2));

/**
 * FTA699 — Rates & Fees Maintenance, as legacy THE.FTA_699_RATEFEE: pick a calendar year, edit its
 * 12 rates and fees, save. The server writes each value to all of its RANGE_BILL_RATE rows (24 per
 * year), inserting them for a year not yet entered.
 */
const RatesMaintenance: FC = () => {
  const notify = useNotification();
  const [years, setYears] = useState<number[]>([]);
  const [year, setYear] = useState<number | null>(null);
  const [data, setData] = useState<RatesYear | null>(null);
  const [values, setValues] = useState<Record<string, string>>({});
  const [errors, setErrors] = useState<Record<string, string | undefined>>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);
  const [saving, setSaving] = useState(false);

  const load = async (y: number) => {
    setLoading(true);
    setError(undefined);
    setErrors({});
    try {
      const d = await getRates(y);
      setData(d);
      setValues(Object.fromEntries(d.rates.map((r) => [r.code, shown(r)])));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load the rates');
      setData(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    let cancelled = false;
    getRateYears()
      .then((ys) => {
        if (cancelled) return;
        setYears(ys);
        // Legacy opened on the current year.
        const current = new Date().getFullYear();
        const first = ys.includes(current) ? current : (ys[0] ?? null);
        setYear(first);
        if (first != null) void load(first);
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Could not load the years');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const onYear = (v: string) => {
    const y = v ? Number(v) : null;
    setYear(y);
    setData(null);
    if (y != null) void load(y);
  };

  const setValue = (code: string, v: string) => {
    setValues((s) => ({ ...s, [code]: v }));
    setErrors((e) => ({ ...e, [code]: undefined }));
  };

  const onSave = async () => {
    if (!data) return;
    const found: Record<string, string | undefined> = {};
    data.rates.forEach((r) => {
      found[r.code] = check(r.label, values[r.code] ?? '');
    });
    if (Object.values(found).some(Boolean)) {
      setErrors(found);
      return;
    }
    setSaving(true);
    try {
      await saveRates({
        calendarYear: data.calendarYear,
        updateTimestamp: data.updateTimestamp,
        rates: values,
      });
      notify.display({
        kind: 'success',
        title: 'Rates saved',
        subtitle: `Rates and fees for ${data.calendarYear} saved.`,
        timeout: 5000,
      });
      await load(data.calendarYear);
    } catch (err) {
      notify.display({
        kind: 'error',
        title: 'Could not save rates',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  const field = (r: RateValue) => (
    <TextInput
      key={r.code}
      id={`rate-${r.code}`}
      labelText={`${r.label} ($)`}
      value={values[r.code] ?? ''}
      maxLength={6}
      inputMode="decimal"
      autoComplete="off"
      className="rates-maintenance__rate-input"
      invalid={!!errors[r.code]}
      invalidText={errors[r.code]}
      disabled={saving || !!data?.corrupt}
      onChange={(e) => setValue(r.code, e.target.value)}
    />
  );

  const rates = data?.rates ?? [];
  const canSave = data != null && !data.corrupt && data.calendarYear === year && !saving;

  return (
    <PageLayout
      title="Rates & Fees Maintenance"
      subtitle="Review and update the rates and fees applied to range tenure billing."
    >
      <SectionTile title="Calendar year" icon={Calendar}>
        <Grid narrow>
          <Column sm={4} md={3} lg={4}>
            <Select
              id="rates-year"
              labelText="Calendar Year"
              value={year ?? ''}
              disabled={loading || saving}
              onChange={(e) => onYear(e.target.value)}
            >
              <SelectItem value="" text="Choose a year" />
              {years.map((y) => (
                <SelectItem key={y} value={y} text={String(y)} />
              ))}
            </Select>
          </Column>
        </Grid>
      </SectionTile>

      {(data !== null || loading || error) && (
        <SectionTile
          title="Rates & fees"
          icon={Currency}
          actions={
            <Button size="md" renderIcon={Save} onClick={() => void onSave()} disabled={!canSave}>
              {saving ? 'Saving…' : 'Save'}
            </Button>
          }
        >
          <AsyncBoundary
            loading={loading}
            error={error}
            onRetry={year != null ? () => void load(year) : undefined}
            loadingText="Loading rates…"
          >
            {data && (
              <Stack gap={5}>
                {data.corrupt ? (
                  <InlineNotification
                    kind="error"
                    lowContrast
                    hideCloseButton
                    title="Rates cannot be shown"
                    subtitle={CORRUPT}
                  />
                ) : (
                  <p className="rates-maintenance__updated">
                    {data.updateTimestamp ? (
                      <>
                        Last updated on {data.updateTimestamp} by{' '}
                        <UserName userId={data.updateUserid} />
                      </>
                    ) : (
                      `No rates have been entered for ${data.calendarYear}.`
                    )}
                  </p>
                )}
                <Grid narrow>
                  <Column sm={4} md={4} lg={8}>
                    <h3 className="rates-maintenance__group">Rates and fees</h3>
                    <Stack gap={5}>
                      {rates.filter((r) => RATE_CODES.includes(r.code)).map(field)}
                    </Stack>
                  </Column>
                  <Column sm={4} md={4} lg={8}>
                    <h3 className="rates-maintenance__group">Licence and permit fees</h3>
                    <Stack gap={5}>
                      {rates.filter((r) => !RATE_CODES.includes(r.code)).map(field)}
                    </Stack>
                  </Column>
                </Grid>
              </Stack>
            )}
          </AsyncBoundary>
        </SectionTile>
      )}
    </PageLayout>
  );
};

export default RatesMaintenance;
