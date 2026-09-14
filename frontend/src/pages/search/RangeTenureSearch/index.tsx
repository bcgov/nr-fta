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

import { EmptyState } from '@/components/EmptyState/EmptyState';
import { StatusTag } from '@/components/StatusTag/StatusTag';
import { useNotification } from '@/context/notification/useNotification';
import { safeErrorMessage } from '@/lib/errorMessage';
import PageLayout from '@/pages/PageLayout';
import {
  getFileClientTypes,
  getFileStatuses,
  getFileTypes,
  getOrgUnits,
  getRangeZones,
  type CodeOption,
} from '@/services/codeLists';
import { DEFAULT_PAGE_SIZE, PAGE_SIZES } from '@/services/paging';
import {
  searchRangeTenures,
  type RangeTenureSearchParams,
  type RangeTenureSummary,
} from '@/services/range_tenure_search';
import { formatDate } from '@/utils/formatDate';

// Column order follows the legacy FTA001R results grid.
const HEADERS = [
  { key: 'orgUnitCode', header: 'Admin org unit' },
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
 * <p>Criteria match the legacy screen: org unit, zone, management unit, file,
 * type, status, both date ranges, client (number / location / name / type), and
 * the six range-provision figures — provision year plus From/To pairs for
 * authorized use, temporary increase, billable and non-billable non-use, and
 * total annual use.
 *
 * <p>Zone is a dropdown that reloads when Admin Org Unit changes, as legacy
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

  const [form, setForm] = useState<RangeTenureSearchParams>(EMPTY_FORM);
  const [rows, setRows] = useState<Row[] | null>(null);
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
  const [codeListsLoading, setCodeListsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    Promise.allSettled([
      getOrgUnits(),
      getFileTypes(),
      getFileStatuses(),
      getFileClientTypes(),
    ]).then((settled) => {
      if (cancelled) return;
      const setters = [setOrgUnits, setFileTypes, setFileStatuses, setClientTypes];
      const names = ['org units', 'file types', 'file statuses', 'client types'];
      const failed: string[] = [];
      settled.forEach((r, i) => {
        if (r.status === 'fulfilled') setters[i](r.value);
        else failed.push(names[i]);
      });
      if (failed.length > 0) setError(`Could not load ${failed.join(', ')}`);
      setCodeListsLoading(false);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  // Zones belong to a district, so the list reloads whenever the org unit
  // changes — and any zone already chosen is cleared, since it may not exist
  // under the new district.
  const orgUnitNo = form.orgUnitCode;
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
    async (nextPage: number, nextSize: number) => {
      const filled = Object.entries(form).filter(
        ([k, v]) => k !== 'page' && k !== 'size' && v !== undefined && v !== '',
      ).length;
      if (filled < MIN_CRITERIA) {
        setError('Enter at least two search criteria.');
        return;
      }
      if (form.issueDateFrom && form.issueDateTo && form.issueDateFrom > form.issueDateTo) {
        setError('Start date from must be on or before start date to.');
        return;
      }
      if (form.expiryDateFrom && form.expiryDateTo && form.expiryDateFrom > form.expiryDateTo) {
        setError('Expiry date from must be on or before expiry date to.');
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const data = await searchRangeTenures({ ...form, page: nextPage, size: nextSize });
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
    [form],
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
    setRows(null);
    setTotalElements(0);
    setPage(0);
    setError(null);
  }, []);

  const hasResults = rows !== null && rows.length > 0;

  const codeItems = (options: CodeOption[]) =>
    options.map((o) => (
      <SelectItem key={o.code} value={o.code} text={o.description || o.code} />
    ));

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
      subtitle="Find a range agreement by org unit, zone, client, status or annual use"
    >
      <Tile className="fsp-search__tile">
        <form className="fsp-search__form" onSubmit={onSubmit}>
          <div className="fsp-search__field-grid">
            <Select
              id="rt-org-unit"
              labelText="Admin org unit"
              value={form.orgUnitCode ?? ''}
              onChange={(e) => {
                set('orgUnitCode', e.target.value);
                // The zone list is about to reload for a different district.
                set('zone', '');
              }}
            >
              <SelectItem value="" text="All org units" />
              {codeItems(orgUnits)}
            </Select>

            <Select
              id="rt-zone"
              labelText="Zone"
              value={form.zone ?? ''}
              onChange={(e) => set('zone', e.target.value)}
            >
              <SelectItem value="" text="Any zone" />
              {codeItems(zones)}
            </Select>

            <TextInput
              id="rt-mgmt-unit-type"
              labelText="Mgmt unit type"
              value={form.mgmtUnitType ?? ''}
              onChange={(e) => set('mgmtUnitType', e.target.value)}
              maxLength={1}
              autoComplete="off"
            />

            <TextInput
              id="rt-mgmt-unit-id"
              labelText="Mgmt unit ID"
              value={form.mgmtUnitId ?? ''}
              onChange={(e) => set('mgmtUnitId', e.target.value)}
              maxLength={3}
              autoComplete="off"
            />

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

            <TextInput
              id="rt-client-number"
              labelText="Client number"
              value={form.clientNumber ?? ''}
              onChange={(e) => set('clientNumber', e.target.value)}
              maxLength={8}
              autoComplete="off"
            />

            <TextInput
              id="rt-client-locn"
              labelText="Client location"
              value={form.clientLocnCode ?? ''}
              onChange={(e) => set('clientLocnCode', e.target.value)}
              maxLength={2}
              autoComplete="off"
            />

            <TextInput
              id="rt-client-name"
              labelText="Client name"
              placeholder="e.g. Meadow Ranch"
              value={form.clientName ?? ''}
              onChange={(e) => set('clientName', e.target.value)}
              maxLength={60}
              autoComplete="off"
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
                pattern="\d{4}-\d{2}-\d{2}"
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
                pattern="\d{4}-\d{2}-\d{2}"
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
                pattern="\d{4}-\d{2}-\d{2}"
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
                pattern="\d{4}-\d{2}-\d{2}"
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
                                  | string
                                  | undefined) ?? '';
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
                                      cell.info.header === 'issueDate'
                                      || cell.info.header === 'expiryDate'
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
