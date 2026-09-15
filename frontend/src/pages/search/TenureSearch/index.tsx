import { Search as SearchIcon } from '@carbon/icons-react';
import {
  Button,
  DataTable,
  DataTableSkeleton,
  DatePicker,
  DatePickerInput,
  Loading,
  Pagination,
  RadioButton,
  RadioButtonGroup,
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
  getFileSources,
  getFileStatuses,
  getFileTypes,
  getMapNotationTypes,
  getOrgUnits,
  type CodeOption,
} from '@/services/codeLists';
import { searchTenures, type TenureSearchParams, type TenureSummary } from '@/services/tenure';
import { formatDate } from '@/utils/formatDate';

// Column order follows the legacy FTA001 results grid.
const HEADERS = [
  { key: 'orgUnitCode', header: 'Admin org unit' },
  { key: 'clientName', header: 'Client name' },
  { key: 'fileClientTypeDesc', header: 'Client type' },
  { key: 'fileTypeCode', header: 'File type' },
  { key: 'forestFileId', header: 'File ID' },
  { key: 'fileStatusDesc', header: 'File status' },
  { key: 'issueDate', header: 'Issue date' },
  { key: 'expiryDate', header: 'Expiry date' },
];

/** A result row carrying the id Carbon's DataTable requires. */
type Row = TenureSummary & { id: string };

const DEFAULT_PAGE_SIZE = 10;

/** Tenure type is a fixed list in the legacy screen, not a code table. */
const TENURE_TYPES = [
  { value: '', label: 'Any' },
  { value: 'T', label: 'Timber' },
  { value: 'R', label: 'Range' },
  { value: 'F', label: 'Recreation' },
];

/** Salvage and Cash Sale are the same yes/no/any triple. */
const YES_NO = [
  { value: '', label: 'Any' },
  { value: 'Y', label: 'Yes' },
  { value: 'N', label: 'No' },
];

const SearchingIcon = () => <Loading small withOverlay={false} description="" />;

/** Em-dash rather than a blank cell, so an empty value still reads as a value. */
const formatCellText = (value: string | null | undefined) =>
  value && value.trim().length > 0 ? value.trim() : '—';

/** Cleared fields are dropped from the querystring rather than sent empty. */
const EMPTY_FORM: TenureSearchParams = { sortBy: 'org' };

/**
 * FTA001 — Tenure Search.
 *
 * <p>Criteria match the legacy screen one for one, which is also the parameter
 * list of `THE.FTA_001_TENR_SRCH.MAINLINE`: org unit, file, client, management
 * unit, associated file, project name, both date ranges, salvage, cash sale,
 * map notation, and the sort choice.
 *
 * <p>Layout follows nr-fsp-new's FSP Search — criteria in a white tile over a
 * responsive grid, then a full-bleed grey results panel. The `fsp-search__*`
 * class names are deliberate: `styles/_tables.scss` and `styles/_overrides.scss`
 * already carry rules for them, so reusing the names inherits that styling
 * rather than duplicating it.
 */
const TenureSearch: FC = () => {
  const navigate = useNavigate();
  const { display } = useNotification();

  const [form, setForm] = useState<TenureSearchParams>(EMPTY_FORM);
  const [rows, setRows] = useState<Row[] | null>(null);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Code lists behind the six dropdowns.
  const [orgUnits, setOrgUnits] = useState<CodeOption[]>([]);
  const [fileTypes, setFileTypes] = useState<CodeOption[]>([]);
  const [fileStatuses, setFileStatuses] = useState<CodeOption[]>([]);
  const [clientTypes, setClientTypes] = useState<CodeOption[]>([]);
  const [fileSources, setFileSources] = useState<CodeOption[]>([]);
  const [mapNotationTypes, setMapNotationTypes] = useState<CodeOption[]>([]);
  const [codeListsLoading, setCodeListsLoading] = useState(true);

  useEffect(() => {
    // Loaded in parallel and settled individually: one list failing shouldn't
    // empty the other five, since the screen still works without it.
    let cancelled = false;
    Promise.allSettled([
      getOrgUnits(),
      getFileTypes(),
      getFileStatuses(),
      getFileClientTypes(),
      getFileSources(),
      getMapNotationTypes(),
    ]).then((settled) => {
      if (cancelled) return;
      const setters = [
        setOrgUnits,
        setFileTypes,
        setFileStatuses,
        setClientTypes,
        setFileSources,
        setMapNotationTypes,
      ];
      const names = [
        'org units',
        'file types',
        'file statuses',
        'client types',
        'file sources',
        'map notation types',
      ];
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

  // Failures surface as a toast rather than inline, so the results area keeps
  // showing the last good page instead of collapsing under an error panel.
  useEffect(() => {
    if (error) {
      display({ kind: 'error', title: 'Search failed', subtitle: error, timeout: 6000 });
    }
  }, [error, display]);

  const set = <K extends keyof TenureSearchParams>(key: K, value: TenureSearchParams[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const runSearch = useCallback(
    async (nextPage: number, nextSize: number) => {
      // The package rejects a date range that runs backwards with a message the
      // user can't act on; catch it here instead of spending the round-trip.
      if (form.issueDateFrom && form.issueDateTo && form.issueDateFrom > form.issueDateTo) {
        setError('Issue Date From must be on or before Issue Date To.');
        return;
      }
      if (form.expiryDateFrom && form.expiryDateTo && form.expiryDateFrom > form.expiryDateTo) {
        setError('Expiry Date From must be on or before Expiry Date To.');
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const data = await searchTenures({ ...form, page: nextPage, size: nextSize });
        // The file id is unique within a page but can recur across pages as the
        // user moves back and forth, so the row key carries the index too.
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

  // Carbon's Pagination is 1-indexed; the backend is 0-indexed.
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
  // Legacy only offers map notation for map-notation files.
  const showMapNotation = form.fileTypeCode === 'M01';

  const codeItems = (options: CodeOption[]) =>
    options.map((o) => <SelectItem key={o.code} value={o.code} text={o.description || o.code} />);

  if (codeListsLoading) {
    return (
      <div className="fsp-search__loading" role="status" aria-live="polite">
        <Loading description="Loading…" withOverlay={false} />
      </div>
    );
  }

  return (
    <PageLayout
      title="Tenure Search"
      subtitle="Find a forest tenure by file, client, management unit or status"
    >
      <Tile className="fsp-search__tile">
        <form className="fsp-search__form" onSubmit={onSubmit}>
          <div className="fsp-search__field-grid">
            <Select
              id="ts-org-unit"
              labelText="Admin org unit"
              value={form.adminOrgUnitNo ?? ''}
              onChange={(e) => set('adminOrgUnitNo', e.target.value)}
            >
              <SelectItem value="" text="All org units" />
              {codeItems(orgUnits)}
            </Select>

            <TextInput
              id="ts-mgmt-unit-type"
              labelText="Mgmt unit type"
              value={form.mgmtUnitType ?? ''}
              onChange={(e) => set('mgmtUnitType', e.target.value)}
              maxLength={1}
              autoComplete="off"
            />

            <TextInput
              id="ts-mgmt-unit-id"
              labelText="Mgmt unit ID"
              value={form.mgmtUnitId ?? ''}
              onChange={(e) => set('mgmtUnitId', e.target.value)}
              maxLength={4}
              autoComplete="off"
            />

            <TextInput
              id="ts-file-id"
              labelText="File ID"
              placeholder="e.g. A19201"
              value={form.forestFileId ?? ''}
              onChange={(e) => set('forestFileId', e.target.value)}
              maxLength={10}
              autoComplete="off"
            />

            <Select
              id="ts-tenure-type"
              labelText="Tenure type"
              value={form.tenureType ?? ''}
              onChange={(e) => set('tenureType', e.target.value)}
            >
              {TENURE_TYPES.map((t) => (
                <SelectItem key={t.value} value={t.value} text={t.label} />
              ))}
            </Select>

            <Select
              id="ts-file-type"
              labelText="File type"
              value={form.fileTypeCode ?? ''}
              onChange={(e) => set('fileTypeCode', e.target.value)}
            >
              <SelectItem value="" text="All file types" />
              {codeItems(fileTypes)}
            </Select>

            <Select
              id="ts-file-status"
              labelText="File status"
              value={form.fileStatus ?? ''}
              onChange={(e) => set('fileStatus', e.target.value)}
            >
              <SelectItem value="" text="Any status" />
              {codeItems(fileStatuses)}
            </Select>

            <TextInput
              id="ts-client-number"
              labelText="Client number"
              value={form.clientNumber ?? ''}
              onChange={(e) => set('clientNumber', e.target.value)}
              maxLength={8}
              autoComplete="off"
            />

            <TextInput
              id="ts-client-locn"
              labelText="Client location"
              value={form.clientLocnCode ?? ''}
              onChange={(e) => set('clientLocnCode', e.target.value)}
              maxLength={2}
              autoComplete="off"
            />

            <TextInput
              id="ts-client-name"
              labelText="Client name"
              placeholder="e.g. West Fraser"
              value={form.clientName ?? ''}
              onChange={(e) => set('clientName', e.target.value)}
              maxLength={60}
              autoComplete="off"
            />

            <Select
              id="ts-client-type"
              labelText="Client type"
              value={form.fileClientType ?? ''}
              onChange={(e) => set('fileClientType', e.target.value)}
            >
              <SelectItem value="" text="Any client type" />
              {codeItems(clientTypes)}
            </Select>

            {showMapNotation && (
              <Select
                id="ts-map-notation"
                labelText="Map notation type"
                value={form.mapNotationTypeCode ?? ''}
                onChange={(e) => set('mapNotationTypeCode', e.target.value)}
              >
                <SelectItem value="" text="Any" />
                {codeItems(mapNotationTypes)}
              </Select>
            )}

            <Select
              id="ts-file-source"
              labelText="Assoc. file source"
              value={form.fileSource ?? ''}
              onChange={(e) => set('fileSource', e.target.value)}
            >
              <SelectItem value="" text="Any source" />
              {codeItems(fileSources)}
            </Select>

            <TextInput
              id="ts-assoc-file-id"
              labelText="Assoc. file ID"
              value={form.assocFileId ?? ''}
              onChange={(e) => set('assocFileId', e.target.value)}
              maxLength={10}
              autoComplete="off"
            />

            <TextInput
              id="ts-file-name"
              labelText="File / project name"
              value={form.fileName ?? ''}
              onChange={(e) => set('fileName', e.target.value)}
              maxLength={30}
              autoComplete="off"
            />

            <Select
              id="ts-salvage"
              labelText="Salvage"
              value={form.salvageInd ?? ''}
              onChange={(e) => set('salvageInd', e.target.value)}
            >
              {YES_NO.map((o) => (
                <SelectItem key={o.value} value={o.value} text={o.label} />
              ))}
            </Select>

            <Select
              id="ts-cash-sale"
              labelText="Cash sale"
              value={form.cashSaleInd ?? ''}
              onChange={(e) => set('cashSaleInd', e.target.value)}
            >
              {YES_NO.map((o) => (
                <SelectItem key={o.value} value={o.value} text={o.label} />
              ))}
            </Select>

            {/* Heading spans the grid so the four date fields start a fresh row. */}
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
                id="ts-issue-from"
                labelText="Issue date from"
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
                id="ts-issue-to"
                labelText="Issue date to"
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
                id="ts-expiry-from"
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
                id="ts-expiry-to"
                labelText="Expiry date to"
                placeholder="YYYY-MM-DD"
                pattern="\d{4}-\d{2}-\d{2}"
              />
            </DatePicker>

            <div className="fsp-search__sort-cell">
              <RadioButtonGroup
                legendText="Sort by"
                name="ts-sort-by"
                valueSelected={form.sortBy ?? 'org'}
                onChange={(value) => set('sortBy', String(value))}
              >
                <RadioButton labelText="Admin org" value="org" id="ts-sort-org" />
                <RadioButton labelText="Client name" value="client" id="ts-sort-client" />
                <RadioButton labelText="File type" value="fileType" id="ts-sort-filetype" />
              </RadioButtonGroup>
            </div>
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

      {/* Nothing is shown until a search has run, so the page opens on the
          criteria rather than on an empty table. */}
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
                    {totalElements.toLocaleString()} {totalElements === 1 ? 'tenure' : 'tenures'}{' '}
                    found
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
                              // Navigate from the file id cell, not row.id —
                              // that carries the disambiguating index suffix.
                              const forestFileId =
                                (row.cells.find((c) => c.info.header === 'forestFileId')?.value as
                                  string | undefined) ?? '';
                              const open = () => navigate(`/tenures/${forestFileId}`);
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
                                        <TableCell key={cell.id}>
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
                  pageSizes={[10, 25, 50, 100]}
                  totalItems={totalElements}
                  onChange={onPaginate}
                  size="md"
                />
              </>
            ) : (
              rows !== null &&
              !error && (
                <EmptyState
                  title="No tenures found"
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

export default TenureSearch;
