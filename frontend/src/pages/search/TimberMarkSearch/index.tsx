import { Search as SearchIcon } from '@carbon/icons-react';
import {
  Button,
  Checkbox,
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
  getFileTypes,
  getHarvestAuthStatuses,
  getLandDistricts,
  getOrgUnits,
  getPrimaryIds,
  getSalvageTypes,
  type CodeOption,
} from '@/services/codeLists';
import { DEFAULT_PAGE_SIZE, PAGE_SIZES } from '@/services/paging';
import {
  searchTimbermarks,
  type TimbermarkSearchParams,
  type TimbermarkSummary,
} from '@/services/timbermark_search';
import { formatDate } from '@/utils/formatDate';

// Column order follows the legacy FTA002 results grid.
const HEADERS = [
  { key: 'orgUnitCode', header: 'District' },
  { key: 'clientName', header: 'Client name' },
  { key: 'clientNumber', header: 'Client number' },
  { key: 'fileTypeCode', header: 'File type' },
  { key: 'forestFileId', header: 'File ID' },
  { key: 'cuttingPermitId', header: 'CP' },
  { key: 'timberMark', header: 'Timber mark' },
  { key: 'salvageInd', header: 'Salvage type' },
  { key: 'certificate', header: 'Certificate' },
  { key: 'markStatusSt', header: 'Mark status' },
  { key: 'markIssueDate', header: 'Issue date' },
  { key: 'markExpiryDate', header: 'Expiry date' },
];

/** A result row carrying the id Carbon's DataTable requires. */
type Row = TimbermarkSummary & { id: string };

const SearchingIcon = () => <Loading small withOverlay={false} description="" />;

const formatCellText = (value: string | null | undefined) =>
  value && value.trim().length > 0 ? value.trim() : '—';

const EMPTY_FORM: TimbermarkSearchParams = { sortBy: 'district' };

/**
 * FTA002 — Timber Mark Search.
 *
 * <p>Criteria match the legacy screen, including its boxed Private Mark panel
 * (certificate, private-marks-only, land district, primary ID, primary detail).
 *
 * <p>Three legacy behaviours are implemented in the backend SQL rather than
 * here, and are surfaced to the user as helper text: a valid timber mark is a
 * key search that ignores every other criterion; the salvage value "ALL" means
 * "has any salvage type"; and the land-index fields select certificates rather
 * than filtering marks directly.
 *
 * <p>Layout and class names are the shared search-screen treatment in
 * `styles/_search.scss`; see TenureSearch for the pattern.
 */
const TimberMarkSearch: FC = () => {
  const navigate = useNavigate();
  const { display } = useNotification();

  const [form, setForm] = useState<TimbermarkSearchParams>(EMPTY_FORM);
  const [rows, setRows] = useState<Row[] | null>(null);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [orgUnits, setOrgUnits] = useState<CodeOption[]>([]);
  const [fileTypes, setFileTypes] = useState<CodeOption[]>([]);
  const [markStatuses, setMarkStatuses] = useState<CodeOption[]>([]);
  const [clientTypes, setClientTypes] = useState<CodeOption[]>([]);
  const [salvageTypes, setSalvageTypes] = useState<CodeOption[]>([]);
  const [landDistricts, setLandDistricts] = useState<CodeOption[]>([]);
  const [primaryIds, setPrimaryIds] = useState<CodeOption[]>([]);
  const [codeListsLoading, setCodeListsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    Promise.allSettled([
      getOrgUnits(),
      getFileTypes(),
      getHarvestAuthStatuses(),
      getFileClientTypes(),
      getSalvageTypes(),
      getLandDistricts(),
      getPrimaryIds(),
    ]).then((settled) => {
      if (cancelled) return;
      const setters = [
        setOrgUnits,
        setFileTypes,
        setMarkStatuses,
        setClientTypes,
        setSalvageTypes,
        setLandDistricts,
        setPrimaryIds,
      ];
      const names = [
        'districts',
        'file types',
        'mark statuses',
        'client types',
        'salvage types',
        'land districts',
        'primary IDs',
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

  useEffect(() => {
    if (error) {
      display({ kind: 'error', title: 'Search failed', subtitle: error, timeout: 6000 });
    }
  }, [error, display]);

  const set = <K extends keyof TimbermarkSearchParams>(key: K, value: TimbermarkSearchParams[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const runSearch = useCallback(
    async (nextPage: number, nextSize: number) => {
      if (form.issueDateFrom && form.issueDateTo && form.issueDateFrom > form.issueDateTo) {
        setError('Issue date from must be on or before issue date to.');
        return;
      }
      if (form.expiryDateFrom && form.expiryDateTo && form.expiryDateFrom > form.expiryDateTo) {
        setError('Expiry date from must be on or before expiry date to.');
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const data = await searchTimbermarks({ ...form, page: nextPage, size: nextSize });
        setRows(
          data.content.map((r, i) => ({
            ...r,
            id: `${r.timberMark ?? r.certificate ?? 'row'}-${r.cuttingPermitId ?? ''}-${i}`,
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
      title="Timber Mark Search"
      subtitle="Find a timber mark by district, file, client, status or private mark details"
    >
      <Tile className="fsp-search__tile">
        <form className="fsp-search__form" onSubmit={onSubmit}>
          <div className="fsp-search__field-grid">
            <Select
              id="tm-district"
              labelText="District"
              value={form.adminOrgUnitNo ?? ''}
              onChange={(e) => set('adminOrgUnitNo', e.target.value)}
            >
              <SelectItem value="" text="All districts" />
              {codeItems(orgUnits)}
            </Select>

            <TextInput
              id="tm-mgmt-unit-type"
              labelText="Mgmt unit type"
              value={form.mgmtUnitType ?? ''}
              onChange={(e) => set('mgmtUnitType', e.target.value)}
              maxLength={1}
              autoComplete="off"
            />

            <TextInput
              id="tm-mgmt-unit-id"
              labelText="Mgmt unit ID"
              value={form.mgmtUnitId ?? ''}
              onChange={(e) => set('mgmtUnitId', e.target.value)}
              maxLength={3}
              autoComplete="off"
            />

            <TextInput
              id="tm-file"
              labelText="File ID"
              placeholder="e.g. A19201"
              value={form.forestFileId ?? ''}
              onChange={(e) => set('forestFileId', e.target.value)}
              maxLength={10}
              autoComplete="off"
            />

            <TextInput
              id="tm-cp"
              labelText="CP"
              value={form.cuttingPermitId ?? ''}
              onChange={(e) => set('cuttingPermitId', e.target.value)}
              maxLength={3}
              autoComplete="off"
            />

            <TextInput
              id="tm-mark"
              labelText="Timber mark"
              placeholder="e.g. 52/1234"
              helperText="A valid mark ignores all other criteria"
              value={form.timberMark ?? ''}
              onChange={(e) => set('timberMark', e.target.value)}
              maxLength={6}
              autoComplete="off"
            />

            <Select
              id="tm-file-type"
              labelText="File type"
              value={form.fileTypeCode ?? ''}
              onChange={(e) => set('fileTypeCode', e.target.value)}
            >
              <SelectItem value="" text="All file types" />
              {codeItems(fileTypes)}
            </Select>

            <Select
              id="tm-status"
              labelText="Mark status"
              value={form.markStatusSt ?? ''}
              onChange={(e) => set('markStatusSt', e.target.value)}
            >
              <SelectItem value="" text="Any status" />
              {codeItems(markStatuses)}
            </Select>

            <TextInput
              id="tm-client-number"
              labelText="Client number"
              value={form.clientNumber ?? ''}
              onChange={(e) => set('clientNumber', e.target.value)}
              maxLength={8}
              autoComplete="off"
            />

            <TextInput
              id="tm-client-locn"
              labelText="Client location"
              value={form.clientLocnCode ?? ''}
              onChange={(e) => set('clientLocnCode', e.target.value)}
              maxLength={2}
              autoComplete="off"
            />

            <TextInput
              id="tm-client-name"
              labelText="Client name"
              value={form.clientName ?? ''}
              onChange={(e) => set('clientName', e.target.value)}
              maxLength={60}
              autoComplete="off"
            />

            <Select
              id="tm-client-type"
              labelText="Client type"
              value={form.fileClientType ?? ''}
              onChange={(e) => set('fileClientType', e.target.value)}
            >
              <SelectItem value="" text="Main and secondary" />
              {codeItems(clientTypes)}
            </Select>

            <Select
              id="tm-salvage"
              labelText="Salvage type"
              value={form.salvageTypeCode ?? ''}
              onChange={(e) => set('salvageTypeCode', e.target.value)}
            >
              <SelectItem value="" text="Any" />
              <SelectItem value="ALL" text="All — has any salvage type" />
              {codeItems(salvageTypes)}
            </Select>

            <TextInput
              id="tm-zone"
              labelText="Zone"
              value={form.districtAdminZone ?? ''}
              onChange={(e) => set('districtAdminZone', e.target.value)}
              maxLength={4}
              autoComplete="off"
            />

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
                id="tm-issue-from"
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
                id="tm-issue-to"
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
                id="tm-expiry-from"
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
                id="tm-expiry-to"
                labelText="Expiry date to"
                placeholder="YYYY-MM-DD"
                pattern="\d{4}-\d{2}-\d{2}"
              />
            </DatePicker>

            <h2 className="fsp-search__group-heading">Private mark criteria</h2>

            <TextInput
              id="tm-certificate"
              className="fsp-search__row-start"
              labelText="Certificate"
              value={form.certificate ?? ''}
              onChange={(e) => set('certificate', e.target.value)}
              maxLength={6}
              autoComplete="off"
            />

            <Select
              id="tm-land-district"
              labelText="Land district"
              value={form.landDistrict ?? ''}
              onChange={(e) => set('landDistrict', e.target.value)}
            >
              <SelectItem value="" text="Any" />
              {codeItems(landDistricts)}
            </Select>

            <Select
              id="tm-primary-id"
              labelText="Primary ID"
              value={form.primaryId ?? ''}
              onChange={(e) => set('primaryId', e.target.value)}
            >
              <SelectItem value="" text="Any" />
              {codeItems(primaryIds)}
            </Select>

            <TextInput
              id="tm-primary-detail"
              labelText="Primary detail"
              value={form.primaryDetail ?? ''}
              onChange={(e) => set('primaryDetail', e.target.value)}
              maxLength={40}
              autoComplete="off"
            />

            <div className="fsp-search__full-cell">
              <Checkbox
                id="tm-private-only"
                labelText="Only private marks"
                checked={form.privateMarkOnlyInd === 'Y'}
                onChange={(_e, { checked }) => set('privateMarkOnlyInd', checked ? 'Y' : '')}
              />
            </div>

            <div className="fsp-search__sort-cell">
              <RadioButtonGroup
                legendText="Sort by"
                name="tm-sort-by"
                valueSelected={form.sortBy ?? 'district'}
                onChange={(value) => set('sortBy', String(value))}
              >
                <RadioButton labelText="District" value="district" id="tm-sort-district" />
                <RadioButton labelText="Client name" value="client" id="tm-sort-client" />
                <RadioButton labelText="File type" value="fileType" id="tm-sort-filetype" />
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
                    {totalElements === 1 ? 'timber mark' : 'timber marks'} found
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
                              const cp =
                                (row.cells.find((c) => c.info.header === 'cuttingPermitId')
                                  ?.value as string | undefined) ?? '';
                              const open = () => {
                                if (cp) navigate(`/harvesting-authority/${encodeURIComponent(cp)}`);
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
                                          {value ? <StatusTag status={value} /> : '—'}
                                        </TableCell>
                                      );
                                    }
                                    if (
                                      cell.info.header === 'markIssueDate' ||
                                      cell.info.header === 'markExpiryDate'
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
                  title="No timber marks found"
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

export default TimberMarkSearch;
