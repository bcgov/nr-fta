import { Search as SearchIcon } from '@carbon/icons-react';
import {
  Button,
  DataTable,
  DataTableSkeleton,
  DatePicker,
  DatePickerInput,
  Loading,
  Pagination,
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
  Tile,
} from '@carbon/react';
import { useCallback, useEffect, useState, type FC, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';

import ClientComboBox from '@/components/ClientComboBox';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import ExportCsvButton from '@/components/ExportCsvButton';
import ManagementUnitComboBox from '@/components/ManagementUnitComboBox';
import { StatusTag } from '@/components/StatusTag/StatusTag';
import { useNotification } from '@/context/notification/useNotification';
import { useSessionState, type LastSearch } from '@/hooks/useSessionState';
import { safeErrorMessage } from '@/lib/errorMessage';
import PageLayout from '@/pages/PageLayout';
import {
  getManagementUnits,
  getFileClientTypes,
  getFileStatuses,
  getFileTypes,
  getOrgUnits,
  getRangeZones,
  type CodeOption,
} from '@/services/codeLists';
import type { ManagementUnit } from '@/services/codeLists';
import { DEFAULT_PAGE_SIZE, PAGE_SIZES } from '@/services/paging';
import {
  rangeTenureExportPath,
  searchRangeTenures,
  type RangeTenureSearchParams,
  type RangeTenureSummary,
} from '@/services/range_tenure_search';
import { formatDate } from '@/utils/formatDate';
import { parseTypedDate, TYPED_DATE_PATTERN } from '@/utils/typedDate';

// Column order follows the legacy FTA001R results grid.
const HEADERS = [
  { key: 'orgUnitCode', header: 'Administration organization unit' },
  { key: 'clientName', header: 'Client name' },
  { key: 'fileClientTypeDesc', header: 'Client type' },
  { key: 'fileTypeCode', header: 'File type' },
  { key: 'forestFileId', header: 'File ID' },
  { key: 'fileStatusDesc', header: 'Status' },
  { key: 'issueDate', header: 'Issue date' },
  { key: 'expiryDate', header: 'Expiry date' },
];

/** A result row carrying the id Carbon's DataTable requires. */
type Row = RangeTenureSummary & { id: string };

const SearchingIcon = () => <Loading small withOverlay={false} description="" />;

const formatCellText = (value: string | null | undefined) =>
  value && value.trim().length > 0 ? value.trim() : '—';

const EMPTY_FORM: RangeTenureSearchParams = {};

/** Legacy requires at least two criteria before it will run this search. */
const MIN_CRITERIA = 2;

/**
 * FTA001R — Range Tenure Search.
 *
 * <p>Criteria match the legacy screen: organization unit, zone, management unit, file,
 * type, status, both date ranges, client (number / location / name / type), and
 * the six range-provision figures — provision year plus From/To pairs for
 * authorized use, temporary increase, billable and non-billable non-use, and
 * total annual use.
 *
 * <p>Zone is a dropdown that reloads when Admin Organization Unit changes, as legacy
 * does; the value is a 4-character zone code matched against
 * `pfu.district_admin_zone`.
 *
 * <p>Two legacy behaviours live in the backend SQL: a blank file type defaults
 * to `E%`/`H%` (range file types), and a blank client type defaults to the main
 * licensee ('A').
 *
 * <p>Layout and class names are the shared search-screen treatment in
 * `styles/_search.scss`; see TenureSearch for the pattern.
 */
const RangeTenureSearch: FC = () => {
  const navigate = useNavigate();
  const { display } = useNotification();

  // Criteria and the last search are kept for the browser tab; see TenureSearch.
  const [form, setForm] = useSessionState<RangeTenureSearchParams>(
    'fta.search.rangeTenure.form',
    EMPTY_FORM,
  );
  const [lastSearch, setLastSearch] = useSessionState<LastSearch<RangeTenureSearchParams> | null>(
    'fta.search.rangeTenure.last',
    null,
  );
  const [rows, setRows] = useState<Row[] | null>(null);
  // The criteria the rows on screen came from. The export must use these, not
  // `form` — the user may have edited a field since searching, and exporting
  // criteria that were never searched would hand back a different result set.
  const [searched, setSearched] = useState<RangeTenureSearchParams | null>(null);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [orgUnits, setOrgUnits] = useState<CodeOption[]>([]);
  const [fileTypes, setFileTypes] = useState<CodeOption[]>([]);
  const [fileStatuses, setFileStatuses] = useState<CodeOption[]>([]);
  const [clientTypes, setClientTypes] = useState<CodeOption[]>([]);
  const [zones, setZones] = useState<CodeOption[]>([]);
  const [mgmtUnits, setMgmtUnits] = useState<ManagementUnit[]>([]);
  const [codeListsLoading, setCodeListsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    Promise.allSettled([
      getOrgUnits(),
      getFileTypes(),
      getFileStatuses(),
      getFileClientTypes(),
      getManagementUnits(),
    ]).then((settled) => {
      if (cancelled) return;
      const setters = [setOrgUnits, setFileTypes, setFileStatuses, setClientTypes, setMgmtUnits];
      const names = [
        'organization units',
        'file types',
        'file statuses',
        'client types',
        'management units',
      ];
      const failed: string[] = [];
      settled.forEach((r, i) => {
        // Each setter takes the shape its own list returns; the array is
        // parallel to the promises above, so index i lines them up.
        if (r.status === 'fulfilled') (setters[i] as (v: unknown) => void)(r.value);
        else failed.push(names[i]);
      });
      if (failed.length > 0) setError(`Could not load ${failed.join(', ')}`);
      setCodeListsLoading(false);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  // Zones belong to a district, so the list reloads whenever the organization unit
  // changes — and any zone already chosen is cleared, since it may not exist
  // under the new district.
  const orgUnitNo = form.orgUnitNo;
  useEffect(() => {
    let cancelled = false;
    getRangeZones(orgUnitNo)
      .then((z) => {
        if (!cancelled) setZones(z);
      })
      .catch(() => {
        if (!cancelled) setZones([]);
      });
    return () => {
      cancelled = true;
    };
  }, [orgUnitNo]);

  useEffect(() => {
    if (error) {
      display({ kind: 'error', title: 'Search failed', subtitle: error, timeout: 6000 });
    }
  }, [error, display]);

  const set = <K extends keyof RangeTenureSearchParams>(
    key: K,
    value: RangeTenureSearchParams[K],
  ) => setForm((prev) => ({ ...prev, [key]: value }));

  const runSearch = useCallback(
    async (nextPage: number, nextSize: number, criteria: RangeTenureSearchParams = form) => {
      const filled = Object.entries(criteria).filter(
        ([k, v]) => k !== 'page' && k !== 'size' && v !== undefined && v !== '',
      ).length;
      if (filled < MIN_CRITERIA) {
        setError('Enter at least two search criteria.');
        return;
      }
      if (
        criteria.issueDateFrom &&
        criteria.issueDateTo &&
        criteria.issueDateFrom > criteria.issueDateTo
      ) {
        setError('Start date from must be on or before start date to.');
        return;
      }
      if (
        criteria.expiryDateFrom &&
        criteria.expiryDateTo &&
        criteria.expiryDateFrom > criteria.expiryDateTo
      ) {
        setError('Expiry date from must be on or before expiry date to.');
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const data = await searchRangeTenures({ ...criteria, page: nextPage, size: nextSize });
        setSearched({ ...criteria });
        setLastSearch({ criteria, page: data.page.number, size: data.page.size });
        setRows(data.content.map((r, i) => ({ ...r, id: `${r.forestFileId}-${i}` })));
        setTotalElements(data.page.totalElements);
        setPage(data.page.number);
        setPageSize(data.page.size);
      } catch (e) {
        setError(safeErrorMessage(e));
        setRows([]);
        setTotalElements(0);
      } finally {
        setLoading(false);
      }
    },
    [form, setLastSearch],
  );

  const onSubmit = useCallback(
    (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      void runSearch(0, pageSize);
    },
    [runSearch, pageSize],
  );

  const onPaginate = useCallback(
    ({ page: newPage, pageSize: newSize }: { page: number; pageSize: number }) => {
      void runSearch(newPage - 1, newSize);
    },
    [runSearch],
  );

  const onClear = useCallback(() => {
    setForm(EMPTY_FORM);
    setLastSearch(null);
    setRows(null);
    setTotalElements(0);
    setPage(0);
    setError(null);
  }, [setForm, setLastSearch]);

  // Back on the page with a search from earlier in this tab: run it again (fresh
  // results, same page), once the code lists — and so the form — are ready.
  const [restored, setRestored] = useState(false);
  useEffect(() => {
    if (codeListsLoading || restored) return;
    setRestored(true);
    if (lastSearch && rows === null) {
      void runSearch(lastSearch.page, lastSearch.size, lastSearch.criteria);
    }
  }, [codeListsLoading, restored, lastSearch, rows, runSearch]);

  const hasResults = rows !== null && rows.length > 0;

  const codeItems = (options: CodeOption[]) =>
    options.map((o) => <SelectItem key={o.code} value={o.code} text={o.description || o.code} />);

  /** The six range-provision figures, each a From/To pair over the same column. */
  const usageRanges: {
    label: string;
    from: keyof RangeTenureSearchParams;
    to: keyof RangeTenureSearchParams;
  }[] = [
    { label: 'Authorized use', from: 'authorizedUseFrom', to: 'authorizedUseTo' },
    { label: 'Temporary increase', from: 'temporaryIncreaseFrom', to: 'temporaryIncreaseTo' },
    { label: 'Billable non-use', from: 'billableNonUseFrom', to: 'billableNonUseTo' },
    {
      label: 'Non-billable non-use',
      from: 'nonBillableNonUseFrom',
      to: 'nonBillableNonUseTo',
    },
    { label: 'Total annual use', from: 'totalAnnualUseFrom', to: 'totalAnnualUseTo' },
  ];

  if (codeListsLoading) {
    return (
      <div className="fsp-search__loading" role="status" aria-live="polite">
        <Loading description="Loading…" withOverlay={false} />
      </div>
    );
  }

  return (
    <PageLayout
      title="Range Tenure Search"
      subtitle="Find a range agreement by organization unit, zone, client, status or annual use"
    >
      <Tile className="fsp-search__tile">
        <form className="fsp-search__form" onSubmit={onSubmit}>
          <div className="fsp-search__field-grid">
            <div className="fsp-search__wide-cell">
              <Select
                id="rt-org-unit"
                labelText="Administration organization unit"
                value={form.orgUnitNo ?? ''}
                onChange={(e) => {
                  set('orgUnitNo', e.target.value);
                  // The zone list is about to reload for a different district.
                  set('zone', '');
                }}
              >
                <SelectItem value="" text="All organization units" />
                {codeItems(orgUnits)}
              </Select>
            </div>

            <Select
              id="rt-zone"
              labelText="Zone"
              value={form.zone ?? ''}
              onChange={(e) => set('zone', e.target.value)}
            >
              <SelectItem value="" text="Any zone" />
              {codeItems(zones)}
            </Select>

            <div className="fsp-search__wide-cell">
              <ManagementUnitComboBox
                id="rt-mgmt-unit"
                units={mgmtUnits}
                typeCode={form.mgmtUnitType}
                unitId={form.mgmtUnitId}
                onChange={(next) => setForm((prev) => ({ ...prev, ...next }))}
              />
            </div>

            <TextInput
              id="rt-file"
              labelText="File"
              placeholder="e.g. RAN076543"
              value={form.forestFileId ?? ''}
              onChange={(e) => set('forestFileId', e.target.value)}
              maxLength={10}
              autoComplete="off"
            />

            <Select
              id="rt-file-type"
              labelText="File type"
              value={form.fileTypeCode ?? ''}
              onChange={(e) => set('fileTypeCode', e.target.value)}
            >
              <SelectItem value="" text="All range file types" />
              {codeItems(fileTypes)}
            </Select>

            <Select
              id="rt-status"
              labelText="Status"
              value={form.fileStatus ?? ''}
              onChange={(e) => set('fileStatus', e.target.value)}
            >
              <SelectItem value="" text="Any status" />
              {codeItems(fileStatuses)}
            </Select>

            <ClientComboBox
              id="rt-client"
              clientNumber={form.clientNumber}
              clientLocnCode={form.clientLocnCode}
              clientName={form.clientName}
              onChange={(next) => setForm((prev) => ({ ...prev, ...next }))}
            />

            <Select
              id="rt-client-type"
              labelText="Client type"
              value={form.fileClientType ?? ''}
              onChange={(e) => set('fileClientType', e.target.value)}
            >
              <SelectItem value="" text="Main licensee" />
              {codeItems(clientTypes)}
            </Select>

            <h2 className="fsp-search__group-heading">Date ranges</h2>

            <DatePicker
              datePickerType="single"
              dateFormat="Y-m-d"
              className="fsp-search__row-start"
              value={form.issueDateFrom ? [form.issueDateFrom] : []}
              onChange={(dates) =>
                set('issueDateFrom', dates[0] ? dates[0].toISOString().slice(0, 10) : '')
              }
            >
              <DatePickerInput
                id="rt-start-from"
                labelText="Start date from"
                placeholder="YYYY-MM-DD"
                pattern={TYPED_DATE_PATTERN}
                onChange={(e) => {
                  const text = e.target.value;
                  if (text.trim() === '') set('issueDateFrom', '');
                  else {
                    const typed = parseTypedDate(text);
                    if (typed) set('issueDateFrom', typed);
                  }
                }}
              />
            </DatePicker>

            <DatePicker
              datePickerType="single"
              dateFormat="Y-m-d"
              value={form.issueDateTo ? [form.issueDateTo] : []}
              onChange={(dates) =>
                set('issueDateTo', dates[0] ? dates[0].toISOString().slice(0, 10) : '')
              }
            >
              <DatePickerInput
                id="rt-start-to"
                labelText="Start date to"
                placeholder="YYYY-MM-DD"
                pattern={TYPED_DATE_PATTERN}
                onChange={(e) => {
                  const text = e.target.value;
                  if (text.trim() === '') set('issueDateTo', '');
                  else {
                    const typed = parseTypedDate(text);
                    if (typed) set('issueDateTo', typed);
                  }
                }}
              />
            </DatePicker>

            <DatePicker
              datePickerType="single"
              dateFormat="Y-m-d"
              value={form.expiryDateFrom ? [form.expiryDateFrom] : []}
              onChange={(dates) =>
                set('expiryDateFrom', dates[0] ? dates[0].toISOString().slice(0, 10) : '')
              }
            >
              <DatePickerInput
                id="rt-expiry-from"
                labelText="Expiry date from"
                placeholder="YYYY-MM-DD"
                pattern={TYPED_DATE_PATTERN}
                onChange={(e) => {
                  const text = e.target.value;
                  if (text.trim() === '') set('expiryDateFrom', '');
                  else {
                    const typed = parseTypedDate(text);
                    if (typed) set('expiryDateFrom', typed);
                  }
                }}
              />
            </DatePicker>

            <DatePicker
              datePickerType="single"
              dateFormat="Y-m-d"
              value={form.expiryDateTo ? [form.expiryDateTo] : []}
              onChange={(dates) =>
                set('expiryDateTo', dates[0] ? dates[0].toISOString().slice(0, 10) : '')
              }
            >
              <DatePickerInput
                id="rt-expiry-to"
                labelText="Expiry date to"
                placeholder="YYYY-MM-DD"
                pattern={TYPED_DATE_PATTERN}
                onChange={(e) => {
                  const text = e.target.value;
                  if (text.trim() === '') set('expiryDateTo', '');
                  else {
                    const typed = parseTypedDate(text);
                    if (typed) set('expiryDateTo', typed);
                  }
                }}
              />
            </DatePicker>

            <h2 className="fsp-search__group-heading">Range provision</h2>

            <TextInput
              id="rt-year"
              className="fsp-search__row-start"
              labelText="Year"
              placeholder="e.g. 2026"
              value={form.provisionYear ?? ''}
              onChange={(e) => set('provisionYear', e.target.value.replace(/\D/g, ''))}
              maxLength={4}
              inputMode="numeric"
              autoComplete="off"
            />

            {usageRanges.flatMap(({ label, from, to }) => [
              <TextInput
                key={from}
                id={`rt-${String(from)}`}
                labelText={`${label} from`}
                value={(form[from] as string | undefined) ?? ''}
                onChange={(e) => set(from, e.target.value.replace(/\D/g, ''))}
                maxLength={30}
                inputMode="numeric"
                autoComplete="off"
              />,
              <TextInput
                key={to}
                id={`rt-${String(to)}`}
                labelText={`${label} to`}
                value={(form[to] as string | undefined) ?? ''}
                onChange={(e) => set(to, e.target.value.replace(/\D/g, ''))}
                maxLength={30}
                inputMode="numeric"
                autoComplete="off"
              />,
            ])}
          </div>

          <div className="fsp-search__actions">
            <Button kind="tertiary" size="md" type="button" onClick={onClear} disabled={loading}>
              Clear all
            </Button>
            <Button
              type="submit"
              size="md"
              disabled={loading}
              renderIcon={loading ? SearchingIcon : SearchIcon}
            >
              {loading ? 'Searching...' : 'Search'}
            </Button>
          </div>
        </form>
      </Tile>

      {(loading || rows !== null) && (
        <div className="fsp-search__results-fullbleed">
          <div className="fsp-search__results">
            {loading ? (
              <>
                <div className="fsp-search__results-header">
                  <span className="fsp-search__results-count fsp-search__results-count--searching">
                    Searching
                    <span className="fsp-search__searching-spinner" aria-hidden="true" />
                  </span>
                </div>
                <div className="fsp-search__table">
                  <DataTableSkeleton
                    headers={HEADERS}
                    rowCount={Math.min(pageSize, 10)}
                    showHeader={false}
                    showToolbar={false}
                    aria-label="Loading search results"
                  />
                </div>
              </>
            ) : hasResults ? (
              <>
                <div className="fsp-search__results-header">
                  <span className="fsp-search__results-count">
                    {totalElements.toLocaleString()}{' '}
                    {totalElements === 1 ? 'agreement' : 'agreements'} found
                  </span>
                  {searched && <ExportCsvButton path={rangeTenureExportPath(searched)} />}
                </div>

                <div className="fsp-search__table">
                  <DataTable rows={rows!} headers={HEADERS}>
                    {({ rows: dtRows, headers, getTableProps, getHeaderProps, getRowProps }) => (
                      <TableContainer>
                        <Table {...getTableProps()} size="md">
                          <TableHead>
                            <TableRow>
                              {headers.map((h) => (
                                <TableHeader {...getHeaderProps({ header: h })} key={h.key}>
                                  {h.header}
                                </TableHeader>
                              ))}
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {dtRows.map((row) => {
                              const fileId =
                                (row.cells.find((c) => c.info.header === 'forestFileId')?.value as
                                  string | undefined) ?? '';
                              const open = () => {
                                if (fileId) navigate(`/range/${encodeURIComponent(fileId)}`);
                              };
                              return (
                                <TableRow
                                  {...getRowProps({ row })}
                                  key={row.id}
                                  className="fsp-search__row--selectable"
                                  onClick={open}
                                  onKeyDown={(e) => {
                                    if (e.key === 'Enter' || e.key === ' ') {
                                      e.preventDefault();
                                      open();
                                    }
                                  }}
                                  tabIndex={0}
                                  role="link"
                                >
                                  {row.cells.map((cell) => {
                                    const value = cell.value as string | null | undefined;
                                    if (cell.info.header === 'fileStatusDesc') {
                                      return (
                                        <TableCell key={cell.id}>
                                          {value ? <StatusTag status={value} /> : '—'}
                                        </TableCell>
                                      );
                                    }
                                    if (
                                      cell.info.header === 'issueDate' ||
                                      cell.info.header === 'expiryDate'
                                    ) {
                                      return (
                                        <TableCell
                                          key={cell.id}
                                          className="fsp-search__cell--nowrap"
                                        >
                                          {formatCellText(formatDate(value))}
                                        </TableCell>
                                      );
                                    }
                                    return (
                                      <TableCell key={cell.id}>{formatCellText(value)}</TableCell>
                                    );
                                  })}
                                </TableRow>
                              );
                            })}
                          </TableBody>
                        </Table>
                      </TableContainer>
                    )}
                  </DataTable>
                </div>

                <Pagination
                  page={page + 1}
                  pageSize={pageSize}
                  pageSizes={PAGE_SIZES}
                  totalItems={totalElements}
                  onChange={onPaginate}
                  size="md"
                />
              </>
            ) : (
              rows !== null &&
              !error && (
                <EmptyState
                  title="No range agreements found"
                  body={
                    <>
                      No records match your search criteria.
                      <br />
                      Try adjusting your filters and searching again.
                    </>
                  }
                />
              )
            )}
          </div>
        </div>
      )}
    </PageLayout>
  );
};

export default RangeTenureSearch;
