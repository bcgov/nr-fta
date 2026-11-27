import { ArrowRight, Save, Reset } from '@carbon/icons-react';
import { Button, TextInput } from '@carbon/react';
import { useState, type FC, type FormEvent } from 'react';

import DateField from '@/components/DateField';
import SectionTile from '@/components/SectionTile';
import { useNotification } from '@/context/notification/useNotification';
import PageLayout from '@/pages/PageLayout';
import { transferMark } from '@/services/mark_transfer';

interface TransferForm {
  sourceForestFileId: string;
  sourceCuttingPermitId: string;
  targetForestFileId: string;
  targetCuttingPermitId: string;
  effective: string;
}

const EMPTY: TransferForm = {
  sourceForestFileId: '',
  sourceCuttingPermitId: '',
  targetForestFileId: '',
  targetCuttingPermitId: '',
  effective: '',
};

/**
 * FTA240 — Timber Mark Transfer. Moves the source forest file / cutting permit's
 * primary timber mark onto a target forest file / cutting permit (the legacy
 * FTA_230_MARKTRANFER save, which finds the mark itself and validates the move).
 * Every field is required, as legacy's form. Gated to FTA_ADMIN; on submit it
 * POSTs to {@code /api/fta/marks/transfer}.
 */
const MarkTransfer: FC = () => {
  const notify = useNotification();
  const [form, setForm] = useState<TransferForm>(EMPTY);
  const [saving, setSaving] = useState(false);

  const set = (f: keyof TransferForm) => (v: string) => setForm((s) => ({ ...s, [f]: v }));
  const canSubmit = Object.values(form).every((v) => v.trim() !== '');

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      const { timberMark } = await transferMark({
        sourceForestFileId: form.sourceForestFileId.trim(),
        sourceCuttingPermitId: form.sourceCuttingPermitId.trim(),
        targetForestFileId: form.targetForestFileId.trim(),
        targetCuttingPermitId: form.targetCuttingPermitId.trim(),
        transferEffDate: form.effective,
      });
      notify.display({
        kind: 'success',
        title: 'Mark transferred.',
        subtitle: `Mark ${timberMark} transferred to file ${form.targetForestFileId.trim()}.`,
        timeout: 5000,
      });
      setForm(EMPTY);
    } catch (err) {
      notify.display({
        kind: 'error',
        title: 'Could not transfer mark.',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 6000,
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <PageLayout
      title="Timber Mark Transfer"
      subtitle="Move the primary timber mark of a source forest file and cutting permit to a target file and cutting permit."
    >
      <SectionTile title="Transfer details" icon={ArrowRight}>
        <form className="fsp-search__form" onSubmit={onSubmit}>
          <div className="fsp-search__field-grid">
            <TextInput
              id="mt-src-file"
              labelText="Source Forest File ID"
              placeholder="e.g. A19201"
              value={form.sourceForestFileId}
              onChange={(e) => set('sourceForestFileId')(e.target.value.toUpperCase())}
              required
            />
            <TextInput
              id="mt-src-cp"
              labelText="Source Cutting Permit"
              placeholder="e.g. 01"
              value={form.sourceCuttingPermitId}
              onChange={(e) => set('sourceCuttingPermitId')(e.target.value.toUpperCase())}
              maxLength={3}
              required
            />
            <TextInput
              id="mt-tgt-file"
              labelText="Target Forest File ID"
              placeholder="e.g. A20115"
              value={form.targetForestFileId}
              onChange={(e) => set('targetForestFileId')(e.target.value.toUpperCase())}
              required
            />
            <TextInput
              id="mt-tgt-cp"
              labelText="Target Cutting Permit"
              placeholder="e.g. 02"
              value={form.targetCuttingPermitId}
              onChange={(e) => set('targetCuttingPermitId')(e.target.value.toUpperCase())}
              maxLength={3}
              required
            />
            <DateField
              id="mt-eff"
              labelText="Legal transfer date"
              value={form.effective}
              onChange={(v) => set('effective')(v)}
            />
          </div>
          <div className="fsp-search__actions">
            <Button
              type="button"
              size="md"
              kind="tertiary"
              renderIcon={Reset}
              onClick={() => setForm(EMPTY)}
              disabled={saving}
            >
              Clear
            </Button>
            <Button type="submit" size="md" renderIcon={Save} disabled={!canSubmit || saving}>
              {saving ? 'Transferring…' : 'Transfer mark'}
            </Button>
          </div>
        </form>
      </SectionTile>
    </PageLayout>
  );
};

export default MarkTransfer;
