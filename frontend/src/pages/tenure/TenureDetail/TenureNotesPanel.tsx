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
import { useCallback, useState, type FC } from 'react';

import AsyncBoundary from '@/components/AsyncBoundary';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { Modal } from '@/components/Modal';
import UserName from '@/components/UserName';
import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import { canAddTenureNotes } from '@/routes/access';
import { addTenureNote, getTenureNotes, TENURE_NOTE_MAX_LENGTH } from '@/services/tenure_notes';
import { formatDate } from '@/utils/formatDate';

import type { TenurePanelProps } from './panelProps';

/**
 * "Apr 10, 2021 2:05 PM" — legacy shows the time as well as the date
 * (`YYYY-MM-DD HH12:MI AM`), since several notes can land on one day.
 */
const formatTimestamp = (value: string | null): string => {
  if (!value) return '—';
  const m = /T(\d{2}):(\d{2})/.exec(value);
  if (!m) return formatDate(value) || value;
  const hours = Number(m[1]);
  const time = `${hours % 12 || 12}:${m[2]} ${hours < 12 ? 'AM' : 'PM'}`;
  return `${formatDate(value)} ${time}`;
};

/** Legacy's form checks (RequiredFieldValidator, StringLengthValidator 4000). */
const validate = (text: string): string | null =>
  !text
    ? 'Note is mandatory.'
    : text.length > TENURE_NOTE_MAX_LENGTH
      ? `Note must not exceed ${TENURE_NOTE_MAX_LENGTH} characters.`
      : null;

/**
 * The Notes tab of the tenure detail — legacy FTA970 (Forest Notes), as the
 * private-mark Notes tab: an empty state, or the list (newest first) with an
 * Add button above it, and a small modal to add. Notes are append-only; as in
 * legacy, a file in status PE cannot take one.
 */
const TenureNotesPanel: FC<TenurePanelProps> = ({ tenure }) => {
  const forestFileId = tenure.forestFileId;
  // Wider than the tab's other writes: the timber mark roles may add notes too.
  const { user } = useAuth();
  const canEdit = canAddTenureNotes(user);
  const { display } = useNotification();
  const fetcher = useCallback(() => getTenureNotes(forestFileId), [forestFileId]);
  const { data, loading, error, reload } = useApiResource(fetcher, [forestFileId]);

  const [open, setOpen] = useState(false);
  const [note, setNote] = useState('');
  const [showValidation, setShowValidation] = useState(false);
  const [saving, setSaving] = useState(false);

  const allowed = canEdit && !!data?.canAdd;

  const trimmed = note.trim();
  const invalid = validate(trimmed);

  const openDialog = () => {
    setNote('');
    setShowValidation(false);
    setOpen(true);
  };

  const closeDialog = () => {
    if (!saving) setOpen(false);
  };

  const submit = async () => {
    if (invalid) {
      setShowValidation(true);
      return;
    }
    setSaving(true);
    try {
      await addTenureNote(forestFileId, trimmed);
      display({ kind: 'success', title: 'Note added', timeout: 5000 });
      setOpen(false);
      reload();
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

  const addButton = (primary: boolean) => (
    <Button
      kind={primary ? 'primary' : 'tertiary'}
      size={primary ? undefined : 'sm'}
      renderIcon={Add}
      disabled={!allowed}
      onClick={openDialog}
    >
      Add note
    </Button>
  );

  return (
    <div className="fsp-info__tab-panel">
      <AsyncBoundary loading={loading} error={error} onRetry={reload} loadingText="Loading notes…">
        {data &&
          (data.notes.length === 0 ? (
            <EmptyState
              icon={<AddComment size={48} />}
              title="No notes for this tenure"
              body="Add a note to record decisions, contacts or anything else about this file."
              action={
                // Always shown; disabled when adding isn't allowed.
                <div className="detail-tab__empty-action">{addButton(true)}</div>
              }
            />
          ) : (
            <div>
              <header className="detail-tab__actions">{addButton(false)}</header>
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
                      {data.notes.map((n, i) => (
                        <TableRow key={`${n.entryTimestamp ?? 'note'}-${i}`}>
                          <TableCell>
                            <UserName userId={n.entryUserid} />
                          </TableCell>
                          <TableCell>{formatTimestamp(n.entryTimestamp)}</TableCell>
                          <TableCell className="detail-tab__long-text">{n.note || '—'}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </div>
            </div>
          ))}
      </AsyncBoundary>

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
            All fields are required unless marked optional. Notes are added to the tenure&apos;s
            forest file and cannot be changed afterwards.
          </p>
          <TextArea
            id="tenure-note"
            labelText="Note"
            rows={6}
            value={note}
            enableCounter
            maxCount={TENURE_NOTE_MAX_LENGTH}
            invalid={showValidation && !!invalid}
            invalidText={invalid ?? undefined}
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

export default TenureNotesPanel;
