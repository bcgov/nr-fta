import { Button, Select, SelectItem, Stack, TextArea, TextInput } from '@carbon/react';
import { useEffect, useState, type FC } from 'react';

import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import DateField from '@/pages/apt/components/DateField';
import {
  COMMITMENT_REASON_MAX,
  createAptCommitment,
  getAptForestFileIds,
  updateAptCommitment,
  type AptCommitment,
} from '@/services/apt/commitments';
import { getCommitmentTypes, getPartitionTypes, type AptCodeOption } from '@/services/apt/lookups';

/**
 * Where the commitment is being entered from, which decides what is fixed and what is picked.
 *
 * - `apportionment` (APT2's MUCommitmentEdit): the partition and apportionment are the page's;
 *   the licence is picked from the unit's licences of the apportionment's FOA type.
 * - `licence` (APT2's CommitmentDetails): the licence is the page's; the partition is picked
 *   from those the licence's management unit uses.
 */
export type CommitmentContext =
  | {
      kind: 'apportionment';
      managementUnitId: number;
      foaType: string;
      partitionCode: string;
      partitionLabel: string;
      paptId: number;
    }
  | {
      kind: 'licence';
      forestFileId: string;
      managementUnitType: string | null;
      managementUnitNumber: string | null;
    };

interface Form {
  forestFileId: string;
  partitionCode: string;
  type: string;
  aac: string;
  includeInLicAac: 'Y' | 'N';
  determinationDate: string;
  effectiveDate: string;
  expiryDate: string;
  reason: string;
}

type Errors = Partial<Record<keyof Form, string>>;

/** APT_COMMITMENTS.ALLOWABLE_ANNUAL_CUT is NUMBER(9): 0 to 999,999,999. */
const AAC_PATTERN = /^\d{1,9}$/;

const formOf = (c: AptCommitment | null, ctx: CommitmentContext): Form => ({
  forestFileId: c?.forestFileId ?? (ctx.kind === 'licence' ? ctx.forestFileId : ''),
  partitionCode: c?.partitionCode ?? (ctx.kind === 'apportionment' ? ctx.partitionCode : ''),
  type: c?.type ?? '',
  aac: c?.allowableAnnualCut != null ? String(c.allowableAnnualCut) : '',
  includeInLicAac: c?.includeInLicAac === 'Y' ? 'Y' : 'N',
  determinationDate: c?.determinationDate ?? '',
  effectiveDate: c?.effectiveDate ?? '',
  expiryDate: c?.expiryDate ?? '',
  reason: '',
});

/**
 * APT2's checks. A reason is needed to change a commitment; from a licence, APT2 asked for one
 * on a new commitment too.
 */
const validate = (f: Form, reasonRequired: boolean): Errors => {
  const e: Errors = {};
  if (!f.forestFileId) e.forestFileId = 'Forest File ID is required.';
  if (!f.partitionCode) e.partitionCode = 'Partition is required.';
  if (!f.type) e.type = 'Type is required.';
  if (!f.aac.trim()) e.aac = 'AAC is required.';
  else if (!AAC_PATTERN.test(f.aac.trim()))
    e.aac = 'AAC must be a whole number between 0 and 999,999,999.';
  if (!f.determinationDate) e.determinationDate = 'Determination Date is required.';
  if (!f.effectiveDate) e.effectiveDate = 'Effective Date is required.';
  if (reasonRequired && !f.reason.trim()) e.reason = 'Reason is required.';
  else if (f.reason.trim().length > COMMITMENT_REASON_MAX)
    e.reason = `Reason cannot exceed ${COMMITMENT_REASON_MAX} characters.`;
  return e;
};

const label = (o: AptCodeOption) => (o.description ? `${o.code} - ${o.description}` : o.code);

/** The list's options, plus the current value when the list doesn't hold it. */
const withCurrent = (options: AptCodeOption[], current: string): AptCodeOption[] =>
  current && !options.some((o) => o.code === current)
    ? [{ code: current, description: null }, ...options]
    : options;

interface Props {
  open: boolean;
  /** The commitment to change, or null for a new one. */
  commitment: AptCommitment | null;
  context: CommitmentContext;
  onClose: () => void;
  /** Called with the commitment as stored, after the dialog has closed. */
  onSaved: (saved: AptCommitment) => void;
}

/**
 * APT2's APTM6300 Commitment Edit, in a dialog. Only an APT administrator is given this dialog;
 * the backend refuses anyone else too.
 */
const CommitmentEditModal: FC<Props> = ({ open, commitment, context, onClose, onSaved }) => {
  const { display } = useNotification();
  const [form, setForm] = useState<Form>(() => formOf(commitment, context));
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);
  const [types, setTypes] = useState<AptCodeOption[] | null>(null);
  const [choices, setChoices] = useState<AptCodeOption[] | null>(null);

  const editing = commitment != null;
  const reasonRequired = editing || context.kind === 'licence';

  useEffect(() => {
    if (open) {
      setForm(formOf(commitment, context));
      setErrors({});
    }
  }, [open, commitment, context]);

  // The commitment types, and the licences (from an apportionment) or the partitions (from a
  // licence) to pick from.
  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setTypes(null);
    setChoices(null);
    const picks: Promise<AptCodeOption[]> =
      context.kind === 'apportionment'
        ? getAptForestFileIds(context.managementUnitId, context.foaType).then((ids) =>
            ids.map((id) => ({ code: id, description: null })),
          )
        : context.managementUnitType?.trim() && context.managementUnitNumber?.trim()
          ? getPartitionTypes(context.managementUnitType, context.managementUnitNumber)
          : Promise.resolve([]);
    Promise.all([getCommitmentTypes(), picks])
      .then(([t, p]) => {
        if (cancelled) return;
        setTypes(t);
        setChoices(p);
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setTypes([]);
        setChoices([]);
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
  }, [open, context, display]);

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
    const found = validate(form, reasonRequired);
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    const body = {
      // A commitment keeps its own apportionment; a new one from an apportionment takes the page's.
      paptId: commitment
        ? commitment.paptId
        : context.kind === 'apportionment'
          ? context.paptId
          : null,
      forestFileId: form.forestFileId,
      aptPartitionSt: form.partitionCode,
      commitTypeSt: form.type,
      aac: Number(form.aac.trim()),
      inclInLicAacInd: form.includeInLicAac,
      determinationDate: form.determinationDate,
      effectiveDate: form.effectiveDate,
      expiryDate: form.expiryDate || null,
      reason: form.reason.trim() || null,
    };
    setSaving(true);
    try {
      const saved = commitment
        ? await updateAptCommitment(commitment.id, body)
        : await createAptCommitment(body);
      display({
        kind: 'success',
        title: commitment ? 'Commitment saved' : 'Commitment added',
        timeout: 5000,
      });
      onClose();
      onSaved(saved);
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not save the commitment',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  const loading = open && (types === null || choices === null);
  const choose = (text: string) => <SelectItem value="" text={loading ? 'Loading…' : text} />;

  return (
    <Modal
      open={open}
      passiveModal
      size="md"
      className="detail-dialog"
      modalHeading={editing ? 'Edit commitment' : 'New commitment'}
      onRequestClose={close}
      preventCloseOnClickOutside
    >
      <Stack gap={5}>
        <div className="detail-dialog__pair">
          {context.kind === 'apportionment' ? (
            <Select
              id="commitment-ffid"
              labelText="Forest File ID"
              value={form.forestFileId}
              invalid={!!errors.forestFileId}
              invalidText={errors.forestFileId}
              disabled={saving || loading}
              onChange={(e) => set('forestFileId', e.target.value)}
            >
              {choose('— Select a licence —')}
              {withCurrent(choices ?? [], form.forestFileId).map((o) => (
                <SelectItem key={o.code} value={o.code} text={o.code} />
              ))}
            </Select>
          ) : (
            <TextInput
              id="commitment-ffid"
              labelText="Forest File ID"
              value={form.forestFileId}
              readOnly
            />
          )}
          {context.kind === 'licence' ? (
            <Select
              id="commitment-partition"
              labelText="Partition"
              value={form.partitionCode}
              invalid={!!errors.partitionCode}
              invalidText={errors.partitionCode}
              disabled={saving || loading}
              onChange={(e) => set('partitionCode', e.target.value)}
            >
              {choose('— Select a partition —')}
              {withCurrent(choices ?? [], form.partitionCode).map((o) => (
                <SelectItem key={o.code} value={o.code} text={label(o)} />
              ))}
            </Select>
          ) : (
            <TextInput
              id="commitment-partition"
              labelText="Partition"
              value={
                commitment && commitment.partitionCode !== context.partitionCode
                  ? (commitment.partitionDescription ?? commitment.partitionCode)
                  : context.partitionLabel
              }
              readOnly
            />
          )}
        </div>
        <Select
          id="commitment-type"
          labelText="Type"
          value={form.type}
          invalid={!!errors.type}
          invalidText={errors.type}
          disabled={saving || loading}
          onChange={(e) => set('type', e.target.value)}
        >
          {choose('— Select a type —')}
          {withCurrent(types ?? [], form.type).map((o) => (
            <SelectItem key={o.code} value={o.code} text={label(o)} />
          ))}
        </Select>
        <div className="detail-dialog__pair">
          <TextInput
            id="commitment-aac"
            labelText="AAC (m³)"
            inputMode="numeric"
            value={form.aac}
            invalid={!!errors.aac}
            invalidText={errors.aac}
            disabled={saving}
            onChange={(e) => set('aac', e.target.value)}
          />
          <Select
            id="commitment-incl"
            labelText="Incl in AAC"
            value={form.includeInLicAac}
            disabled={saving}
            onChange={(e) => set('includeInLicAac', e.target.value === 'Y' ? 'Y' : 'N')}
          >
            <SelectItem value="Y" text="Yes" />
            <SelectItem value="N" text="No" />
          </Select>
        </div>
        <div className="detail-dialog__pair">
          <DateField
            id="commitment-determination"
            labelText="Determination Date"
            value={form.determinationDate}
            invalidText={errors.determinationDate}
            disabled={saving}
            onChange={(v) => set('determinationDate', v)}
          />
          <DateField
            id="commitment-effective"
            labelText="Effective Date"
            value={form.effectiveDate}
            invalidText={errors.effectiveDate}
            disabled={saving}
            onChange={(v) => set('effectiveDate', v)}
          />
        </div>
        <div className="detail-dialog__pair">
          <DateField
            id="commitment-expiry"
            labelText="Expiry Date (optional)"
            value={form.expiryDate}
            disabled={saving}
            onChange={(v) => set('expiryDate', v)}
          />
        </div>
        <TextArea
          id="commitment-reason"
          labelText={reasonRequired ? 'Reason' : 'Reason (optional)'}
          rows={3}
          maxCount={COMMITMENT_REASON_MAX}
          enableCounter
          value={form.reason}
          invalid={!!errors.reason}
          invalidText={errors.reason}
          disabled={saving}
          onChange={(e) => set('reason', e.target.value)}
        />
      </Stack>
      <div className="detail-dialog__actions">
        <Button kind="tertiary" disabled={saving} onClick={close}>
          Cancel
        </Button>
        <Button kind="primary" disabled={saving || loading} onClick={() => void submit()}>
          {saving ? 'Saving…' : 'Save'}
        </Button>
      </div>
    </Modal>
  );
};

export default CommitmentEditModal;
