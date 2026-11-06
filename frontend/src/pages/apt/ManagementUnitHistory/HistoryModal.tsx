import { Button, Checkbox, Stack, TextArea, TextInput } from '@carbon/react';
import { useEffect, useState, type FC } from 'react';

import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import DateField from '@/pages/apt/components/DateField';
import {
  createAptManagementUnitHistory,
  updateAptManagementUnitHistory,
  type AptManagementUnitHistory,
  type AptManagementUnitHistoryRequest,
} from '@/services/apt/managementUnits';

import { areaProblem, day, MAX_COMMENTS, numText, toNumber, wholeProblem } from './shared';

interface Form {
  effectiveDate: string;
  determinationDate: string;
  inventoryRevDate: string;
  apportionmentDeterminationDt: string;
  apportionmentEffectiveDate: string;
  aac: string;
  aacHa: string;
  longTermHarvestLevel: string;
  totalArea: string;
  productiveArea: string;
  currentNetOperatingArea: string;
  futureHarvestLandbaseArea: string;
  comments: string;
  /** Create only: copy the source determination's partitions and apportionments. */
  copy: boolean;
}

type Errors = Partial<Record<keyof Form, string>>;

const EMPTY: Form = {
  effectiveDate: '',
  determinationDate: '',
  inventoryRevDate: '',
  apportionmentDeterminationDt: '',
  apportionmentEffectiveDate: '',
  aac: '',
  aacHa: '',
  longTermHarvestLevel: '',
  totalArea: '',
  productiveArea: '',
  currentNetOperatingArea: '',
  futureHarvestLandbaseArea: '',
  comments: '',
  copy: false,
};

const formOf = (h: AptManagementUnitHistory): Form => ({
  effectiveDate: h.effectiveDate ?? '',
  determinationDate: h.determinationDate ?? '',
  inventoryRevDate: h.inventoryRevDate ?? '',
  apportionmentDeterminationDt: h.apportionmentDeterminationDt ?? '',
  apportionmentEffectiveDate: h.apportionmentEffectiveDate ?? '',
  aac: numText(h.allowableAnnualCut),
  aacHa: numText(h.allowableAnnualCutHa),
  longTermHarvestLevel: numText(h.longTermHarvestLevel),
  totalArea: numText(h.totalArea),
  productiveArea: numText(h.productiveArea),
  currentNetOperatingArea: numText(h.currentNetOperatingArea),
  futureHarvestLandbaseArea: numText(h.futureHarvestLandbaseArea),
  comments: h.comments ?? '',
  copy: false,
});

/** APT2's checks (SharedHistoryForm and its modal), with the areas' two decimals kept. */
const validate = (f: Form): Errors => {
  const e: Errors = {};
  if (!f.effectiveDate) e.effectiveDate = 'Effective Date is required.';
  if (!f.determinationDate) e.determinationDate = 'AAC Determination Date is required.';
  const checks: [keyof Form, string | null][] = [
    ['aac', wholeProblem('AAC (m³)', f.aac, true)],
    ['aacHa', wholeProblem('AAC (ha)', f.aacHa)],
    ['longTermHarvestLevel', wholeProblem('Long Term Harvest Level', f.longTermHarvestLevel)],
    ['totalArea', areaProblem('Total Area', f.totalArea)],
    ['productiveArea', areaProblem('Productive Area', f.productiveArea)],
    [
      'currentNetOperatingArea',
      areaProblem('Current Net Operating Area', f.currentNetOperatingArea),
    ],
    [
      'futureHarvestLandbaseArea',
      areaProblem('Future Harvest Landbase Area', f.futureHarvestLandbaseArea),
    ],
  ];
  checks.forEach(([k, problem]) => {
    if (problem) e[k] = problem;
  });
  if (f.comments.length > MAX_COMMENTS) e.comments = 'Comments cannot exceed 2000 characters.';
  return e;
};

const bodyOf = (f: Form, prevId: number | null): AptManagementUnitHistoryRequest => ({
  prevId,
  effectiveDate: f.effectiveDate,
  determinationDate: f.determinationDate,
  inventoryRevDate: f.inventoryRevDate || null,
  apportionmentDeterminationDt: f.apportionmentDeterminationDt || null,
  apportionmentEffectiveDate: f.apportionmentEffectiveDate || null,
  allowableAnnualCut: toNumber(f.aac) ?? 0,
  allowableAnnualCutHa: toNumber(f.aacHa),
  longTermHarvestLevel: toNumber(f.longTermHarvestLevel),
  totalArea: toNumber(f.totalArea),
  productiveArea: toNumber(f.productiveArea),
  currentNetOperatingArea: toNumber(f.currentNetOperatingArea),
  futureHarvestLandbaseArea: toNumber(f.futureHarvestLandbaseArea),
  comments: f.comments.trim() ? f.comments : null,
});

interface Props {
  muId: number | string;
  open: boolean;
  /** `edit` changes `source`; `create` adds a determination, starting from `source` if given. */
  mode: 'create' | 'edit';
  source: AptManagementUnitHistory | null;
  onClose: () => void;
  /** After a save, with the determination as stored. */
  onSaved: (saved: AptManagementUnitHistory) => void;
}

/**
 * A management unit's AAC determination, new or edited (APT2's NewManagementUnitHistoryModal and
 * the history screen's detail form). A new one starts from the selected determination's values,
 * as in APT2, and can also take a copy of its partitions and apportionments — what
 * APT_MGMT_UNIT_HISTORY_INS_UPD's previous-id parameter is for, which APT2 never sent.
 */
const HistoryModal: FC<Props> = ({ muId, open, mode, source, onClose, onSaved }) => {
  const { display } = useNotification();
  const [form, setForm] = useState<Form>(EMPTY);
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open) {
      setForm(source ? formOf(source) : EMPTY);
      setErrors({});
    }
  }, [open, source]);

  const set = <K extends keyof Form>(key: K, value: Form[K]) => {
    setForm((f) => ({ ...f, [key]: value }));
    setErrors((e) => {
      if (!e[key]) return e;
      const next = { ...e };
      delete next[key];
      return next;
    });
  };

  const close = () => {
    if (!saving) onClose();
  };

  const submit = async () => {
    const found = validate(form);
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    setSaving(true);
    try {
      const saved =
        mode === 'edit' && source
          ? await updateAptManagementUnitHistory(muId, source.id, bodyOf(form, null))
          : await createAptManagementUnitHistory(
              muId,
              bodyOf(form, form.copy && source ? source.id : null),
            );
      display({
        kind: 'success',
        title: mode === 'edit' ? 'Determination saved' : 'Determination added',
        timeout: 5000,
      });
      onSaved(saved);
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not save the determination',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  const text = (key: keyof Form, label: string, decimal = false) => (
    <TextInput
      id={`apt-muh-${key}`}
      labelText={label}
      inputMode={decimal ? 'decimal' : 'numeric'}
      value={form[key] as string}
      invalid={!!errors[key]}
      invalidText={errors[key]}
      disabled={saving}
      onChange={(e) => set(key, e.target.value)}
    />
  );
  const date = (key: keyof Form, label: string) => (
    <DateField
      id={`apt-muh-${key}`}
      labelText={label}
      value={form[key] as string}
      invalidText={errors[key]}
      disabled={saving}
      onChange={(v) => set(key, v)}
    />
  );

  return (
    <Modal
      open={open}
      passiveModal
      size="lg"
      className="detail-dialog"
      modalHeading={mode === 'edit' ? 'Edit AAC determination' : 'New AAC determination'}
      onRequestClose={close}
      preventCloseOnClickOutside
    >
      <Stack gap={5}>
        <div className="detail-dialog__pair">
          {date('effectiveDate', 'Effective date')}
          {date('determinationDate', 'AAC determination date')}
        </div>
        <div className="detail-dialog__pair">
          {text('aac', 'AAC (m³)')}
          {text('aacHa', 'AAC (ha)')}
        </div>
        <div className="detail-dialog__pair">
          {date('apportionmentDeterminationDt', 'Apportionment determination date')}
          {date('apportionmentEffectiveDate', 'Apportionment effective date')}
        </div>
        <div className="detail-dialog__pair">
          {date('inventoryRevDate', 'Inventory revision date')}
          {text('longTermHarvestLevel', 'Long term harvest level (m³)')}
        </div>
        <div className="detail-dialog__pair">
          {text('totalArea', 'Total area (ha)', true)}
          {text('productiveArea', 'Productive area (ha)', true)}
        </div>
        <div className="detail-dialog__pair">
          {text('currentNetOperatingArea', 'Current net operating area (ha)', true)}
          {text('futureHarvestLandbaseArea', 'Future harvest landbase area (ha)', true)}
        </div>
        <TextArea
          id="apt-muh-comments"
          labelText="Comments"
          value={form.comments}
          maxCount={MAX_COMMENTS}
          enableCounter
          invalid={!!errors.comments}
          invalidText={errors.comments}
          disabled={saving}
          onChange={(e) => set('comments', e.target.value)}
        />
        {mode === 'create' && source && (
          <Checkbox
            id="apt-muh-copy"
            labelText={`Copy the partitions, apportionments and BCTS distributions of the determination effective ${day(source.effectiveDate)}`}
            checked={form.copy}
            disabled={saving}
            onChange={(_, { checked }) => set('copy', checked)}
          />
        )}
      </Stack>
      <div className="detail-dialog__actions">
        <Button kind="tertiary" disabled={saving} onClick={close}>
          Cancel
        </Button>
        <Button kind="primary" disabled={saving} onClick={() => void submit()}>
          {saving ? 'Saving…' : 'Save'}
        </Button>
      </div>
    </Modal>
  );
};

export default HistoryModal;
