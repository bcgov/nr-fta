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
import { useCallback, useEffect, useMemo, useState, type FC, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';

import { EmptyState } from '@/components/EmptyState/EmptyState';
import { useNotification } from '@/context/notification/useNotification';
import { safeErrorMessage } from '@/lib/errorMessage';
import PageLayout from '@/pages/PageLayout';
import {
  getFileTypes,
  getHarvestAuthClientTypes,
  getHarvestAuthStatuses,
  getLicenceToCutCodes,
  getOrgUnits,
  getSalvageTypes,
  type CodeOption,
} from '@/services/codeLists';
import {
  searchHarvestingAuthorities,
  showOilAndGasColumns,
  SORT_CLIENT,
  SORT_DISTRICT,
  SORT_FILE_TYPE,
  type HarvestingSearchParams,
  type HarvestingSearchResult,
} from '@/services/harvesting_search';
import { DEFAULT_PAGE_SIZE, PAGE_SIZES } from '@/services/paging';

/** The seven columns always shown, in legacy result-grid order. */
const BASE_HEADERS = [
  { key: 'orgUnitCode', header: 'District' },
  { key: 'clientName', header: 'Client name' },
  { key: 'clientNumber', header: 'Client number' },
  { key: 'fileTypeCode', header: 'File type' },
  { key: 'forestFileId', header: 'File ID' },
  { key: 'cuttingPermitId', header: 'CP' },
  { key: 'timberMark', header: 'Timber mark' },
];

/**
 * The five extra columns shown only for an oil and gas search. NTS and NTS
 * Mapsheet are each rendered as a single cell joining three source columns, as
 * the legacy grid does.
 */
const OG_HEADERS = [
  { key: 'ogcNumber', header: 'OGC number' },
  { key: 'nts', header: 'NTS' },
  { key: 'ntsMapsheet', header: 'NTS mapsheet' },
  { key: 'programNumber', header: 'Program number' },
  { key: 'geographicIdentifier', header: 'Geographic identifier' },
];

type Row = HarvestingSearchResult & { id: string; nts: string; ntsMapsheet: string };

const SearchingIcon = () => <Loading small withOverlay={false} description="" />;

const formatCellText = (value: string | null | undefined) =>
  value && value.trim().length > 0 ? value.trim() : '—';

/** Joins the NTS parts into one cell, skipping the blanks. */
const joinParts = (...parts: (string | null | undefined)[]) =>
  parts
    .map((p) => p?.trim())
    .filter((p) => p && p.length > 0)
    .join(' ');

const EMPTY_FORM: HarvestingSearchParams = { sortBy: SORT_DISTRICT };

/** The minimum criteria a non-key search needs, mirroring the backend. */
const MIN_CRITERIA = 2;

/**
 * FTA005 — Harvesting Authority Search.
 *
 * <p>Backed by `GET /api/fta/harvesting-authorities`, which ports the standalone
 * procedure `THE.FTA_005_HVA_SEARCH`.
 *
 * <p>Three legacy rules shape this screen, all enforced by the backend as well;
 * they are checked here first so the user gets inline guidance rather than a
 * rejected request:
 *
 * - A timber mark that validates is a *key search* — every other criterion is
 *   ignored. So are File ID + CP, and File ID + HVA ID.
 * - A CP or HVA ID requires a File ID, and the two cannot both be given.
 * - Otherwise at least two criteria are required and District is mandatory.
 *
 * <p>The oil and gas columns appear when the checkbox is ticked *or* the file
 * type is A11, but only the checkbox filters rows — the two are independent.
 */
const HarvestingAuthoritySearch: FC = () => {
  const navigate = useNavigate();
  const { display } = useNotification();

  const [form, setForm] = useState<HarvestingSearchParams>(EMPTY_FORM);
  const [rows, setRows] = useState<Row[] | null>(null);
  const [showOg, setShowOg] = useState(false);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [orgUnits, setOrgUnits] = useState<CodeOption[]>([]);
  const [fileTypes, setFileTypes] = useState<CodeOption[]>([]);
  const [cpStatuses, setCpStatuses] = useState<CodeOption[]>([]);
  const [clientTypes, setClientTypes] = useState<CodeOption[]>([]);
  const [salvageTypes, setSalvageTypes] = useState<CodeOption[]>([]);
  const [purposes, setPurposes] = useState<CodeOption[]>([]);
  const [codeListsLoading, setCodeListsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    Promise.allSettled([
      getOrgUnits(),
      getFileTypes(),
      getHarvestAuthStatuses(),
      getHarvestAuthClientTypes(),
      getSalvageTypes(),
      getLicenceToCutCodes(),
    ]).then((settled) => {
      if (cancelled) return;
      const setters = [
        setOrgUnits,
        setFileTypes,
        setCpStatuses,
        setClientTypes,
        setSalvageTypes,
        setPurposes,
      ];
      const names = [
        'districts',
        'file types',
        'CP statuses',
        'client types',
        'salvage types',
        'purposes',
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

  const set = <K extends keyof HarvestingSearchParams>(key: K, value: HarvestingSearchParams[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const hasValue = (v: string | undefined) => v !== undefined && v.trim().length > 0;

  /** Counts criteria the way the backend does — the O&G checkbox counts, sort does not. */
  const criteriaCount = useMemo(() => {
    const { sortBy, page: _p, size: _s, searchOnlyOg, ...rest } = form;
    void sortBy;
    void _p;
    void _s;
    let count = Object.values(rest).filter((v) => hasValue(v as string | undefined)).length;
    if (searchOnlyOg === 'Y') count += 1;
    return count;
  }, [form]);

  const headers = useMemo(
    () => (showOg ? [...BASE_HEADERS, ...OG_HEADERS] : BASE_HEADERS),
    [showOg],
  );

  /** Mirrors the backend guards so the user sees the problem before submitting. */
  const validate = useCallback((): string | null => {
    const hasFile = hasValue(form.forestFileId);
    const hasCp = hasValue(form.cuttingPermitId);
    const hasHva = hasValue(form.hvaId);

    if (!hasFile && (hasCp || hasHva)) {
      return 'A Cutting Permit or HVA ID can only be used together with a File ID.';
    }
    if (hasFile && hasCp && hasHva) {
      return 'Supply either a Cutting Permit or an HVA ID with the File ID, not both.';
    }
    // A key search bypasses the remaining rules, exactly as the backend does.
    const isKeySearch = hasValue(form.timberMark) || (hasFile && (hasCp || hasHva));
    if (isKeySearch) return null;

    if (criteriaCount < MIN_CRITERIA) {
      return `Please enter at least ${MIN_CRITERIA} search criteria.`;
    }
    if (!hasValue(form.forestDistrict)) {
      return 'District is required unless you search by a key.';
    }
    if (form.issueDateFrom && form.issueDateTo && form.issueDateFrom > form.issueDateTo) {
      return 'Issue date from must be on or before issue date to.';
    }
    if (form.expiryDateFrom && form.expiryDateTo && form.expiryDateFrom > form.expiryDateTo) {
      return 'Expiry date from must be on or before expiry date to.';
    }
    return null;
  }, [form, criteriaCount]);

  const runSearch = useCallback(
    async (nextPage: number, nextSize: number) => {
      const problem = validate();
      if (problem) {
        setError(problem);
        return;
      }
      setLoading(true);
      setError(null);
      // Decided before the request, from the submitted criteria, so the columns
      // match the search that produced the rows.
      const og = showOilAndGasColumns(form);
      try {
        const data = await searchHarvestingAuthorities({
          ...form,
          page: nextPage,
          size: nextSize,
        });
        setRows(
          data.content.map((r, i) => ({
            ...r,
            id: `${r.hvaSkey ?? r.forestFileId ?? 'row'}-${r.cuttingPermitId ?? ''}-${i}`,
            nts: joinParts(r.ntsMapquarter, r.ntsMapunit, r.ntsMapblock),
            ntsMapsheet: joinParts(r.ntsMapsheetGrid, r.ntsMapsheetLetter, r.ntsMapsheetSquare),
          })),
        );
        setShowOg(og);
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
    [form, validate],
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
    setShowOg(false);
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
      title="Harvesting Authority Search"
      subtitle="Find cutting permits and harvesting authorities by file, client, status or oil and gas details"
    >
      <Tile className="fsp-search__tile">
        <form className="fsp-search__form" onSubmit={onSubmit}>
          <div className="fsp-search__field-grid">
            <Select
              id="ha-district"
              labelText="District"
              value={form.forestDistrict ?? ''}
              onChange={(e) => set('forestDistrict', e.target.value)}
            >
              <SelectItem value="" text="All districts" />
              {codeItems(orgUnits)}
            </Select>

            <TextInput
              id="ha-mgmt-unit-type"
              labelText="Mgmt unit type"
              value={form.mgmtUnitType ?? ''}
              onChange={(e) => set('mgmtUnitType', e.target.value)}
              maxLength={1}
              autoComplete="off"
            />

            <TextInput
              id="ha-mgmt-unit-id"
              labelText="Mgmt unit ID"
              value={form.mgmtUnitId ?? ''}
              onChange={(e) => set('mgmtUnitId', e.target.value)}
              maxLength={4}
              autoComplete="off"
            />

            <TextInput
              id="ha-file"
              labelText="File ID"
              placeholder="e.g. A19201"
              value={form.forestFileId ?? ''}
              onChange={(e) => set('forestFileId', e.target.value)}
              maxLength={10}
              autoComplete="off"
            />

            <TextInput
              id="ha-cp"
              labelText="CP"
              helperText="Requires a File ID"
              value={form.cuttingPermitId ?? ''}
              onChange={(e) => set('cuttingPermitId', e.target.value)}
              maxLength={3}
              autoComplete="off"
            />

            <TextInput
              id="ha-mark"
              labelText="Timber mark"
              placeholder="e.g. 52/1234"
              helperText="A valid mark ignores all other criteria"
              value={form.timberMark ?? ''}
              onChange={(e) => set('timberMark', e.target.value)}
              maxLength={10}
              autoComplete="off"
            />

            <TextInput
              id="ha-hva-id"
              labelText="HVA ID"
              helperText="Requires a File ID"
              value={form.hvaId ?? ''}
              onChange={(e) => set('hvaId', e.target.value)}
              maxLength={30}
              autoComplete="off"
            />

            <Select
              id="ha-file-type"
              labelText="File type"
              value={form.fileTypeCode ?? ''}
              onChange={(e) => set('fileTypeCode', e.target.value)}
            >
              <SelectItem value="" text="All file types" />
              {codeItems(fileTypes)}
            </Select>

            <Select
              id="ha-cp-status"
              labelText="CP status"
              value={form.harvestAuthStatusCode ?? ''}
              onChange={(e) => set('harvestAuthStatusCode', e.target.value)}
            >
              <SelectItem value="" text="Any status" />
              {codeItems(cpStatuses)}
            </Select>

            <TextInput
              id="ha-client-number"
              labelText="Client number"
              value={form.clientNumber ?? ''}
              onChange={(e) => set('clientNumber', e.target.value)}
              maxLength={8}
              autoComplete="off"
            />

            <TextInput
              id="ha-client-locn"
              labelText="Client location"
              value={form.clientLocationCode ?? ''}
              onChange={(e) => set('clientLocationCode', e.target.value)}
              maxLength={2}
              autoComplete="off"
            />

            <TextInput
              id="ha-client-name"
              labelText="Client name"
              value={form.clientName ?? ''}
              onChange={(e) => set('clientName', e.target.value)}
              maxLength={60}
              autoComplete="off"
            />

            <Select
              id="ha-client-type"
              labelText="Client type"
              helperText="Defaults to the CP licensee"
              value={form.clientTypeCode ?? ''}
              onChange={(e) => set('clientTypeCode', e.target.value)}
            >
              <SelectItem value="" text="File licensee" />
              {codeItems(clientTypes)}
            </Select>

            <Select
              id="ha-salvage"
              labelText="Salvage type"
              value={form.salvageTypeCode ?? ''}
              onChange={(e) => set('salvageTypeCode', e.target.value)}
            >
              <SelectItem value="" text="Any" />
              {codeItems(salvageTypes)}
            </Select>

            <TextInput
              id="ha-zone"
              labelText="Zone"
              value={form.zone ?? ''}
              onChange={(e) => set('zone', e.target.value)}
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
                id="ha-issue-from"
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
                id="ha-issue-to"
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
                id="ha-expiry-from"
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
                id="ha-expiry-to"
                labelText="Expiry date to"
                placeholder="YYYY-MM-DD"
                pattern="\d{4}-\d{2}-\d{2}"
              />
            </DatePicker>

            <h2 className="fsp-search__group-heading">Oil and gas criteria</h2>

            <TextInput
              id="ha-invoice"
              className="fsp-search__row-start"
              labelText="Invoice number"
              value={form.invoiceNumber ?? ''}
              onChange={(e) => set('invoiceNumber', e.target.value)}
              maxLength={7}
              autoComplete="off"
            />

            <TextInput
              id="ha-ogc"
              labelText="App determination number"
              value={form.ogcNumber ?? ''}
              onChange={(e) => set('ogcNumber', e.target.value)}
              maxLength={10}
              autoComplete="off"
            />

            <TextInput
              id="ha-geo-id"
              labelText="Geographic identifier"
              value={form.geographicIdentifier ?? ''}
              onChange={(e) => set('geographicIdentifier', e.target.value)}
              maxLength={50}
              autoComplete="off"
            />

            <Select
              id="ha-purpose"
              labelText="Purpose"
              value={form.purposeCode ?? ''}
              onChange={(e) => set('purposeCode', e.target.value)}
            >
              <SelectItem value="" text="Any" />
              {codeItems(purposes)}
            </Select>

            <TextInput
              id="ha-nts-quarter"
              labelText="NTS quarter"
              value={form.ntsQuarter ?? ''}
              onChange={(e) => set('ntsQuarter', e.target.value)}
              maxLength={1}
              autoComplete="off"
            />

            <TextInput
              id="ha-nts-unit"
              labelText="NTS map unit"
              value={form.ntsMapUnit ?? ''}
              onChange={(e) => set('ntsMapUnit', e.target.value)}
              maxLength={4}
              autoComplete="off"
            />

            <TextInput
              id="ha-nts-block"
              labelText="NTS map block"
              value={form.ntsMapBlock ?? ''}
              onChange={(e) => set('ntsMapBlock', e.target.value)}
              maxLength={1}
              autoComplete="off"
            />

            <TextInput
              id="ha-mapsheet-grid"
              labelText="Mapsheet grid"
              value={form.ntsMapsheetGrid ?? ''}
              onChange={(e) => set('ntsMapsheetGrid', e.target.value)}
              maxLength={3}
              autoComplete="off"
            />

            <TextInput
              id="ha-mapsheet-letter"
              labelText="Mapsheet letter"
              value={form.ntsMapsheetLetter ?? ''}
              onChange={(e) => set('ntsMapsheetLetter', e.target.value)}
              maxLength={1}
              autoComplete="off"
            />

            <TextInput
              id="ha-mapsheet-square"
              labelText="Mapsheet square"
              value={form.ntsMapsheetSquare ?? ''}
              onChange={(e) => set('ntsMapsheetSquare', e.target.value)}
              maxLength={3}
              autoComplete="off"
            />

            <div className="fsp-search__full-cell">
              <Checkbox
                id="ha-only-og"
                labelText="Only oil and gas (restricts to file type A11)"
                checked={form.searchOnlyOg === 'Y'}
                onChange={(_e, { checked }) => set('searchOnlyOg', checked ? 'Y' : '')}
              />
            </div>

            <div className="fsp-search__sort-cell">
              <RadioButtonGroup
                legendText="Sort by"
                name="ha-sort-by"
                valueSelected={form.sortBy ?? SORT_DISTRICT}
                onChange={(value) => set('sortBy', String(value))}
              >
                <RadioButton labelText="District" value={SORT_DISTRICT} id="ha-sort-district" />
                <RadioButton labelText="Client name" value={SORT_CLIENT} id="ha-sort-client" />
                <RadioButton labelText="File type" value={SORT_FILE_TYPE} id="ha-sort-filetype" />
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
                    headers={headers}
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
                    {totalElements === 1 ? 'harvesting authority' : 'harvesting authorities'} found
                  </span>
                </div>

                <div className="fsp-search__table">
                  <DataTable rows={rows!} headers={headers}>
                    {({
                      rows: dtRows,
                      headers: hdrs,
                      getTableProps,
                      getHeaderProps,
                      getRowProps,
                    }) => (
                      <TableContainer>
                        <Table {...getTableProps()} size="md">
                          <TableHead>
                            <TableRow>
                              {hdrs.map((h) => (
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
                                  {row.cells.map((cell) => (
                                    <TableCell key={cell.id}>
                                      {formatCellText(cell.value as string | null | undefined)}
                                    </TableCell>
                                  ))}
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
                  title="No harvesting authorities found"
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

export default HarvestingAuthoritySearch;
