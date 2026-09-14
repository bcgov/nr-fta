import { Archive } from '@carbon/icons-react';
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
  Tag,
} from '@carbon/react';
import { useState, type FC } from 'react';

import DestructiveModal from '@/components/core/DestructiveModal';
import SectionTile from '@/components/SectionTile';
import { useNotification } from '@/context/notification/useNotification';
import { MOCK_TENURES } from '@/mocks/tenures';
import PageLayout from '@/pages/PageLayout';
import { archiveTenures } from '@/services/archive_tenures';

/**
 * FTA640 — Archive Tenures. Select expired/inactive tenures to archive (mock).
 * Only Expired tenures are eligible.
 */
const ArchiveTenures: FC = () => {
  const notify = useNotification();
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [saving, setSaving] = useState(false);
  // Archiving is a bulk action over whatever is checked, and nothing in the UI
  // undoes it, so it is confirmed rather than fired straight from the button.
  const [confirmOpen, setConfirmOpen] = useState(false);

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

  const onArchive = async () => {
    // Archive exactly the files the user selected — the backend only touches
    // still-active files among this explicit list, so it can never archive
    // more than what's checked here.
    setSaving(true);
    try {
      const { updated } = await archiveTenures({ forestFileIds: [...selected] });
      notify.display({
        kind: 'success',
        title: 'Tenures archived',
        subtitle: `${updated} tenure(s) archived.`,
        timeout: 5000,
      });
      setSelected(new Set());
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

  return (
    <PageLayout
      title="Archive Tenures"
      subtitle="Select expired tenures to move them into the archive."
    >
      <SectionTile
        title="Tenures"
        icon={Archive}
        description="Only tenures in an Expired status are eligible for archiving."
        actions={
          <Button
            size="md"
            kind="danger"
            renderIcon={Archive}
            disabled={selected.size === 0 || saving}
            onClick={() => setConfirmOpen(true)}
          >
            {saving ? 'Archiving…' : `Archive ${selected.size} tenure(s)`}
          </Button>
        }
      >
        <div className="bordered-table">
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableHeader>Archive</TableHeader>
                  <TableHeader>File ID</TableHeader>
                  <TableHeader>Type</TableHeader>
                  <TableHeader>Status</TableHeader>
                  <TableHeader>Licensee</TableHeader>
                  <TableHeader>Expiry</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {MOCK_TENURES.map((t) => {
                  const eligible = t.status === 'Expired';
                  return (
                    <TableRow key={t.fileId}>
                      <TableCell>
                        <Checkbox
                          id={`arc-${t.fileId}`}
                          labelText=""
                          checked={selected.has(t.fileId)}
                          disabled={!eligible}
                          onChange={() => toggle(t.fileId)}
                        />
                      </TableCell>
                      <TableCell>{t.fileId}</TableCell>
                      <TableCell>{t.fileType}</TableCell>
                      <TableCell>
                        <Tag type={eligible ? 'gray' : 'green'}>{t.status}</Tag>
                      </TableCell>
                      <TableCell>{t.licensee}</TableCell>
                      <TableCell>{t.expiryDate}</TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        </div>
      </SectionTile>

      <DestructiveModal
        open={confirmOpen}
        title="Archive tenures?"
        message={`${selected.size} tenure(s) will be moved out of the active list. Of the files selected, only those still active are changed, and archiving is not undone from this screen.`}
        confirmButtonText="Archive"
        loading={saving}
        onConfirm={() => void onArchive()}
        onCancel={() => setConfirmOpen(false)}
      />
    </PageLayout>
  );
};

export default ArchiveTenures;
