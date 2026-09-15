import { Search as SearchIcon } from '@carbon/icons-react';
import {
  Button,
  DataTable,
  DataTableSkeleton,
  Loading,
  Pagination,
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

import { EmptyState } from '@/components/EmptyState/EmptyState';
import { StatusTag } from '@/components/StatusTag/StatusTag';
import { useNotification } from '@/context/notification/useNotification';
import { safeErrorMessage } from '@/lib/errorMessage';
import PageLayout from '@/pages/PageLayout';
import {
  searchClients,
  type ClientSearchParams,
  type ClientSearchResult,
} from '@/services/client_search';
import { DEFAULT_PAGE_SIZE, PAGE_SIZES } from '@/services/paging';

// Column order follows the legacy SIL21 results grid.
const HEADERS = [
  { key: 'clientAcronym', header: 'Client acronym' },
  { key: 'clientNumber', header: 'Client number' },
  { key: 'clientLocnCode', header: 'Location code' },
  { key: 'clientName', header: 'Client name' },
  { key: 'clientLocnName', header: 'Location' },
  { key: 'city', header: 'City' },
  { key: 'clientStatusCode', header: 'Status' },
];

/** A result row carrying the id Carbon's DataTable requires. */
type Row = ClientSearchResult & { id: string };

const SearchingIcon = () => <Loading small withOverlay={false} description="" />;

const formatCellText = (value: string | null | undefined) =>
  value && value.trim().length > 0 ? value.trim() : '—';

const EMPTY_FORM: ClientSearchParams = {};

/**
 * SIL21 — Client Search.
 *
 * <p>Criteria match the legacy screen: acronym, client number, and the three
 * name parts. The client number is an exact match; everything else is a prefix
 * match, as the legacy package does it.
 *
 * <p>Reference data, so rows are not clickable — there is no client detail
 * screen. Legacy uses this same page as the "..." picker other screens launch;
 * that mode is not implemented here.
 *
 * <p>Layout and class names are the shared search-screen treatment in
 * `styles/_search.scss`; see TenureSearch for the pattern.
 */
const ClientSearch: FC = () => {
  const { display } = useNotification();

  const [form, setForm] = useState<ClientSearchParams>(EMPTY_FORM);
  const [rows, setRows] = useState<Row[] | null>(null);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (error) {
      display({ kind: 'error', title: 'Search failed', subtitle: error, timeout: 6000 });
    }
  }, [error, display]);

  const set = <K extends keyof ClientSearchParams>(key: K, value: ClientSearchParams[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const runSearch = useCallback(
    async (nextPage: number, nextSize: number) => {
      // Legacy requires at least one criterion; an unfiltered search would scan
      // every forest client in the province.
      const hasCriterion = Boolean(
        form.clientNumber ||
        form.clientAcronym ||
        form.clientName ||
        form.legalFirstName ||
        form.legalMiddleName,
      );
      if (!hasCriterion) {
        setError('Enter at least one search criterion.');
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const data = await searchClients({ ...form, page: nextPage, size: nextSize });
        // A client appears once per location, so the client number alone is not
        // unique across rows — the location code and index disambiguate.
        setRows(
          data.content.map((r, i) => ({
            ...r,
            id: `${r.clientNumber ?? 'client'}-${r.clientLocnCode ?? i}-${i}`,
          })),
        );
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

  return (
    <PageLayout title="Client Search" subtitle="Find a forest client by number, acronym or name">
      <Tile className="fsp-search__tile">
        <form className="fsp-search__form" onSubmit={onSubmit}>
          <div className="fsp-search__field-grid">
            <TextInput
              id="cl-acronym"
              labelText="Client acronym"
              placeholder="e.g. CANFOR"
              value={form.clientAcronym ?? ''}
              onChange={(e) => set('clientAcronym', e.target.value)}
              maxLength={8}
              autoComplete="off"
            />

            <TextInput
              id="cl-num"
              labelText="Client number"
              placeholder="e.g. 00001012"
              value={form.clientNumber ?? ''}
              onChange={(e) => set('clientNumber', e.target.value)}
              maxLength={8}
              autoComplete="off"
            />

            <TextInput
              id="cl-last-name"
              labelText="Last name"
              placeholder="e.g. Canfor"
              value={form.clientName ?? ''}
              onChange={(e) => set('clientName', e.target.value)}
              maxLength={30}
              autoComplete="off"
            />

            <TextInput
              id="cl-first-name"
              labelText="First name"
              value={form.legalFirstName ?? ''}
              onChange={(e) => set('legalFirstName', e.target.value)}
              maxLength={30}
              autoComplete="off"
            />

            <TextInput
              id="cl-middle-name"
              labelText="Middle name"
              value={form.legalMiddleName ?? ''}
              onChange={(e) => set('legalMiddleName', e.target.value)}
              maxLength={30}
              autoComplete="off"
            />
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
                    {totalElements.toLocaleString()} {totalElements === 1 ? 'client' : 'clients'}{' '}
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
                            {dtRows.map((row) => (
                              <TableRow {...getRowProps({ row })} key={row.id}>
                                {row.cells.map((cell) => {
                                  const value = cell.value as string | null | undefined;
                                  if (cell.info.header === 'clientStatusCode') {
                                    return (
                                      <TableCell key={cell.id}>
                                        {value ? <StatusTag status={value} /> : '—'}
                                      </TableCell>
                                    );
                                  }
                                  return (
                                    <TableCell key={cell.id}>{formatCellText(value)}</TableCell>
                                  );
                                })}
                              </TableRow>
                            ))}
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
                  title="No clients found"
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

export default ClientSearch;
