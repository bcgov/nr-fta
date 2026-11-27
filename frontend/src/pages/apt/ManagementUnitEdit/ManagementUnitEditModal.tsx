import {
  Button,
  InlineLoading,
  InlineNotification,
  Select,
  SelectItem,
  Stack,
  TextArea,
} from '@carbon/react';
import { useEffect, useState, type FC } from 'react';

import DateField from '@/components/DateField';
import DefinitionGrid from '@/components/DefinitionGrid';
import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import { dash, MAX_COMMENTS, unitLabel } from '@/pages/apt/ManagementUnitHistory/shared';
import { useMuTypeName } from '@/pages/apt/muTypes';
import {
  getAptOrgUnits,
  getManagementUnitStatuses,
  type AptCodeOption,
  type AptOrgUnit,
} from '@/services/apt/lookups';
import {
  createAptManagementUnit,
  getAptUnassignedManagementUnits,
  searchAptManagementUnits,
  type AptManagementUnit,
  type AptManagementUnitRequest,
  type AptUnassignedManagementUnit,
} from '@/services/apt/managementUnits';

interface Form {
  /** The unassigned unit picked, by its code. */
  code: string;
  muType: string;
  muNo: string;
  name: string;
  orgUnitNo: string;
  sourceId: string;
  status: string;
  effectiveDate: string;
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
  comments: '',
};

/** APT2's checks (APTM1200). */
const validate = (f: Form): Errors => {
  const e: Errors = {};
  if (!f.code) e.code = 'Management Unit is required.';
  if (!f.orgUnitNo) e.orgUnitNo = 'Org Unit is required.';
  if (!f.status) e.status = 'Status is required.';
  if (!f.effectiveDate) e.effectiveDate = 'Status Effective Date is required.';
  if (f.comments.length > MAX_COMMENTS) e.comments = 'Comments cannot exceed 2000 characters.';
  return e;
};

interface Loaded {
  orgUnits: AptOrgUnit[];
  statuses: AptCodeOption[];
  sources: AptManagementUnit[];
  unassigned: AptUnassignedManagementUnit[];
}

interface Props {
  open: boolean;
  onClose: () => void;
  /** Called with the unit as stored, after the dialog has closed. */
  onSaved: (saved: AptManagementUnit) => void;
}

/**
 * APTM1200 — add a management unit, picked from the code-table units APT has no record of, in a
 * dialog over the search. Only APT administrators are offered it. A unit is edited in place on
 * its own page (ManagementUnitHistory/useUnitEdit).
 */
const ManagementUnitEditModal: FC<Props> = ({ open, onClose, onSaved }) => {
  const muType = useMuTypeName();
  const { display } = useNotification();

  const [data, setData] = useState<Loaded | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [form, setForm] = useState<Form>(EMPTY);
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);

  // The lists, read each time the dialog opens.
  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setData(null);
    setLoadError(null);
    setForm(EMPTY);
    setErrors({});
    Promise.all([
      getAptOrgUnits(),
      getManagementUnitStatuses(),
      searchAptManagementUnits(),
      getAptUnassignedManagementUnits(),
    ])
      .then(([orgUnits, statuses, sources, unassigned]) => {
        if (cancelled) return;
        setData({ orgUnits, statuses, sources, unassigned });
      })
      .catch((err: unknown) => {
        if (!cancelled) setLoadError(err instanceof Error ? err.message : 'Request failed');
      });
    return () => {
      cancelled = true;
    };
  }, [open]);

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

  // Every code-table unit already in apportionment: nothing to pick.
  const nothingToAdd = !!data && data.unassigned.length === 0;

  const close = () => {
    if (!saving) onClose();
  };

  const submit = async () => {
    const found = validate(form);
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
      orcsFileNumber: null,
      comments: form.comments.trim() ? form.comments : null,
    };
    setSaving(true);
    try {
      const saved = await createAptManagementUnit(body);
      display({
        kind: 'success',
        title: `Management unit ${saved.mgmtUnitName ?? ''} created.`,
        timeout: 5000,
      });
      onClose();
      onSaved(saved);
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not create the management unit.',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  const sources = data?.sources ?? [];

  return (
    <Modal
      open={open}
      passiveModal
      size="lg"
      className="detail-dialog"
      modalHeading="Add management unit"
      onRequestClose={close}
      preventCloseOnClickOutside
    >
      {loadError ? (
        <InlineNotification
          kind="error"
          lowContrast
          hideCloseButton
          title="Couldn’t load the management unit."
          subtitle={loadError}
        />
      ) : !data ? (
        <InlineLoading description="Loading…" />
      ) : nothingToAdd ? (
        <InlineNotification
          kind="info"
          lowContrast
          hideCloseButton
          title="There are no management units to add."
          subtitle="Every management unit in FTA's code tables already has an apportionment record."
        />
      ) : (
        <Stack gap={5}>
          <Select
            id="apt-mu-edit-unit"
            labelText="Management unit"
            value={form.code}
            invalid={!!errors.code}
            invalidText={errors.code}
            disabled={saving}
            onChange={(e) => pick(e.target.value)}
          >
            <SelectItem value="" text="— Select a management unit —" />
            {data.unassigned.map((u) => (
              <SelectItem
                key={u.managementUnitCode}
                value={u.managementUnitCode}
                text={`${u.managementUnitName ?? u.managementUnitCode} (${u.managementUnitCode})`}
              />
            ))}
          </Select>
          {form.code && (
            <DefinitionGrid
              items={[
                { label: 'Management unit type', value: dash(muType(form.muType)) },
                { label: 'Management unit no.', value: dash(form.muNo) },
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
                <SelectItem key={s.code} value={s.code} text={s.description || s.code} />
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
        </Stack>
      )}
      <div className="detail-dialog__actions">
        <Button kind="tertiary" disabled={saving} onClick={close}>
          Cancel
        </Button>
        <Button
          kind="primary"
          disabled={saving || !data || nothingToAdd}
          onClick={() => void submit()}
        >
          {saving ? 'Saving…' : 'Create'}
        </Button>
      </div>
    </Modal>
  );
};

export default ManagementUnitEditModal;
