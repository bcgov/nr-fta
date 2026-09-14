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
import { getBlockStatuses, getOrgUnits, type CodeOption } from '@/services/codeLists';
import {
  searchCutBlocks,
  type CutblockSearchParams,
  type CutblockSearchResult,
} from '@/services/cutblock_search';
import { DEFAULT_PAGE_SIZE, PAGE_SIZES } from '@/services/paging';
import { formatDate } from '@/utils/formatDate';

// Column order follows the legacy FTA003 results grid.
const HEADERS = [
  { key: 'orgUnitCode', header: 'District' },
  { key: 'clientName', header: 'Client name' },
  { key: 'clientNumber', header: 'Client number' },
  { key: 'forestFileId', header: 'File ID' },
  { key: 'cuttingPermitId', header: 'CP' },
  { key: 'timberMark', header: 'Timber mark' },
  { key: 'cutBlockId', header: 'Cut block' },
  { key: 'blockStatusSt', header: 'Block status' },
  { key: 'disturbanceStartDate', header: 'Start date' },
  { key: 'disturbanceEndDate', header: 'End date' },
];

/** A result row carrying the id Carbon's DataTable requires. */
type Row = CutblockSearchResult & { id: string };

const SearchingIcon = () => <Loading small withOverlay={false} description="" />;

const formatCellText = (value: string | null | undefined) =>
  value && value.trim().length > 0 ? value.trim() : '—';

const EMPTY_FORM: CutblockSearchParams = { sortBy: 'district' };

/**
 * FTA003 — Cut Block Search.
 *
 * <p>Criteria match the legacy screen: district, zone, file, CP, timber mark,
 * cut block, client (number / location / name), block status, the managed-by
 * file and CP pair, and the harvest date range.
 *
 * <p>Two legacy rules live in the backend SQL rather than here: Managed by CP
 * only applies when Managed by File is also given, and the client criteria match
 * only 'A' and 'L' file-client types.
 *
 * <p>Layout and class names are the shared search-screen treatment in
 * `styles/_search.scss`; see TenureSearch for the pattern.
 */
const CutBlockSearch: FC = () => {
  const navigate = useNavigate();
  const { display } = useNotification();

  const [form, setForm] = useState<CutblockSearchParams>(EMPTY_FORM);
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
    Promise.allSettled([getOrgUnits(), getBlockStatuses()]).then((settled) => {
      if (cancelled) return;
      const [orgRes, statusRes] = settled;
      if (orgRes.status === 'fulfilled') setOrgUnits(orgRes.value);
      if (statusRes.status === 'fulfilled') setStatuses(statusRes.value);
      const failed = [
        orgRes.status === 'rejected' ? 'districts' : null,
        statusRes.status === 'rejected' ? 'block statuses' : null,
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

  const set = <K extends keyof CutblockSearchParams>(key: K, value: CutblockSearchParams[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const runSearch = useCallback(
    async (nextPage: number, nextSize: number) => {
      if (
        form.harvestStartDateFrom
        && form.harvestStartDateTo
        && form.harvestStartDateFrom > form.harvestStartDateTo
      ) {
        setError('Harvest date from must be on or before harvest date to.');
        return;
      }
      // Legacy ignores a managed-by CP given without its file; say so rather
      // than running a search that quietly drops the criterion.
      if (form.managedByCp && !form.managedByFile) {
        setError('Managed by CP also needs a managed by file.');
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const data = await searchCutBlocks({ ...form, page: nextPage, size: nextSize });
        setRows(
          data.content.map((r, i) => ({ ...r, id: `${r.cbSkey ?? r.cutBlockId ?? 'row'}-${i}` })),
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

  if (codeListsLoading) {
    return (
      <div className="fsp-search__loading" role="status" aria-live="polite">
        <Loading description="Loading…" withOverlay={false} />
      </div>
    );
  }

  return (
    <PageLayout
      title="Cut Block Search"
      subtitle="Find a cut block by district, file, permit, client or status"
    >
      <Tile className="fsp-search__tile">
        <form className="fsp-search__form" onSubmit={onSubmit}>
          <div className="fsp-search__field-grid">
            <Select
              id="cb-district"
              labelText="District"
              value={form.orgUnitNo ?? ''}
              onChange={(e) => set('orgUnitNo', e.target.value)}
            >
              <SelectItem value="" text="All districts" />
              {orgUnits.map((o) => (
                <SelectItem key={o.code} value={o.code} text={o.description || o.code} />
              ))}
            </Select>

            <TextInput
              id="cb-zone"
              labelText="Zone"
              value={form.districtAdminZone ?? ''}
              onChange={(e) => set('districtAdminZone', e.target.value)}
              maxLength={4}
              autoComplete="off"
            />

            <TextInput
              id="cb-file"
              labelText="File ID"
              placeholder="e.g. A19201"
              value={form.forestFileId ?? ''}
              onChange={(e) => set('forestFileId', e.target.value)}
              maxLength={10}
              autoComplete="off"
            />

            <TextInput
              id="cb-cp"
              labelText="CP"
              value={form.cuttingPermitId ?? ''}
              onChange={(e) => set('cuttingPermitId', e.target.value)}
              maxLength={3}
              autoComplete="off"
            />

            <TextInput
              id="cb-timber-mark"
              labelText="Timber mark"
              value={form.timberMark ?? ''}
              onChange={(e) => set('timberMark', e.target.value)}
              maxLength={6}
              autoComplete="off"
            />

            <TextInput
              id="cb-block"
              labelText="Cut block"
              value={form.cutBlockId ?? ''}
              onChange={(e) => set('cutBlockId', e.target.value)}
              maxLength={10}
              autoComplete="off"
            />

            <TextInput
              id="cb-client-number"
              labelText="Client number"
              value={form.clientNumber ?? ''}
              onChange={(e) => set('clientNumber', e.target.value)}
              maxLength={8}
              autoComplete="off"
            />

            <TextInput
              id="cb-client-locn"
              labelText="Client location"
              value={form.clientLocnCode ?? ''}
              onChange={(e) => set('clientLocnCode', e.target.value)}
              maxLength={2}
              autoComplete="off"
            />

            <TextInput
              id="cb-client-name"
              labelText="Client name"
              value={form.clientName ?? ''}
              onChange={(e) => set('clientName', e.target.value)}
              maxLength={60}
              autoComplete="off"
            />

            <Select
              id="cb-status"
              labelText="Block status"
              value={form.blockStatusSt ?? ''}
              onChange={(e) => set('blockStatusSt', e.target.value)}
            >
              <SelectItem value="" text="Any status" />
              {statuses.map((o) => (
                <SelectItem key={o.code} value={o.code} text={o.description || o.code} />
              ))}
            </Select>

            <TextInput
              id="cb-managed-file"
              labelText="Managed by file"
              value={form.managedByFile ?? ''}
              onChange={(e) => set('managedByFile', e.target.value)}
              maxLength={8}
              autoComplete="off"
            />

            <TextInput
              id="cb-managed-cp"
              labelText="Managed by CP"
              helperText="Needs a managed by file"
              value={form.managedByCp ?? ''}
              onChange={(e) => set('managedByCp', e.target.value)}
              maxLength={3}
              autoComplete="off"
            />

            <h2 className="fsp-search__group-heading">Harvest date</h2>

            <DatePicker
              datePickerType="single"
              dateFormat="Y-m-d"
              className="fsp-search__row-start"
              value={form.harvestStartDateFrom ? [form.harvestStartDateFrom] : []}
              onChange={(dates) =>
                set(
                  'harvestStartDateFrom',
                  dates[0] ? dates[0].toISOString().slice(0, 10) : '',
                )
              }
            >
              <DatePickerInput
                id="cb-harvest-from"
                labelText="Harvest date from"
                placeholder="YYYY-MM-DD"
                pattern="\d{4}-\d{2}-\d{2}"
              />
            </DatePicker>

            <DatePicker
              datePickerType="single"
              dateFormat="Y-m-d"
              value={form.harvestStartDateTo ? [form.harvestStartDateTo] : []}
              onChange={(dates) =>
                set('harvestStartDateTo', dates[0] ? dates[0].toISOString().slice(0, 10) : '')
              }
            >
              <DatePickerInput
                id="cb-harvest-to"
                labelText="Harvest date to"
                placeholder="YYYY-MM-DD"
                pattern="\d{4}-\d{2}-\d{2}"
              />
            </DatePicker>

            <div className="fsp-search__sort-cell">
              <RadioButtonGroup
                legendText="Sort by"
                name="cb-sort-by"
                valueSelected={form.sortBy ?? 'district'}
                onChange={(value) => set('sortBy', String(value))}
              >
                <RadioButton labelText="District" value="district" id="cb-sort-district" />
                <RadioButton labelText="Client name" value="client" id="cb-sort-client" />
                <RadioButton labelText="File ID" value="fileId" id="cb-sort-file" />
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
                    {totalElements === 1 ? 'cut block' : 'cut blocks'} found
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
                              const blockId =
                                (row.cells.find((c) => c.info.header === 'cutBlockId')?.value as
                                  | string
                                  | undefined) ?? '';
                              const open = () => {
                                if (blockId) navigate(`/cut-block/${encodeURIComponent(blockId)}`);
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
                                    if (cell.info.header === 'blockStatusSt') {
                                      return (
                                        <TableCell key={cell.id}>
                                          {value ? <StatusTag status={value} /> : '—'}
                                        </TableCell>
                                      );
                                    }
                                    if (
                                      cell.info.header === 'disturbanceStartDate'
                                      || cell.info.header === 'disturbanceEndDate'
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
                  title="No cut blocks found"
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

export default CutBlockSearch;
