import { Button, Column, Grid, Select, SelectItem, TextInput } from '@carbon/react';
import { Save, Reset } from '@carbon/icons-react';
import { useState, type FC, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import { canEdit } from '@/routes/access';
import PageLayout from '@/pages/PageLayout';
import { createMarkApplication } from '@/services/mark_write';
import './MarkApplication.scss';

// value = org_unit_code resolved by the backend; text = display label.
const ORG_UNITS = [
  { code: 'DCC', label: 'DCC — Cariboo-Chilcotin' },
  { code: 'DPG', label: 'DPG — Prince George' },
  { code: 'DND', label: 'DND — Nadina' },
  { code: 'DSQ', label: 'DSQ — Sea to Sky' },
  { code: 'DVA', label: 'DVA — Campbell River' },
  { code: 'DMK', label: 'DMK — Mackenzie' },
];

interface MarkForm {
  markNumber: string;
  holderName: string;
  holderClient: string;
  orgUnit: string;
  timberOrigin: string;
}

const EMPTY: MarkForm = { markNumber: '', holderName: '', holderClient: '', orgUnit: '', timberOrigin: '' };

/**
 * FTA510 — Private Mark Application. Create form for a new private timber mark.
 * Write action gated to FTA_ADMIN; on submit raises a notification (mock — no
 * backend yet) and returns to the mark list.
 */
const MarkApplication: FC = () => {
  const { user } = useAuth();
  const notify = useNotification();
  const navigate = useNavigate();
  const [form, setForm] = useState<MarkForm>(EMPTY);
  const [saving, setSaving] = useState(false);
  const readOnly = !canEdit(user);

  const set = (f: keyof MarkForm) => (v: string) => setForm((s) => ({ ...s, [f]: v }));

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      const { markNumber } = await createMarkApplication({
        markNumber: form.markNumber,
        holderName: form.holderName,
        holderClient: form.holderClient,
        orgUnit: form.orgUnit,
        timberOrigin: form.timberOrigin,
      });
      notify.display({
        kind: 'success',
        title: 'Mark application submitted',
        subtitle: `Mark ${markNumber || '(new)'} submitted.`,
        timeout: 5000,
      });
      navigate('/marks');
    } catch (err) {
      notify.display({
        kind: 'error',
        title: 'Could not submit mark application',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 6000,
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <PageLayout title="Mark Application">
      {readOnly && (
        <p className="mark-app__readonly">
          You have read-only access. Submitting a mark application requires the Administrator role.
        </p>
      )}
      <form className="mark-app__form" onSubmit={onSubmit}>
        <Grid narrow>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="ma-num" labelText="Mark Number" placeholder="e.g. 13 0092"
              disabled={readOnly} value={form.markNumber} onChange={(e) => set('markNumber')(e.target.value)} required />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="ma-holder" labelText="Holder Name" placeholder="e.g. Meadow Ranch Ltd."
              disabled={readOnly} value={form.holderName} onChange={(e) => set('holderName')(e.target.value)} />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="ma-client" labelText="Holder Client Number" placeholder="8-digit number"
              disabled={readOnly} value={form.holderClient} onChange={(e) => set('holderClient')(e.target.value)} />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <Select id="ma-org" labelText="Org Unit" disabled={readOnly}
              value={form.orgUnit} onChange={(e) => set('orgUnit')(e.target.value)}>
              <SelectItem value="" text="Choose an org unit" />
              {ORG_UNITS.map((o) => <SelectItem key={o.code} value={o.code} text={o.label} />)}
            </Select>
          </Column>
          <Column sm={4} md={4} lg={8}>
            <TextInput id="ma-origin" labelText="Timber Origin" placeholder="e.g. Private Land — fee simple"
              disabled={readOnly} value={form.timberOrigin} onChange={(e) => set('timberOrigin')(e.target.value)} />
          </Column>
        </Grid>
        <div className="mark-app__actions">
          <Button type="submit" renderIcon={Save} disabled={readOnly || saving}>
            {saving ? 'Submitting…' : 'Submit application'}
          </Button>
          <Button type="button" kind="ghost" renderIcon={Reset} onClick={() => setForm(EMPTY)} disabled={readOnly || saving}>Clear</Button>
        </div>
      </form>
    </PageLayout>
  );
};

export default MarkApplication;
