import { Archive, Search as SearchIcon } from '@carbon/icons-react';
import {
  Button,
  Column,
  Grid,
  Select,
  SelectItem,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TextInput,
} from '@carbon/react';
import { useEffect, useState, type FC, type FormEvent } from 'react';

import AsyncBoundary from '@/components/AsyncBoundary';
import DestructiveModal from '@/components/core/DestructiveModal';
import EmptyState from '@/components/EmptyState/EmptyState';
import SectionTile from '@/components/SectionTile';
import { useNotification } from '@/context/notification/useNotification';
import PageLayout from '@/pages/PageLayout';
import {
  archiveTenures,
  getArchiveTenuresSummary,
  type ArchiveTenuresCriteria,
  type ArchiveTenuresSummary,
} from '@/services/archive_tenures';
import { getDistricts, getRegions, type CodeOption } from '@/services/codeLists';
import './ArchiveTenures.scss';

interface Form {
  regionNo: string;
  districtNo: string;
  expiryYear: string;
}

const EMPTY: Form = { regionNo: '', districtNo: '', expiryYear: '' };

/**
 * FTA640 — Archive Tenures, as legacy THE.FTA_640_ARCHIVETEN: pick a region (and
 * optionally one district) and an expiry year, see how many active grazing and
 * hay range tenures expired that year in each district, then archive all of them
 * (status AR). Archiving is refused until the year is over.
 *
 * The archive uses the criteria of the last count, not whatever is in the form,
 * so it changes exactly what the table shows (legacy's "key changed" check).
 */
const ArchiveTenures: FC = () => {
  const notify = useNotification();
  const [regions, setRegions] = useState<CodeOption[]>([]);
  const [districts, setDistricts] = useState<CodeOption[]>([]);
  const [form, setForm] = useState<Form>(EMPTY);
  const [searched, setSearched] = useState<ArchiveTenuresCriteria | null>(null);
  const [rows, setRows] = useState<ArchiveTenuresSummary[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);
  const [saving, setSaving] = useState(false);
  // Archiving changes every counted tenure and nothing in the UI undoes it.
  const [confirmOpen, setConfirmOpen] = useState(false);

  useEffect(() => {
    let cancelled = false;
    Promise.allSettled([getRegions(), getDistricts()]).then(([r, d]) => {
      if (cancelled) return;
      if (r.status === 'fulfilled') setRegions(r.value);
      if (d.status === 'fulfilled') setDistricts(d.value);
      if (r.status === 'rejected' || d.status === 'rejected') {
        notify.display({
          kind: 'error',
          title: 'Could not load the region and district lists',
          subtitle: 'Reload the page to try again.',
          timeout: 6000,
        });
      }
    });
    return () => {
      cancelled = true;
    };
    // Load once; notify is stable for the page's life.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const set = (f: keyof Form) => (v: string) => setForm((s) => ({ ...s, [f]: v }));

  const count = async (c: ArchiveTenuresCriteria) => {
    setLoading(true);
    setError(undefined);
    try {
      setRows(await getArchiveTenuresSummary(c));
      setSearched(c);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not count the tenures');
      setRows(null);
      setSearched(null);
    } finally {
      setLoading(false);
    }
  };

  const onGo = (e: FormEvent) => {
    e.preventDefault();
    void count({
      regionNo: form.regionNo,
      districtNo: form.districtNo || null,
      expiryYear: Number(form.expiryYear),
    });
  };

  const onArchive = async () => {
    if (!searched) return;
    setSaving(true);
    try {
      const { updated } = await archiveTenures(searched);
      notify.display({
        kind: 'success',
        title: 'Tenures archived',
        subtitle: `${updated} tenure(s) archived.`,
        timeout: 5000,
      });
      // Re-count, so the table shows they are gone.
      await count(searched);
    } catch (err) {
      notify.display({
        kind: 'error',
        title: 'Could not archive tenures',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 6000,
      });
    } finally {
      setSaving(false);
      setConfirmOpen(false);
    }
  };

  const grazing = rows?.reduce((n, r) => n + r.grazingCount, 0) ?? 0;
  const hay = rows?.reduce((n, r) => n + r.hayCount, 0) ?? 0;
  const total = grazing + hay;
  const yearIsOver = searched != null && searched.expiryYear < new Date().getFullYear();
  const canGo = form.regionNo !== '' && /^\d{4}$/.test(form.expiryYear);

  return (
    <PageLayout
      title="Archive Tenures"
      subtitle="Archive the active grazing and hay tenures whose term expired in a given year."
    >
      <SectionTile title="Criteria" icon={SearchIcon}>
        <form onSubmit={onGo}>
          <Grid narrow>
            <Column sm={4} md={3} lg={5}>
              <Select
                id="arc-region"
                labelText="Region"
                value={form.regionNo}
                onChange={(e) => set('regionNo')(e.target.value)}
              >
                <SelectItem value="" text="Choose a region" />
                {regions.map((r) => (
                  <SelectItem key={r.code} value={r.code} text={r.description} />
                ))}
              </Select>
            </Column>
            <Column sm={4} md={3} lg={5}>
              <Select
                id="arc-district"
                labelText="District (optional)"
                value={form.districtNo}
                onChange={(e) => set('districtNo')(e.target.value)}
              >
                <SelectItem value="" text="All districts in the region" />
                {districts.map((d) => (
                  <SelectItem key={d.code} value={d.code} text={d.description} />
                ))}
              </Select>
            </Column>
            <Column sm={4} md={2} lg={3}>
              <TextInput
                id="arc-year"
                labelText="Expiry Year"
                placeholder="e.g. 2025"
                value={form.expiryYear}
                onChange={(e) => set('expiryYear')(e.target.value.replace(/\D/g, ''))}
                maxLength={4}
                inputMode="numeric"
                autoComplete="off"
              />
            </Column>
            <Column sm={4} md={8} lg={3} className="archive-tenures__go">
              <Button type="submit" size="md" renderIcon={SearchIcon} disabled={!canGo || loading}>
                Go
              </Button>
            </Column>
          </Grid>
        </form>
      </SectionTile>

      {(rows !== null || loading || error) && (
        <SectionTile
          title="Tenures to archive"
          icon={Archive}
          actions={
            <Button
              size="md"
              kind="danger"
              renderIcon={Archive}
              disabled={!yearIsOver || total === 0 || saving || loading}
              onClick={() => setConfirmOpen(true)}
            >
              {saving ? 'Archiving…' : `Archive ${total} tenure(s)`}
            </Button>
          }
        >
          <AsyncBoundary
            loading={loading}
            error={error}
            onRetry={searched ? () => void count(searched) : undefined}
            loadingText="Counting tenures…"
          >
            {rows && rows.length === 0 ? (
              <EmptyState
                title="No tenures to archive"
                body="No active grazing or hay tenures expired in that year for these criteria."
              />
            ) : (
              rows && (
                <div className="bordered-table">
                  <TableContainer>
                    <Table>
                      <TableHead>
                        <TableRow>
                          <TableHeader>District</TableHeader>
                          <TableHeader>Grazing</TableHeader>
                          <TableHeader>Hay</TableHeader>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {rows.map((r) => (
                          <TableRow key={r.district}>
                            <TableCell>{r.district}</TableCell>
                            <TableCell>{r.grazingCount}</TableCell>
                            <TableCell>{r.hayCount}</TableCell>
                          </TableRow>
                        ))}
                        <TableRow>
                          <TableCell>
                            <strong>Total</strong>
                          </TableCell>
                          <TableCell>
                            <strong>{grazing}</strong>
                          </TableCell>
                          <TableCell>
                            <strong>{hay}</strong>
                          </TableCell>
                        </TableRow>
                      </TableBody>
                    </Table>
                  </TableContainer>
                </div>
              )
            )}
          </AsyncBoundary>
        </SectionTile>
      )}

      <DestructiveModal
        open={confirmOpen}
        title="Archive tenures?"
        message={`All ${total} active grazing and hay tenure(s) counted above will be set to Archived. This is not undone from this screen.`}
        confirmButtonText="Archive"
        loading={saving}
        onConfirm={() => void onArchive()}
        onCancel={() => setConfirmOpen(false)}
      />
    </PageLayout>
  );
};

export default ArchiveTenures;
