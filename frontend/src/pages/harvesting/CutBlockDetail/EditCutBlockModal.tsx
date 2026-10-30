import { Select, SelectItem, TextArea, TextInput } from '@carbon/react';
import { useEffect, useState, type FC } from 'react';

import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import { getBlockStatuses, type CodeOption } from '@/services/codeLists';
import {
  getCutRegulationCodes,
  getReforestDeclareTypes,
  saveCutBlock,
  type CutblockDetail,
} from '@/services/cutblock_detail';
import { getFireHarvestingReasons } from '@/services/tenure_cutblocks';
import { parseTypedDate } from '@/utils/typedDate';

interface Form {
  blockStatus: string;
  blockStatusDate: string;
  cutRegulationCode: string;
  reforestDeclareTypeCode: string;
  fireHarvestingReasonCode: string;
  underPartitionOrder: string;
  reportedFireDate: string;
  wasteAssessmentRequired: string;
  plannedGrossBlockArea: string;
  plannedNetBlockArea: string;
  plannedHarvestDate: string;
  disturbanceGrossArea: string;
  disturbanceStartDate: string;
  disturbanceEndDate: string;
  spExemptInd: string;
  cutBlockDescription: string;
  overrideReason: string;
}

const text = (v: string | number | null) => (v === null ? '' : String(v));

const initial = (b: CutblockDetail): Form => ({
  blockStatus: text(b.blockStatus),
  blockStatusDate: text(b.blockStatusDate),
  cutRegulationCode: text(b.cutRegulationCode),
  reforestDeclareTypeCode: text(b.reforestDeclareTypeCode),
  fireHarvestingReasonCode: text(b.fireHarvestingReasonCode),
  underPartitionOrder: text(b.underPartitionOrder),
  reportedFireDate: text(b.reportedFireDate),
  wasteAssessmentRequired: text(b.wasteAssessmentRequired),
  plannedGrossBlockArea: text(b.plannedGrossBlockArea),
  plannedNetBlockArea: text(b.plannedNetBlockArea),
  plannedHarvestDate: text(b.plannedHarvestDate),
  disturbanceGrossArea: text(b.disturbanceGrossArea),
  disturbanceStartDate: text(b.disturbanceStartDate),
  disturbanceEndDate: text(b.disturbanceEndDate),
  spExemptInd: text(b.spExemptInd),
  cutBlockDescription: text(b.cutBlockDescription),
  overrideReason: '',
});

const DATE_FIELDS = [
  'blockStatusDate',
  'reportedFireDate',
  'plannedHarvestDate',
  'disturbanceStartDate',
  'disturbanceEndDate',
] as const;

const NUMBER_FIELDS = [
  'plannedGrossBlockArea',
  'plannedNetBlockArea',
  'disturbanceGrossArea',
] as const;

const orNull = (v: string) => (v.trim() === '' ? null : v.trim());
const numberOrNull = (v: string) => (v.trim() === '' ? null : Number(v));
const dateOrNull = (v: string) => (v.trim() === '' ? null : parseTypedDate(v));

/** FTA904's editable fields. The backend's CutBlockRules decides what is locked and enforces it. */
const EditCutBlockModal: FC<{
  block: CutblockDetail;
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
}> = ({ block, open, onClose, onSaved }) => {
  const { display } = useNotification();
  const { rules } = block;
  const [form, setForm] = useState<Form>(() => initial(block));
  const [saving, setSaving] = useState(false);
  const [statuses, setStatuses] = useState<CodeOption[]>([]);
  const [cutRegulations, setCutRegulations] = useState<CodeOption[]>([]);
  const [reforestTypes, setReforestTypes] = useState<CodeOption[]>([]);
  const [fireReasons, setFireReasons] = useState<CodeOption[]>([]);

  useEffect(() => {
    if (open) setForm(initial(block));
  }, [open, block]);

  // Settled individually: a list that fails just leaves its select with the current value.
  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    const into = (setter: (v: CodeOption[]) => void) => (v: CodeOption[]) => {
      if (!cancelled) setter(v);
    };
    getBlockStatuses()
      .then(into(setStatuses))
      .catch(() => undefined);
    getCutRegulationCodes()
      .then(into(setCutRegulations))
      .catch(() => undefined);
    getReforestDeclareTypes()
      .then(into(setReforestTypes))
      .catch(() => undefined);
    getFireHarvestingReasons()
      .then(into(setFireReasons))
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [open]);

  const set = (key: keyof Form, value: string) => setForm((f) => ({ ...f, [key]: value }));

  const badDate = (key: (typeof DATE_FIELDS)[number]) =>
    form[key].trim() !== '' && parseTypedDate(form[key]) === null;
  const badNumber = (key: (typeof NUMBER_FIELDS)[number]) =>
    form[key].trim() !== '' && Number.isNaN(Number(form[key]));

  const valid =
    form.blockStatus !== '' &&
    !DATE_FIELDS.some((k) => badDate(k)) &&
    !NUMBER_FIELDS.some((k) => badNumber(k));
  const statusChanged = form.blockStatus !== (block.blockStatus ?? '');

  const submit = async () => {
    setSaving(true);
    try {
      const saved = await saveCutBlock(block.cbSkey, {
        blockStatus: form.blockStatus,
        blockStatusDate: dateOrNull(form.blockStatusDate),
        cutBlockDescription: orNull(form.cutBlockDescription),
        cutRegulationCode: orNull(form.cutRegulationCode),
        reforestDeclareTypeCode: orNull(form.reforestDeclareTypeCode),
        fireHarvestingReasonCode: orNull(form.fireHarvestingReasonCode),
        underPartitionOrder: orNull(form.underPartitionOrder),
        reportedFireDate: dateOrNull(form.reportedFireDate),
        wasteAssessmentRequired: orNull(form.wasteAssessmentRequired),
        plannedGrossBlockArea: numberOrNull(form.plannedGrossBlockArea),
        plannedNetBlockArea: numberOrNull(form.plannedNetBlockArea),
        plannedHarvestDate: dateOrNull(form.plannedHarvestDate),
        disturbanceGrossArea: numberOrNull(form.disturbanceGrossArea),
        disturbanceStartDate: dateOrNull(form.disturbanceStartDate),
        disturbanceEndDate: dateOrNull(form.disturbanceEndDate),
        spExemptInd: orNull(form.spExemptInd),
        overrideReason: orNull(form.overrideReason),
        cbRevisionCount: block.cbRevisionCount,
        cboaRevisionCount: block.cboaRevisionCount ?? 0,
      });
      display({
        kind: 'success',
        title: `Cut block ${saved.cutBlockId} saved`,
        subtitle: saved.warnings.join(' '),
        timeout: saved.warnings.length > 0 ? 9000 : 7000,
      });
      onSaved();
      onClose();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not save the cut block',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  const dateInput = (key: (typeof DATE_FIELDS)[number], label: string, disabled = false) => (
    <TextInput
      id={`cb-edit-${key}`}
      labelText={label}
      placeholder="yyyy-mm-dd"
      value={form[key]}
      disabled={disabled}
      invalid={badDate(key)}
      invalidText="Enter a date as yyyy-mm-dd."
      onChange={(e) => set(key, e.target.value)}
    />
  );

  const numberInput = (key: (typeof NUMBER_FIELDS)[number], label: string, disabled = false) => (
    <TextInput
      id={`cb-edit-${key}`}
      labelText={label}
      value={form[key]}
      disabled={disabled}
      invalid={badNumber(key)}
      invalidText="Enter a number."
      onChange={(e) => set(key, e.target.value)}
    />
  );

  const codeSelect = (key: keyof Form, label: string, options: CodeOption[], disabled = false) => (
    <Select
      id={`cb-edit-${key}`}
      labelText={label}
      value={form[key]}
      disabled={disabled}
      onChange={(e) => set(key, e.target.value)}
    >
      <SelectItem value="" text="" />
      {/* The block's own value stays selectable when its code is expired or a list failed. */}
      {form[key] && !options.some((o) => o.code === form[key]) && (
        <SelectItem value={form[key]} text={form[key]} />
      )}
      {options.map((o) => (
        <SelectItem key={o.code} value={o.code} text={o.description} />
      ))}
    </Select>
  );

  const yesNo = (key: keyof Form, label: string, disabled = false) => (
    <Select
      id={`cb-edit-${key}`}
      labelText={label}
      value={form[key]}
      disabled={disabled}
      onChange={(e) => set(key, e.target.value)}
    >
      <SelectItem value="" text="" />
      <SelectItem value="Y" text="Yes" />
      <SelectItem value="N" text="No" />
    </Select>
  );

  return (
    <Modal
      open={open}
      modalHeading={`Edit cut block ${block.cutBlockId ?? ''}`}
      primaryButtonText={saving ? 'Saving…' : 'Save'}
      secondaryButtonText="Cancel"
      primaryButtonDisabled={saving || !valid}
      onRequestClose={onClose}
      onSecondarySubmit={onClose}
      onRequestSubmit={submit}
    >
      <div style={{ display: 'grid', gap: '1rem' }}>
        {codeSelect('blockStatus', 'Status', statuses, rules.statusLocked)}
        {dateInput('blockStatusDate', 'As of')}
        {rules.seniorAuthority && statusChanged && (
          <TextInput
            id="cb-edit-overrideReason"
            labelText="District Override Reason"
            value={form.overrideReason}
            onChange={(e) => set('overrideReason', e.target.value)}
          />
        )}
        {numberInput('plannedGrossBlockArea', 'Planned Gross (ha)', rules.plannedLocked)}
        {numberInput('plannedNetBlockArea', 'Planned Net (ha)')}
        {dateInput('plannedHarvestDate', 'Planned Start', rules.plannedLocked)}
        {numberInput('disturbanceGrossArea', 'Logged Area (ha)', rules.disturbanceLocked)}
        {dateInput('disturbanceStartDate', 'Logged Start', rules.disturbanceLocked)}
        {dateInput('disturbanceEndDate', 'Logged Complete', rules.disturbanceLocked)}
        {yesNo('spExemptInd', 'SP Exempt', rules.spExemptLocked)}
        {codeSelect('cutRegulationCode', 'Cut Management', cutRegulations)}
        {block.harvestTypeCode === 'F' &&
          codeSelect('reforestDeclareTypeCode', 'Reforest Declare Type', reforestTypes)}
        {yesNo('wasteAssessmentRequired', "Waste Assessment Req'd")}
        {yesNo('underPartitionOrder', 'Under Partition Order')}
        {codeSelect('fireHarvestingReasonCode', 'Harvesting Reason', fireReasons)}
        {dateInput('reportedFireDate', 'Reported Fire Date')}
        <TextArea
          id="cb-edit-description"
          labelText="Block Description"
          maxCount={120}
          enableCounter
          value={form.cutBlockDescription}
          onChange={(e) => set('cutBlockDescription', e.target.value)}
        />
      </div>
    </Modal>
  );
};

export default EditCutBlockModal;
