import { ArrowLeft, Report, Save } from '@carbon/icons-react';
import { Button, TextArea, TextInput } from '@carbon/react';
import { useState, type FC } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import DetailTile from '@/components/DetailTile';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import PageLayout from '@/pages/PageLayout';
import { blockQuery, getCutblockDetail, relabelCutBlock } from '@/services/cutblock_detail';

const MAX_REASON = 200;

/**
 * FTA231 — re-label a cut block, with a mandatory reason. Reached from the cut block detail's
 * Re-label; {@code FTA_231_CUTBLK_RELABEL} does the work and refuses a block other systems
 * reference.
 */
const CutBlockRelabel: FC = () => {
  const { blockId = '' } = useParams();
  const [searchParams] = useSearchParams();
  const forestFileId = searchParams.get('forestFileId') ?? '';
  const cuttingPermitId = searchParams.get('cuttingPermitId') ?? undefined;
  const navigate = useNavigate();
  const { display } = useNotification();
  const { data, loading, error, reload } = useApiResource(
    () => getCutblockDetail(blockId, { forestFileId, cuttingPermitId }),
    [blockId, forestFileId, cuttingPermitId],
  );
  const [label, setLabel] = useState('');
  const [reason, setReason] = useState('');
  const [saving, setSaving] = useState(false);

  const query = blockQuery(forestFileId, cuttingPermitId);
  const detailPath = `/cut-block/${encodeURIComponent(blockId)}${query}`;
  const canSubmit =
    !!data?.rules.canRelabel && label.trim() !== '' && reason.trim() !== '' && !saving;

  const submit = async () => {
    if (!data) return;
    setSaving(true);
    try {
      const saved = await relabelCutBlock(data.cbSkey, {
        newCutBlockId: label.trim(),
        reason: reason.trim(),
        revisionCount: data.cbRevisionCount,
      });
      display({
        kind: 'success',
        title: `Cut block re-labelled ${saved.cutBlockId}`,
        subtitle:
          'Re-label successful. Please create new Exhibit A using TUT and submit to FTA. Provide a copy to the licensee.',
        timeout: 9000,
      });
      navigate(`/cut-block/${encodeURIComponent(saved.cutBlockId)}${query}`);
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not re-label the cut block',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <PageLayout
      title={`Re-label Cut Block ${blockId}`}
      actions={
        <Button size="md" renderIcon={Save} disabled={!canSubmit} onClick={submit}>
          {saving ? 'Saving…' : 'Save'}
        </Button>
      }
      backLink={
        <Link to={detailPath} className="back-link">
          <ArrowLeft size={16} /> Back to Cut Block {blockId}
        </Link>
      }
    >
      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Loading cut block…"
      >
        {data && (
          <div style={{ display: 'grid', gap: '1rem', maxWidth: '40rem' }}>
            <DetailTile
              title="Cut block"
              icon={Report}
              fields={[
                { label: 'Forest File', value: data.forestFileId ?? '—' },
                { label: 'Cutting Permit', value: data.cuttingPermitId ?? '—' },
                { label: 'Timber Mark', value: data.timberMark ?? '—' },
                { label: 'Current Label', value: data.cutBlockId ?? '—' },
              ]}
            />
            <TextInput
              id="relabel-new-label"
              labelText="New Label"
              maxLength={10}
              value={label}
              onChange={(e) => setLabel(e.target.value.toUpperCase())}
            />
            <TextArea
              id="relabel-reason"
              labelText="Relabel Reason"
              maxCount={MAX_REASON}
              enableCounter
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </div>
        )}
      </AsyncBoundary>
    </PageLayout>
  );
};

export default CutBlockRelabel;
