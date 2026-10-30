import { ArrowLeft, List, Save, Tag } from '@carbon/icons-react';
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
  TextInput,
} from '@carbon/react';
import { useCallback, useState, type FC } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import SectionTile from '@/components/SectionTile';
import Tombstone from '@/components/Tombstone';
import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import PageLayout from '@/pages/PageLayout';
import { canEditHarvest, canManageHarvest } from '@/routes/access';
import { assignMarks, getAssignMarks, type AssignMarksRequest } from '@/services/assign_marks';
import { formatDate } from '@/utils/formatDate';

const date = (v: string | null) => formatDate(v) || '—';

/**
 * FTA908 — Assign Marks to Blocks, offered on a Fort St. John authorization. Each
 * block takes an existing mark or, for senior staff, a new one. Backed by
 * {@code /api/fta/cutting-permits/{cpId}/assign-marks}, where the id is the HVA id.
 */
const AssignMarks: FC = () => {
  const { cpId = '' } = useParams();
  const [searchParams] = useSearchParams();
  const forestFileId = searchParams.get('forestFileId') ?? '';
  const { user } = useAuth();
  const notify = useNotification();
  const readOnly = !canEditHarvest(user);
  const maySpecifyNew = canManageHarvest(user);

  const fetcher = useCallback(() => {
    if (!forestFileId) return Promise.reject(new Error('This link does not name a forest file.'));
    return getAssignMarks(cpId, forestFileId);
  }, [cpId, forestFileId]);
  const { data, loading, error, reload } = useApiResource(fetcher, [cpId, forestFileId]);

  const [allMark, setAllMark] = useState('');
  const [marks, setMarks] = useState<Record<number, string>>({});
  const [newMarks, setNewMarks] = useState<Record<number, string>>({});
  const [saving, setSaving] = useState(false);

  const permitPath = `/harvesting-authority/${encodeURIComponent(cpId)}?forestFileId=${encodeURIComponent(forestFileId)}`;

  const assignments = (data?.blocks ?? [])
    .map((b) => {
      const created = (newMarks[b.cbSkey] ?? '').trim();
      const chosen = marks[b.cbSkey] ?? '';
      return {
        cbSkey: b.cbSkey,
        timberMark: !created && chosen && chosen !== b.timberMark ? chosen : null,
        newTimberMark: created || null,
        revisionCount: b.revisionCount,
      };
    })
    .filter((a) => a.timberMark || a.newTimberMark);

  const submit = async (req: Omit<AssignMarksRequest, 'forestFileId' | 'hvaSkey'>) => {
    if (!data) return;
    setSaving(true);
    try {
      const { updated } = await assignMarks(cpId, {
        ...req,
        forestFileId: data.forestFileId,
        hvaSkey: data.hvaSkey,
      });
      notify.display({
        kind: 'success',
        title: 'Timber marks assigned',
        subtitle: `${updated} block${updated === 1 ? '' : 's'} updated for ${cpId}.`,
        timeout: 5000,
      });
      setAllMark('');
      setMarks({});
      setNewMarks({});
      reload();
    } catch (err) {
      notify.display({
        kind: 'error',
        title: 'Could not assign marks',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 6000,
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <PageLayout
      title={`Assign Marks to Blocks — ${cpId}`}
      backLink={
        <Link to={permitPath} className="back-link">
          <ArrowLeft size={16} /> Back to Harvesting Authority {cpId}
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
              ariaLabel="Harvesting authority summary"
              items={[
                {
                  label: 'File',
                  value: (
                    <Link to={`/tenures/${encodeURIComponent(data.forestFileId)}`}>
                      {data.forestFileId}
                    </Link>
                  ),
                },
                { label: 'Authorization ID', value: data.authorizationId },
                { label: 'Approval Date', value: date(data.approvalDate) },
                { label: 'Expiry Date', value: date(data.expiryDate) },
                { label: 'Licensee', value: data.licensee ?? '—' },
                {
                  label: 'Higher Level Plan Reference',
                  value: data.higherLevelPlanReference ?? '—',
                },
              ]}
            />

            <SectionTile
              title="Assign mark to all blocks"
              icon={Tag}
              actions={
                <Button
                  size="md"
                  kind="tertiary"
                  renderIcon={Tag}
                  disabled={readOnly || saving || !allMark}
                  onClick={() => submit({ assignToAllMark: allMark, assignments: [] })}
                >
                  Assign mark to all
                </Button>
              }
            >
              <div style={{ maxWidth: '20rem' }}>
                <Select
                  id="assign-all-mark"
                  labelText="Timber Mark"
                  value={allMark}
                  disabled={readOnly || saving}
                  onChange={(e) => setAllMark(e.target.value)}
                >
                  <SelectItem value="" text="" />
                  {data.marks.map((m) => (
                    <SelectItem key={m} value={m} text={m} />
                  ))}
                </Select>
              </div>
            </SectionTile>

            <SectionTile
              title="Blocks"
              icon={List}
              actions={
                <Button
                  size="md"
                  renderIcon={Save}
                  disabled={readOnly || saving || assignments.length === 0}
                  onClick={() => submit({ assignToAllMark: null, assignments })}
                >
                  {saving ? 'Saving…' : 'Save assignments'}
                </Button>
              }
            >
              <div className="bordered-table">
                <TableContainer>
                  <Table>
                    <TableHead>
                      <TableRow>
                        <TableHeader>Cut Block</TableHeader>
                        <TableHeader>Timber Mark</TableHeader>
                        <TableHeader>Specify New Timber Mark</TableHeader>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {data.blocks.map((b) => {
                        const created = !!(newMarks[b.cbSkey] ?? '').trim();
                        return (
                          <TableRow key={b.cbSkey}>
                            <TableCell>{b.cutBlockId}</TableCell>
                            <TableCell>
                              <Select
                                id={`assign-mark-${b.cbSkey}`}
                                labelText={`Timber mark for block ${b.cutBlockId}`}
                                hideLabel
                                size="sm"
                                value={marks[b.cbSkey] ?? b.timberMark ?? ''}
                                disabled={readOnly || saving || created}
                                onChange={(e) =>
                                  setMarks((m) => ({ ...m, [b.cbSkey]: e.target.value }))
                                }
                              >
                                <SelectItem value="" text="" />
                                {[
                                  ...new Set([
                                    ...(b.timberMark ? [b.timberMark] : []),
                                    ...data.marks,
                                  ]),
                                ].map((m) => (
                                  <SelectItem key={m} value={m} text={m} />
                                ))}
                              </Select>
                            </TableCell>
                            <TableCell>
                              <TextInput
                                id={`assign-new-${b.cbSkey}`}
                                labelText={`New timber mark for block ${b.cutBlockId}`}
                                hideLabel
                                size="sm"
                                maxLength={6}
                                disabled={readOnly || saving || !maySpecifyNew}
                                value={newMarks[b.cbSkey] ?? ''}
                                onChange={(e) =>
                                  setNewMarks((m) => ({
                                    ...m,
                                    [b.cbSkey]: e.target.value.toUpperCase(),
                                  }))
                                }
                              />
                            </TableCell>
                          </TableRow>
                        );
                      })}
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

export default AssignMarks;
