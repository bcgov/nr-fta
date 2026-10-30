import { Button, Checkbox, Select, SelectItem, Stack, TextInput } from '@carbon/react';
import { useEffect, useState, type FC } from 'react';

import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import {
  getHarvestAuthStatuses,
  getLicenceToCutCodes,
  getMarkingInstruments,
  getMarkingMethods,
  getSalvageTypes,
  type CodeOption,
} from '@/services/codeLists';
import {
  getPermitLookups,
  savePermit,
  type CuttingPermitDetail,
  type PermitLookups,
} from '@/services/cutting_permit_detail';
import { parseTypedDate, TYPED_DATE_PATTERN } from '@/utils/typedDate';

/** Legacy FTA902's limits; the backend enforces them too. */
const MAX_LOCATION = 50;
const AREA_PATTERN = /^\d{1,8}(\.\d{1,4})?$/;
const WHOLE_PATTERN = /^\d{1,4}$/;

interface Form {
  status: string;
  statusDate: string;
  issueDate: string;
  termYears: string;
  termMonths: string;
  expiryDate: string;
  extendDate: string;
  extendReason: string;
  extendCount: string;
  feeWaived: string;
  feePaid: string;
  quota: string;
  salvage: string;
  zone: string;
  location: string;
  area: string;
  mgmtType: string;
  mgmtId: string;
  purpose: string;
  method: string;
  instrument: string;
  region: string;
  deciduous: boolean;
  catastrophic: boolean;
  cruiseBased: boolean;
  frz: boolean;
}

type Errors = Partial<Record<keyof Form, string>>;

const str = (v: string | number | null | undefined) =>
  v === null || v === undefined ? '' : `${v}`;
const orNull = (v: string) => v.trim() || null;

const formOf = (cp: CuttingPermitDetail): Form => ({
  status: str(cp.statusCode),
  statusDate: str(cp.statusDate),
  issueDate: str(cp.issueDate),
  termYears: str(cp.tenureTermYears),
  termMonths: str(cp.tenureTermMonths),
  expiryDate: str(cp.expiryDate),
  extendDate: str(cp.extendDate),
  extendReason: str(cp.extendReasonCode),
  extendCount: str(cp.extendCount),
  feeWaived: str(cp.extAppFeeWaived) || 'U',
  feePaid: str(cp.extAppFeePaid) || 'U',
  quota: str(cp.quotaTypeCode),
  salvage: str(cp.salvageTypeCode),
  zone: str(cp.districtAdmnZone),
  location: str(cp.location),
  area: str(cp.harvestArea),
  mgmtType: str(cp.mgmtUnitTypeCode),
  mgmtId: str(cp.mgmtUnitId),
  purpose: str(cp.licenceToCutCode),
  method: str(cp.markingMethodCode),
  instrument: str(cp.markingInstrumentCode),
  region: str(cp.crownLandsRegionCode),
  deciduous: cp.deciduousInd === 'Y',
  catastrophic: cp.catastrophicInd === 'Y',
  cruiseBased: cp.cruiseBasedInd === 'Y',
  frz: cp.withinFibreRecoveryZone === 'Y',
});

const validate = (f: Form): Errors => {
  const e: Errors = {};
  if (!f.status) e.status = 'Status is mandatory.';
  const dates: (keyof Form)[] = ['statusDate', 'issueDate', 'expiryDate', 'extendDate'];
  dates.forEach((k) => {
    const v = f[k] as string;
    if (v.trim() && !parseTypedDate(v)) e[k] = 'Enter a date as yyyy-mm-dd.';
  });
  const whole: (keyof Form)[] = ['termYears', 'termMonths', 'extendCount'];
  whole.forEach((k) => {
    const v = f[k] as string;
    if (v.trim() && !WHOLE_PATTERN.test(v.trim())) e[k] = 'Enter a whole number.';
  });
  if (f.area.trim() && !AREA_PATTERN.test(f.area.trim()))
    e.area = 'Enter hectares, with up to 4 decimal places.';
  return e;
};

const FEE_OPTIONS = [
  { code: 'U', text: 'Unknown' },
  { code: 'Y', text: 'Yes' },
  { code: 'N', text: 'No' },
];

interface Lists {
  statuses: CodeOption[];
  salvage: CodeOption[];
  methods: CodeOption[];
  instruments: CodeOption[];
  purposes: CodeOption[];
  lookups: PermitLookups;
}

interface Props {
  cp: CuttingPermitDetail;
  open: boolean;
  onClose: () => void;
  /** Call after a save, to re-read the permit. */
  onSaved: () => void;
}

/**
 * FTA902's editable fields, in a dialog. Each field follows the permit's rules; the backend
 * refuses a change to one they lock. Legacy's PL/SQL decides the rest on save (status
 * transitions, term limits, expiry against the tenure), so its message is what comes back.
 *
 * District is not here: legacy's District field never reached FTA_902_CP_DETAIL.SAVE's UPDATE.
 */
const PermitEditModal: FC<Props> = ({ cp, open, onClose, onSaved }) => {
  const { display } = useNotification();
  const rules = cp.rules;
  const [form, setForm] = useState<Form>(() => formOf(cp));
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);
  const [lists, setLists] = useState<Lists | null>(null);

  useEffect(() => {
    if (open) {
      setForm(formOf(cp));
      setErrors({});
    }
  }, [open, cp]);

  useEffect(() => {
    if (!open || lists) return;
    let cancelled = false;
    Promise.all([
      getHarvestAuthStatuses(),
      getSalvageTypes(),
      getMarkingMethods(),
      getMarkingInstruments(),
      getLicenceToCutCodes(),
      getPermitLookups(),
    ])
      .then(([statuses, salvage, methods, instruments, purposes, lookups]) => {
        if (!cancelled) setLists({ statuses, salvage, methods, instruments, purposes, lookups });
      })
      .catch((err) => {
        if (cancelled) return;
        display({
          kind: 'error',
          title: 'Could not load the form',
          subtitle: err instanceof Error ? err.message : 'Request failed',
          timeout: 9000,
        });
      });
    return () => {
      cancelled = true;
    };
  }, [open, lists, display]);

  const set = <K extends keyof Form>(key: K, value: Form[K]) => {
    setForm((f) => ({ ...f, [key]: value }));
    setErrors((e) => {
      if (!e[key]) return e;
      const next = { ...e };
      delete next[key];
      return next;
    });
  };

  // Fta902CpDetailAction: a new extended date's fee answers start over, and a waived fee is unpaid.
  const setExtendDate = (value: string) =>
    setForm((f) => ({
      ...f,
      extendDate: value,
      feeWaived: value === str(cp.extendDate) ? f.feeWaived : 'U',
      feePaid: value === str(cp.extendDate) ? f.feePaid : 'U',
    }));
  const setFeeWaived = (value: string) =>
    setForm((f) => ({ ...f, feeWaived: value, feePaid: value === 'Y' ? 'N' : f.feePaid }));

  const close = () => {
    if (!saving) onClose();
  };

  const submit = async () => {
    const found = validate(form);
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    const date = (v: string) => (v.trim() ? parseTypedDate(v) : null);
    setSaving(true);
    try {
      const { warnings } = await savePermit(cp.hvaSkey, {
        revisionCount: cp.revisionCount,
        statusCode: form.status,
        statusDate: date(form.statusDate),
        issueDate: date(form.issueDate),
        expiryDate: date(form.expiryDate),
        extendDate: date(form.extendDate),
        extendReasonCode: orNull(form.extendReason),
        extendCount: orNull(form.extendCount),
        termYears: orNull(form.termYears),
        termMonths: orNull(form.termMonths),
        quotaTypeCode: orNull(form.quota),
        salvageTypeCode: orNull(form.salvage),
        deciduousInd: form.deciduous ? 'Y' : 'N',
        catastrophicInd: form.catastrophic ? 'Y' : 'N',
        cruiseBasedInd: form.cruiseBased ? 'Y' : 'N',
        crownLandsRegionCode: orNull(form.region),
        markingMethodCode: orNull(form.method),
        markingInstrumentCode: orNull(form.instrument),
        districtAdmnZone: orNull(form.zone),
        location: orNull(form.location),
        harvestArea: orNull(form.area),
        mgmtUnitTypeCode: orNull(form.mgmtType),
        mgmtUnitId: orNull(form.mgmtId),
        purposeCode: orNull(form.purpose),
        extAppFeeWaived: orNull(form.feeWaived),
        extAppFeePaid: orNull(form.feePaid),
        withinFibreRecoveryZone: form.frz ? 'Y' : 'N',
      });
      display({
        kind: warnings.length > 0 ? 'warning' : 'success',
        title: `Cutting permit ${cp.cuttingPermitId ?? ''} saved`,
        subtitle: warnings.join(' '),
        timeout: 7000,
      });
      onClose();
      onSaved();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not save the cutting permit',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  const loading = open && !lists;
  const options = (items: CodeOption[] | undefined) =>
    (items ?? []).map((o) => (
      <SelectItem key={o.code} value={o.code} text={o.description || o.code} />
    ));
  const choose = (text: string) => <SelectItem value="" text={loading ? 'Loading…' : text} />;
  const dateInput = (
    key: 'statusDate' | 'issueDate' | 'expiryDate',
    label: string,
    off: boolean,
  ) => (
    <TextInput
      id={`cp-edit-${key}`}
      labelText={label}
      placeholder="yyyy-mm-dd"
      pattern={TYPED_DATE_PATTERN}
      value={form[key]}
      invalid={!!errors[key]}
      invalidText={errors[key]}
      disabled={saving || off}
      onChange={(e) => set(key, e.target.value)}
    />
  );
  const termsOff = rules.termsLocked;

  return (
    <Modal
      open={open}
      passiveModal
      size="lg"
      className="detail-dialog"
      modalHeading="Edit cutting permit"
      onRequestClose={close}
      preventCloseOnClickOutside
    >
      <Stack gap={5}>
        <div className="detail-dialog__pair">
          <Select
            id="cp-edit-status"
            labelText="Status"
            value={form.status}
            invalid={!!errors.status}
            invalidText={errors.status}
            disabled={saving || loading}
            onChange={(e) => set('status', e.target.value)}
          >
            {choose('— Select status —')}
            {options(lists?.statuses)}
          </Select>
          {dateInput('statusDate', 'As of', false)}
        </div>
        <div className="detail-dialog__pair">
          {dateInput('issueDate', 'Issue Date', termsOff)}
          {dateInput('expiryDate', 'Expires', termsOff)}
        </div>
        <div className="detail-dialog__pair">
          <TextInput
            id="cp-edit-years"
            labelText="Mark Term (years)"
            inputMode="numeric"
            value={form.termYears}
            invalid={!!errors.termYears}
            invalidText={errors.termYears}
            disabled={saving || termsOff}
            onChange={(e) => set('termYears', e.target.value)}
          />
          <TextInput
            id="cp-edit-months"
            labelText="Mark Term (months)"
            inputMode="numeric"
            value={form.termMonths}
            invalid={!!errors.termMonths}
            invalidText={errors.termMonths}
            disabled={saving || termsOff}
            onChange={(e) => set('termMonths', e.target.value)}
          />
        </div>
        <div className="detail-dialog__pair">
          <TextInput
            id="cp-edit-extend"
            labelText="Extended Date"
            placeholder="yyyy-mm-dd"
            pattern={TYPED_DATE_PATTERN}
            value={form.extendDate}
            invalid={!!errors.extendDate}
            invalidText={errors.extendDate}
            disabled={saving || !rules.canEditExtend}
            onChange={(e) => setExtendDate(e.target.value)}
          />
          <Select
            id="cp-edit-reason"
            labelText="Extension Reason"
            value={form.extendReason}
            disabled={saving || loading || !rules.canEditExtend}
            onChange={(e) => set('extendReason', e.target.value)}
          >
            {choose('— None —')}
            {options(lists?.lookups.extendReasons)}
          </Select>
        </div>
        <div className="detail-dialog__pair">
          <TextInput
            id="cp-edit-count"
            labelText="Extension Count"
            inputMode="numeric"
            value={form.extendCount}
            invalid={!!errors.extendCount}
            invalidText={errors.extendCount}
            disabled={saving || termsOff}
            onChange={(e) => set('extendCount', e.target.value)}
          />
          <Select
            id="cp-edit-waived"
            labelText="CP Extension Application Fee Waived"
            value={form.feeWaived}
            disabled={saving}
            onChange={(e) => setFeeWaived(e.target.value)}
          >
            {FEE_OPTIONS.map((o) => (
              <SelectItem key={o.code} value={o.code} text={o.text} />
            ))}
          </Select>
        </div>
        <div className="detail-dialog__pair">
          <Select
            id="cp-edit-paid"
            labelText="CP Extension Application Fee Paid"
            value={form.feePaid}
            disabled={saving}
            onChange={(e) => set('feePaid', e.target.value)}
          >
            {FEE_OPTIONS.map((o) => (
              <SelectItem key={o.code} value={o.code} text={o.text} />
            ))}
          </Select>
          <Select
            id="cp-edit-quota"
            labelText="Quota Type"
            value={form.quota}
            disabled={saving || loading}
            onChange={(e) => set('quota', e.target.value)}
          >
            {choose('— Default for the file type —')}
            {options(lists?.lookups.quotaTypes)}
          </Select>
        </div>
        <div className="detail-dialog__pair">
          <Select
            id="cp-edit-salvage"
            labelText="Salvage Type"
            value={form.salvage}
            disabled={saving || loading}
            onChange={(e) => set('salvage', e.target.value)}
          >
            {choose('— None —')}
            {options(lists?.salvage)}
          </Select>
          <TextInput
            id="cp-edit-zone"
            labelText="Zone"
            value={form.zone}
            disabled={saving}
            onChange={(e) => set('zone', e.target.value)}
          />
        </div>
        <TextInput
          id="cp-edit-location"
          labelText="Location"
          value={form.location}
          maxLength={MAX_LOCATION}
          disabled={saving}
          onChange={(e) => set('location', e.target.value)}
        />
        <div className="detail-dialog__pair">
          <TextInput
            id="cp-edit-area"
            labelText="Area (Hectares)"
            inputMode="decimal"
            value={form.area}
            invalid={!!errors.area}
            invalidText={errors.area}
            disabled={saving || !rules.canEditArea}
            onChange={(e) => set('area', e.target.value)}
          />
          <Select
            id="cp-edit-region"
            labelText="Land Region"
            value={form.region}
            disabled={saving || loading}
            onChange={(e) => set('region', e.target.value)}
          >
            {choose('— None —')}
            {options(lists?.lookups.crownLandsRegions)}
          </Select>
        </div>
        <div className="detail-dialog__pair">
          <TextInput
            id="cp-edit-mgmt-type"
            labelText="Management Unit Type"
            value={form.mgmtType}
            disabled={saving}
            onChange={(e) => set('mgmtType', e.target.value.toUpperCase())}
          />
          <TextInput
            id="cp-edit-mgmt-id"
            labelText="Management Unit"
            value={form.mgmtId}
            disabled={saving}
            onChange={(e) => set('mgmtId', e.target.value.toUpperCase())}
          />
        </div>
        <div className="detail-dialog__pair">
          <Select
            id="cp-edit-method"
            labelText="Compliance Method"
            value={form.method}
            disabled={saving || loading || !rules.canEditMarking}
            onChange={(e) => set('method', e.target.value)}
          >
            {choose('— None —')}
            {options(lists?.methods)}
          </Select>
          <Select
            id="cp-edit-instrument"
            labelText="Marking Instrument"
            value={form.instrument}
            disabled={saving || loading || !rules.canEditMarking}
            onChange={(e) => set('instrument', e.target.value)}
          >
            {choose('— None —')}
            {options(lists?.instruments)}
          </Select>
        </div>
        {rules.canEditPurpose && (
          <Select
            id="cp-edit-purpose"
            labelText="Purpose"
            value={form.purpose}
            disabled={saving || loading}
            onChange={(e) => set('purpose', e.target.value)}
          >
            {choose('— None —')}
            {options(lists?.purposes)}
          </Select>
        )}
        <fieldset className="detail-dialog__checks">
          <legend className="cds--label">Indicators</legend>
          <Checkbox
            id="cp-edit-deciduous"
            labelText="Deciduous"
            checked={form.deciduous}
            disabled={saving}
            onChange={(_, { checked }) => set('deciduous', checked)}
          />
          <Checkbox
            id="cp-edit-catastrophic"
            labelText="Catastrophic"
            checked={form.catastrophic}
            disabled={saving}
            onChange={(_, { checked }) => set('catastrophic', checked)}
          />
          <Checkbox
            id="cp-edit-cruise"
            labelText="Cruise based"
            checked={form.cruiseBased}
            disabled={saving}
            onChange={(_, { checked }) => set('cruiseBased', checked)}
          />
          <Checkbox
            id="cp-edit-frz"
            labelText="Within Fibre Recovery Zone"
            checked={form.frz}
            disabled={saving}
            onChange={(_, { checked }) => set('frz', checked)}
          />
        </fieldset>
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

export default PermitEditModal;
