import { Button, Column, Grid, TextInput } from '@carbon/react';
import { Search as SearchIcon, Reset } from '@carbon/icons-react';
import { useState, type FC, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import AsyncBoundary from '@/components/AsyncBoundary';
import SearchResultsTable, { type ColumnDef } from '@/components/SearchResultsTable';
import PageLayout from '@/pages/PageLayout';
import {
  searchRangeUnits,
  type RangeUnitSearchParams,
  type RangeUnitSummary,
} from '@/services/range_unit_search';
import './RangeUnitSearch.scss';

const HEADERS: ColumnDef[] = [
  { key: 'rangeUnitId', header: 'Unit ID' },
  { key: 'rangeUnitName', header: 'Name' },
  { key: 'pastureId', header: 'Pasture ID' },
  { key: 'pastureName', header: 'Pasture' },
  { key: 'rangeUnitStatusDesc', header: 'Status' },
];

/**
 * FTA006 — Range Unit / Pasture Search. Criteria form + results table backed by
 * the backend {@code GET /api/fta/range-units} endpoint (which ports
 * THE.FTA_006_RU_SRCH). Links each row to its Range Unit detail (FTA630).
 */
const RangeUnitSearch: FC = () => {
  const [criteria, setCriteria] = useState<RangeUnitSearchParams>({});
  const [rows, setRows] = useState<RangeUnitSummary[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);

  const onField = (f: keyof RangeUnitSearchParams) => (v: string) =>
    setCriteria((c) => ({ ...c, [f]: v }));

  const runSearch = async (params: RangeUnitSearchParams) => {
    setLoading(true);
    setError(undefined);
    try {
      setRows(await searchRangeUnits(params));
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
    <PageLayout title="Range Unit / Pasture Search">
      <form className="range-unit-search__form" onSubmit={onSearch}>
        <Grid narrow>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="ru-name" labelText="Name" placeholder="e.g. Big Creek"
              value={criteria.rangeUnitName ?? ''} onChange={(e) => onField('rangeUnitName')(e.target.value)} />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="ru-pasture" labelText="Pasture" placeholder="e.g. North"
              value={criteria.pastureName ?? ''} onChange={(e) => onField('pastureName')(e.target.value)} />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="ru-org-unit" labelText="Org Unit" placeholder="Org unit no"
              value={criteria.orgUnitNo ?? ''} onChange={(e) => onField('orgUnitNo')(e.target.value)} />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="ru-status" labelText="Status" placeholder="e.g. A"
              value={criteria.rangeStatus ?? ''} onChange={(e) => onField('rangeStatus')(e.target.value)} />
          </Column>
        </Grid>
        <div className="range-unit-search__actions">
          <Button type="submit" renderIcon={SearchIcon}>Search</Button>
          <Button type="button" kind="ghost" renderIcon={Reset} onClick={onReset}>Reset</Button>
        </div>
      </form>

      <AsyncBoundary loading={loading} error={error} onRetry={() => void runSearch(criteria)} loadingText="Searching…">
        {rows !== null && (
          <SearchResultsTable
            rows={rows.map((r, i) => ({ ...r, id: `${r.rangeUnitId}-${r.pastureId ?? i}` }))}
            headers={HEADERS}
            emptyTitle="No range units found"
            renderCell={(row, key) => {
              if (key === 'rangeUnitId') return <Link to={`/range-unit/${row.rangeUnitId}`}>{row.rangeUnitId}</Link>;
              if (key === 'rangeUnitName') return row.rangeUnitName ?? '—';
              if (key === 'pastureName') return row.pastureName ?? '—';
              if (key === 'rangeUnitStatusDesc') return row.rangeUnitStatusDesc ?? '—';
              return undefined;
            }}
          />
        )}
      </AsyncBoundary>
    </PageLayout>
  );
};

export default RangeUnitSearch;
