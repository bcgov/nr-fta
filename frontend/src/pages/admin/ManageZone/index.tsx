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
import { Add } from '@carbon/icons-react';
import { useEffect, useState, type FC } from 'react';
import AsyncBoundary from '@/components/AsyncBoundary';
import { useNotification } from '@/context/notification/useNotification';
import PageLayout from '@/pages/PageLayout';
import { saveRangeZone, searchRangeZones, type ManageZone as RangeZone } from '@/services/manage_zone';

/**
 * FTA631R — Manage Range Zone. Lists range zones (with an add action) backed by
 * the backend {@code GET /api/fta/admin/range-zones} endpoint (which ports
 * THE.FTA_631_RANGE_ZONE).
 */
const ManageZone: FC = () => {
  const notify = useNotification();
  const [rows, setRows] = useState<RangeZone[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);

  const load = async () => {
    setLoading(true);
    setError(undefined);
    try {
      setRows(await searchRangeZones());
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load range zones');
      setRows(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const onAdd = async () => {
    setSaving(true);
    try {
      const { rangeZoneCode, updated } = await saveRangeZone({
        rangeZoneCode: '',
        zoneDescription: null,
        adminForestDistrictNo: null,
        contact: null,
        contactUserId: null,
        contactPhoneNumber: null,
        contactEmailAddress: null,
      });
      notify.display({
        kind: 'success',
        title: 'Zone saved',
        subtitle: `Range zone ${rangeZoneCode || '(new)'} saved (${updated} row(s)).`,
        timeout: 5000,
      });
      await load();
    } catch (e) {
      notify.display({
        kind: 'error',
        title: 'Could not save zone',
        subtitle: e instanceof Error ? e.message : 'Request failed',
        timeout: 6000,
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <PageLayout title="Manage Range Zone">
      <div style={{ marginBottom: '1.5rem' }}>
        <Button kind="tertiary" renderIcon={Add} onClick={() => void onAdd()} disabled={saving}>
          {saving ? 'Saving…' : 'Add zone'}
        </Button>
      </div>
      <AsyncBoundary loading={loading} error={error} onRetry={() => void load()} loadingText="Loading range zones…">
        {rows !== null && (
          <TableContainer title="Range zones" description={`${rows.length} zone(s)`}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableHeader>Zone ID</TableHeader>
                  <TableHeader>Name</TableHeader>
                  <TableHeader>District</TableHeader>
                  <TableHeader>Contact</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((z) => (
                  <TableRow key={z.rangeZoneCode}>
                    <TableCell>{z.rangeZoneCode}</TableCell>
                    <TableCell>{z.zoneDescription ?? '—'}</TableCell>
                    <TableCell>
                      {z.orgUnitName
                        ? `${z.orgUnitCode ?? ''} — ${z.orgUnitName}`.replace(/^ — /, '')
                        : z.orgUnitCode ?? z.adminForestDistrictNo ?? '—'}
                    </TableCell>
                    <TableCell>{z.contact ?? '—'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </AsyncBoundary>
    </PageLayout>
  );
};

export default ManageZone;
