import { Save, Location } from '@carbon/icons-react';
import { Button, Select, SelectItem } from '@carbon/react';
import { useState, type FC } from 'react';

import SectionTile from '@/components/SectionTile';
import { useNotification } from '@/context/notification/useNotification';
import { ORG_UNITS } from '@/mocks/reference';
import PageLayout from '@/pages/PageLayout';
import { setDefaultOrgUnit } from '@/services/org_unit_maint';

import './OrgUnitMaintenance.scss';

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
    <PageLayout
      title="Org Unit Maintenance"
      subtitle="Set the default org unit applied to your searches and new records."
    >
      <SectionTile
        title="Default org unit"
        icon={Location}
        actions={
          <Button size="md" renderIcon={Save} onClick={() => void onSave()} disabled={saving}>
            {saving ? 'Saving…' : 'Save default'}
          </Button>
        }
      >
        <div className="org-unit-maintenance__field">
          <Select
            id="ou-default"
            labelText="Default org unit"
            value={orgUnit}
            onChange={(e) => setOrgUnit(e.target.value)}
          >
            {ORG_UNITS.map((o) => (
              <SelectItem key={o} value={o} text={o} />
            ))}
          </Select>
        </div>
      </SectionTile>
    </PageLayout>
  );
};

export default OrgUnitMaintenance;
