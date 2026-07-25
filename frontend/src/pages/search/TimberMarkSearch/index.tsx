import { Button, Column, Grid, Tag, TextInput } from '@carbon/react';
import { Search as SearchIcon, Reset } from '@carbon/icons-react';
import { useState, type FC, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import AsyncBoundary from '@/components/AsyncBoundary';
import SearchResultsTable, { type ColumnDef } from '@/components/SearchResultsTable';
import PageLayout from '@/pages/PageLayout';
import {
  searchTimbermarks,
  type TimbermarkSearchParams,
  type TimbermarkSummary,
} from '@/services/timbermark_search';
import './TimberMarkSearch.scss';

const HEADERS: ColumnDef[] = [
  { key: 'timberMark', header: 'Timber Mark' },
  { key: 'cuttingPermitId', header: 'Cutting Permit' },
  { key: 'forestFileId', header: 'File ID' },
  { key: 'markStatusSt', header: 'Status' },
  { key: 'orgUnitCode', header: 'Org Unit' },
];

/**
 * FTA002 — Timber Mark Search. Criteria form + results table backed by the
 * backend {@code GET /api/fta/timber-marks} endpoint (which ports
 * THE.FTA_002_MARK_SRCH). Finds timber marks (via their harvesting authority)
 * and links to the cutting permit detail.
 */
const TimberMarkSearch: FC = () => {
  const [criteria, setCriteria] = useState<TimbermarkSearchParams>({});
  const [rows, setRows] = useState<TimbermarkSummary[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);

  const onField = (field: keyof TimbermarkSearchParams) => (value: string) =>
    setCriteria((c) => ({ ...c, [field]: value }));

  const runSearch = async (params: TimbermarkSearchParams) => {
    setLoading(true);
    setError(undefined);
    try {
      setRows(await searchTimbermarks(params));
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
    <PageLayout title="Timber Mark Search">
      <form className="tm-search__form" onSubmit={onSearch}>
        <Grid narrow>
          <Column sm={4} md={4} lg={5}>
            <TextInput
              id="tm-mark"
              labelText="Timber Mark"
              placeholder="e.g. 52/1234"
              value={criteria.timberMark ?? ''}
              onChange={(e) => onField('timberMark')(e.target.value)}
            />
          </Column>
        </Grid>
        <div className="tm-search__actions">
          <Button type="submit" renderIcon={SearchIcon}>Search</Button>
          <Button type="button" kind="ghost" renderIcon={Reset} onClick={onReset}>Reset</Button>
        </div>
      </form>

      <AsyncBoundary loading={loading} error={error} onRetry={() => void runSearch(criteria)} loadingText="Searching…">
        {rows !== null && (
          <SearchResultsTable
            rows={rows.map((r, i) => ({ ...r, id: r.cuttingPermitId ?? r.timberMark ?? String(i) }))}
            headers={HEADERS}
            emptyTitle="No timber marks found"
            renderCell={(row, key) => {
              if (key === 'timberMark')
                return <Link to={`/harvesting-authority/${row.cuttingPermitId}`}>{row.timberMark}</Link>;
              if (key === 'cuttingPermitId')
                return row.cuttingPermitId ? (
                  <Link to={`/harvesting-authority/${row.cuttingPermitId}`}>{row.cuttingPermitId}</Link>
                ) : (
                  '—'
                );
              if (key === 'forestFileId')
                return row.forestFileId ? (
                  <Link to={`/tenures/${row.forestFileId}`}>{row.forestFileId}</Link>
                ) : (
                  '—'
                );
              if (key === 'markStatusSt')
                return row.markStatusSt ? <Tag type="green">{row.markStatusSt}</Tag> : '—';
              if (key === 'orgUnitCode') return row.orgUnitCode ?? '—';
              return undefined;
            }}
          />
        )}
      </AsyncBoundary>
    </PageLayout>
  );
};

export default TimberMarkSearch;
