import {
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TextInput,
} from '@carbon/react';
import { ArrowLeft, Save } from '@carbon/icons-react';
import { useState, type FC } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import Tombstone from '@/components/Tombstone';
import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import { canEdit } from '@/routes/access';
import PageLayout from '@/pages/PageLayout';
import { findHarvestingAuthority, cutBlocksForCp, cbSkeyFor, hvaSkeyFor } from '@/mocks/harvesting';
import { assignMarks } from '@/services/assign_marks';

/**
 * FTA908 — Assign Marks to Blocks (Hauling Authority). Assign a hauling timber
 * mark to each cut block on the permit. Gated to FTA_ADMIN; submit is mock-only.
 */
const AssignMarks: FC = () => {
  const { cpId = '' } = useParams();
  const { user } = useAuth();
  const notify = useNotification();
  const navigate = useNavigate();
  const cp = findHarvestingAuthority(cpId);
  const readOnly = !canEdit(user);
  const [marks, setMarks] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  if (!cp) {
    return (
      <PageLayout title="Cutting permit not found">
        <p style={{ marginBottom: '1.5rem' }}>No cutting permit matches “{cpId}”.</p>
        <Button as={Link} to="/search/harvesting-authority" renderIcon={ArrowLeft} kind="tertiary">
          Back to Harvesting Authority Search
        </Button>
      </PageLayout>
    );
  }

  const blocks = cutBlocksForCp(cp.cpId);

  const onSubmit = async () => {
    setSaving(true);
    try {
      const assignments = blocks
        .filter((b) => (marks[b.blockId] ?? '').trim() !== '')
        .map((b) => ({
          cbSkey: String(cbSkeyFor(b.blockId)),
          cutBlockId: b.blockId,
          timberMark: b.timberMark,
          newTimberMark: marks[b.blockId].trim(),
          revisionCount: '0',
        }));
      const { updated } = await assignMarks(cp.cpId, {
        forestFileId: cp.fileId,
        hvaSkey: String(hvaSkeyFor(cp.cpId)),
        assignments,
      });
      notify.display({
        kind: 'success',
        title: 'Hauling marks assigned',
        subtitle: `${updated} block${updated === 1 ? '' : 's'} updated for ${cp.cpId}.`,
        timeout: 5000,
      });
      navigate(`/harvesting-authority/${cp.cpId}`);
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
    <PageLayout title={`Assign Marks to Blocks — ${cp.cpId}`}>
      <Link to={`/harvesting-authority/${cp.cpId}`} className="fta-back">
        <ArrowLeft size={16} /> Back to Cutting Permit {cp.cpId}
      </Link>

      <Tombstone
        ariaLabel="Cutting permit summary"
        items={[
          { label: 'Cutting Permit', value: cp.cpId },
          { label: 'Primary Mark', value: cp.timberMark },
          { label: 'Forest File', value: <Link to={`/tenures/${cp.fileId}`}>{cp.fileId}</Link> },
          { label: 'Blocks', value: String(blocks.length) },
        ]}
      />

      <TableContainer title="Assign hauling timber mark per block">
        <Table>
          <TableHead>
            <TableRow>
              <TableHeader>Block</TableHeader>
              <TableHeader>Status</TableHeader>
              <TableHeader>Hauling Timber Mark</TableHeader>
            </TableRow>
          </TableHead>
          <TableBody>
            {blocks.map((b) => (
              <TableRow key={b.blockId}>
                <TableCell>{b.blockId}</TableCell>
                <TableCell>{b.status}</TableCell>
                <TableCell>
                  <TextInput
                    id={`mark-${b.blockId}`}
                    labelText=""
                    placeholder="e.g. 52/1234"
                    size="sm"
                    disabled={readOnly}
                    value={marks[b.blockId] ?? ''}
                    onChange={(e) => setMarks((m) => ({ ...m, [b.blockId]: e.target.value }))}
                  />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      <div style={{ marginTop: '1.5rem' }}>
        <Button renderIcon={Save} disabled={readOnly || saving} onClick={onSubmit}>
          {saving ? 'Saving…' : 'Save assignments'}
        </Button>
      </div>
    </PageLayout>
  );
};

export default AssignMarks;
