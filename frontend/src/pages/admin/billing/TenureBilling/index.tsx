import { DocumentTasks, Money, Search as SearchIcon, View } from '@carbon/icons-react';
import {
  Button,
  Column,
  Grid,
  InlineNotification,
  Select,
  SelectItem,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TextInput,
} from '@carbon/react';
import { useEffect, useState, type FC, type FormEvent } from 'react';
import { Link } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import EmptyState from '@/components/EmptyState/EmptyState';
import SectionTile from '@/components/SectionTile';
import UserName from '@/components/UserName';
import { useNotification } from '@/context/notification/useNotification';
import PageLayout from '@/pages/PageLayout';
import {
  getBillingInstructions,
  prepareBillingInstructions,
  type BillingInstructionRow,
  type BillingInstructionsCriteria,
} from '@/services/billing';
import {
  getBillingInstructionCodes,
  getDistricts,
  getRangeFileTypes,
  type CodeOption,
} from '@/services/codeLists';

import InvoicePreviewModal from './InvoicePreviewModal';
import '../billing.scss';

const EMPTY: BillingInstructionsCriteria = {
  orgUnitNo: '',
  calendarYear: '',
  forestFileId: '',
  fileTypeCode: '',
  clientName: '',
};

/**
 * FTA680 — Tenure Billing Instructions, as legacy THE.FTA_680_TENURE_BILLING:
 * list a district's range tenures for a billing year with the instruction each
 * will be billed on, let the user change them, and Prepare (record) them all.
 * Prepare checks every row and saves none if any is refused.
 *
 * Prepare uses the criteria of the last Go, not whatever is in the form, as
 * legacy refused a changed year ("key changed").
 */
const TenureBilling: FC = () => {
  const notify = useNotification();
  const [districts, setDistricts] = useState<CodeOption[]>([]);
  const [fileTypes, setFileTypes] = useState<CodeOption[]>([]);
  const [instrCodes, setInstrCodes] = useState<CodeOption[]>([]);
  const [form, setForm] = useState<BillingInstructionsCriteria>(EMPTY);
  const [searched, setSearched] = useState<BillingInstructionsCriteria | null>(null);
  const [rows, setRows] = useState<BillingInstructionRow[] | null>(null);
  const [chosen, setChosen] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);
  const [saving, setSaving] = useState(false);
  const [rowErrors, setRowErrors] = useState<string[]>([]);
  const [previewOf, setPreviewOf] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    Promise.allSettled([getDistricts(), getRangeFileTypes(), getBillingInstructionCodes()]).then(
      ([d, t, i]) => {
        if (cancelled) return;
        if (d.status === 'fulfilled') setDistricts(d.value);
        if (t.status === 'fulfilled') setFileTypes(t.value);
        if (i.status === 'fulfilled') setInstrCodes(i.value);
        if ([d, t, i].some((r) => r.status === 'rejected')) {
          notify.display({
            kind: 'error',
            title: 'Could not load the dropdown lists',
            subtitle: 'Reload the page to try again.',
            timeout: 6000,
          });
        }
      },
    );
    return () => {
      cancelled = true;
    };
    // Load once; notify is stable for the page's life.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const set = (f: keyof BillingInstructionsCriteria) => (v: string) =>
    setForm((s) => ({ ...s, [f]: v }));

  const search = async (c: BillingInstructionsCriteria) => {
    setLoading(true);
    setError(undefined);
    setRowErrors([]);
    try {
      const result = await getBillingInstructions(c);
      setRows(result);
      setChosen(Object.fromEntries(result.map((r) => [r.forestFileId, r.instructionCode])));
      setSearched(c);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load the billing instructions');
      setRows(null);
      setSearched(null);
    } finally {
      setLoading(false);
    }
  };

  const onGo = (e: FormEvent) => {
    e.preventDefault();
    void search({ ...form, forestFileId: form.forestFileId?.trim().toUpperCase() });
  };

  const onPrepare = async () => {
    if (!searched || !rows) return;
    setSaving(true);
    setRowErrors([]);
    try {
      const { created } = await prepareBillingInstructions(
        searched,
        rows.map((r) => ({
          forestFileId: r.forestFileId,
          instructionCode: chosen[r.forestFileId] ?? '',
        })),
      );
      notify.display({
        kind: 'success',
        title: 'Prepare successful',
        subtitle: created ? undefined : 'No new instructions were created.',
        timeout: 5000,
      });
      await search(searched);
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Request failed';
      // A refused Prepare lists one line per row.
      setRowErrors(msg.split('\n'));
    } finally {
      setSaving(false);
    }
  };

  const canGo = form.orgUnitNo !== '' && /^\d{4}$/.test(form.calendarYear);

  return (
    <PageLayout
      title="Tenure Billing Instructions"
      subtitle="Set the billing instruction for each range tenure in a district, then prepare them for billing."
    >
      <SectionTile title="Criteria" icon={SearchIcon}>
        <form onSubmit={onGo}>
          <Grid narrow>
            <Column sm={4} md={4} lg={6}>
              <Select
                id="bill-org"
                labelText="Org Unit"
                value={form.orgUnitNo}
                onChange={(e) => set('orgUnitNo')(e.target.value)}
              >
                <SelectItem value="" text="Choose a district" />
                {districts.map((d) => (
                  <SelectItem key={d.code} value={d.code} text={d.description} />
                ))}
              </Select>
            </Column>
            <Column sm={4} md={4} lg={6}>
              <TextInput
                id="bill-name"
                labelText="Client Name (optional)"
                value={form.clientName ?? ''}
                onChange={(e) => set('clientName')(e.target.value)}
                maxLength={60}
                autoComplete="off"
              />
            </Column>
            <Column sm={4} md={2} lg={4}>
              <TextInput
                id="bill-year"
                labelText="Year"
                placeholder="e.g. 2025"
                value={form.calendarYear}
                onChange={(e) => set('calendarYear')(e.target.value.replace(/\D/g, ''))}
                maxLength={4}
                inputMode="numeric"
                autoComplete="off"
              />
            </Column>
            <Column sm={4} md={3} lg={5}>
              <TextInput
                id="bill-file"
                labelText="Tenure (optional)"
                placeholder="e.g. RAN076543"
                value={form.forestFileId ?? ''}
                onChange={(e) => set('forestFileId')(e.target.value)}
                maxLength={10}
                autoComplete="off"
              />
            </Column>
            <Column sm={4} md={3} lg={5}>
              <Select
                id="bill-type"
                labelText="Tenure Type (optional)"
                value={form.fileTypeCode ?? ''}
                onChange={(e) => set('fileTypeCode')(e.target.value)}
              >
                <SelectItem value="" text="All range tenure types" />
                {fileTypes.map((t) => (
                  <SelectItem key={t.code} value={t.code} text={t.description} />
                ))}
              </Select>
            </Column>
            <Column sm={4} md={8} lg={2} className="billing__go">
              <Button type="submit" size="md" renderIcon={SearchIcon} disabled={!canGo || loading}>
                Go
              </Button>
            </Column>
          </Grid>
        </form>
        <InlineNotification
          kind="warning"
          lowContrast
          hideCloseButton
          className="billing__caution"
          title="Caution"
          subtitle="Prepare looks only at the instructions on screen. A prepared but not yet approved instruction that you leave unchanged may be removed. To change one tenure without affecting the others, search for that tenure alone."
        />
      </SectionTile>

      {(rows !== null || loading || error) && (
        <SectionTile
          title="Range tenures"
          icon={Money}
          description={
            rows ? `${rows.length} row${rows.length === 1 ? '' : 's'} returned` : undefined
          }
          actions={
            <Button
              size="md"
              renderIcon={DocumentTasks}
              disabled={!rows || rows.length === 0 || saving || loading}
              onClick={() => void onPrepare()}
            >
              {saving ? 'Preparing…' : 'Prepare'}
            </Button>
          }
        >
          {rowErrors.length > 0 && (
            <InlineNotification
              kind="error"
              lowContrast
              title="Nothing was prepared"
              onClose={() => setRowErrors([])}
            >
              <ul className="billing__errors">
                {rowErrors.map((m) => (
                  <li key={m}>{m}</li>
                ))}
              </ul>
            </InlineNotification>
          )}
          <AsyncBoundary
            loading={loading}
            error={error}
            onRetry={searched ? () => void search(searched) : undefined}
            loadingText="Loading billing instructions…"
          >
            {rows && rows.length === 0 ? (
              <EmptyState
                title="No range tenures found"
                body="No active range tenures in that district cover that year for these criteria."
              />
            ) : (
              rows && (
                <div className="bordered-table">
                  <TableContainer>
                    <Table size="sm">
                      <TableHead>
                        <TableRow>
                          <TableHeader>Row</TableHeader>
                          <TableHeader>Instr</TableHeader>
                          <TableHeader>Tenure</TableHeader>
                          <TableHeader>Type</TableHeader>
                          <TableHeader>Client Name</TableHeader>
                          <TableHeader>Invoice</TableHeader>
                          <TableHeader>Certified</TableHeader>
                          <TableHeader>Certified By</TableHeader>
                          <TableHeader aria-label="Actions" />
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {rows.map((r, i) => (
                          <TableRow key={r.forestFileId}>
                            <TableCell>{i + 1}</TableCell>
                            <TableCell>
                              <Select
                                id={`bill-instr-${r.forestFileId}`}
                                labelText={`Instruction for ${r.forestFileId}`}
                                hideLabel
                                size="sm"
                                className="billing__instr"
                                disabled={r.locked || saving}
                                value={chosen[r.forestFileId] ?? ''}
                                onChange={(e) =>
                                  setChosen((c) => ({ ...c, [r.forestFileId]: e.target.value }))
                                }
                              >
                                <SelectItem value="" text="" />
                                {instrCodes.map((c) => (
                                  <SelectItem key={c.code} value={c.code} text={c.code} />
                                ))}
                                {/* A locked row may hold a code no longer offered. */}
                                {r.instructionCode &&
                                  !instrCodes.some((c) => c.code === r.instructionCode) && (
                                    <SelectItem
                                      value={r.instructionCode}
                                      text={r.instructionCode}
                                    />
                                  )}
                              </Select>
                            </TableCell>
                            <TableCell>
                              <Link to={`/range/${encodeURIComponent(r.forestFileId)}`}>
                                {r.forestFileId}
                              </Link>
                            </TableCell>
                            <TableCell>{r.fileTypeCode}</TableCell>
                            <TableCell>{r.clientName}</TableCell>
                            <TableCell>{r.invoiceNumber || '—'}</TableCell>
                            <TableCell>{r.certifiedDate || '—'}</TableCell>
                            <TableCell>
                              <UserName userId={r.certifiedBy} />
                            </TableCell>
                            <TableCell>
                              <Button
                                kind="ghost"
                                size="sm"
                                renderIcon={View}
                                onClick={() => setPreviewOf(r.forestFileId)}
                              >
                                Preview
                              </Button>
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </div>
              )
            )}
          </AsyncBoundary>
        </SectionTile>
      )}

      {previewOf && searched && (
        <InvoicePreviewModal
          forestFileId={previewOf}
          calendarYear={searched.calendarYear}
          onClose={() => setPreviewOf(null)}
        />
      )}
    </PageLayout>
  );
};

export default TenureBilling;
