import { Button, Column, Grid, TextInput } from '@carbon/react';
import { Search as SearchIcon, Reset } from '@carbon/icons-react';
import { useState, type FC, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import AsyncBoundary from '@/components/AsyncBoundary';
import SearchResultsTable, { type ColumnDef } from '@/components/SearchResultsTable';
import PageLayout from '@/pages/PageLayout';
import {
  searchHarvestingAuthorities,
  type HarvestingSearchParams,
  type HarvestingSearchResult,
} from '@/services/harvesting_search';
import './HarvestingAuthoritySearch.scss';

const HEADERS: ColumnDef[] = [
  { key: 'cuttingPermitId', header: 'Cutting Permit' },
  { key: 'timberMark', header: 'Timber Mark' },
  { key: 'forestFileId', header: 'File ID' },
  { key: 'fileTypeCode', header: 'File Type' },
  { key: 'orgUnitCode', header: 'Org Unit' },
  { key: 'clientName', header: 'Licensee' },
];

/**
 * FTA005 — Harvesting Authority Search. Finds cutting permits / timber marks
 * and links each to its Cutting Permit detail (FTA902). Backed by the backend
 * {@code GET /api/fta/harvesting-authorities} endpoint (ports THE.FTA_HVA_SEARCH).
 */
const HarvestingAuthoritySearch: FC = () => {
  const [criteria, setCriteria] = useState<HarvestingSearchParams>({});
  const [rows, setRows] = useState<HarvestingSearchResult[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);

  const onField = (f: keyof HarvestingSearchParams) => (v: string) =>
    setCriteria((c) => ({ ...c, [f]: v }));

  const runSearch = async (params: HarvestingSearchParams) => {
    setLoading(true);
    setError(undefined);
    try {
      setRows(await searchHarvestingAuthorities(params));
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
    <PageLayout title="Harvesting Authority Search">
      <form className="ha-search__form" onSubmit={onSearch}>
        <Grid narrow>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="ha-cp" labelText="Cutting Permit" placeholder="e.g. CP-01"
              value={criteria.cuttingPermitId ?? ''} onChange={(e) => onField('cuttingPermitId')(e.target.value)} />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="ha-mark" labelText="Timber Mark" placeholder="e.g. 52/1234"
              value={criteria.timberMark ?? ''} onChange={(e) => onField('timberMark')(e.target.value)} />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="ha-file" labelText="Forest File ID" placeholder="e.g. A19201"
              value={criteria.forestFileId ?? ''} onChange={(e) => onField('forestFileId')(e.target.value)} />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="ha-client" labelText="Licensee / Client" placeholder="e.g. West Fraser"
              value={criteria.clientName ?? ''} onChange={(e) => onField('clientName')(e.target.value)} />
          </Column>
        </Grid>
        <div className="ha-search__actions">
          <Button type="submit" renderIcon={SearchIcon}>Search</Button>
          <Button type="button" kind="ghost" renderIcon={Reset} onClick={onReset}>Reset</Button>
        </div>
      </form>

      <AsyncBoundary loading={loading} error={error} onRetry={() => void runSearch(criteria)} loadingText="Searching…">
        {rows !== null && (
          <SearchResultsTable
            rows={rows.map((r, i) => ({
              ...r,
              id: String(r.hvaSkey ?? r.cuttingPermitId ?? r.forestFileId ?? i),
            }))}
            headers={HEADERS}
            emptyTitle="No harvesting authorities found"
            renderCell={(row, key) => {
              if (key === 'cuttingPermitId')
                return row.cuttingPermitId ? (
                  <Link to={`/harvesting-authority/${row.cuttingPermitId}`}>{row.cuttingPermitId}</Link>
                ) : '—';
              if (key === 'forestFileId')
                return row.forestFileId ? (
                  <Link to={`/tenures/${row.forestFileId}`}>{row.forestFileId}</Link>
                ) : '—';
              if (key === 'clientName') return row.clientName ?? '—';
              return undefined;
            }}
          />
        )}
      </AsyncBoundary>
    </PageLayout>
  );
};

export default HarvestingAuthoritySearch;
