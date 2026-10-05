import { Add, AddComment } from '@carbon/icons-react';
import {
  Button,
  Stack,
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

import { EmptyState } from '@/components/EmptyState/EmptyState';
import { Modal } from '@/components/Modal';
import UserName from '@/components/UserName';
import { useNotification } from '@/context/notification/useNotification';
import type { MarkNote } from '@/services/mark_detail';
import { addMarkNote, MARK_NOTE_MAX_LENGTH } from '@/services/mark_write';
import { formatDate } from '@/utils/formatDate';

/**
 * "Apr 10, 2021 2:05 PM" — the legacy screen shows the time as well as the
 * date (`YYYY-MM-DD HH12:MI AM`), since several notes can land on one day.
 */
const formatTimestamp = (value: string | null): string => {
  if (!value) return '—';
  const m = /T(\d{2}):(\d{2})/.exec(value);
  if (!m) return formatDate(value) || value;
  const hours = Number(m[1]);
  const time = `${hours % 12 || 12}:${m[2]} ${hours < 12 ? 'AM' : 'PM'}`;
  return `${formatDate(value)} ${time}`;
};

interface Props {
  /** The detail route's id: a timber mark, or a certificate with `byCertificate`. */
  id: string;
  byCertificate: boolean;
  /** Null until the application is given a forest file — notes need one. */
  forestFileId: string | null;
  notes: MarkNote[];
  /** Whether the user's role may add notes (FTA_ADMIN and the two timber mark roles). */
  canAdd: boolean;
  /** Called after a note is saved, to re-read the mark. */
  onAdded: () => void;
}

/**
 * The Notes tab of the private-mark detail — legacy FTA970 (Forest Notes),
 * which the private-mark tab set shares with the tenure screens. Laid out as
 * the Land index, Associated clients and Amendments tabs: an empty state, or
 * the list (newest first) with an Add button above it, and a small modal to
 * add. Notes belong to the mark's forest file and are append-only; as in
 * legacy, an application without a forest file cannot take a note.
 */
const NotesPanel: FC<Props> = ({ id, byCertificate, forestFileId, notes, canAdd, onAdded }) => {
  const { display } = useNotification();
  const [open, setOpen] = useState(false);
  const [note, setNote] = useState('');
  const [showValidation, setShowValidation] = useState(false);
  const [saving, setSaving] = useState(false);

  // Why the Add button is disabled, if it is.
  const disabledReason = !canAdd
    ? 'Your role cannot add a note.'
    : forestFileId === null
      ? 'This application has no forest file yet, so it cannot take notes.'
      : null;
  const allowed = disabledReason === null;

  const trimmed = note.trim();
  const error = !trimmed
    ? 'A note is required.'
    : trimmed.length > MARK_NOTE_MAX_LENGTH
      ? `A note can be at most ${MARK_NOTE_MAX_LENGTH} characters.`
      : null;

  const openDialog = () => {
    setNote('');
    setShowValidation(false);
    setOpen(true);
  };

  const closeDialog = () => {
    if (!saving) setOpen(false);
  };

  const submit = async () => {
    if (error) {
      setShowValidation(true);
      return;
    }
    setSaving(true);
    try {
      await addMarkNote(id, trimmed, byCertificate);
      display({ kind: 'success', title: 'Note added', timeout: 5000 });
      setOpen(false);
      onAdded();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not add the note',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fsp-info__tab-panel">
      {notes.length === 0 ? (
        <EmptyState
          icon={<AddComment size={48} />}
          title="No notes for this mark"
          body="Add a note to record decisions, contacts or anything else about this mark."
          action={
            // Always shown; disabled, with the reason, when adding isn't allowed.
            <div className="detail-tab__empty-action">
              <Button kind="primary" renderIcon={Add} disabled={!allowed} onClick={openDialog}>
                Add note
              </Button>
              {disabledReason && <p className="detail-tab__reason">{disabledReason}</p>}
            </div>
          }
        />
      ) : (
        <div>
          <header className="detail-tab__actions">
            {disabledReason && <p className="detail-tab__reason">{disabledReason}</p>}
            <Button
              kind="tertiary"
              size="sm"
              renderIcon={Add}
              disabled={!allowed}
              onClick={openDialog}
            >
              Add note
            </Button>
          </header>
          <div className="bordered-table">
            <TableContainer>
              <Table size="md" useZebraStyles>
                <TableHead>
                  <TableRow>
                    <TableHeader>Entered By</TableHeader>
                    <TableHeader>Entered</TableHeader>
                    <TableHeader>Note</TableHeader>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {notes.map((n, i) => (
                    <TableRow key={`${n.entryTimestamp ?? 'note'}-${i}`}>
                      <TableCell>
                        <UserName userId={n.entryUserid} />
                      </TableCell>
                      <TableCell className="mark-notes__when">
                        {formatTimestamp(n.entryTimestamp)}
                      </TableCell>
                      <TableCell className="mark-notes__text">{n.note || '—'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </div>
        </div>
      )}

      {/* Add dialog — the Land index dialog's shape. */}
      <Modal
        open={open}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading="Add note"
        onRequestClose={closeDialog}
        preventCloseOnClickOutside
      >
        <Stack gap={5}>
          <p className="detail-dialog__subtitle">
            Notes are added to the mark&apos;s forest file and cannot be changed afterwards.
          </p>
          <TextArea
            id="mark-note"
            labelText="Note"
            rows={6}
            value={note}
            enableCounter
            maxCount={MARK_NOTE_MAX_LENGTH}
            invalid={showValidation && !!error}
            invalidText={error ?? undefined}
            disabled={saving}
            onChange={(e) => setNote(e.target.value)}
          />
        </Stack>
        <div className="detail-dialog__actions">
          <Button kind="tertiary" disabled={saving} onClick={closeDialog}>
            Cancel
          </Button>
          <Button kind="primary" disabled={saving} onClick={() => void submit()}>
            {saving ? 'Adding…' : 'Add note'}
          </Button>
        </div>
      </Modal>
    </div>
  );
};

export default NotesPanel;
