import { DocumentAdd, Search as SearchIcon } from '@carbon/icons-react';
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
import { useCallback, useEffect, useRef, useState, type FC, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import ClientComboBox from '@/components/ClientComboBox';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import ExportCsvButton from '@/components/ExportCsvButton';
import { statusCodeVariant } from '@/components/StatusTag/statusCodes';
import { StatusTag } from '@/components/StatusTag/StatusTag';
import UserName from '@/components/UserName';
import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import { useSessionState, type LastSearch } from '@/hooks/useSessionState';
import { safeErrorMessage } from '@/lib/errorMessage';
import PageLayout from '@/pages/PageLayout';
import { canEditMarks } from '@/routes/access';
import {
  getPrivateMarkStatuses,
  getThreeLetterDistricts,
  type CodeOption,
} from '@/services/codeLists';
import { markDetailPath } from '@/services/mark_detail';
import {
  listMarks,
  markListExportPath,
  type MarkListParams,
  type MarkListRow,
} from '@/services/mark_list';
import { DEFAULT_PAGE_SIZE, PAGE_SIZES } from '@/services/paging';
import { formatDate } from '@/utils/formatDate';

import NewMarkApplicationModal from '../NewMarkApplicationModal';

// Column order follows the legacy FTA500 grid.
const HEADERS = [
  { key: 'certificate', header: 'Certificate' },
  { key: 'timberMark', header: 'Timber mark' },
  { key: 'markApplDate', header: 'Application date' },
  { key: 'orgUnitCode', header: 'District' },
  { key: 'markStatusSt', header: 'Status' },
  { key: 'clientName', header: 'Client' },
  { key: 'idir', header: 'Last updated by' },
];

/** A result row carrying the id Carbon's DataTable requires. */
type Row = MarkListRow & { id: string };

const SearchingIcon = () => <Loading small withOverlay={false} description="" />;

const formatCellText = (value: string | null | undefined) =>
  value && value.trim().length > 0 ? value.trim() : '—';

const EMPTY_FORM: MarkListParams = {};

/**
 * FTA500 — Private Mark Application/Amendment List.
 *
 * <p>Legacy offers a single District filter; the mark, holder and status
 * criteria here narrow that same list rather than reproducing a legacy
 * behaviour. Status is backed by `PRIVATE_MARK_STATUS_CODE` — the codes the
 * underlying columns actually hold.
 *
 * <p>Layout and class names are the shared search-screen treatment in
 * `styles/_search.scss`; see TenureSearch for the pattern.
 */
const MarkList: FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { display } = useNotification();
  const { user } = useAuth();
  const mayCreate = canEditMarks(user);

  // The New Mark Application modal opens over this list from its button. A
  // direct link to /marks/application renders this page with it open too; the
  // menu's "New Application" opens its own copy over whatever page is showing.
  const onNewRoute = location.pathname === '/marks/application';
  const [newOpen, setNewOpen] = useState(false);
  const modalOpen = mayCreate && (newOpen || onNewRoute);
  const closeNew = () => {
    setNewOpen(false);
    if (onNewRoute) navigate('/marks', { replace: true });
  };

  // Criteria and the last search are kept for the browser tab, so coming back
  // from a mark shows the same list again.
  const [form, setForm] = useSessionState<MarkListParams>('fta.marks.list.form', EMPTY_FORM);
  const [lastSearch, setLastSearch] = useSessionState<LastSearch<MarkListParams> | null>(
    'fta.marks.list.last',
    null,
  );
  const [rows, setRows] = useState<Row[] | null>(null);
  // The criteria the rows on screen came from. The export must use these, not
  // `form` — the user may have edited a field since searching, and exporting
  // criteria that were never searched would hand back a different result set.
  const [searched, setSearched] = useState<MarkListParams | null>(null);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [districts, setDistricts] = useState<CodeOption[]>([]);
  const [statuses, setStatuses] = useState<CodeOption[]>([]);
  const [codeListsLoading, setCodeListsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    Promise.allSettled([getThreeLetterDistricts(), getPrivateMarkStatuses()]).then((settled) => {
      if (cancelled) return;
      const [orgRes, statusRes] = settled;
      if (orgRes.status === 'fulfilled') setDistricts(orgRes.value);
      if (statusRes.status === 'fulfilled') setStatuses(statusRes.value);
      const failed = [
        orgRes.status === 'rejected' ? 'districts' : null,
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

  const set = <K extends keyof MarkListParams>(key: K, value: MarkListParams[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const runSearch = useCallback(
    async (nextPage: number, nextSize: number, criteria: MarkListParams = form) => {
      setLoading(true);
      setError(null);
      try {
        const data = await listMarks({ ...criteria, page: nextPage, size: nextSize });
        setSearched({ ...criteria });
        setLastSearch({ criteria, page: data.page.number, size: data.page.size });
        setRows(
          data.content.map((r, i) => ({
            ...r,
            id: `${r.processType ?? ''}-${r.certificate ?? ''}-${r.timberMark ?? ''}-${i}`,
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
    [form, setLastSearch],
  );

  // Open on the last search from this tab, or else the unfiltered list rather
  // than an empty page. Once only: runSearch changes identity as the form is
  // edited, and the ref also absorbs StrictMode's double effect run in development.
  const autoSearched = useRef(false);
  useEffect(() => {
    if (autoSearched.current) return;
    autoSearched.current = true;
    if (lastSearch) void runSearch(lastSearch.page, lastSearch.size, lastSearch.criteria);
    else void runSearch(0, pageSize);
  }, [runSearch, pageSize, lastSearch]);

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
    setSearched(null);
    setTotalElements(0);
    setPage(0);
    setError(null);
  }, [setForm, setLastSearch]);

  const hasResults = rows !== null && rows.length > 0;
  const rowsById = new Map((rows ?? []).map((r) => [r.id, r]));
  const statusNames = new Map(statuses.map((o) => [o.code, o.description]));

  if (codeListsLoading) {
    return (
      <div className="fsp-search__loading" role="status" aria-live="polite">
        <Loading description="Loading…" withOverlay={false} />
      </div>
    );
  }

  return (
    <PageLayout
      title="Private Mark Applications"
      subtitle="Search private timber mark applications and amendments, or start a new one"
      actions={
        mayCreate ? (
          <Button
            size="md"
            kind="tertiary"
            renderIcon={DocumentAdd}
            onClick={() => setNewOpen(true)}
          >
            New Mark Application
          </Button>
        ) : undefined
      }
    >
      <NewMarkApplicationModal open={modalOpen} onClose={closeNew} />
      <Tile className="fsp-search__tile">
        <form className="fsp-search__form" onSubmit={onSubmit}>
          <div className="fsp-search__field-grid">
            <Select
              id="mk-district"
              labelText="District"
              value={form.hdrDistrict ?? ''}
              onChange={(e) => set('hdrDistrict', e.target.value)}
            >
              <SelectItem value="" text="All districts" />
              {districts.map((o) => (
                <SelectItem key={o.code} value={o.code} text={o.description || o.code} />
              ))}
            </Select>

            <TextInput
              id="mk-num"
              labelText="Timber mark"
              placeholder="e.g. 12 3456"
              value={form.timberMark ?? ''}
              onChange={(e) => set('timberMark', e.target.value)}
              maxLength={6}
              autoComplete="off"
            />

            <ClientComboBox
              id="mk-holder"
              titleText="Client / holder"
              clientNumber={form.clientNumber}
              clientLocnCode={form.clientLocnCode}
              clientName={form.clientName}
              onChange={(next) => setForm((prev) => ({ ...prev, ...next }))}
            />

            <Select
              id="mk-status"
              labelText="Status"
              value={form.markStatusSt ?? ''}
              onChange={(e) => set('markStatusSt', e.target.value)}
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
                    aria-label="Loading marks"
                  />
                </div>
              </>
            ) : hasResults ? (
              <>
                <div className="fsp-search__results-header">
                  <span className="fsp-search__results-count">
                    {totalElements.toLocaleString()} {totalElements === 1 ? 'mark' : 'marks'} found
                  </span>
                  {searched && <ExportCsvButton path={markListExportPath(searched)} />}
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
                              // An application not yet issued has no timber
                              // mark; it opens by its certificate instead.
                              const source = rowsById.get(row.id);
                              const path = source
                                ? markDetailPath(source.timberMark, source.certificate)
                                : null;
                              const open = () => {
                                if (path) navigate(path);
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
                                    if (cell.info.header === 'markStatusSt') {
                                      return (
                                        <TableCell key={cell.id}>
                                          {value ? (
                                            <StatusTag
                                              status={statusNames.get(value) || value}
                                              variant={statusCodeVariant(value)}
                                            />
                                          ) : (
                                            '—'
                                          )}
                                        </TableCell>
                                      );
                                    }
                                    if (cell.info.header === 'idir') {
                                      return (
                                        <TableCell key={cell.id}>
                                          <UserName userId={value} />
                                        </TableCell>
                                      );
                                    }
                                    if (cell.info.header === 'markApplDate') {
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
                  title="No marks found"
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

export default MarkList;
