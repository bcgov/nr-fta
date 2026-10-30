import { ArrowLeft, List, Pause } from '@carbon/icons-react';
import {
  Button,
  Checkbox,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { useCallback, useState, type FC } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import SectionTile from '@/components/SectionTile';
import Tombstone from '@/components/Tombstone';
import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import PageLayout from '@/pages/PageLayout';
import { canManageHarvest } from '@/routes/access';
import { getPermitSuspension, suspendBlocks } from '@/services/suspend_blocks';
import { formatDate } from '@/utils/formatDate';

import SuspensionFields from './SuspensionFields';
import { EMPTY_SUSPENSION, suspensionComplete, type SuspensionForm } from './suspensionForm';

const date = (v: string | null) => formatDate(v) || '—';

/**
 * FTA912 — Suspend Multiple Blocks within a permit. A CP id is unique only within
 * its tenure, so the page needs the tenure in its link. Backed by
 * {@code /api/fta/cutting-permits/{cpId}/suspend-blocks}.
 */
const SuspendBlocks: FC = () => {
  const { cpId = '' } = useParams();
  const [searchParams] = useSearchParams();
  const forestFileId = searchParams.get('forestFileId') ?? '';
  const { user } = useAuth();
  const notify = useNotification();
  const navigate = useNavigate();
  const readOnly = !canManageHarvest(user);

  const fetcher = useCallback(() => {
    if (!forestFileId) return Promise.reject(new Error('This link does not name a forest file.'));
    return getPermitSuspension(cpId, forestFileId);
  }, [cpId, forestFileId]);
  const { data, loading, error, reload } = useApiResource(fetcher, [cpId, forestFileId]);

  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [suspendAll, setSuspendAll] = useState(false);
  const [form, setForm] = useState<SuspensionForm>(EMPTY_SUSPENSION);
  const [saving, setSaving] = useState(false);

  const permitPath = `/harvesting-authority/${encodeURIComponent(cpId)}?forestFileId=${encodeURIComponent(forestFileId)}`;

  const toggle = (cbSkey: number) =>
    setSelected((s) => {
      const next = new Set(s);
      if (next.has(cbSkey)) {
        next.delete(cbSkey);
      } else {
        next.add(cbSkey);
      }
      return next;
    });

  const onSubmit = async () => {
    setSaving(true);
    try {
      const { suspended } = await suspendBlocks(cpId, {
        forestFileId,
        cbSkeys: suspendAll ? [] : [...selected],
        suspendAllBlocks: suspendAll,
        partitionCode: form.partitionCode,
        suspOrderNumber: form.orderNumber,
        suspStartDate: form.startDate,
        suspEndDate: form.endDate,
      });
      notify.display({
        kind: 'success',
        title: 'Blocks suspended',
        subtitle: `${suspended} block${suspended === 1 ? '' : 's'} on ${cpId} suspended.`,
        timeout: 5000,
      });
      navigate(permitPath);
    } catch (err) {
      notify.display({
        kind: 'error',
        title: 'Could not suspend blocks',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 6000,
      });
    } finally {
      setSaving(false);
    }
  };

  const hasTargets = suspendAll || selected.size > 0;

  return (
    <PageLayout
      title={`Suspend Blocks — ${cpId}`}
      backLink={
        <Link to={permitPath} className="back-link">
          <ArrowLeft size={16} /> Back to Cutting Permit {cpId}
        </Link>
      }
    >
      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Loading cut blocks…"
      >
        {data && (
          <>
            <Tombstone
              ariaLabel="Cutting permit summary"
              items={[
                {
                  label: 'File',
                  value: (
                    <Link to={`/tenures/${encodeURIComponent(data.forestFileId)}`}>
                      {data.forestFileId}
                    </Link>
                  ),
                },
                { label: 'Cutting Permit', value: data.cuttingPermitId },
                { label: 'Effective', value: date(data.effectiveDate) },
                { label: 'Expiry', value: date(data.expiryDate) },
                { label: 'Extension', value: date(data.extendDate) },
              ]}
            />

            <SectionTile
              title="Suspension details"
              icon={Pause}
              actions={
                <Button
                  size="md"
                  kind="danger"
                  renderIcon={Pause}
                  disabled={readOnly || saving || !hasTargets || !suspensionComplete(form)}
                  onClick={onSubmit}
                >
                  {saving ? 'Suspending…' : 'Suspend blocks'}
                </Button>
              }
            >
              <SuspensionFields
                idPrefix="susp"
                value={form}
                onChange={setForm}
                partitionCodes={data.partitionCodes}
                disabled={readOnly || saving}
              />
            </SectionTile>

            <SectionTile title="Blocks" icon={List}>
              <Checkbox
                id="susp-all"
                labelText="Suspend all eligible blocks"
                checked={suspendAll}
                disabled={readOnly || saving}
                onChange={(_, { checked }) => setSuspendAll(checked)}
              />
              <div className="bordered-table">
                <TableContainer>
                  <Table>
                    <TableHead>
                      <TableRow>
                        <TableHeader>Suspend</TableHeader>
                        <TableHeader>Cut Block</TableHeader>
                        <TableHeader>Status</TableHeader>
                        <TableHeader>Planned Harvest Date</TableHeader>
                        <TableHeader>Gross (ha)</TableHeader>
                        <TableHeader>Net (ha)</TableHeader>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {data.blocks.map((b) => (
                        <TableRow key={b.cbSkey}>
                          <TableCell>
                            <Checkbox
                              id={`susp-${b.cbSkey}`}
                              labelText={`Suspend block ${b.cutBlockId}`}
                              hideLabel
                              checked={suspendAll ? b.selectable : selected.has(b.cbSkey)}
                              disabled={readOnly || saving || suspendAll || !b.selectable}
                              onChange={() => toggle(b.cbSkey)}
                            />
                          </TableCell>
                          <TableCell>{b.cutBlockId}</TableCell>
                          <TableCell>
                            {b.statusDescription
                              ? `${b.statusCode} - ${b.statusDescription}`
                              : b.statusCode}
                          </TableCell>
                          <TableCell>{date(b.plannedHarvestDate)}</TableCell>
                          <TableCell>{b.plannedGrossArea ?? '—'}</TableCell>
                          <TableCell>{b.plannedNetArea ?? '—'}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </div>
            </SectionTile>
          </>
        )}
      </AsyncBoundary>
    </PageLayout>
  );
};

export default SuspendBlocks;
