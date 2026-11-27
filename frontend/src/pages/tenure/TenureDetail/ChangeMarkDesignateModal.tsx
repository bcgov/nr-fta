import { Button, Stack, TextInput } from '@carbon/react';
import { useEffect, useState, type FC, type FormEvent } from 'react';

import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import { safeErrorMessage } from '@/lib/errorMessage';
import {
  changeTenureMarkDesignate,
  getMarkDesignate,
  type MarkDesignate,
} from '@/services/tenure_details';

/** Legacy's Source File field: ten characters. */
const MAX_FILE_ID = 10;

/**
 * FTA101 Change Mark Designate, opened by the Details tab's button: "reuse of
 * older mark designates". It opens on this file and its own designate; Go shows
 * the Source File's designate, and Save Designate copies it onto this file. The
 * package refuses a source in another region or of another file type, or that
 * shares no licensee with this file. Save is for the source Go last showed.
 */
const ChangeMarkDesignateModal: FC<{
  forestFileId: string;
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
}> = ({ forestFileId, open, onClose, onSaved }) => {
  const [source, setSource] = useState('');
  const [shown, setShown] = useState<MarkDesignate | null>(null);
  const notify = useNotification();
  // Errors are a snackbar, which shows above the dialog; the dialog stays open.
  const fail = (err: unknown) =>
    notify.display({
      kind: 'error',
      title: 'Could not change the mark designate.',
      subtitle: safeErrorMessage(err),
      timeout: 0,
    });
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  // Opening reads this file itself, as legacy's screen did.
  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setSource(forestFileId);
    setShown(null);
    setLoading(true);
    getMarkDesignate(forestFileId, '')
      .then((d) => {
        if (!cancelled) setShown(d);
      })
      .catch((err: unknown) => {
        if (!cancelled) fail(err);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // fail only raises a toast; reading on open is keyed to the file alone.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, forestFileId]);

  const onGo = async (e?: FormEvent) => {
    e?.preventDefault();
    setLoading(true);
    setShown(null);
    try {
      setShown(await getMarkDesignate(forestFileId, source.trim()));
    } catch (err) {
      fail(err);
    } finally {
      setLoading(false);
    }
  };

  const onSave = async () => {
    if (!shown) return;
    setSaving(true);
    try {
      await changeTenureMarkDesignate(forestFileId, shown.sourceFileId, shown.revisionCount);
      onSaved();
      onClose();
    } catch (err) {
      fail(err);
    } finally {
      setSaving(false);
    }
  };

  const busy = loading || saving;
  const current = !!shown && source.trim().toUpperCase() === shown.sourceFileId;

  return (
    <Modal
      open={open}
      passiveModal
      size="sm"
      className="detail-dialog"
      modalHeading="Change Mark Designate"
      onRequestClose={() => {
        if (!saving) onClose();
      }}
      preventCloseOnClickOutside
    >
      <Stack gap={5}>
        <form onSubmit={(e) => void onGo(e)}>
          <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'flex-end' }}>
            <TextInput
              id="change-designate-source"
              labelText="Source File"
              value={source}
              maxLength={MAX_FILE_ID}
              disabled={busy}
              onChange={(e) => setSource(e.target.value.toUpperCase())}
            />
            <Button kind="tertiary" size="md" type="submit" disabled={busy}>
              {loading ? 'Loading…' : 'Go'}
            </Button>
          </div>
        </form>
        <TextInput
          id="change-designate-designate"
          labelText="Designate"
          value={shown?.markDesignate ?? ''}
          readOnly
        />
      </Stack>
      <div className="detail-dialog__actions">
        <Button kind="tertiary" disabled={saving} onClick={onClose}>
          Cancel
        </Button>
        <Button kind="primary" disabled={busy || !current} onClick={() => void onSave()}>
          {saving ? 'Saving…' : 'Save Designate'}
        </Button>
      </div>
    </Modal>
  );
};

export default ChangeMarkDesignateModal;
