import { ArrowLeft, Pause, Save, TrashCan } from '@carbon/icons-react';
import { Button, Checkbox, Select, SelectItem } from '@carbon/react';
import { useCallback, useEffect, useState, type FC } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import ConfirmationModal from '@/components/ConfirmationModal';
import SectionTile from '@/components/SectionTile';
import Tombstone from '@/components/Tombstone';
import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import PageLayout from '@/pages/PageLayout';
import { canManageHarvest } from '@/routes/access';
import {
  deleteSuspension,
  getBlockSuspension,
  suspendBlock,
  updateSuspension,
} from '@/services/suspend_blocks';
import { formatDate } from '@/utils/formatDate';

import SuspensionFields from '../SuspendBlocks/SuspensionFields';
import {
  EMPTY_SUSPENSION,
  suspensionComplete,
  type SuspensionForm,
} from '../SuspendBlocks/suspensionForm';

const date = (v: string | null) => formatDate(v) || '—';

/**
 * FTA914 — one cut block's suspension: suspend it, or save, end or delete its
 * suspension. The route's id is the block's CB_SKEY, as a block's label is unique
 * only within its permit. Backed by {@code /api/fta/cut-blocks/{cbSkey}/suspensions}.
 */
const BlockSuspension: FC = () => {
  const { blockId = '' } = useParams();
  const { user } = useAuth();
  const notify = useNotification();
  const navigate = useNavigate();

  const fetcher = useCallback(() => getBlockSuspension(blockId), [blockId]);
  const { data, loading, error, reload } = useApiResource(fetcher, [blockId]);

  const [form, setForm] = useState<SuspensionForm>(EMPTY_SUSPENSION);
  const [postStatus, setPostStatus] = useState('');
  const [updateAll, setUpdateAll] = useState(false);
  const [saving, setSaving] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const suspension = data?.suspension ?? null;
  useEffect(() => {
    setForm(
      suspension
        ? {
            partitionCode: suspension.partitionCode,
            orderNumber: suspension.orderNumber,
            startDate: suspension.startDate ?? '',
            endDate: suspension.endDate ?? '',
          }
        : EMPTY_SUSPENSION,
    );
  }, [suspension]);

  const mayManage = canManageHarvest(user);
  const editable = !!data && mayManage && (suspension ? data.canEdit : data.canSuspend);

  const blockPath = data
    ? `/cut-block/${encodeURIComponent(data.cutBlockId)}?forestFileId=${encodeURIComponent(
        data.forestFileId,
      )}${data.cuttingPermitId ? `&cuttingPermitId=${encodeURIComponent(data.cuttingPermitId)}` : ''}`
    : '/search/cut-block';

  const run = async (title: string, action: () => Promise<void>, stay: boolean) => {
    setSaving(true);
    try {
      await action();
      notify.display({ kind: 'success', title, timeout: 5000 });
      if (stay) reload();
      else navigate(blockPath);
    } catch (err) {
      notify.display({
        kind: 'error',
        title: 'Could not change the suspension',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 6000,
      });
    } finally {
      setSaving(false);
    }
  };

  const update = (action: 'SAVE' | 'END') => {
    if (!data || !suspension) return;
    return run(
      action === 'END' ? 'Suspension ended' : 'Suspension saved',
      () =>
        updateSuspension(data.cbSkey, suspension.suspensionId, {
          ...form,
          action,
          postStatusCode: action === 'END' ? postStatus : null,
          updateAllBlocks: updateAll,
          revisionCount: suspension.revisionCount,
        }),
      action === 'SAVE',
    );
  };

  const actions = !data ? undefined : suspension ? (
    <>
      <Button
        size="md"
        kind="tertiary"
        renderIcon={Save}
        disabled={!editable || saving || !suspensionComplete(form)}
        onClick={() => update('SAVE')}
      >
        Save suspension
      </Button>
      <Button
        size="md"
        kind="tertiary"
        renderIcon={Pause}
        disabled={!editable || saving || !suspensionComplete(form) || !postStatus}
        onClick={() => update('END')}
      >
        End suspension
      </Button>
      <Button
        size="md"
        kind="danger"
        renderIcon={TrashCan}
        disabled={!editable || saving}
        onClick={() => setConfirmDelete(true)}
      >
        Delete suspension
      </Button>
    </>
  ) : (
    <Button
      size="md"
      kind="danger"
      renderIcon={Pause}
      disabled={!editable || saving || !suspensionComplete(form)}
      onClick={() => run('Block suspended', () => suspendBlock(data.cbSkey, form), false)}
    >
      Suspend block
    </Button>
  );

  return (
    <PageLayout
      title={data ? `Suspension — Cut Block ${data.cutBlockId}` : 'Cut Block Suspension'}
      backLink={
        <Link to={blockPath} className="back-link">
          <ArrowLeft size={16} /> Back to Cut Block
        </Link>
      }
    >
      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Loading suspension…"
      >
        {data && (
          <>
            <Tombstone
              ariaLabel="Cut block summary"
              items={[
                {
                  label: 'File',
                  value: (
                    <Link to={`/tenures/${encodeURIComponent(data.forestFileId)}`}>
                      {data.forestFileId}
                    </Link>
                  ),
                },
                { label: 'Cutting Permit', value: data.cuttingPermitId ?? '—' },
                { label: 'Cut Block', value: data.cutBlockId },
                {
                  label: 'Status',
                  value: data.statusDescription
                    ? `${data.statusCode} - ${data.statusDescription}`
                    : data.statusCode,
                },
                {
                  label: 'Pre-suspension Status',
                  value: suspension?.preStatusDescription ?? suspension?.preStatusCode ?? '—',
                },
                { label: 'CP Issue', value: date(data.permitIssueDate) },
                { label: 'CP Expiry', value: date(data.permitExpiryDate) },
                { label: 'CP Extension', value: date(data.permitExtendDate) },
                { label: 'Months Remaining', value: suspension?.monthsRemaining ?? '—' },
              ]}
            />

            <SectionTile title="Suspension details" icon={Pause} actions={actions}>
              <SuspensionFields
                idPrefix="block-susp"
                value={form}
                onChange={setForm}
                partitionCodes={data.partitionCodes}
                disabled={!editable || saving}
              />
              {suspension && (
                <div style={{ display: 'grid', gap: '1rem', maxWidth: '40rem', marginTop: '1rem' }}>
                  <Select
                    id="block-susp-post"
                    labelText="Post-Suspension Cut Block Status"
                    value={postStatus}
                    disabled={!editable || saving}
                    onChange={(e) => setPostStatus(e.target.value)}
                  >
                    <SelectItem value="" text="" />
                    {data.postStatusCodes.map((c) => (
                      <SelectItem
                        key={c.code}
                        value={c.code}
                        text={`${c.code} - ${c.description}`}
                      />
                    ))}
                  </Select>
                  <Checkbox
                    id="block-susp-all"
                    labelText={`Change all blocks within tenure ${data.forestFileId}, CP ${
                      data.cuttingPermitId ?? '—'
                    } and order ${suspension.orderNumber}`}
                    checked={updateAll}
                    disabled={!editable || saving}
                    onChange={(_, { checked }) => setUpdateAll(checked)}
                  />
                </div>
              )}
            </SectionTile>

            {suspension && (
              <ConfirmationModal
                open={confirmDelete}
                onClose={() => setConfirmDelete(false)}
                heading="Delete suspension"
                confirmLabel="Delete"
                danger
                onConfirm={() =>
                  run(
                    'Suspension deleted',
                    () =>
                      deleteSuspension(data.cbSkey, suspension.suspensionId, {
                        updateAllBlocks: updateAll,
                        revisionCount: suspension.revisionCount,
                      }),
                    false,
                  )
                }
              >
                This restores the block to the status it had before it was suspended.
              </ConfirmationModal>
            )}
          </>
        )}
      </AsyncBoundary>
    </PageLayout>
  );
};

export default BlockSuspension;
