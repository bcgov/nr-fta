import { Button, Select, SelectItem } from '@carbon/react';
import { Save } from '@carbon/icons-react';
import { useState, type FC } from 'react';
import { useNotification } from '@/context/notification/useNotification';
import PageLayout from '@/pages/PageLayout';
import { ORG_UNITS } from '@/mocks/reference';
import { setDefaultOrgUnit } from '@/services/org_unit_maint';

/**
 * SIL99 — Org Unit Maintenance. Sets the user's default org unit.
 */
const OrgUnitMaintenance: FC = () => {
  const notify = useNotification();
  const [orgUnit, setOrgUnit] = useState(ORG_UNITS[0]);
  const [saving, setSaving] = useState(false);

  const onSave = async () => {
    setSaving(true);
    try {
      await setDefaultOrgUnit({ orgUnitCode: orgUnit });
      notify.display({
        kind: 'success',
        title: 'Default org unit saved',
        subtitle: `${orgUnit} set as your default.`,
        timeout: 5000,
      });
    } catch (err) {
      notify.display({
        kind: 'error',
        title: 'Could not save default org unit',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 6000,
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <PageLayout title="Org Unit Maintenance">
      <p style={{ maxWidth: '44rem', marginBottom: '1.5rem' }}>
        Set the default org unit applied to your searches and new records.
      </p>
      <div style={{ maxWidth: '28rem' }}>
        <Select id="ou-default" labelText="Default org unit" value={orgUnit} onChange={(e) => setOrgUnit(e.target.value)}>
          {ORG_UNITS.map((o) => <SelectItem key={o} value={o} text={o} />)}
        </Select>
        <div style={{ marginTop: '1.5rem' }}>
          <Button renderIcon={Save} onClick={onSave} disabled={saving}>
            {saving ? 'Saving…' : 'Save default'}
          </Button>
        </div>
      </div>
    </PageLayout>
  );
};

export default OrgUnitMaintenance;
