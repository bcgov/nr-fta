import { Location, Save } from '@carbon/icons-react';
import {
  Button,
  InlineNotification,
  RadioButton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { useCallback, useEffect, useState, type FC } from 'react';

import AsyncBoundary from '@/components/AsyncBoundary';
import DefinitionGrid from '@/components/DefinitionGrid';
import SectionTile from '@/components/SectionTile';
import UserName from '@/components/UserName';
import { useNotification } from '@/context/notification/useNotification';
import PageLayout from '@/pages/PageLayout';
import { getOrgUnitMaint, saveDefaultOrgUnit, type OrgUnitMaint } from '@/services/org_unit_maint';

import './OrgUnitMaintenance.scss';

/**
 * SIL99 — User Org Maintenance (legacy Admin > Org Unit Maintenance, THE.SIL_99_USER_ORG_DEFAULT):
 * choose which of your org units is your default. The search screens, Add New tenure, and the
 * billing and audit screens preselect it in their district fields, as legacy's did.
 *
 * <p>Your org units are the districts your FTA_ADMIN grant covers — the ones a district-scoped
 * grant names, or every district. Legacy also let you look up another user and set theirs; here
 * you set only your own.
 */
const OrgUnitMaintenance: FC = () => {
  const notify = useNotification();
  const [data, setData] = useState<OrgUnitMaint | null>(null);
  const [chosen, setChosen] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | undefined>(undefined);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const d = await getOrgUnitMaint();
      setData(d);
      setChosen(d.defaultOrgUnitNo ?? '');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load your org units');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const onSave = async () => {
    setSaving(true);
    try {
      const d = await saveDefaultOrgUnit(chosen);
      setData(d);
      setChosen(d.defaultOrgUnitNo ?? '');
      notify.display({ kind: 'success', title: 'Save successful.', timeout: 5000 });
    } catch (e) {
      notify.display({
        kind: 'error',
        title: 'Could not save your default org unit.',
        subtitle: e instanceof Error ? e.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  const orgUnits = data?.orgUnits ?? [];

  return (
    <PageLayout
      title="Organization Unit Maintenance"
      subtitle="Choose your default organization unit. Search, Add New and the billing and audit screens start with it selected."
    >
      <SectionTile
        title="Default organization unit"
        icon={Location}
        actions={
          <Button
            size="md"
            renderIcon={Save}
            onClick={() => void onSave()}
            disabled={saving || loading || orgUnits.length === 0 || chosen === ''}
          >
            {saving ? 'Saving…' : 'Save'}
          </Button>
        }
      >
        <AsyncBoundary
          loading={loading}
          error={error}
          onRetry={() => void load()}
          loadingText="Loading your org units…"
        >
          {data && (
            <>
              <DefinitionGrid
                items={[{ label: 'User', value: <UserName userId={data.userId} /> }]}
              />
              {data.error ? (
                <InlineNotification
                  kind="error"
                  lowContrast
                  hideCloseButton
                  title="No org units."
                  subtitle={data.error}
                />
              ) : (
                <div className="bordered-table org-unit-maintenance__table">
                  <TableContainer>
                    <Table size="md" useZebraStyles>
                      <TableHead>
                        <TableRow>
                          <TableHeader>Default</TableHeader>
                          <TableHeader>Org Unit</TableHeader>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {orgUnits.map((o) => (
                          <TableRow key={o.code}>
                            <TableCell>
                              <RadioButton
                                id={`ou-default-${o.code}`}
                                name="ou-default"
                                value={o.code}
                                labelText={`Default: ${o.description}`}
                                hideLabel
                                checked={chosen === o.code}
                                disabled={saving}
                                onChange={() => setChosen(o.code)}
                              />
                            </TableCell>
                            <TableCell>
                              <label htmlFor={`ou-default-${o.code}`}>{o.description}</label>
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </div>
              )}
            </>
          )}
        </AsyncBoundary>
      </SectionTile>
    </PageLayout>
  );
};

export default OrgUnitMaintenance;
