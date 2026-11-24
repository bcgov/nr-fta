import { Add } from '@carbon/icons-react';
import { Button, Stack } from '@carbon/react';
import { useEffect, useState, type FC } from 'react';

import ClientComboBox from '@/components/ClientComboBox';
import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import { APT_RELATIONSHIP, createAptRelatedClient } from '@/services/apt/linkages';

type Side = 'parent' | 'child';

/** What the client picker holds: a picked client, or text typed without picking one. */
interface Picked {
  clientNumber: string;
  clientLocnCode: string;
  clientName: string;
}

const EMPTY: Picked = { clientNumber: '', clientLocnCode: '', clientName: '' };

interface Props {
  open: boolean;
  onClose: () => void;
  /** Called after a relationship is created, to re-read the list. */
  onCreated: () => void;
}

/**
 * APT2's Create Relationship (APTM9200): a parent corporation and the subsidiary it controls
 * (always the CONTROLS relationship), each picked with the forest client autocomplete
 * every other FTA screen uses. Rendered only for APT administrators.
 */
const CreateRelationshipModal: FC<Props> = ({ open, onClose, onCreated }) => {
  const { display } = useNotification();
  const [parent, setParent] = useState<Picked>(EMPTY);
  const [child, setChild] = useState<Picked>(EMPTY);
  const [errors, setErrors] = useState<Partial<Record<Side, string>>>({});
  const [saving, setSaving] = useState(false);
  // Carbon's ComboBox keeps its text between openings; a new key starts each one empty.
  const [round, setRound] = useState(0);

  useEffect(() => {
    if (!open) return;
    setParent(EMPTY);
    setChild(EMPTY);
    setErrors({});
    setRound((r) => r + 1);
  }, [open]);

  const set = (side: Side) => (next: Picked) => {
    (side === 'parent' ? setParent : setChild)(next);
    setErrors((e) => ({ ...e, [side]: undefined }));
  };

  const create = async () => {
    // A relationship needs an actual client; typed text that matches none is not one.
    const found: Partial<Record<Side, string>> = {};
    if (!parent.clientNumber) found.parent = 'Pick the parent corporation from the list.';
    if (!child.clientNumber) found.child = 'Pick the subsidiary corporation from the list.';
    if (found.parent || found.child) {
      setErrors(found);
      return;
    }
    setSaving(true);
    try {
      await createAptRelatedClient({
        parentClientNumber: parent.clientNumber,
        clientNumber: child.clientNumber,
        relationshipCode: APT_RELATIONSHIP.code,
      });
      display({
        kind: 'success',
        title: 'Relationship created',
        subtitle: `Client ${parent.clientNumber} controls client ${child.clientNumber}.`,
        timeout: 6000,
      });
      onCreated();
      onClose();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not create the relationship',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      passiveModal
      size="md"
      className="detail-dialog"
      modalHeading="Create relationship"
      onRequestClose={onClose}
      preventCloseOnClickOutside
    >
      <Stack gap={5}>
        <ClientComboBox
          key={`parent-${round}`}
          id="apt-link-parent"
          inDialog
          titleText="Parent corporation"
          helperText="The corporation that controls the subsidiary."
          clientNumber={parent.clientNumber}
          clientLocnCode={parent.clientLocnCode}
          clientName={parent.clientName}
          invalid={!!errors.parent}
          invalidText={errors.parent}
          disabled={saving}
          onChange={set('parent')}
        />
        <ClientComboBox
          key={`child-${round}`}
          id="apt-link-child"
          inDialog
          titleText="Subsidiary corporation"
          helperText="The corporation the parent controls."
          clientNumber={child.clientNumber}
          clientLocnCode={child.clientLocnCode}
          clientName={child.clientName}
          invalid={!!errors.child}
          invalidText={errors.child}
          disabled={saving}
          onChange={set('child')}
        />
      </Stack>
      <div className="detail-dialog__actions">
        <Button kind="tertiary" disabled={saving} onClick={onClose}>
          Cancel
        </Button>
        <Button kind="primary" renderIcon={Add} disabled={saving} onClick={() => void create()}>
          {saving ? 'Creating…' : 'Create relationship'}
        </Button>
      </div>
    </Modal>
  );
};

export default CreateRelationshipModal;
