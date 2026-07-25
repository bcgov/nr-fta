import { Button, Column, Grid, TextInput } from '@carbon/react';
import { Save, Reset } from '@carbon/icons-react';
import { useState, type FC, type FormEvent } from 'react';
import { useNotification } from '@/context/notification/useNotification';
import PageLayout from '@/pages/PageLayout';
import { transferMark } from '@/services/mark_transfer';

interface TransferForm {
  timberMark: string;
  sourceForestFileId: string;
  sourceCuttingPermitId: string;
  targetForestFileId: string;
  targetCuttingPermitId: string;
  effective: string;
}

const EMPTY: TransferForm = {
  timberMark: '',
  sourceForestFileId: '',
  sourceCuttingPermitId: '',
  targetForestFileId: '',
  targetCuttingPermitId: '',
  effective: '',
};

/**
 * FTA240 — Timber Mark Transfer. Re-points a timber mark from a source forest
 * file / cutting permit onto a target forest file / cutting permit (the legacy
 * FTA_230_MARKTRANFER move). Gated to FTA_ADMIN; on submit it POSTs to
 * {@code /api/fta/marks/transfer}.
 */
const MarkTransfer: FC = () => {
  const notify = useNotification();
  const [form, setForm] = useState<TransferForm>(EMPTY);
  const [saving, setSaving] = useState(false);

  const set = (f: keyof TransferForm) => (v: string) => setForm((s) => ({ ...s, [f]: v }));
  const canSubmit =
    form.timberMark.trim() !== '' &&
    form.sourceForestFileId.trim() !== '' &&
    form.targetForestFileId.trim() !== '';

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      const { timberMark } = await transferMark({
        timberMark: form.timberMark.trim(),
        sourceForestFileId: form.sourceForestFileId.trim(),
        sourceCuttingPermitId: form.sourceCuttingPermitId.trim(),
        targetForestFileId: form.targetForestFileId.trim(),
        targetCuttingPermitId: form.targetCuttingPermitId.trim(),
        transferEffDate: form.effective,
        userOrgNo: '', // resolved server-side from the authenticated user's org
      });
      notify.display({
        kind: 'success',
        title: 'Mark transferred',
        subtitle: `Mark ${timberMark} transferred to file ${form.targetForestFileId.trim()}.`,
        timeout: 5000,
      });
      setForm(EMPTY);
    } catch (err) {
      notify.display({
        kind: 'error',
        title: 'Could not transfer mark',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 6000,
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <PageLayout title="Timber Mark Transfer">
      <form style={{ maxWidth: '48rem' }} onSubmit={onSubmit}>
        <Grid narrow>
          <Column sm={4} md={4} lg={8}>
            <TextInput id="mt-mark" labelText="Timber Mark" placeholder="e.g. 52/1234"
              value={form.timberMark} onChange={(e) => set('timberMark')(e.target.value)} required />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="mt-src-file" labelText="Source Forest File ID" placeholder="e.g. A19201"
              value={form.sourceForestFileId} onChange={(e) => set('sourceForestFileId')(e.target.value)} required />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="mt-src-cp" labelText="Source Cutting Permit (optional)" placeholder="e.g. CP-01"
              value={form.sourceCuttingPermitId} onChange={(e) => set('sourceCuttingPermitId')(e.target.value)} />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="mt-tgt-file" labelText="Target Forest File ID" placeholder="e.g. A20115"
              value={form.targetForestFileId} onChange={(e) => set('targetForestFileId')(e.target.value)} required />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="mt-tgt-cp" labelText="Target Cutting Permit (optional)" placeholder="e.g. CP-02"
              value={form.targetCuttingPermitId} onChange={(e) => set('targetCuttingPermitId')(e.target.value)} />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="mt-eff" labelText="Effective date" placeholder="yyyy-mm-dd"
              value={form.effective} onChange={(e) => set('effective')(e.target.value)} />
          </Column>
        </Grid>
        <div style={{ display: 'flex', gap: '0.5rem', marginTop: '1.5rem' }}>
          <Button type="submit" renderIcon={Save} disabled={!canSubmit || saving}>
            {saving ? 'Transferring…' : 'Transfer mark'}
          </Button>
          <Button type="button" kind="ghost" renderIcon={Reset} onClick={() => setForm(EMPTY)} disabled={saving}>Clear</Button>
        </div>
      </form>
    </PageLayout>
  );
};

export default MarkTransfer;
