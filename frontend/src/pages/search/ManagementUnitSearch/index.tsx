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
import { useNotification } from '@/context/notification/useNotification';
import { safeErrorMessage } from '@/lib/errorMessage';
import PageLayout from '@/pages/PageLayout';
import {
  searchManagementUnits,
  type MgmtUnitSearch,
  type MgmtUnitSearchParams,
} from '@/services/mgmt_unit_search';
import { DEFAULT_PAGE_SIZE, PAGE_SIZES } from '@/services/paging';
import { formatDate } from '@/utils/formatDate';

const HEADERS = [
  { key: 'mgmtUnitTypeCode', header: 'MU type' },
  { key: 'description', header: 'Description' },
  { key: 'effectiveDate', header: 'Effective date' },
  { key: 'expiryDate', header: 'Expiry date' },
];

/** A result row carrying the id Carbon's DataTable requires. */
type Row = MgmtUnitSearch & { id: string };

const SearchingIcon = () => <Loading small withOverlay={false} description="" />;

const formatCellText = (value: string | null | undefined) =>
  value && value.trim().length > 0 ? value.trim() : '—';

const EMPTY_FORM: MgmtUnitSearchParams = {};

/**
 * SIL004 — Management Unit Search.
 *
 * <p>The legacy screen offers a single management-unit-type dropdown; the code
 * and description filters here narrow that same list rather than reproducing a
 * legacy behaviour. Reference data, so rows are not clickable.
 *
 * <p>Layout and class names are the shared search-screen treatment in
 * `styles/_search.scss`; see TenureSearch for the pattern.
 */
const ManagementUnitSearch: FC = () => {
  const { display } = useNotification();

  const [form, setForm] = useState<MgmtUnitSearchParams>(EMPTY_FORM);
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

  const set = <K extends keyof MgmtUnitSearchParams>(key: K, value: MgmtUnitSearchParams[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const runSearch = useCallback(
    async (nextPage: number, nextSize: number) => {
      setLoading(true);
      setError(null);
      try {
        const data = await searchManagementUnits({ ...form, page: nextPage, size: nextSize });
        setRows(data.content.map((r) => ({ ...r, id: r.mgmtUnitTypeCode })));
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
    <PageLayout
      title="Management Unit Search"
      subtitle="Find a management-unit type by code or description"
    >
      <Tile className="fsp-search__tile">
        <form className="fsp-search__form" onSubmit={onSubmit}>
          <div className="fsp-search__field-grid">
            <TextInput
              id="mu-type"
              labelText="MU type code"
              placeholder="e.g. TS"
              value={form.mgmtUnitTypeCode ?? ''}
              onChange={(e) => set('mgmtUnitTypeCode', e.target.value)}
              maxLength={1}
              autoComplete="off"
            />

            <TextInput
              id="mu-description"
              labelText="Description"
              placeholder="e.g. Timber Supply Area"
              value={form.description ?? ''}
              onChange={(e) => set('description', e.target.value)}
              maxLength={120}
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
                    {totalElements.toLocaleString()}{' '}
                    {totalElements === 1 ? 'management unit type' : 'management unit types'} found
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
                                  if (
                                    cell.info.header === 'effectiveDate'
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
                  title="No management units found"
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

export default ManagementUnitSearch;
