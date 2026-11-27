import { DocumentTasks, Money, Printer, Search as SearchIcon } from '@carbon/icons-react';
import {
  Button,
  Checkbox,
  Select,
  SelectItem,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { useEffect, useRef, useState, type FC, type FormEvent } from 'react';
import { Link } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import DefinitionGrid from '@/components/DefinitionGrid';
import EmptyState from '@/components/EmptyState/EmptyState';
import SectionTile from '@/components/SectionTile';
import UserName from '@/components/UserName';
import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import { useDefaultOrgUnitIn } from '@/hooks/useDefaultOrgUnit';
import { downloadBlob } from '@/lib/downloadBlob';
import { safeErrorMessage } from '@/lib/errorMessage';
import PageLayout from '@/pages/PageLayout';
import { getTenureAdminOrgUnits, type CodeOption } from '@/services/codeLists';
import {
  getRentsFees,
  getRentsFeesReport,
  prepareRentsFees,
  RENT_FEE_INDICATORS,
  RENTS_FEES_REPORT_FILENAME,
  type RentFeeList,
  type RentsFeesCriteria,
} from '@/services/rentsFees';

import { useDistrictScope } from '../billing/useDistrictScope';
import '../billing/billing.scss';

/**
 * FTA670 — Annual Rents and Fees Preparation, on legacy THE.FTA_670_RENTFEE: the tenures due
 * for annual rent billing by the end of next month in an org unit (or, with Rollup, its region
 * or the province), each with an Opt the user may set to R or D (D deletes it from the billing
 * list). Prepare saves every changed Opt, or none if any row is refused.
 *
 * The Org Unit list is legacy's (districts, regions, HQ). A district-scoped administrator gets
 * only their districts and no Rollup, as legacy's district user; with one district it is
 * preselected, locked and searched on arrival, as legacy auto-queried for a district user.
 * Prepare and Print use the criteria of the last Go. Print downloads the FTA406 Rents and Fees
 * Preparation Report (legacy FTAR406P), offered as legacy did only once Go has listed rows.
 */
const RentsFees: FC = () => {
  const notify = useNotification();
  const { user } = useAuth();
  const [orgUnits, setOrgUnits] = useState<CodeOption[]>([]);
  const [form, setForm] = useState<RentsFeesCriteria>({ orgUnitNo: '', rollup: false });
  const scope = useDistrictScope(orgUnits, 'admin');
  const orgUnitNo = scope.locked?.code ?? form.orgUnitNo;
  // Legacy's setDefaults put the user's org unit in a blank Org Unit.
  const defaultOrgUnit = useDefaultOrgUnitIn(scope.options);
  useEffect(() => {
    if (defaultOrgUnit) setForm((f) => (f.orgUnitNo ? f : { ...f, orgUnitNo: defaultOrgUnit }));
  }, [defaultOrgUnit]);

  const [searched, setSearched] = useState<RentsFeesCriteria | null>(null);
  const [list, setList] = useState<RentFeeList | null>(null);
  const [chosen, setChosen] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [printing, setPrinting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getTenureAdminOrgUnits()
      .then((o) => !cancelled && setOrgUnits(o))
      .catch(() => {
        if (cancelled) return;
        notify.display({
          kind: 'error',
          title: 'Could not load the org units.',
          subtitle: 'Reload the page to try again.',
          timeout: 6000,
        });
      });
    return () => {
      cancelled = true;
    };
    // Load once; notify is stable for the page's life.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const search = async (c: RentsFeesCriteria) => {
    setLoading(true);
    try {
      const result = await getRentsFees(c);
      setList(result);
      setChosen(result.rows.map((r) => r.invoicePrepInd));
      setSearched(c);
    } catch (e) {
      notify.display({
        kind: 'error',
        title: 'Could not load the rents and fees.',
        subtitle: safeErrorMessage(e),
        timeout: 0,
      });
      setList(null);
      setSearched(null);
    } finally {
      setLoading(false);
    }
  };

  // Legacy auto-queried for a district user, whose org unit it had already set.
  const autoQueried = useRef(false);
  useEffect(() => {
    if (scope.locked && !autoQueried.current) {
      autoQueried.current = true;
      void search({ orgUnitNo: scope.locked.code, rollup: false });
    }
    // Once, when the locked district is known.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scope.locked]);

  const onGo = (e: FormEvent) => {
    e.preventDefault();
    void search({ orgUnitNo, rollup: scope.unscoped && form.rollup });
  };

  const onPrepare = async () => {
    if (!searched || !list) return;
    setSaving(true);
    try {
      const { saved, message } = await prepareRentsFees(
        searched,
        list.rows.map((row, i) => ({ row, invoicePrepIndNew: chosen[i] ?? '' })),
      );
      notify.display({ kind: saved > 0 ? 'success' : 'info', title: message, timeout: 5000 });
      if (saved > 0) await search(searched);
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

  const onPrint = async () => {
    if (!searched) return;
    setPrinting(true);
    try {
      downloadBlob(await getRentsFeesReport(searched), RENTS_FEES_REPORT_FILENAME);
    } catch (err) {
      notify.display({
        kind: 'error',
        title: 'Could not produce the report.',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 6000,
      });
    } finally {
      setPrinting(false);
    }
  };

  const rows = list?.rows;

  return (
    <PageLayout
      title="Annual Rents & Fees Preparation"
      subtitle="List the tenures due for annual rent billing by the end of next month, and prepare them."
    >
      <SectionTile title="Criteria" icon={SearchIcon}>
        <form className="fsp-search__form" onSubmit={onGo}>
          <div className="fsp-search__field-grid">
            <div className="fsp-search__wide-cell">
              <Select
                id="rentfee-org"
                labelText="Org Unit"
                value={orgUnitNo}
                disabled={!!scope.locked}
                onChange={(e) => setForm((f) => ({ ...f, orgUnitNo: e.target.value }))}
              >
                <SelectItem value="" text="Choose an org unit" />
                {scope.options.map((o) => (
                  <SelectItem key={o.code} value={o.code} text={o.description} />
                ))}
              </Select>
            </div>
            {/* Legacy hid Rollup from district users. */}
            {scope.unscoped && (
              <div className="fsp-search__full-cell">
                <Checkbox
                  id="rentfee-rollup"
                  labelText="Rollup"
                  checked={form.rollup}
                  onChange={(_, { checked }) => setForm((f) => ({ ...f, rollup: checked }))}
                />
              </div>
            )}
          </div>
          <div className="fsp-search__actions">
            <Button
              type="submit"
              size="md"
              renderIcon={SearchIcon}
              disabled={orgUnitNo === '' || loading}
            >
              Go
            </Button>
          </div>
        </form>
      </SectionTile>

      {(list !== null || loading) && (
        <SectionTile
          title="Tenures"
          icon={Money}
          description={
            rows ? `${rows.length} row${rows.length === 1 ? '' : 's'} returned` : undefined
          }
          actions={
            <>
              <Button
                size="md"
                renderIcon={DocumentTasks}
                disabled={!rows || rows.length === 0 || saving || loading}
                onClick={() => void onPrepare()}
              >
                {saving ? 'Preparing…' : 'Prepare'}
              </Button>
              <Button
                size="md"
                kind="tertiary"
                renderIcon={Printer}
                disabled={!rows || rows.length === 0 || printing || saving || loading}
                onClick={() => void onPrint()}
              >
                {printing ? 'Printing…' : 'Print'}
              </Button>
            </>
          }
        >
          {list && (
            <DefinitionGrid
              items={[
                {
                  label: 'Prepared By',
                  value: <UserName userId={user?.providerUsername ?? user?.userName} />,
                },
                {
                  label: 'Billing Period',
                  value: `${list.billFrom ?? '—'} to ${list.billTo ?? '—'}`,
                },
              ]}
            />
          )}
          <AsyncBoundary loading={loading} loadingText="Loading rents and fees…">
            {rows && rows.length === 0 ? (
              <EmptyState
                title="No records found"
                body="No tenures in that org unit are due for annual rent billing."
              />
            ) : (
              rows && (
                <div className="bordered-table">
                  <TableContainer>
                    <Table size="sm">
                      <TableHead>
                        <TableRow>
                          <TableHeader>Opt</TableHeader>
                          <TableHeader>Tenure</TableHeader>
                          <TableHeader>Type</TableHeader>
                          <TableHeader>Org</TableHeader>
                          <TableHeader>Client Number</TableHeader>
                          <TableHeader>Client Name</TableHeader>
                          <TableHeader>Anniversary Date</TableHeader>
                          <TableHeader className="billing__num">Billable Units</TableHeader>
                          <TableHeader>Unit Type</TableHeader>
                          <TableHeader className="billing__num">Rate ($)</TableHeader>
                          <TableHeader className="billing__num">Amount ($)</TableHeader>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {rows.map((r, i) => (
                          <TableRow key={`${r.forestFileId}-${r.calendarYear}-${i}`}>
                            <TableCell>
                              <Select
                                id={`rentfee-opt-${i}`}
                                labelText={`Opt for ${r.forestFileId}`}
                                hideLabel
                                size="sm"
                                className="billing__instr"
                                disabled={saving}
                                value={chosen[i] ?? ''}
                                onChange={(e) =>
                                  setChosen((c) => c.map((v, j) => (j === i ? e.target.value : v)))
                                }
                              >
                                {RENT_FEE_INDICATORS.map((v) => (
                                  <SelectItem key={v} value={v} text={v} />
                                ))}
                              </Select>
                            </TableCell>
                            <TableCell>
                              <Link to={`/tenures/${encodeURIComponent(r.forestFileId)}`}>
                                {r.forestFileId}
                              </Link>
                            </TableCell>
                            <TableCell>{r.fileTypeCode}</TableCell>
                            <TableCell>{r.orgCode}</TableCell>
                            <TableCell>
                              {[r.clientNumber, r.clientLocnCode].filter(Boolean).join(' ')}
                            </TableCell>
                            <TableCell>{r.clientName}</TableCell>
                            <TableCell>{r.anniversaryDate}</TableCell>
                            <TableCell className="billing__num">{r.unitsBillable}</TableCell>
                            <TableCell>{r.unitType}</TableCell>
                            <TableCell className="billing__num">{r.unitRate}</TableCell>
                            <TableCell className="billing__num">{r.amount}</TableCell>
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
    </PageLayout>
  );
};

export default RentsFees;
