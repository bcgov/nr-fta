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
import { Archive } from '@carbon/icons-react';
import { useState, type FC } from 'react';
import { useNotification } from '@/context/notification/useNotification';
import PageLayout from '@/pages/PageLayout';
import { MOCK_TENURES } from '@/mocks/tenures';
import { archiveTenures } from '@/services/archive_tenures';

/**
 * FTA640 — Archive Tenures. Select expired/inactive tenures to archive (mock).
 * Only Expired tenures are eligible.
 */
const ArchiveTenures: FC = () => {
  const notify = useNotification();
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [saving, setSaving] = useState(false);

  const toggle = (id: string) =>
    setSelected((s) => {
      const next = new Set(s);
      next.has(id) ? next.delete(id) : next.add(id);
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
    }
  };

  return (
    <PageLayout title="Archive Tenures">
      <p style={{ maxWidth: '44rem', marginBottom: '1.5rem' }}>
        Select expired tenures to archive. Only tenures in an <strong>Expired</strong> status are eligible.
      </p>
      <TableContainer title="Tenures">
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
      <div style={{ marginTop: '1.5rem' }}>
        <Button renderIcon={Archive} disabled={selected.size === 0 || saving} onClick={onArchive}>
          {saving ? 'Archiving…' : `Archive ${selected.size} tenure(s)`}
        </Button>
      </div>
    </PageLayout>
  );
};

export default ArchiveTenures;
