import { ArrowLeft, Enterprise } from '@carbon/icons-react';
import {
  Button,
  InlineNotification,
  Select,
  SelectItem,
  Stack,
  TextArea,
  TextInput,
} from '@carbon/react';
import { useCallback, useEffect, useState, type FC } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import DateField from '@/components/DateField';
import DefinitionGrid from '@/components/DefinitionGrid';
import { SectionTile } from '@/components/SectionTile';
import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import { dash, MAX_COMMENTS, unitLabel } from '@/pages/apt/ManagementUnitHistory/shared';
import PageLayout from '@/pages/PageLayout';
import { canEditApt } from '@/routes/access';
import { aptPaths } from '@/routes/aptPaths';
import {
  getAptOrgUnits,
  getManagementUnitStatuses,
  type AptCodeOption,
  type AptOrgUnit,
} from '@/services/apt/lookups';
import {
  createAptManagementUnit,
  getAptManagementUnit,
  getAptUnassignedManagementUnits,
  searchAptManagementUnits,
  updateAptManagementUnit,
  type AptManagementUnit,
  type AptManagementUnitRequest,
  type AptUnassignedManagementUnit,
} from '@/services/apt/managementUnits';

interface Form {
  /** Create only: the unassigned unit picked, by its code. */
  code: string;
  muType: string;
  muNo: string;
  name: string;
  orgUnitNo: string;
  sourceId: string;
  status: string;
  effectiveDate: string;
  orcs: string;
  comments: string;
}

type Errors = Partial<Record<keyof Form, string>>;

const EMPTY: Form = {
  code: '',
  muType: '',
  muNo: '',
  name: '',
  orgUnitNo: '',
  sourceId: '',
  status: '',
  effectiveDate: '',
  orcs: '',
  comments: '',
};

const formOf = (u: AptManagementUnit): Form => ({
  code: '',
  muType: u.aptMgmtUnitSt,
  muNo: u.mgmtUnitNumber,
  name: u.mgmtUnitName ?? '',
  orgUnitNo: u.orgUnitNo == null ? '' : String(u.orgUnitNo),
  sourceId: u.muId == null ? '' : String(u.muId),
  status: u.mgmtUnitStatusSt,
  effectiveDate: u.statusEffectiveDate ?? '',
  orcs: u.orcsFileNumber ?? '',
  comments: u.comments ?? '',
});

/** APT2's checks (APTM1200), plus the ORCS column's 13 characters. */
const validate = (f: Form, creating: boolean): Errors => {
  const e: Errors = {};
  if (creating && !f.code) e.code = 'Management Unit is required.';
  if (!f.orgUnitNo) e.orgUnitNo = 'Org Unit is required.';
  if (!f.status) e.status = 'Status is required.';
  if (!f.effectiveDate) e.effectiveDate = 'Status Effective Date is required.';
  if (f.orcs.trim().length > 13) e.orcs = 'ORCS File # can be at most 13 characters.';
  if (f.comments.length > MAX_COMMENTS) e.comments = 'Comments cannot exceed 2000 characters.';
  return e;
};

interface Loaded {
  unit: AptManagementUnit | null;
  orgUnits: AptOrgUnit[];
  statuses: AptCodeOption[];
  sources: AptManagementUnit[];
  unassigned: AptUnassignedManagementUnit[];
}

/**
 * APTM1200 — create a management unit (picked from the code-table units APT has no record of)
 * or edit one. Only APT administrators reach the form. Type and number identify the unit, so
 * they are fixed once it exists; APT2 let the number be edited while still showing the old
 * name, which could leave the unit nameless and out of every search.
 */
const ManagementUnitEdit: FC = () => {
  const { muId } = useParams();
  const creating = !muId;
  const { user } = useAuth();
  const canEdit = canEditApt(user);
  const navigate = useNavigate();
  const { display } = useNotification();

  const fetcher = useCallback(async (): Promise<Loaded> => {
    const [unit, orgUnits, statuses, sources, unassigned] = await Promise.all([
      muId ? getAptManagementUnit(muId) : Promise.resolve(null),
      getAptOrgUnits(),
      getManagementUnitStatuses(),
      searchAptManagementUnits(),
      muId ? Promise.resolve([]) : getAptUnassignedManagementUnits(),
    ]);
    return { unit, orgUnits, statuses, sources, unassigned };
  }, [muId]);
  const { data, loading, error, reload } = useApiResource(fetcher, [muId, canEdit]);

  const [form, setForm] = useState<Form>(EMPTY);
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setForm(data?.unit ? formOf(data.unit) : EMPTY);
    setErrors({});
  }, [data]);

  const set = <K extends keyof Form>(key: K, value: Form[K]) => {
    setForm((f) => ({ ...f, [key]: value }));
    setErrors((e) => {
      if (!e[key]) return e;
      const next = { ...e };
      delete next[key];
      return next;
    });
  };

  const pick = (code: string) => {
    const u = data?.unassigned.find((x) => x.managementUnitCode === code);
    setForm((f) => ({
      ...f,
      code,
      muType: u?.managementUnitType ?? '',
      muNo: u?.managementUnitNumber ?? '',
      name: u?.managementUnitName ?? '',
    }));
    setErrors((e) => ({ ...e, code: undefined }));
  };

  const back = muId ? aptPaths.managementUnitHistory(muId) : aptPaths.managementUnits();

  const submit = async () => {
    const found = validate(form, creating);
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    const body: AptManagementUnitRequest = {
      orgUnitNo: Number(form.orgUnitNo),
      muType: form.muType,
      muNo: form.muNo,
      status: form.status,
      effectiveDate: form.effectiveDate,
      muId: form.sourceId ? Number(form.sourceId) : null,
      orcsFileNumber: form.orcs.trim() || null,
      comments: form.comments.trim() ? form.comments : null,
    };
    setSaving(true);
    try {
      const saved = muId
        ? await updateAptManagementUnit(muId, body)
        : await createAptManagementUnit(body);
      display({
        kind: 'success',
        title: `Management unit ${saved.mgmtUnitName ?? ''} ${creating ? 'created' : 'saved'}`,
        timeout: 5000,
      });
      navigate(aptPaths.managementUnitHistory(saved.id));
    } catch (err) {
      display({
        kind: 'error',
        title: `Could not ${creating ? 'create' : 'save'} the management unit`,
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  const title = creating ? 'New Management Unit' : 'Edit Management Unit';
  const backLink = (
    <Link to={back} className="back-link">
      <ArrowLeft size={16} /> Back to {creating ? 'Management Units' : 'Management Unit History'}
    </Link>
  );

  if (!canEdit) {
    return (
      <PageLayout title={title} backLink={backLink}>
        <InlineNotification
          kind="info"
          lowContrast
          hideCloseButton
          title="Read only"
          subtitle="Creating and editing management units is for APT administrators."
        />
      </PageLayout>
    );
  }

  const sources = (data?.sources ?? []).filter((u) => String(u.id) !== muId);

  return (
    <PageLayout title={title} backLink={backLink}>
      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Loading management unit…"
      >
        {data && (
          <SectionTile title="Management unit" icon={Enterprise}>
            <Stack gap={5}>
              {creating ? (
                <Select
                  id="apt-mu-edit-unit"
                  labelText="Management unit"
                  value={form.code}
                  invalid={!!errors.code}
                  invalidText={errors.code}
                  disabled={saving}
                  onChange={(e) => pick(e.target.value)}
                >
                  <SelectItem
                    value=""
                    text={
                      data.unassigned.length
                        ? '— Select a management unit —'
                        : 'Every management unit is already in APT'
                    }
                  />
                  {data.unassigned.map((u) => (
                    <SelectItem
                      key={u.managementUnitCode}
                      value={u.managementUnitCode}
                      text={`${u.managementUnitName ?? u.managementUnitCode} (${u.managementUnitCode})`}
                    />
                  ))}
                </Select>
              ) : null}
              {(!creating || form.code) && (
                <DefinitionGrid
                  items={[
                    { label: 'MU type', value: dash(form.muType) },
                    { label: 'MU no.', value: dash(form.muNo) },
                    { label: 'Name', value: dash(form.name) },
                  ]}
                />
              )}
              <div className="detail-dialog__pair">
                <Select
                  id="apt-mu-edit-org"
                  labelText="Org unit"
                  value={form.orgUnitNo}
                  invalid={!!errors.orgUnitNo}
                  invalidText={errors.orgUnitNo}
                  disabled={saving}
                  onChange={(e) => set('orgUnitNo', e.target.value)}
                >
                  <SelectItem value="" text="— Select an org unit —" />
                  {data.orgUnits.map((o) => (
                    <SelectItem
                      key={o.orgUnitNo}
                      value={String(o.orgUnitNo)}
                      text={o.displayName ?? o.code}
                    />
                  ))}
                </Select>
                <Select
                  id="apt-mu-edit-source"
                  labelText="Source management unit"
                  value={form.sourceId}
                  disabled={saving}
                  onChange={(e) => set('sourceId', e.target.value)}
                >
                  <SelectItem value="" text="— None —" />
                  {sources.map((u) => (
                    <SelectItem key={u.id} value={String(u.id)} text={unitLabel(u)} />
                  ))}
                </Select>
              </div>
              <div className="detail-dialog__pair">
                <Select
                  id="apt-mu-edit-status"
                  labelText="Status"
                  value={form.status}
                  invalid={!!errors.status}
                  invalidText={errors.status}
                  disabled={saving}
                  onChange={(e) => set('status', e.target.value)}
                >
                  <SelectItem value="" text="— Select a status —" />
                  {data.statuses.map((s) => (
                    <SelectItem
                      key={s.code}
                      value={s.code}
                      text={s.description ? `${s.code} - ${s.description}` : s.code}
                    />
                  ))}
                </Select>
                <DateField
                  id="apt-mu-edit-effective"
                  labelText="Status effective date"
                  value={form.effectiveDate}
                  invalidText={errors.effectiveDate}
                  disabled={saving}
                  onChange={(v) => set('effectiveDate', v)}
                />
              </div>
              <div className="detail-dialog__pair">
                <TextInput
                  id="apt-mu-edit-orcs"
                  labelText="ORCS file #"
                  value={form.orcs}
                  maxLength={13}
                  invalid={!!errors.orcs}
                  invalidText={errors.orcs}
                  disabled={saving}
                  onChange={(e) => set('orcs', e.target.value)}
                />
              </div>
              <TextArea
                id="apt-mu-edit-comments"
                labelText="Comments"
                value={form.comments}
                maxCount={MAX_COMMENTS}
                enableCounter
                invalid={!!errors.comments}
                invalidText={errors.comments}
                disabled={saving}
                onChange={(e) => set('comments', e.target.value)}
              />
              <div className="detail-dialog__actions">
                <Button kind="tertiary" disabled={saving} onClick={() => navigate(back)}>
                  Cancel
                </Button>
                <Button kind="primary" disabled={saving} onClick={() => void submit()}>
                  {saving ? 'Saving…' : creating ? 'Create' : 'Save'}
                </Button>
              </div>
            </Stack>
          </SectionTile>
        )}
      </AsyncBoundary>
    </PageLayout>
  );
};

export default ManagementUnitEdit;
