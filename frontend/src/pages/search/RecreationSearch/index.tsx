import { Search as SearchIcon } from '@carbon/icons-react';
import {
  Button,
  DataTable,
  DataTableSkeleton,
  DatePicker,
  DatePickerInput,
  InlineNotification,
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
  getOrgUnits,
  getRecreationControlAccessTypes,
  getRecreationDistricts,
  getRecreationFileStatuses,
  getRecreationMaintainStandards,
  getRecreationProjectTypes,
  getRecreationRiskRatings,
  type CodeOption,
} from '@/services/codeLists';
import { DEFAULT_PAGE_SIZE, PAGE_SIZES } from '@/services/paging';
import {
  hasUnappliedCriteria,
  NEW_FILES,
  OLD_FILES,
  searchRecreation,
  SORT_ADMIN_ORG,
  SORT_FILE_ID,
  SORT_FILE_STATUS,
  SORT_PROJECT_NAME,
  type RecreationSearchParams,
  type RecreationSearchResult,
} from '@/services/recreation_search';

/** Columns in legacy result-grid order. */
const HEADERS = [
  { key: 'forestFileId', header: 'File ID' },
  { key: 'fileStatusCode', header: 'File status' },
  { key: 'orgUnitCode', header: 'Org unit' },
  { key: 'projectName', header: 'Project name' },
  { key: 'projectType', header: 'Project type' },
];

type Row = RecreationSearchResult & { id: string };

const SearchingIcon = () => <Loading small withOverlay={false} description="" />;

const formatCellText = (value: string | null | undefined) =>
  value && value.trim().length > 0 ? value.trim() : '—';

const EMPTY_FORM: RecreationSearchParams = { sortBy: SORT_FILE_ID };

/**
 * FTA007 — Recreation Search.
 *
 * <p>Backed by `GET /api/fta/recreation`, which ports
 * `THE.FTA_007_REC_SEARCH.MAINLINE`.
 *
 * <p>Recreation files come in two generations: "new" files live in
 * `RECREATION_PROJECT` with ids like `REC…`, "old" ones in `REC_PROJECT` with
 * ids like `900…`. Four criteria — risk rating, controlled access, maintenance
 * standard and resource feature — are columns on the new table only, so they
 * cannot apply to old files. Legacy drops them silently and returns a wider
 * result set than the user asked for; this screen says so instead.
 *
 * <p>Two more legacy behaviours are surfaced as helper text rather than
 * changed: File ID is an exact match unless the user types `%`, and Defined
 * Camping Spaces finds projects with *more* than the number given.
 */
const RecreationSearch: FC = () => {
  const navigate = useNavigate();
  const { display } = useNotification();

  const [form, setForm] = useState<RecreationSearchParams>(EMPTY_FORM);
  const [rows, setRows] = useState<Row[] | null>(null);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [orgUnits, setOrgUnits] = useState<CodeOption[]>([]);
  const [fileStatuses, setFileStatuses] = useState<CodeOption[]>([]);
  const [projectTypes, setProjectTypes] = useState<CodeOption[]>([]);
  const [riskRatings, setRiskRatings] = useState<CodeOption[]>([]);
  const [accessTypes, setAccessTypes] = useState<CodeOption[]>([]);
  const [maintainStds, setMaintainStds] = useState<CodeOption[]>([]);
  const [districts, setDistricts] = useState<CodeOption[]>([]);
  const [codeListsLoading, setCodeListsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    Promise.allSettled([
      getOrgUnits(),
      getRecreationFileStatuses(),
      getRecreationProjectTypes(),
      getRecreationRiskRatings(),
      getRecreationControlAccessTypes(),
      getRecreationMaintainStandards(),
      getRecreationDistricts(),
    ]).then((settled) => {
      if (cancelled) return;
      const setters = [
        setOrgUnits,
        setFileStatuses,
        setProjectTypes,
        setRiskRatings,
        setAccessTypes,
        setMaintainStds,
        setDistricts,
      ];
      const names = [
        'org units',
        'file statuses',
        'project types',
        'risk ratings',
        'access types',
        'maintenance standards',
        'recreation districts',
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

  const set = <K extends keyof RecreationSearchParams>(key: K, value: RecreationSearchParams[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  /** Warn before searching when old files are asked for alongside new-only filters. */
  const unapplied = useMemo(() => hasUnappliedCriteria(form), [form]);

  const validate = useCallback((): string | null => {
    if (!form.orgUnit || form.orgUnit.trim().length === 0) {
      return 'Admin Org Unit is required.';
    }
    if (form.fileStatusFrom && form.fileStatusTo && form.fileStatusFrom > form.fileStatusTo) {
      return 'File Status From must be on or before File Status To.';
    }
    if (form.definedCampingSpaces && form.definedCampingSpaces.trim().length > 0) {
      const n = Number(form.definedCampingSpaces.trim());
      if (!Number.isInteger(n) || n < 1 || n > 200) {
        return 'Defined Camping Spaces must be a whole number between 1 and 200.';
      }
    }
    return null;
  }, [form]);

  const runSearch = useCallback(
    async (nextPage: number, nextSize: number) => {
      const problem = validate();
      if (problem) {
        setError(problem);
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const data = await searchRecreation({ ...form, page: nextPage, size: nextSize });
        setRows(data.content.map((r, i) => ({ ...r, id: `${r.forestFileId ?? 'row'}-${i}` })));
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
      title="Recreation Search"
      subtitle="Find recreation projects by org unit, file, project details or district"
    >
      <Tile className="fsp-search__tile">
        <form className="fsp-search__form" onSubmit={onSubmit}>
          <div className="fsp-search__field-grid">
            <Select
              id="rec-org-unit"
              labelText="Admin org unit (required)"
              value={form.orgUnit ?? ''}
              onChange={(e) => set('orgUnit', e.target.value)}
              invalid={Boolean(error) && !form.orgUnit}
            >
              <SelectItem value="" text="Select an org unit" />
              {codeItems(orgUnits)}
            </Select>

            <TextInput
              id="rec-mgmt-unit-type"
              labelText="Mgmt unit type"
              value={form.mgmtUnitType ?? ''}
              onChange={(e) => set('mgmtUnitType', e.target.value)}
              maxLength={1}
              autoComplete="off"
            />

            <TextInput
              id="rec-mgmt-unit-number"
              labelText="Mgmt unit ID"
              value={form.mgmtUnitNumber ?? ''}
              onChange={(e) => set('mgmtUnitNumber', e.target.value)}
              maxLength={3}
              autoComplete="off"
            />

            <TextInput
              id="rec-file-id"
              labelText="File ID"
              placeholder="e.g. REC1234"
              helperText="Exact match — use % as a wildcard"
              value={form.fileId ?? ''}
              onChange={(e) => set('fileId', e.target.value)}
              maxLength={10}
              autoComplete="off"
            />

            <Select
              id="rec-file-status"
              labelText="File status"
              value={form.fileStatus ?? ''}
              onChange={(e) => set('fileStatus', e.target.value)}
            >
              <SelectItem value="" text="Any status" />
              {codeItems(fileStatuses)}
            </Select>

            <Select
              id="rec-project-type"
              labelText="Project type"
              value={form.projectType ?? ''}
              onChange={(e) => set('projectType', e.target.value)}
            >
              <SelectItem value="" text="Any type" />
              {codeItems(projectTypes)}
            </Select>

            <TextInput
              id="rec-project-name"
              className="fsp-search__wide-cell"
              labelText="Project name"
              helperText="Matches anywhere in the name"
              value={form.projectName ?? ''}
              onChange={(e) => set('projectName', e.target.value)}
              maxLength={100}
              autoComplete="off"
            />

            <Select
              id="rec-district"
              labelText="Recreation district"
              value={form.recreationDistrict ?? ''}
              onChange={(e) => set('recreationDistrict', e.target.value)}
            >
              <SelectItem value="" text="Any district" />
              {codeItems(districts)}
            </Select>

            <TextInput
              id="rec-camping-spaces"
              labelText="Defined camping spaces"
              helperText="Finds projects with more than this many"
              value={form.definedCampingSpaces ?? ''}
              onChange={(e) => set('definedCampingSpaces', e.target.value)}
              maxLength={3}
              autoComplete="off"
            />

            <Select
              id="rec-old-file"
              labelText="Recreation file generation"
              value={form.oldFileInd ?? ''}
              onChange={(e) => set('oldFileInd', e.target.value)}
            >
              <SelectItem value="" text="Old and new" />
              <SelectItem value={NEW_FILES} text="New (REC…)" />
              <SelectItem value={OLD_FILES} text="Old (900…)" />
            </Select>

            <h2 className="fsp-search__group-heading">File status date range</h2>

            <DatePicker
              datePickerType="single"
              dateFormat="Y-m-d"
              className="fsp-search__row-start"
              value={form.fileStatusFrom ? [form.fileStatusFrom] : []}
              onChange={(dates) =>
                set('fileStatusFrom', dates[0] ? dates[0].toISOString().slice(0, 10) : '')
              }
            >
              <DatePickerInput
                id="rec-status-from"
                labelText="File status from"
                placeholder="YYYY-MM-DD"
                pattern="\d{4}-\d{2}-\d{2}"
              />
            </DatePicker>

            <DatePicker
              datePickerType="single"
              dateFormat="Y-m-d"
              value={form.fileStatusTo ? [form.fileStatusTo] : []}
              onChange={(dates) =>
                set('fileStatusTo', dates[0] ? dates[0].toISOString().slice(0, 10) : '')
              }
            >
              <DatePickerInput
                id="rec-status-to"
                labelText="File status to"
                placeholder="YYYY-MM-DD"
                pattern="\d{4}-\d{2}-\d{2}"
              />
            </DatePicker>

            <h2 className="fsp-search__group-heading">New recreation files only</h2>

            <Select
              id="rec-risk-rating"
              className="fsp-search__row-start"
              labelText="Risk rating"
              value={form.riskRating ?? ''}
              onChange={(e) => set('riskRating', e.target.value)}
            >
              <SelectItem value="" text="Any" />
              {codeItems(riskRatings)}
            </Select>

            <Select
              id="rec-access-type"
              labelText="Controlled access type"
              value={form.controlledAccessType ?? ''}
              onChange={(e) => set('controlledAccessType', e.target.value)}
            >
              <SelectItem value="" text="Any" />
              {codeItems(accessTypes)}
            </Select>

            <Select
              id="rec-maintain-std"
              labelText="Maintenance standard"
              value={form.maintenanceStandard ?? ''}
              onChange={(e) => set('maintenanceStandard', e.target.value)}
            >
              <SelectItem value="" text="Any" />
              {codeItems(maintainStds)}
            </Select>

            <Select
              id="rec-resource-feature"
              labelText="Resource feature"
              value={form.resourceFeatureInd ?? ''}
              onChange={(e) => set('resourceFeatureInd', e.target.value)}
            >
              <SelectItem value="" text="Any" />
              <SelectItem value="Y" text="Yes" />
              <SelectItem value="N" text="No" />
            </Select>

            <div className="fsp-search__sort-cell">
              <RadioButtonGroup
                legendText="Sort by"
                name="rec-sort-by"
                valueSelected={form.sortBy ?? SORT_FILE_ID}
                onChange={(value) => set('sortBy', String(value))}
              >
                <RadioButton labelText="File ID" value={SORT_FILE_ID} id="rec-sort-file" />
                <RadioButton labelText="Admin org" value={SORT_ADMIN_ORG} id="rec-sort-org" />
                <RadioButton
                  labelText="File status"
                  value={SORT_FILE_STATUS}
                  id="rec-sort-status"
                />
                <RadioButton
                  labelText="Project name"
                  value={SORT_PROJECT_NAME}
                  id="rec-sort-name"
                />
              </RadioButtonGroup>
            </div>
          </div>

          {unapplied && (
            <InlineNotification
              kind="warning"
              lowContrast
              hideCloseButton
              title="Some filters will not apply"
              subtitle="Risk rating, controlled access, maintenance standard and resource feature are recorded only on new recreation files. Searching old files ignores them."
            />
          )}

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
                    {totalElements.toLocaleString()} {totalElements === 1 ? 'project' : 'projects'}{' '}
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
                              // Legacy offers a per-row "Details" button; the
                              // whole row is the target here, as on the other
                              // search screens.
                              const fileId =
                                (row.cells.find((c) => c.info.header === 'forestFileId')?.value as
                                  string | undefined) ?? '';
                              const open = () => {
                                if (fileId) navigate(`/recreation/${encodeURIComponent(fileId)}`);
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
                  title="No recreation projects found"
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

export default RecreationSearch;
