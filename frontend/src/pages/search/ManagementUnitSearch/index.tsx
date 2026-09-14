import { Search as SearchIcon, Reset } from '@carbon/icons-react';
import { Button, Column, Grid, TextInput } from '@carbon/react';
import { useState, type FC, type FormEvent } from 'react';

import AsyncBoundary from '@/components/AsyncBoundary';
import SearchResultsTable, { type ColumnDef } from '@/components/SearchResultsTable';
import SectionTile from '@/components/SectionTile';
import PageLayout from '@/pages/PageLayout';
import {
  searchManagementUnits,
  type MgmtUnitSearch,
  type MgmtUnitSearchParams,
} from '@/services/mgmt_unit_search';

import './ManagementUnitSearch.scss';

const HEADERS: ColumnDef[] = [
  { key: 'mgmtUnitTypeCode', header: 'MU Type' },
  { key: 'description', header: 'Description' },
  { key: 'effectiveDate', header: 'Effective Date' },
  { key: 'expiryDate', header: 'Expiry Date' },
];

/**
 * SIL004 — Management Unit Search. Code-table lookup of management-unit types,
 * backed by the backend {@code GET /api/fta/management-units} endpoint (which
 * ports THE.PKG_SIL_CODE_LISTS.GET_MGMT_UNIT_TYPE_CODE). Reference data;
 * results are a flat table.
 */
const ManagementUnitSearch: FC = () => {
  const [criteria, setCriteria] = useState<MgmtUnitSearchParams>({});
  const [rows, setRows] = useState<MgmtUnitSearch[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);

  const onField = (field: keyof MgmtUnitSearchParams) => (value: string) =>
    setCriteria((c) => ({ ...c, [field]: value }));

  const runSearch = async (params: MgmtUnitSearchParams) => {
    setLoading(true);
    setError(undefined);
    try {
      setRows(await searchManagementUnits(params));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Search failed');
      setRows(null);
    } finally {
      setLoading(false);
    }
  };

  const onSearch = (e: FormEvent) => {
    e.preventDefault();
    void runSearch(criteria);
  };

  const onReset = () => {
    setCriteria({});
    setRows(null);
    setError(undefined);
  };

  return (
    <PageLayout
      title="Management Unit Search"
      subtitle="Find a management-unit type by code or description"
    >
      <SectionTile title="Search criteria" icon={SearchIcon}>
        <form className="mu-search__form" onSubmit={onSearch}>
          <Grid narrow>
            <Column sm={4} md={4} lg={4}>
              <TextInput
                id="mu-type"
                labelText="MU Type Code"
                placeholder="e.g. TS"
                value={criteria.mgmtUnitTypeCode ?? ''}
                onChange={(e) => onField('mgmtUnitTypeCode')(e.target.value)}
              />
            </Column>
            <Column sm={4} md={4} lg={4}>
              <TextInput
                id="mu-description"
                labelText="Description"
                placeholder="e.g. Timber Supply Area"
                value={criteria.description ?? ''}
                onChange={(e) => onField('description')(e.target.value)}
              />
            </Column>
          </Grid>
          <div className="mu-search__actions">
            <Button type="submit" size="md" renderIcon={SearchIcon}>
              Search
            </Button>
            <Button type="button" size="md" kind="tertiary" renderIcon={Reset} onClick={onReset}>
              Reset
            </Button>
          </div>
        </form>
      </SectionTile>

      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={() => void runSearch(criteria)}
        loadingText="Searching…"
      >
        {rows !== null && (
          <div className="bordered-table">
            <SearchResultsTable
              rows={rows.map((r) => ({ ...r, id: r.mgmtUnitTypeCode }))}
              headers={HEADERS}
              emptyTitle="No management units found"
              renderCell={(row, key) => {
                if (key === 'description') return row.description ?? '—';
                if (key === 'effectiveDate') return row.effectiveDate ?? '—';
                if (key === 'expiryDate') return row.expiryDate ?? '—';
                return undefined;
              }}
            />
          </div>
        )}
      </AsyncBoundary>
    </PageLayout>
  );
};

export default ManagementUnitSearch;
