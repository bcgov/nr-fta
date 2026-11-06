import { Add, Search } from '@carbon/icons-react';
import { Button, TextInput } from '@carbon/react';
import { useState, type FC } from 'react';

import SectionTile from '@/components/SectionTile';
import { useNotification } from '@/context/notification/useNotification';
import {
  APT_RELATIONSHIP,
  createAptRelatedClient,
  searchAptClients,
  type AptClient,
} from '@/services/apt/linkages';

import ClientSearchModal from './ClientSearchModal';

type Side = 'parent' | 'child';

interface Picked {
  number: string;
  name: string;
}

const EMPTY: Picked = { number: '', name: '' };

const SUBJECT: Record<Side, string> = { parent: 'corporation', child: 'licensee' };

interface Props {
  /** Called after a relationship is created, to re-read the list. */
  onCreated: () => void;
}

/**
 * APT2's Create Relationship form (APTM9200): a controlling corporation, the fixed CONTROLS
 * relationship, and the licensee it controls. Each client is typed or found with APT's client
 * search. Rendered only for APT administrators.
 */
const NewRelationshipForm: FC<Props> = ({ onCreated }) => {
  const { display } = useNotification();
  const [parent, setParent] = useState<Picked>(EMPTY);
  const [child, setChild] = useState<Picked>(EMPTY);
  const [errors, setErrors] = useState<Partial<Record<Side, string>>>({});
  const [finding, setFinding] = useState<Side | null>(null);
  const [saving, setSaving] = useState(false);

  const setSide = (side: Side, value: Picked) => {
    (side === 'parent' ? setParent : setChild)(value);
    setErrors((e) => ({ ...e, [side]: undefined }));
  };

  // A whole client number typed by hand: show whose it is, as Find would have.
  const nameTyped = async (side: Side, number: string) => {
    if (number.length !== 8) return;
    try {
      const match = (await searchAptClients({ clientNumber: number })).find(
        (c) => c.clientNumber === number,
      );
      const set = side === 'parent' ? setParent : setChild;
      set((p) => (p.number === number ? { number, name: match?.clientName ?? '' } : p));
    } catch {
      // The name is a convenience; the create checks the number either way.
    }
  };

  const picked = (client: AptClient) => {
    if (finding) setSide(finding, { number: client.clientNumber, name: client.clientName ?? '' });
    setFinding(null);
  };

  const create = async () => {
    const found: Partial<Record<Side, string>> = {};
    if (!parent.number) found.parent = 'Enter the corporation’s client number.';
    if (!child.number) found.child = 'Enter the licensee’s client number.';
    if (found.parent || found.child) {
      setErrors(found);
      return;
    }
    setSaving(true);
    try {
      await createAptRelatedClient({
        parentClientNumber: parent.number,
        clientNumber: child.number,
        relationshipCode: APT_RELATIONSHIP.code,
      });
      display({
        kind: 'success',
        title: 'Relationship created',
        subtitle: `${parent.name || parent.number} controls ${child.name || child.number}.`,
        timeout: 6000,
      });
      setParent(EMPTY);
      setChild(EMPTY);
      onCreated();
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

  const clientRow = (side: Side, label: string, value: Picked) => (
    <div className="apt-linkages__row">
      <TextInput
        id={`apt-link-${side}-number`}
        labelText={`${label} client number`}
        inputMode="numeric"
        maxLength={8}
        value={value.number}
        invalid={!!errors[side]}
        invalidText={errors[side]}
        disabled={saving}
        onChange={(e) => setSide(side, { number: e.target.value.replace(/\D/g, ''), name: '' })}
        onBlur={(e) => void nameTyped(side, e.target.value)}
      />
      <TextInput id={`apt-link-${side}-name`} labelText={label} value={value.name} readOnly />
      <Button
        kind="tertiary"
        size="md"
        renderIcon={Search}
        disabled={saving}
        onClick={() => setFinding(side)}
      >
        Find
      </Button>
    </div>
  );

  return (
    <SectionTile
      title="New relationship"
      icon={Add}
      actions={
        <Button size="md" renderIcon={Add} disabled={saving} onClick={() => void create()}>
          {saving ? 'Creating…' : 'Create relationship'}
        </Button>
      }
    >
      <div className="apt-linkages__form">
        {clientRow('parent', 'Corporation', parent)}
        <div className="apt-linkages__row">
          <TextInput
            id="apt-link-relationship"
            labelText="Relationship"
            value={APT_RELATIONSHIP.label}
            readOnly
          />
        </div>
        {clientRow('child', 'Licensee', child)}
      </div>

      {finding && (
        <ClientSearchModal
          open
          subject={SUBJECT[finding]}
          onClose={() => setFinding(null)}
          onSelect={picked}
        />
      )}
    </SectionTile>
  );
};

export default NewRelationshipForm;
