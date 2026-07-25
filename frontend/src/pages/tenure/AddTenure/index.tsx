import {
  Button,
  Column,
  DatePicker,
  DatePickerInput,
  Grid,
  Select,
  SelectItem,
  TextInput,
} from '@carbon/react';
import { Save, Reset } from '@carbon/icons-react';
import { useState, type FC, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import { canEdit } from '@/routes/access';
import PageLayout from '@/pages/PageLayout';
import { createTenure } from '@/services/tenure_write';
import './AddTenure.scss';

// value = the code stored/resolved by the backend; text = the display label.
const FILE_TYPES = [
  { code: 'FL', label: 'Forest Licence' },
  { code: 'TFL', label: 'Tree Farm Licence' },
  { code: 'WL', label: 'Woodlot Licence' },
  { code: 'CFA', label: 'Community Forest Agreement' },
  { code: 'FNWL', label: 'First Nations Woodland Licence' },
];

const ORG_UNITS = [
  { code: 'DCC', label: 'DCC — Cariboo-Chilcotin' },
  { code: 'DPG', label: 'DPG — Prince George' },
  { code: 'DND', label: 'DND — Nadina' },
  { code: 'DSQ', label: 'DSQ — Sea to Sky' },
  { code: 'DVA', label: 'DVA — Campbell River' },
  { code: 'DMK', label: 'DMK — Mackenzie' },
];

interface NewTenureForm {
  fileId: string;
  fileType: string;
  orgUnit: string;
  licensee: string;
  clientNumber: string;
  issueDate: string;
}

const EMPTY: NewTenureForm = {
  fileId: '',
  fileType: '',
  orgUnit: '',
  licensee: '',
  clientNumber: '',
  issueDate: '',
};

/**
 * FTA010 — Add New Tenure. Create form for a new forest file. Write action is
 * gated to FTA_ADMIN; on submit it POSTs to {@code /api/fta/tenures} and
 * returns to Tenure Search on success.
 */
const AddTenure: FC = () => {
  const { user } = useAuth();
  const notify = useNotification();
  const navigate = useNavigate();
  const [form, setForm] = useState<NewTenureForm>(EMPTY);
  const [saving, setSaving] = useState(false);
  const readOnly = !canEdit(user);

  const set = (f: keyof NewTenureForm) => (v: string) => setForm((s) => ({ ...s, [f]: v }));

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      const { forestFileId } = await createTenure({
        forestFileId: form.fileId,
        fileTypeCode: form.fileType,
        orgUnitCode: form.orgUnit,
        clientNumber: form.clientNumber,
        clientName: form.licensee,
        issueDate: form.issueDate || null,
      });
      notify.display({
        kind: 'success',
        title: 'Tenure created',
        subtitle: `File ${forestFileId} created.`,
        timeout: 5000,
      });
      navigate('/search/tenure');
    } catch (err) {
      notify.display({
        kind: 'error',
        title: 'Could not create tenure',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 6000,
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <PageLayout title="Add New Tenure">
      {readOnly && (
        <p className="add-tenure__readonly">
          You have read-only access. Creating a tenure requires the Administrator role.
        </p>
      )}
      <form className="add-tenure__form" onSubmit={onSubmit}>
        <Grid narrow>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="at-file" labelText="Forest File ID" placeholder="e.g. A19999"
              disabled={readOnly} value={form.fileId} onChange={(e) => set('fileId')(e.target.value)} required />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <Select id="at-type" labelText="File Type" disabled={readOnly}
              value={form.fileType} onChange={(e) => set('fileType')(e.target.value)}>
              <SelectItem value="" text="Choose a type" />
              {FILE_TYPES.map((t) => <SelectItem key={t.code} value={t.code} text={t.label} />)}
            </Select>
          </Column>
          <Column sm={4} md={4} lg={4}>
            <Select id="at-org" labelText="Org Unit" disabled={readOnly}
              value={form.orgUnit} onChange={(e) => set('orgUnit')(e.target.value)}>
              <SelectItem value="" text="Choose an org unit" />
              {ORG_UNITS.map((o) => <SelectItem key={o.code} value={o.code} text={o.label} />)}
            </Select>
          </Column>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="at-licensee" labelText="Licensee / Client" placeholder="e.g. West Fraser Mills Ltd."
              disabled={readOnly} value={form.licensee} onChange={(e) => set('licensee')(e.target.value)} />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="at-client" labelText="Client Number" placeholder="8-digit number"
              disabled={readOnly} value={form.clientNumber} onChange={(e) => set('clientNumber')(e.target.value)} />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <DatePicker datePickerType="single" onChange={(d) => set('issueDate')(d[0]?.toISOString().slice(0, 10) ?? '')}>
              <DatePickerInput id="at-issue" labelText="Issue Date" placeholder="yyyy-mm-dd" disabled={readOnly} />
            </DatePicker>
          </Column>
        </Grid>
        <div className="add-tenure__actions">
          <Button type="submit" renderIcon={Save} disabled={readOnly || saving}>
            {saving ? 'Creating…' : 'Create tenure'}
          </Button>
          <Button type="button" kind="ghost" renderIcon={Reset} onClick={() => setForm(EMPTY)} disabled={readOnly || saving}>Clear</Button>
        </div>
      </form>
    </PageLayout>
  );
};

export default AddTenure;
