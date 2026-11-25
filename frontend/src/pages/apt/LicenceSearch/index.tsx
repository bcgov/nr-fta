import { Search as SearchIcon } from '@carbon/icons-react';
import {
  Button,
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
import ExportCsvButton from '@/components/ExportCsvButton';
import { statusCodeVariant } from '@/components/StatusTag/statusCodes';
import { StatusTag } from '@/components/StatusTag/StatusTag';
import { useNotification } from '@/context/notification/useNotification';
import { useSessionState, type LastSearch } from '@/hooks/useSessionState';
import { nextColumnSort, sortHeaderProps } from '@/lib/columnSort';
import { safeErrorMessage } from '@/lib/errorMessage';
import { loadMuTypes } from '@/pages/apt/muTypes';
import PageLayout from '@/pages/PageLayout';
import { aptPaths } from '@/routes/aptPaths';
import {
  aptLicenceExportPath,
  searchAptLicences,
  type AptLicence,
  type AptLicenceSearchParams,
} from '@/services/apt/licences';
import { type AptCodeOption } from '@/services/apt/lookups';
import { DEFAULT_PAGE_SIZE, PAGE_SIZES } from '@/services/paging';
import { formatDate } from '@/utils/formatDate';

type Criteria = Omit<AptLicenceSearchParams, 'page' | 'size'>;

const EMPTY: Criteria = {
  forestFileId: '',
  clientName: '',
  orgUnitName: '',
  managementUnitName: '',
  managementUnitType: '',
  managementUnitNumber: '',
};

const HEADERS = [
  { key: 'forestFileId', header: 'Forest File ID' },
  { key: 'clientName', header: 'Licensee' },
  { key: 'mgmtUnitName', header: 'Management Unit' },
  { key: 'orgUnitName', header: 'Org. Unit' },
  { key: 'fileStatus', header: 'File Status' },
  { key: 'fileStatusDate', header: 'File Status Date' },
];

const dash = (v: string | null | undefined) => (v && v.trim() ? v : '—');

const SearchingIcon = () => <Loading small withOverlay={false} description="" />;

/**
 * APT2's APTM4200 Search for Licences ({@code APT_LICENCES_GET}). Any one criterion will do (APT2
 * required the Forest File ID); `%` in the Forest File ID matches anything. Selecting a row opens the licence's APT page, where
 * its FOA type and commitments are kept.
 */
const AptLicenceSearch: FC = () => {
  const navigate = useNavigate();
  const { display, clear } = useNotification();
  const [form, setForm] = useSessionState<Criteria>('fta.apt.licences.form', EMPTY);
  const [lastSearch, setLastSearch] = useSessionState<LastSearch<Criteria> | null>(
    'fta.apt.licences.last',
    null,
  );
  const [rows, setRows] = useState<AptLicence[] | null>(null);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [loading, setLoading] = useState(false);
  const [muTypes, setMuTypes] = useState<AptCodeOption[]>([]);

  useEffect(() => {
    let cancelled = false;
    loadMuTypes()
      .then((t) => {
        if (!cancelled) setMuTypes(t);
      })
      .catch((e: unknown) => {
        if (!cancelled)
          display({
            kind: 'error',
            title: 'Could not load management unit types',
            subtitle: safeErrorMessage(e),
            timeout: 6000,
          });
      });
    return () => {
      cancelled = true;
    };
  }, [display]);

  const set = <K extends keyof Criteria>(key: K, value: Criteria[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  const runSearch = useCallback(
    async (nextPage: number, nextSize: number, criteria: Criteria) => {
      const fields: (keyof Criteria)[] = [
        'forestFileId',
        'clientName',
        'orgUnitName',
        'managementUnitName',
        'managementUnitType',
        'managementUnitNumber',
      ];
      if (fields.every((k) => !String(criteria[k] ?? '').trim())) {
        clear();
        display({
          kind: 'warning',
          title: 'Enter at least one search criterion.',
          timeout: 8000,
          autoDismiss: true,
        });
        return;
      }
      clear();
      setLoading(true);
      try {
        const data = await searchAptLicences({ ...criteria, page: nextPage, size: nextSize });
        // One row per licence, as APT2 shows (dedupeRows): the query yields a row per licensee
        // and related client, and the last one wins.
        setRows(Array.from(new Map(data.content.map((l) => [l.forestFileId, l])).values()));
        setTotal(data.page.totalElements);
        setPage(data.page.number);
        setPageSize(data.page.size);
        setLastSearch({ criteria, page: data.page.number, size: data.page.size });
      } catch (e) {
        display({
          kind: 'error',
          title: 'Search failed',
          subtitle: safeErrorMessage(e),
          timeout: 6000,
        });
        setRows([]);
        setTotal(0);
      } finally {
        setLoading(false);
      }
    },
    [display, clear, setLastSearch],
  );

  // Back on the page with a search from earlier in this tab: run it again.
  const [restored, setRestored] = useState(false);
  useEffect(() => {
    if (restored) return;
    setRestored(true);
    if (lastSearch) void runSearch(lastSearch.page, lastSearch.size, lastSearch.criteria);
  }, [restored, lastSearch, runSearch]);

  const onSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    void runSearch(0, pageSize, form);
  };

  const onClear = () => {
    setForm(EMPTY);
    setLastSearch(null);
    setRows(null);
    setTotal(0);
    setPage(0);
  };

  const searched = lastSearch?.criteria ?? form;

  // A header click re-sorts the licences on screen (the criteria last searched), from the
  // first page. The sort also goes into the form, so searching again keeps it.
  const onSort = (key: string) => {
    const sort = nextColumnSort(searched.sort, key);
    setForm((prev) => ({ ...prev, sort }));
    void runSearch(0, pageSize, { ...searched, sort });
  };

  return (
    <PageLayout
      title="Licence Search"
      subtitle="Find a licence to see its apportionment commitments"
    >
      <Tile className="fsp-search__tile">
        <form className="fsp-search__form" onSubmit={onSubmit} noValidate>
          <div className="fsp-search__field-grid">
            <TextInput
              id="apt-lic-ffid"
              labelText="Forest File ID"
              value={form.forestFileId}
              maxLength={10}
              autoComplete="off"
              onChange={(e) => set('forestFileId', e.target.value)}
            />
            <TextInput
              id="apt-lic-client"
              labelText="Licensee"
              value={form.clientName ?? ''}
              autoComplete="off"
              onChange={(e) => set('clientName', e.target.value)}
            />
            <TextInput
              id="apt-lic-org"
              labelText="Org. Unit Name"
              value={form.orgUnitName ?? ''}
              autoComplete="off"
              onChange={(e) => set('orgUnitName', e.target.value)}
            />
            <TextInput
              id="apt-lic-mu-name"
              labelText="Management Unit Name"
              value={form.managementUnitName ?? ''}
              autoComplete="off"
              onChange={(e) => set('managementUnitName', e.target.value)}
            />
            <Select
              id="apt-lic-mu-type"
              labelText="Management Unit Type"
              value={form.managementUnitType ?? ''}
              onChange={(e) => set('managementUnitType', e.target.value)}
            >
              <SelectItem value="" text="Any type" />
              {muTypes.map((t) => (
                <SelectItem key={t.code} value={t.code} text={t.description || t.code} />
              ))}
            </Select>
            <TextInput
              id="apt-lic-mu-number"
              labelText="Management Unit Number"
              inputMode="numeric"
              value={form.managementUnitNumber ?? ''}
              autoComplete="off"
              onChange={(e) => set('managementUnitNumber', e.target.value.replace(/\D/g, ''))}
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
                  rowCount={Math.min(pageSize, 10)}
                  showHeader={false}
                  showToolbar={false}
                  aria-label="Loading search results"
                />
              </div>
            ) : rows && rows.length > 0 ? (
              <>
                <div className="fsp-search__results-header">
                  <span className="fsp-search__results-count">
                    {total.toLocaleString()} {total === 1 ? 'licence' : 'licences'} found
                  </span>
                  <ExportCsvButton path={aptLicenceExportPath(searched)} />
                </div>
                <div className="fsp-search__table">
                  <TableContainer>
                    <Table size="md" aria-label="Licence search results">
                      <TableHead>
                        <TableRow>
                          {HEADERS.map((h) => (
                            <TableHeader
                              key={h.key}
                              {...sortHeaderProps(searched.sort, h.key, onSort)}
                            >
                              {h.header}
                            </TableHeader>
                          ))}
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {rows.map((l) => {
                          const open = () => navigate(aptPaths.licenceDetail(l.forestFileId));
                          return (
                            <TableRow
                              key={l.forestFileId}
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
                              <TableCell>{l.forestFileId}</TableCell>
                              <TableCell>{dash(l.clientName)}</TableCell>
                              <TableCell>{dash(l.mgmtUnitName)}</TableCell>
                              <TableCell>{dash(l.orgUnitName)}</TableCell>
                              <TableCell>
                                {l.fileStatusSt || l.fileStatus ? (
                                  <StatusTag
                                    status={l.fileStatus || l.fileStatusSt || ''}
                                    variant={statusCodeVariant(l.fileStatusSt)}
                                  />
                                ) : (
                                  '—'
                                )}
                              </TableCell>
                              <TableCell>{dash(formatDate(l.fileStatusDate))}</TableCell>
                            </TableRow>
                          );
                        })}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </div>
                <Pagination
                  page={page + 1}
                  pageSize={pageSize}
                  pageSizes={PAGE_SIZES}
                  totalItems={total}
                  onChange={({ page: p, pageSize: s }) => void runSearch(p - 1, s, searched)}
                  size="md"
                />
              </>
            ) : (
              <EmptyState
                title="No licences found"
                body="No licence matches your search criteria. Try adjusting them and searching again."
              />
            )}
          </div>
        </div>
      )}
    </PageLayout>
  );
};

export default AptLicenceSearch;
