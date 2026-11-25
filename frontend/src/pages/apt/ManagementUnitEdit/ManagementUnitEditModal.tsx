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
  /** Not shown; an existing unit's is sent back unchanged so a save keeps it. */
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

/** APT2's checks (APTM1200). */
const validate = (f: Form, creating: boolean): Errors => {
  const e: Errors = {};
  if (creating && !f.code) e.code = 'Management Unit is required.';
  if (!f.orgUnitNo) e.orgUnitNo = 'Org Unit is required.';
  if (!f.status) e.status = 'Status is required.';
  if (!f.effectiveDate) e.effectiveDate = 'Status Effective Date is required.';
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

interface Props {
  open: boolean;
  /** The unit to edit; none to add one. */
  unitId?: number;
  onClose: () => void;
  /** Called with the unit as stored, after the dialog has closed. */
  onSaved: (saved: AptManagementUnit) => void;
}

/**
 * APTM1200 — add a management unit (picked from the code-table units APT has no record of)
 * or edit one, in a dialog over the search or the unit's page. Only APT administrators are
 * offered it. Type and number identify the unit, so they are fixed once it exists; APT2 let the
 * number be edited while still showing the old name, which could leave the unit nameless and
 * out of every search.
 */
const ManagementUnitEditModal: FC<Props> = ({ open, unitId, onClose, onSaved }) => {
  const muType = useMuTypeName();
  const creating = unitId === undefined;
  const { display } = useNotification();

  const [data, setData] = useState<Loaded | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [form, setForm] = useState<Form>(EMPTY);
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);

  // The unit and the lists, read each time the dialog opens.
  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setData(null);
    setLoadError(null);
    setForm(EMPTY);
    setErrors({});
    Promise.all([
      unitId !== undefined ? getAptManagementUnit(unitId) : Promise.resolve(null),
      getAptOrgUnits(),
      getManagementUnitStatuses(),
      searchAptManagementUnits(),
      unitId !== undefined ? Promise.resolve([]) : getAptUnassignedManagementUnits(),
    ])
      .then(([unit, orgUnits, statuses, sources, unassigned]) => {
        if (cancelled) return;
        setData({ unit, orgUnits, statuses, sources, unassigned });
        setForm(unit ? formOf(unit) : EMPTY);
      })
      .catch((err: unknown) => {
        if (!cancelled) setLoadError(err instanceof Error ? err.message : 'Request failed');
      });
    return () => {
      cancelled = true;
    };
  }, [open, unitId]);

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

  // Adding, with every code-table unit already in apportionment: nothing to pick.
  const nothingToAdd = creating && !!data && data.unassigned.length === 0;

  const close = () => {
    if (!saving) onClose();
  };

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
      const saved =
        unitId !== undefined
          ? await updateAptManagementUnit(unitId, body)
          : await createAptManagementUnit(body);
      display({
        kind: 'success',
        title: `Management unit ${saved.mgmtUnitName ?? ''} ${creating ? 'created' : 'saved'}`,
        timeout: 5000,
      });
      onClose();
      onSaved(saved);
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

  const sources = (data?.sources ?? []).filter((u) => u.id !== unitId);

  return (
    <Modal
      open={open}
      passiveModal
      size="lg"
      className="detail-dialog"
      modalHeading={creating ? 'Add management unit' : 'Edit management unit'}
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
              <SelectItem value="" text="— Select a management unit —" />
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
          {saving ? 'Saving…' : creating ? 'Create' : 'Save'}
        </Button>
      </div>
    </Modal>
  );
};

export default ManagementUnitEditModal;
