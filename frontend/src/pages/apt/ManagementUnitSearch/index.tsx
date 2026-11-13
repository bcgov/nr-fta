import { Add, Search as SearchIcon } from '@carbon/icons-react';
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
import { useCallback, useEffect, useMemo, useRef, useState, type FC, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import DateField from '@/components/DateField';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import { useSessionState } from '@/hooks/useSessionState';
import { nextColumnSort, sortHeaderProps, sortRows } from '@/lib/columnSort';
import { safeErrorMessage } from '@/lib/errorMessage';
import { dash, day, unitLabel } from '@/pages/apt/ManagementUnitHistory/shared';
import PageLayout from '@/pages/PageLayout';
import { canEditApt } from '@/routes/access';
import { aptPaths } from '@/routes/aptPaths';
import {
  getAptOrgUnits,
  getManagementUnitStatuses,
  getManagementUnitTypes,
  type AptCodeOption,
  type AptOrgUnit,
} from '@/services/apt/lookups';
import { searchAptManagementUnits, type AptManagementUnit } from '@/services/apt/managementUnits';
import { DEFAULT_PAGE_SIZE, PAGE_SIZES } from '@/services/paging';

/** The search criteria as typed; all optional. */
interface Criteria {
  type: string;
  number: string;
  /** Matched client-side against the unit's name, as APT2 did. */
  name: string;
  orgUnitNo: string;
  sourceId: string;
  status: string;
  effectiveDate: string;
  orcs: string;
}

const EMPTY: Criteria = {
  type: '',
  number: '',
  name: '',
  orgUnitNo: '',
  sourceId: '',
  status: '',
  effectiveDate: '',
  orcs: '',
};

const HEADERS = [
  { key: 'mgmtUnitName', header: 'Name' },
  { key: 'aptMgmtUnitSt', header: 'MU type' },
  { key: 'mgmtUnitNumber', header: 'MU no.' },
  { key: 'mgmtUnitStatusSt', header: 'Status' },
  { key: 'statusEffectiveDate', header: 'Effective date' },
  { key: 'orgUnitName', header: 'Org unit' },
  { key: 'orcsFileNumber', header: 'ORCS file #' },
];

interface Lists {
  types: AptCodeOption[];
  statuses: AptCodeOption[];
  orgUnits: AptOrgUnit[];
  units: AptManagementUnit[];
}

const NO_LISTS: Lists = { types: [], statuses: [], orgUnits: [], units: [] };

const SearchingIcon = () => <Loading small withOverlay={false} description="" />;

/** `CODE - description`, or whichever is known. */
const codeLabel = (o: AptCodeOption) => (o.description ? `${o.code} - ${o.description}` : o.code);

/**
 * APTM1100 — search for management units (APT2's ManagementUnitSearch). Every criterion is
 * optional; the name is matched on the results, as APT2 did, since the routine has no name
 * filter. A result's name opens its history (APTM1000). APT administrators can start a new unit.
 */
const ManagementUnitSearch: FC = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { display } = useNotification();
  const canEdit = canEditApt(user);

  const [form, setForm] = useSessionState<Criteria>('fta.apt.mu.form', EMPTY);
  const [lastSearch, setLastSearch] = useSessionState<Criteria | null>('fta.apt.mu.last', null);
  const [rows, setRows] = useState<AptManagementUnit[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  // Column sort. Every unit is already in the browser, so it is sorted here, then paged.
  const [sort, setSort] = useState<string | undefined>();
  const [lists, setLists] = useState<Lists>(NO_LISTS);
  const [listsLoading, setListsLoading] = useState(true);

  // The dropdowns. Settled individually: a list that fails just stays empty.
  useEffect(() => {
    let cancelled = false;
    Promise.allSettled([
      getManagementUnitTypes(),
      getManagementUnitStatuses(),
      getAptOrgUnits(),
      searchAptManagementUnits(),
    ]).then(([types, statuses, orgUnits, units]) => {
      if (cancelled) return;
      const ok = <T,>(r: PromiseSettledResult<T[]>) => (r.status === 'fulfilled' ? r.value : []);
      setLists({
        types: ok(types),
        statuses: ok(statuses),
        orgUnits: ok(orgUnits),
        units: ok(units),
      });
      setListsLoading(false);
      if ([types, statuses, orgUnits, units].some((r) => r.status === 'rejected')) {
        display({
          kind: 'warning',
          title: 'Some search lists could not be loaded',
          timeout: 6000,
        });
      }
    });
    return () => {
      cancelled = true;
    };
  }, [display]);

  const names = useMemo(
    () =>
      Array.from(
        new Set(lists.units.map((u) => u.mgmtUnitName).filter((n): n is string => !!n)),
      ).sort(),
    [lists.units],
  );

  const set = <K extends keyof Criteria>(key: K, value: Criteria[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const runSearch = useCallback(
    async (criteria: Criteria) => {
      setLoading(true);
      try {
        const data = await searchAptManagementUnits({
          managementUnitType: criteria.type || undefined,
          managementUnitNumber: criteria.number.trim() || undefined,
          status: criteria.status || undefined,
          effectiveDate: criteria.effectiveDate || undefined,
          orgUnitNumber: criteria.orgUnitNo ? Number(criteria.orgUnitNo) : undefined,
          orcsFileNumber: criteria.orcs.trim() || undefined,
          sourceManagementUnitId: criteria.sourceId ? Number(criteria.sourceId) : undefined,
        });
        const unique = Array.from(new Map(data.map((u) => [u.id, u])).values());
        setRows(criteria.name ? unique.filter((u) => u.mgmtUnitName === criteria.name) : unique);
        setLastSearch(criteria);
        setPage(1);
      } catch (e) {
        display({
          kind: 'error',
          title: 'Search failed',
          subtitle: safeErrorMessage(e),
          timeout: 6000,
        });
        setRows([]);
      } finally {
        setLoading(false);
      }
    },
    [display, setLastSearch],
  );

  const onSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    void runSearch(form);
  };

  const onClear = () => {
    setForm(EMPTY);
    setLastSearch(null);
    setRows(null);
    setPage(1);
    setSort(undefined);
  };

  // Back on the page with a search from earlier in this tab: run it again.
  const restored = useRef(false);
  useEffect(() => {
    if (restored.current) return;
    restored.current = true;
    if (lastSearch) void runSearch(lastSearch);
  }, [lastSearch, runSearch]);

  const onSort = (key: string) => {
    setSort((prev) => nextColumnSort(prev, key));
    setPage(1);
  };

  // The Name column falls back to "<type> <number>" for a unit with no name; sort on what shows.
  const paged = sortRows(rows ?? [], sort, (u, key) =>
    key === 'mgmtUnitName'
      ? u.mgmtUnitName || `${u.aptMgmtUnitSt} ${u.mgmtUnitNumber}`
      : u[key as keyof AptManagementUnit],
  )
    .slice((page - 1) * pageSize, page * pageSize)
    .map((u) => ({ ...u, id: String(u.id) }));
  const byId = new Map((rows ?? []).map((u) => [String(u.id), u]));
  const choose = (text: string) => <SelectItem value="" text={listsLoading ? 'Loading…' : text} />;

  return (
    <PageLayout
      title="Management Units"
      subtitle="Find a management unit to see its AAC determinations, partitions and apportionments"
      actions={
        canEdit ? (
          <Button
            size="md"
            kind="primary"
            renderIcon={Add}
            onClick={() => navigate(aptPaths.newManagementUnit())}
          >
            New management unit
          </Button>
        ) : undefined
      }
    >
      <Tile className="fsp-search__tile">
        <form className="fsp-search__form" onSubmit={onSubmit}>
          <div className="fsp-search__field-grid">
            <Select
              id="apt-mu-type"
              labelText="MU type"
              value={form.type}
              disabled={listsLoading}
              onChange={(e) => set('type', e.target.value)}
            >
              {choose('Any type')}
              {lists.types.map((t) => (
                <SelectItem key={t.code} value={t.code} text={codeLabel(t)} />
              ))}
            </Select>
            <TextInput
              id="apt-mu-number"
              labelText="MU no."
              value={form.number}
              maxLength={4}
              autoComplete="off"
              onChange={(e) => set('number', e.target.value)}
            />
            <Select
              id="apt-mu-name"
              labelText="Name"
              value={form.name}
              disabled={listsLoading}
              onChange={(e) => set('name', e.target.value)}
            >
              {choose('Any name')}
              {names.map((n) => (
                <SelectItem key={n} value={n} text={n} />
              ))}
            </Select>
            <Select
              id="apt-mu-org"
              labelText="Org unit"
              value={form.orgUnitNo}
              disabled={listsLoading}
              onChange={(e) => set('orgUnitNo', e.target.value)}
            >
              {choose('Any org unit')}
              {lists.orgUnits.map((o) => (
                <SelectItem
                  key={o.orgUnitNo}
                  value={String(o.orgUnitNo)}
                  text={o.displayName ?? o.code}
                />
              ))}
            </Select>
            <Select
              id="apt-mu-source"
              labelText="Source management unit"
              value={form.sourceId}
              disabled={listsLoading}
              onChange={(e) => set('sourceId', e.target.value)}
            >
              {choose('Any source')}
              {lists.units.map((u) => (
                <SelectItem key={u.id} value={String(u.id)} text={unitLabel(u)} />
              ))}
            </Select>
            <Select
              id="apt-mu-status"
              labelText="Status"
              value={form.status}
              disabled={listsLoading}
              onChange={(e) => set('status', e.target.value)}
            >
              {choose('Any status')}
              {lists.statuses.map((s) => (
                <SelectItem key={s.code} value={s.code} text={codeLabel(s)} />
              ))}
            </Select>
            <DateField
              id="apt-mu-effective"
              labelText="Status effective date"
              value={form.effectiveDate}
              onChange={(v) => set('effectiveDate', v)}
            />
            <TextInput
              id="apt-mu-orcs"
              labelText="ORCS file #"
              value={form.orcs}
              maxLength={13}
              autoComplete="off"
              onChange={(e) => set('orcs', e.target.value)}
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
              <div className="fsp-search__table">
                <DataTableSkeleton
                  headers={HEADERS}
                  rowCount={5}
                  showHeader={false}
                  showToolbar={false}
                  aria-label="Loading search results"
                />
              </div>
            ) : rows && rows.length > 0 ? (
              <>
                <div className="fsp-search__results-header">
                  <span className="fsp-search__results-count">
                    {rows.length.toLocaleString()}{' '}
                    {rows.length === 1 ? 'management unit' : 'management units'} found
                  </span>
                </div>
                <div className="fsp-search__table">
                  <DataTable rows={paged} headers={HEADERS}>
                    {({ rows: dtRows, headers, getTableProps, getHeaderProps, getRowProps }) => (
                      <TableContainer>
                        <Table {...getTableProps()} size="md">
                          <TableHead>
                            <TableRow>
                              {headers.map((h) => (
                                <TableHeader
                                  {...getHeaderProps({ header: h })}
                                  {...sortHeaderProps(sort, h.key, onSort)}
                                  key={h.key}
                                >
                                  {h.header}
                                </TableHeader>
                              ))}
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {dtRows.map((row) => {
                              const unit = byId.get(row.id)!;
                              return (
                                <TableRow {...getRowProps({ row })} key={row.id}>
                                  <TableCell>
                                    <Link to={aptPaths.managementUnitHistory(unit.id)}>
                                      {unit.mgmtUnitName ||
                                        `${unit.aptMgmtUnitSt} ${unit.mgmtUnitNumber}`}
                                    </Link>
                                  </TableCell>
                                  <TableCell>{dash(unit.aptMgmtUnitSt)}</TableCell>
                                  <TableCell>{dash(unit.mgmtUnitNumber)}</TableCell>
                                  <TableCell>{dash(unit.mgmtUnitStatusSt)}</TableCell>
                                  <TableCell className="fsp-search__cell--nowrap">
                                    {day(unit.statusEffectiveDate)}
                                  </TableCell>
                                  <TableCell>{dash(unit.orgUnitName)}</TableCell>
                                  <TableCell>{dash(unit.orcsFileNumber)}</TableCell>
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
                  page={page}
                  pageSize={pageSize}
                  pageSizes={PAGE_SIZES}
                  totalItems={rows.length}
                  onChange={({ page: p, pageSize: s }: { page: number; pageSize: number }) => {
                    setPage(p);
                    setPageSize(s);
                  }}
                  size="md"
                />
              </>
            ) : (
              <EmptyState
                title="No management units found"
                body="No records match your search criteria. Try adjusting your filters and searching again."
              />
            )}
          </div>
        </div>
      )}
    </PageLayout>
  );
};

export default ManagementUnitSearch;
