import { Button, TextArea, TextInput } from '@carbon/react';
import { ArrowLeft, Save } from '@carbon/icons-react';
import { useState, type FC } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import Tombstone from '@/components/Tombstone';
import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import { canEdit } from '@/routes/access';
import PageLayout from '@/pages/PageLayout';
import { performCutblockAction } from '@/services/cutblock_action';
import { findCutBlock } from '@/mocks/harvesting';

type ActionKind = 'amend' | 'suspend' | 'relabel';

const ACTION_META: Record<ActionKind, { legacy: string; title: string; verb: string }> = {
  amend: { legacy: 'FTA905', title: 'Cut Block Amendment', verb: 'Save amendment' },
  suspend: { legacy: 'FTA914', title: 'Suspend Cut Block', verb: 'Suspend block' },
  relabel: { legacy: 'FTA231', title: 'Cut Block Re-label', verb: 'Re-label block' },
};

/**
 * FTA905 / FTA914 / FTA231 — cut-block amendment, suspension, and re-label
 * flows. One parameterized screen reached from the Cut Block detail; the
 * `action` route segment selects the form. Gated to FTA_ADMIN; submit is
 * mock-only.
 */
const CutBlockAction: FC = () => {
  const { blockId = '', action = 'amend' } = useParams<{ blockId: string; action: ActionKind }>();
  const { user } = useAuth();
  const notify = useNotification();
  const navigate = useNavigate();
  const block = findCutBlock(blockId);
  const readOnly = !canEdit(user);
  const [reason, setReason] = useState('');
  const [newLabel, setNewLabel] = useState('');
  const [saving, setSaving] = useState(false);

  const kind = (['amend', 'suspend', 'relabel'] as ActionKind[]).includes(action as ActionKind)
    ? (action as ActionKind)
    : 'amend';
  const meta = ACTION_META[kind];

  if (!block) {
    return (
      <PageLayout title="Cut block not found">
        <p style={{ marginBottom: '1.5rem' }}>No cut block matches “{blockId}”.</p>
        <Button as={Link} to="/search/cut-block" renderIcon={ArrowLeft} kind="tertiary">
          Back to Cut Block Search
        </Button>
      </PageLayout>
    );
  }

  const canSubmit = !readOnly && (kind === 'relabel' ? newLabel.trim() !== '' : reason.trim() !== '');

  const onSubmit = async () => {
    setSaving(true);
    try {
      await performCutblockAction(block.blockId, {
        action: kind,
        reason: kind === 'relabel' ? null : reason,
        newCutBlockId: kind === 'relabel' ? newLabel : null,
      });
      notify.display({
        kind: 'success',
        title: `${meta.title} recorded`,
        subtitle: `${meta.title} for ${block.blockId} saved.`,
        timeout: 5000,
      });
      navigate(`/cut-block/${block.blockId}`);
    } catch (err) {
      notify.display({
        kind: 'error',
        title: `Could not save ${meta.title.toLowerCase()}`,
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 6000,
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <PageLayout title={`${meta.title} — ${block.blockId}`}>
      <Link to={`/cut-block/${block.blockId}`} className="fta-back">
        <ArrowLeft size={16} /> Back to Cut Block {block.blockId}
      </Link>

      <Tombstone
        ariaLabel="Cut block summary"
        items={[
          { label: 'Block', value: block.blockId },
          { label: 'Cutting Permit', value: <Link to={`/harvesting-authority/${block.cpId}`}>{block.cpId}</Link> },
          { label: 'Status', value: block.status },
          { label: 'Gross Area', value: `${block.areaHa.toFixed(1)} ha` },
        ]}
      />

      <div style={{ maxWidth: '40rem' }}>
        {kind === 'relabel' ? (
          <TextInput
            id="cba-label"
            labelText="New block label"
            placeholder="e.g. BLK-001A"
            disabled={readOnly}
            value={newLabel}
            onChange={(e) => setNewLabel(e.target.value)}
          />
        ) : (
          <TextArea
            id="cba-reason"
            labelText={kind === 'suspend' ? 'Suspension reason' : 'Amendment description'}
            placeholder={kind === 'suspend' ? 'Why is this block being suspended?' : 'Describe the amendment'}
            disabled={readOnly}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
        )}
        <div style={{ marginTop: '1.5rem' }}>
          <Button renderIcon={Save} disabled={!canSubmit || saving} onClick={onSubmit}>
            {saving ? 'Saving…' : meta.verb}
          </Button>
        </div>
      </div>
    </PageLayout>
  );
};

export default CutBlockAction;
