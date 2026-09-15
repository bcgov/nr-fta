import { Search as SearchIcon } from '@carbon/icons-react';
import {
  Button,
  DataTable,
  DataTableSkeleton,
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
import { getOrgUnits, getRangeUnitStatuses, type CodeOption } from '@/services/codeLists';
import { DEFAULT_PAGE_SIZE, PAGE_SIZES } from '@/services/paging';
import {
  searchRangeUnits,
  type RangeUnitSearchParams,
  type RangeUnitSummary,
} from '@/services/range_unit_search';

// Column order follows the legacy FTA006 results grid.
const HEADERS = [
  { key: 'rangeUnitId', header: 'Range unit' },
  { key: 'pastureId', header: 'Pasture ID' },
  { key: 'rangeUnitName', header: 'Range unit name' },
  { key: 'pastureName', header: 'Pasture name' },
  { key: 'rangeUnitStatusDesc', header: 'Status' },
];

/** A result row carrying the id Carbon's DataTable requires. */
type Row = RangeUnitSummary & { id: string };

const SearchingIcon = () => <Loading small withOverlay={false} description="" />;

const formatCellText = (value: string | null | undefined) =>
  value && value.trim().length > 0 ? value.trim() : '—';

const EMPTY_FORM: RangeUnitSearchParams = {};

/**
 * FTA006 — Range Unit / Pasture Search.
 *
 * <p>Criteria match the legacy screen: Admin Org Unit (mandatory), Pasture Name,
 * Range Unit Name and Status. Picking a region returns every district beneath
 * it — the backend expands the org unit through its rollup, as the legacy
 * package does.
 *
 * <p>Layout and class names are the shared search-screen treatment in
 * `styles/_search.scss`; see TenureSearch for the pattern.
 */
const RangeUnitSearch: FC = () => {
  const navigate = useNavigate();
  const { display } = useNotification();

  const [form, setForm] = useState<RangeUnitSearchParams>(EMPTY_FORM);
  const [rows, setRows] = useState<Row[] | null>(null);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [orgUnits, setOrgUnits] = useState<CodeOption[]>([]);
  const [statuses, setStatuses] = useState<CodeOption[]>([]);
  const [codeListsLoading, setCodeListsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    Promise.allSettled([getOrgUnits(), getRangeUnitStatuses()]).then((settled) => {
      if (cancelled) return;
      const [orgRes, statusRes] = settled;
      if (orgRes.status === 'fulfilled') setOrgUnits(orgRes.value);
      if (statusRes.status === 'fulfilled') setStatuses(statusRes.value);
      const failed = [
        orgRes.status === 'rejected' ? 'org units' : null,
        statusRes.status === 'rejected' ? 'statuses' : null,
      ].filter(Boolean);
      if (failed.length > 0) setError(`Could not load ${failed.join(', ')}`);
      setCodeListsLoading(false);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (error) {
      display({ kind: 'error', title: 'Search failed', subtitle: error, timeout: 6000 });
    }
  }, [error, display]);

  const set = <K extends keyof RangeUnitSearchParams>(key: K, value: RangeUnitSearchParams[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const runSearch = useCallback(
    async (nextPage: number, nextSize: number) => {
      // Legacy marks Admin Org Unit mandatory. Without it the query spans every
      // district in the province, so it's refused here rather than run.
      if (!form.orgUnitNo) {
        setError('Admin org unit is required.');
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const data = await searchRangeUnits({ ...form, page: nextPage, size: nextSize });
        setRows(data.content.map((r, i) => ({ ...r, id: `${r.rangeUnitId}-${r.pastureId ?? i}` })));
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

  if (codeListsLoading) {
    return (
      <div className="fsp-search__loading" role="status" aria-live="polite">
        <Loading description="Loading…" withOverlay={false} />
      </div>
    );
  }

  return (
    <PageLayout
      title="Range Unit / Pasture Search"
      subtitle="Find a range unit or pasture by name, org unit or status"
    >
      <Tile className="fsp-search__tile">
        <form className="fsp-search__form" onSubmit={onSubmit}>
          <div className="fsp-search__field-grid">
            <Select
              id="ru-org-unit"
              labelText="Admin org unit (required)"
              value={form.orgUnitNo ?? ''}
              onChange={(e) => set('orgUnitNo', e.target.value)}
            >
              <SelectItem value="" text="Select an org unit" />
              {orgUnits.map((o) => (
                <SelectItem key={o.code} value={o.code} text={o.description || o.code} />
              ))}
            </Select>

            <TextInput
              id="ru-name"
              labelText="Range unit name"
              placeholder="e.g. Big Creek"
              value={form.rangeUnitName ?? ''}
              onChange={(e) => set('rangeUnitName', e.target.value)}
              maxLength={30}
              autoComplete="off"
            />

            <TextInput
              id="ru-pasture"
              labelText="Pasture name"
              placeholder="e.g. North"
              value={form.pastureName ?? ''}
              onChange={(e) => set('pastureName', e.target.value)}
              maxLength={30}
              autoComplete="off"
            />

            <Select
              id="ru-status"
              labelText="Status"
              value={form.rangeStatus ?? ''}
              onChange={(e) => set('rangeStatus', e.target.value)}
            >
              <SelectItem value="" text="Any status" />
              {statuses.map((o) => (
                <SelectItem key={o.code} value={o.code} text={o.description || o.code} />
              ))}
            </Select>
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
                    {totalElements === 1 ? 'range unit' : 'range units'} found
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
                              const rangeUnitId =
                                (row.cells.find((c) => c.info.header === 'rangeUnitId')?.value as
                                  string | undefined) ?? '';
                              const open = () => navigate(`/range-unit/${rangeUnitId}`);
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
                                    if (cell.info.header === 'rangeUnitStatusDesc') {
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
                  title="No range units found"
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

export default RangeUnitSearch;
