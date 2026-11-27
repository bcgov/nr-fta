import { Archive, Search as SearchIcon } from '@carbon/icons-react';
import {
  Button,
  Select,
  SelectItem,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { useEffect, useState, type FC, type FormEvent } from 'react';

import AsyncBoundary from '@/components/AsyncBoundary';
import DestructiveModal from '@/components/core/DestructiveModal';
import EmptyState from '@/components/EmptyState/EmptyState';
import SectionTile from '@/components/SectionTile';
import YearSelect from '@/components/YearSelect';
import { useNotification } from '@/context/notification/useNotification';
import { useDefaultOrgUnit } from '@/hooks/useDefaultOrgUnit';
import { safeErrorMessage } from '@/lib/errorMessage';
import { FIRST_ARCHIVE_YEAR } from '@/lib/years';
import PageLayout from '@/pages/PageLayout';
import {
  archiveTenures,
  getArchiveTenuresSummary,
  type ArchiveTenuresCriteria,
  type ArchiveTenuresSummary,
} from '@/services/archive_tenures';
import {
  getDistrictRegions,
  getThreeLetterDistricts,
  getRegions,
  type CodeOption,
} from '@/services/codeLists';

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
  // Each district's region, so the District list offers only the chosen region's districts.
  const [districtRegions, setDistrictRegions] = useState<Record<string, string>>({});
  const [form, setForm] = useState<Form>(EMPTY);
  const [searched, setSearched] = useState<ArchiveTenuresCriteria | null>(null);
  const [rows, setRows] = useState<ArchiveTenuresSummary[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  // Archiving changes every counted tenure and nothing in the UI undoes it.
  const [confirmOpen, setConfirmOpen] = useState(false);

  useEffect(() => {
    let cancelled = false;
    Promise.allSettled([getRegions(), getThreeLetterDistricts(), getDistrictRegions()]).then(
      ([r, d, dr]) => {
        if (cancelled) return;
        if (r.status === 'fulfilled') setRegions(r.value);
        if (d.status === 'fulfilled') setDistricts(d.value);
        if (dr.status === 'fulfilled') setDistrictRegions(dr.value);
        if (r.status === 'rejected' || d.status === 'rejected' || dr.status === 'rejected') {
          notify.display({
            kind: 'error',
            title: 'Could not load the region and district lists.',
            subtitle: 'Reload the page to try again.',
            timeout: 6000,
          });
        }
      },
    );
    return () => {
      cancelled = true;
    };
    // Load once; notify is stable for the page's life.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Legacy defaulted Region to a region user's org unit, and Region and District to a district
  // user's region and org unit (Org Unit Maintenance).
  const userDefault = useDefaultOrgUnit();
  useEffect(() => {
    const no = userDefault?.orgUnitNo;
    if (!no) return;
    const level = userDefault.orgLevelCode;
    const regionNo = level === 'R' ? no : level === 'D' ? userDefault.rollupRegionNo : null;
    const districtNo = level === 'D' && districts.some((o) => o.code === no) ? no : '';
    if (!regionNo || !regions.some((o) => o.code === regionNo)) return;
    if (level === 'D' && !districtNo) return;
    setForm((f) => (f.regionNo || f.districtNo ? f : { ...f, regionNo, districtNo }));
  }, [userDefault, regions, districts]);

  const set = (f: keyof Form) => (v: string) => setForm((s) => ({ ...s, [f]: v }));

  // The region drives the District list: only that region's districts, none before a region is
  // chosen. Changing the region drops a district that isn't in it.
  const regionDistricts = form.regionNo
    ? districts.filter((d) => districtRegions[d.code] === form.regionNo)
    : [];
  const setRegion = (regionNo: string) =>
    setForm((s) => ({
      ...s,
      regionNo,
      districtNo: s.districtNo && districtRegions[s.districtNo] === regionNo ? s.districtNo : '',
    }));

  const count = async (c: ArchiveTenuresCriteria) => {
    setLoading(true);
    try {
      setRows(await getArchiveTenuresSummary(c));
      setSearched(c);
    } catch (e) {
      notify.display({
        kind: 'error',
        title: 'Could not count the tenures.',
        subtitle: safeErrorMessage(e),
        timeout: 0,
      });
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
        title: 'Tenures archived.',
        subtitle: `${updated} tenure(s) archived.`,
        timeout: 5000,
      });
      // Re-count, so the table shows they are gone.
      await count(searched);
    } catch (err) {
      notify.display({
        kind: 'error',
        title: 'Could not archive tenures.',
        subtitle: safeErrorMessage(err),
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
  const canGo = form.regionNo !== '' && /^[1-9]\d{3}$/.test(form.expiryYear);

  return (
    <PageLayout
      title="Archive Tenures"
      subtitle="Archive the active grazing and hay tenures whose term expired in a given year."
    >
      <SectionTile title="Criteria" icon={SearchIcon}>
        <form className="fsp-search__form" onSubmit={onGo}>
          <div className="fsp-search__field-grid">
            <Select
              id="arc-region"
              labelText="Region"
              value={form.regionNo}
              onChange={(e) => setRegion(e.target.value)}
            >
              <SelectItem value="" text="Choose a region" />
              {regions.map((r) => (
                <SelectItem key={r.code} value={r.code} text={r.description} />
              ))}
            </Select>
            <Select
              id="arc-district"
              labelText="District (optional)"
              value={form.districtNo}
              disabled={!form.regionNo}
              onChange={(e) => set('districtNo')(e.target.value)}
            >
              <SelectItem value="" text="All districts in the region" />
              {regionDistricts.map((d) => (
                <SelectItem key={d.code} value={d.code} text={d.description} />
              ))}
            </Select>
            <YearSelect
              id="arc-year"
              labelText="Expiry Year"
              value={form.expiryYear}
              onChange={set('expiryYear')}
              first={FIRST_ARCHIVE_YEAR}
              placeholder="Choose a year"
            />
          </div>
          <div className="fsp-search__actions">
            <Button type="submit" size="md" renderIcon={SearchIcon} disabled={!canGo || loading}>
              Go
            </Button>
          </div>
        </form>
      </SectionTile>

      {(rows !== null || loading) && (
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
          <AsyncBoundary loading={loading} loadingText="Counting tenures…">
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
                        <TableRow className="table-total-row">
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
