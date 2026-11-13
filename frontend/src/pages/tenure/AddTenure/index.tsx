import { Save, Reset, DocumentAdd } from '@carbon/icons-react';
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
import { useEffect, useState, type FC, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';

import SectionTile from '@/components/SectionTile';
import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import PageLayout from '@/pages/PageLayout';
import { canEdit } from '@/routes/access';
import { getFileTypes, getOrgUnits, orgUnitCodeOf, type CodeOption } from '@/services/codeLists';
import { createTenure } from '@/services/tenure_write';
import { parseTypedDate, TYPED_DATE_PATTERN } from '@/utils/typedDate';
import './AddTenure.scss';

interface NewTenureForm {
  fileId: string;
  fileType: string;
  orgUnit: string;
  issueDate: string;
}

const EMPTY: NewTenureForm = {
  fileId: '',
  fileType: '',
  orgUnit: '',
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
  // THE.FILE_TYPE_CODE and THE.ORG_UNIT: the insert's file type is a foreign key,
  // and the backend resolves the org unit by its code, so both must be real values.
  const [fileTypes, setFileTypes] = useState<CodeOption[]>([]);
  const [orgUnits, setOrgUnits] = useState<CodeOption[]>([]);

  useEffect(() => {
    let cancelled = false;
    Promise.allSettled([getFileTypes(), getOrgUnits()]).then(([types, units]) => {
      if (cancelled) return;
      if (types.status === 'fulfilled') setFileTypes(types.value);
      if (units.status === 'fulfilled') setOrgUnits(units.value);
      if (types.status === 'rejected' || units.status === 'rejected') {
        notify.display({
          kind: 'error',
          title: 'Could not load the file type and organization unit lists',
          subtitle: 'Reload the page to try again.',
          timeout: 6000,
        });
      }
    });
    return () => {
      cancelled = true;
    };
    // Load once; notify is stable for the page's life.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const set = (f: keyof NewTenureForm) => (v: string) => setForm((s) => ({ ...s, [f]: v }));

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      const { forestFileId } = await createTenure({
        forestFileId: form.fileId,
        fileTypeCode: form.fileType,
        orgUnitCode: form.orgUnit,
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
    <PageLayout title="Add New Tenure" subtitle="Create a new forest tenure file.">
      {readOnly && (
        <p className="add-tenure__readonly">
          You have read-only access. Creating a tenure requires the Administrator role.
        </p>
      )}
      <SectionTile title="New tenure details" icon={DocumentAdd}>
        <form className="add-tenure__form" onSubmit={onSubmit}>
          <Grid narrow>
            <Column sm={4} md={4} lg={4}>
              <TextInput
                id="at-file"
                labelText="Forest File ID"
                placeholder="e.g. A19999"
                disabled={readOnly}
                value={form.fileId}
                onChange={(e) => set('fileId')(e.target.value)}
                required
              />
            </Column>
            <Column sm={4} md={4} lg={4}>
              <Select
                id="at-type"
                labelText="File Type"
                disabled={readOnly}
                value={form.fileType}
                onChange={(e) => set('fileType')(e.target.value)}
              >
                <SelectItem value="" text="Choose a type" />
                {fileTypes.map((t) => (
                  <SelectItem key={t.code} value={t.code} text={t.description} />
                ))}
              </Select>
            </Column>
            <Column sm={4} md={4} lg={4}>
              <Select
                id="at-org"
                labelText="Organization Unit"
                disabled={readOnly}
                value={form.orgUnit}
                onChange={(e) => set('orgUnit')(e.target.value)}
              >
                <SelectItem value="" text="Choose an organization unit" />
                {orgUnits.map((o) => (
                  <SelectItem key={o.code} value={orgUnitCodeOf(o)} text={o.description} />
                ))}
              </Select>
            </Column>
            <Column sm={4} md={4} lg={4}>
              <DatePicker
                datePickerType="single"
                dateFormat="Y-m-d"
                value={form.issueDate ? [form.issueDate] : []}
                onChange={(d) => set('issueDate')(d[0]?.toISOString().slice(0, 10) ?? '')}
              >
                <DatePickerInput
                  id="at-issue"
                  labelText="Issue Date"
                  placeholder="yyyy-mm-dd"
                  pattern={TYPED_DATE_PATTERN}
                  disabled={readOnly}
                  onChange={(e) => {
                    const text = e.target.value;
                    if (text.trim() === '') set('issueDate')('');
                    else {
                      const typed = parseTypedDate(text);
                      if (typed) set('issueDate')(typed);
                    }
                  }}
                />
              </DatePicker>
            </Column>
          </Grid>
          <div className="add-tenure__actions">
            <Button type="submit" size="md" renderIcon={Save} disabled={readOnly || saving}>
              {saving ? 'Creating…' : 'Create tenure'}
            </Button>
            <Button
              type="button"
              size="md"
              kind="ghost"
              renderIcon={Reset}
              onClick={() => setForm(EMPTY)}
              disabled={readOnly || saving}
            >
              Clear
            </Button>
          </div>
        </form>
      </SectionTile>
    </PageLayout>
  );
};

export default AddTenure;
