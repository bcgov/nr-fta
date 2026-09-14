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
  TextArea,
} from '@carbon/react';
import { useState, type FC } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';

import SectionTile from '@/components/SectionTile';
import Tombstone from '@/components/Tombstone';
import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import { findHarvestingAuthority, cutBlocksForCp, cbSkeyFor } from '@/mocks/harvesting';
import PageLayout from '@/pages/PageLayout';
import { canEdit } from '@/routes/access';
import { suspendBlocks } from '@/services/suspend_blocks';

/**
 * FTA912 — Suspend Multiple Blocks within a permit. Select the cut blocks to
 * suspend, give a reason, and submit. Gated to FTA_ADMIN; submit is mock-only.
 */
const SuspendBlocks: FC = () => {
  const { cpId = '' } = useParams();
  const { user } = useAuth();
  const notify = useNotification();
  const navigate = useNavigate();
  const cp = findHarvestingAuthority(cpId);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [reason, setReason] = useState('');
  const [saving, setSaving] = useState(false);
  const readOnly = !canEdit(user);

  if (!cp) {
    return (
      <PageLayout title="Cutting permit not found">
        <p style={{ marginBottom: '1.5rem' }}>No cutting permit matches “{cpId}”.</p>
        <Button
          as={Link}
          to="/search/harvesting-authority"
          renderIcon={ArrowLeft}
          kind="tertiary"
          size="md"
        >
          Back to Harvesting Authority Search
        </Button>
      </PageLayout>
    );
  }

  const blocks = cutBlocksForCp(cp.cpId);
  const toggle = (id: string) =>
    setSelected((s) => {
      const next = new Set(s);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });

  const onSubmit = async () => {
    setSaving(true);
    try {
      const { suspended } = await suspendBlocks(cp.cpId, {
        forestFileId: cp.fileId,
        cbSkeys: blocks
          .filter((b) => selected.has(b.blockId))
          .map((b) => String(cbSkeyFor(b.blockId))),
        suspendAllBlocks: false,
        partitionCode: null,
        suspOrderNumber: null,
        suspStartDate: null,
        suspEndDate: null,
        reason,
      });
      notify.display({
        kind: 'success',
        title: 'Blocks suspended',
        subtitle: `${suspended} block(s) on ${cp.cpId} suspended.`,
        timeout: 5000,
      });
      navigate(`/harvesting-authority/${cp.cpId}`);
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

  return (
    <PageLayout
      title={`Suspend Blocks — ${cp.cpId}`}
      subtitle="Select the cut blocks to suspend and record a reason"
    >
      <Link to={`/harvesting-authority/${cp.cpId}`} className="back-link">
        <ArrowLeft size={16} /> Back to Cutting Permit {cp.cpId}
      </Link>

      <Tombstone
        ariaLabel="Cutting permit summary"
        items={[
          { label: 'Cutting Permit', value: cp.cpId },
          { label: 'Timber Mark', value: cp.timberMark },
          { label: 'Forest File', value: <Link to={`/tenures/${cp.fileId}`}>{cp.fileId}</Link> },
          { label: 'Blocks', value: String(blocks.length) },
        ]}
      />

      <SectionTile title="Blocks" icon={List} description="Select the blocks to suspend">
        <div className="bordered-table">
          <TableContainer title="Select blocks to suspend">
            <Table>
              <TableHead>
                <TableRow>
                  <TableHeader>Suspend</TableHeader>
                  <TableHeader>Block</TableHeader>
                  <TableHeader>Status</TableHeader>
                  <TableHeader>Area (ha)</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {blocks.map((b) => (
                  <TableRow key={b.blockId}>
                    <TableCell>
                      <Checkbox
                        id={`sus-${b.blockId}`}
                        labelText=""
                        checked={selected.has(b.blockId)}
                        disabled={readOnly || b.status === 'Suspended'}
                        onChange={() => toggle(b.blockId)}
                      />
                    </TableCell>
                    <TableCell>{b.blockId}</TableCell>
                    <TableCell>{b.status}</TableCell>
                    <TableCell>{b.areaHa.toFixed(1)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </div>
      </SectionTile>

      <SectionTile title="Suspension details" icon={Pause}>
        <div style={{ maxWidth: '40rem' }}>
          <TextArea
            id="sus-reason"
            labelText="Suspension reason"
            placeholder="Why are these blocks being suspended?"
            value={reason}
            disabled={readOnly}
            onChange={(e) => setReason(e.target.value)}
          />
          <div style={{ marginTop: '1.5rem' }}>
            <Button
              size="md"
              kind="danger"
              renderIcon={Pause}
              disabled={readOnly || saving || selected.size === 0 || !reason.trim()}
              onClick={onSubmit}
            >
              {saving ? 'Suspending…' : `Suspend ${selected.size} block(s)`}
            </Button>
          </div>
        </div>
      </SectionTile>
    </PageLayout>
  );
};

export default SuspendBlocks;
