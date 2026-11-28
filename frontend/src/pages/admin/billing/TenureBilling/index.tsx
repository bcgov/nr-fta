import { DocumentTasks, Launch, Money, Search as SearchIcon, View } from '@carbon/icons-react';
import {
  Button,
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
import { useCallback, useEffect, useState, type FC, type FormEvent } from 'react';
import { Link } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import EmptyState from '@/components/EmptyState/EmptyState';
import SectionTile from '@/components/SectionTile';
import UserName from '@/components/UserName';
import YearSelect from '@/components/YearSelect';
import { useNotification } from '@/context/notification/useNotification';
import { useDefaultOrgUnitIn } from '@/hooks/useDefaultOrgUnit';
import { safeErrorMessage } from '@/lib/errorMessage';
import { gbsUrl } from '@/lib/externalUrls';
import { FIRST_BILLING_YEAR } from '@/lib/years';
import PageLayout from '@/pages/PageLayout';
import {
  getBillingInstructions,
  getInvoicePreview,
  prepareBillingInstructions,
  type BillingInstructionRow,
  type BillingInstructionsCriteria,
} from '@/services/billing';
import {
  getBillingInstructionCodes,
  getDistrictOrgUnits,
  getRangeFileTypes,
  type CodeOption,
} from '@/services/codeLists';

import InvoicePreviewModal from '../InvoicePreviewModal';
import { currentYear, useDistrictScope } from '../useDistrictScope';

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
  const [form, setForm] = useState<BillingInstructionsCriteria>(() => ({
    ...EMPTY,
    // Legacy setDefaults: the current year.
    calendarYear: currentYear(),
  }));
  const scope = useDistrictScope(districts);
  // A single administered district is preselected and locked, as legacy's protected Org Unit.
  const orgUnitNo = scope.locked?.code ?? form.orgUnitNo;
  // Legacy defaulted Org Unit to the user's org unit when blank (Org Unit Maintenance).
  const defaultDistrict = useDefaultOrgUnitIn(scope.options);
  useEffect(() => {
    if (defaultDistrict) setForm((f) => (f.orgUnitNo ? f : { ...f, orgUnitNo: defaultDistrict }));
  }, [defaultDistrict]);
  const [searched, setSearched] = useState<BillingInstructionsCriteria | null>(null);
  const [rows, setRows] = useState<BillingInstructionRow[] | null>(null);
  const [chosen, setChosen] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [previewOf, setPreviewOf] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    Promise.allSettled([
      getDistrictOrgUnits(),
      getRangeFileTypes(),
      getBillingInstructionCodes(),
    ]).then(([d, t, i]) => {
      if (cancelled) return;
      if (d.status === 'fulfilled') setDistricts(d.value);
      if (t.status === 'fulfilled') setFileTypes(t.value);
      if (i.status === 'fulfilled') setInstrCodes(i.value);
      if ([d, t, i].some((r) => r.status === 'rejected')) {
        notify.display({
          kind: 'error',
          title: 'Could not load the dropdown lists.',
          subtitle: 'Reload the page to try again.',
          timeout: 6000,
        });
      }
    });
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
    try {
      const result = await getBillingInstructions(c);
      setRows(result);
      setChosen(Object.fromEntries(result.map((r) => [r.forestFileId, r.instructionCode])));
      setSearched(c);
    } catch (e) {
      notify.display({
        kind: 'error',
        title: 'Could not load the billing instructions.',
        subtitle: safeErrorMessage(e),
        timeout: 0,
      });
      setRows(null);
      setSearched(null);
    } finally {
      setLoading(false);
    }
  };

  const onGo = (e: FormEvent) => {
    e.preventDefault();
    void search({ ...form, orgUnitNo, forestFileId: form.forestFileId?.trim().toUpperCase() });
  };

  const onPrepare = async () => {
    if (!searched || !rows) return;
    setSaving(true);
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
        title: 'Prepare successful.',
        subtitle: created ? undefined : 'No new instructions were created.',
        timeout: 5000,
      });
      await search(searched);
    } catch (err) {
      // A refused Prepare lists one refusal per row, one per line.
      notify.display({
        kind: 'error',
        title: 'Nothing was prepared.',
        subtitle: safeErrorMessage(err),
        timeout: 0,
      });
    } finally {
      setSaving(false);
    }
  };

  // Preview prices the row for the year of the last Go, as legacy passed the header's year.
  const previewYear = searched?.calendarYear;
  const previewFetcher = useCallback(
    () => getInvoicePreview(previewOf ?? '', previewYear ?? ''),
    [previewOf, previewYear],
  );

  const canGo = orgUnitNo !== '' && /^\d{4}$/.test(form.calendarYear);

  return (
    <PageLayout
      title="Tenure Billing Instructions"
      subtitle="Set the billing instruction for each range tenure in a district, then prepare them for billing."
    >
      <SectionTile title="Criteria" icon={SearchIcon}>
        <form className="fsp-search__form" onSubmit={onGo}>
          <div className="fsp-search__field-grid">
            <div className="fsp-search__wide-cell">
              <Select
                id="bill-org"
                labelText="Org Unit"
                value={orgUnitNo}
                disabled={!!scope.locked}
                onChange={(e) => set('orgUnitNo')(e.target.value)}
              >
                <SelectItem value="" text="Choose a district" />
                {scope.options.map((d) => (
                  <SelectItem key={d.code} value={d.code} text={d.description} />
                ))}
              </Select>
            </div>
            <TextInput
              id="bill-name"
              labelText="Client Name (optional)"
              value={form.clientName ?? ''}
              onChange={(e) => set('clientName')(e.target.value)}
              maxLength={60}
              autoComplete="off"
            />
            <YearSelect
              id="bill-year"
              labelText="Year"
              value={form.calendarYear}
              onChange={set('calendarYear')}
              first={FIRST_BILLING_YEAR}
            />
            <TextInput
              id="bill-file"
              labelText="Tenure (optional)"
              placeholder="e.g. RAN076543"
              value={form.forestFileId ?? ''}
              onChange={(e) => set('forestFileId')(e.target.value)}
              maxLength={10}
              autoComplete="off"
            />
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
          </div>
          <div className="fsp-search__actions">
            <Button type="submit" size="md" renderIcon={SearchIcon} disabled={!canGo || loading}>
              Go
            </Button>
          </div>
        </form>
        <InlineNotification
          kind="warning"
          lowContrast
          hideCloseButton
          className="billing__caution"
          title="CAUTION:"
        >
          {/* Legacy FTA680's caution, word for word. */}
          <div className="billing__caution-text">
            <p>
              If you are changing the billing instructions below please be aware that any changes
              you make will either overwrite or delete the previous set of billing instructions
              currently shown on the screen that have been previously prepared but not yet approved.
              This means that it will only look at the new instructions you&apos;ve entered so if
              you do not change instructions on previously prepared instructions that is displayed
              on the screen it will be deleted if the certified timestamp is later than the date the
              tenure was billed last.
            </p>
            <p>
              If you do not want to affect previous instructions please type in the specific range
              tenure you want to change the billing instruction for and hit GO and only that range
              tenure will show up in the list. This will ensure that the other instructions that
              were previously prepared are not affected.
            </p>
          </div>
        </InlineNotification>
      </SectionTile>

      {(rows !== null || loading) && (
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
          <AsyncBoundary loading={loading} loadingText="Loading billing instructions…">
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
                          <TableHeader>Actions</TableHeader>
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
                            <TableCell className="detail-tab__row-action">
                              <Button
                                kind="ghost"
                                size="sm"
                                renderIcon={View}
                                onClick={() => setPreviewOf(r.forestFileId)}
                              >
                                Preview
                              </Button>
                              {/* Legacy's G.B.S.: opens GBS's main page on every row. */}
                              <Button
                                kind="ghost"
                                size="sm"
                                renderIcon={Launch}
                                href={gbsUrl() ?? undefined}
                                disabled={!gbsUrl()}
                                target="_blank"
                                rel="noopener noreferrer"
                              >
                                G.B.S.
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
          fetcher={previewFetcher}
          onClose={() => setPreviewOf(null)}
        />
      )}
    </PageLayout>
  );
};

export default TenureBilling;
