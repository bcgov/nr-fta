import { Button, Column, Grid, Tag, TextInput } from '@carbon/react';
import { Search as SearchIcon, Reset } from '@carbon/icons-react';
import { useState, type FC, type FormEvent } from 'react';
import AsyncBoundary from '@/components/AsyncBoundary';
import SearchResultsTable, { type ColumnDef } from '@/components/SearchResultsTable';
import PageLayout from '@/pages/PageLayout';
import { searchClients, type ClientSearchParams, type ClientSearchResult } from '@/services/client_search';

const HEADERS: ColumnDef[] = [
  { key: 'displayClientNumber', header: 'Client #' },
  { key: 'clientName', header: 'Name' },
  { key: 'clientLocnName', header: 'Location' },
  { key: 'city', header: 'City' },
  { key: 'clientStatusCode', header: 'Status' },
];

/**
 * SIL21 — Client Search. Code-table lookup of forest clients backed by the
 * backend {@code GET /api/fta/clients} endpoint (which ports
 * THE.FTA_SIL_21_CLIENT_SEARCH_V002). Reference data, so results are a flat
 * table (no detail screen).
 */
const ClientSearch: FC = () => {
  const [criteria, setCriteria] = useState<ClientSearchParams>({});
  const [rows, setRows] = useState<ClientSearchResult[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);

  const onField = (field: keyof ClientSearchParams) => (value: string) =>
    setCriteria((c) => ({ ...c, [field]: value }));

  const runSearch = async (params: ClientSearchParams) => {
    setLoading(true);
    setError(undefined);
    try {
      setRows(await searchClients(params));
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
    <PageLayout title="Client Search">
      <form style={{ maxWidth: '64rem', marginBottom: '2rem' }} onSubmit={onSearch}>
        <Grid narrow>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="cl-num" labelText="Client Number" placeholder="e.g. 00001012"
              value={criteria.clientNumber ?? ''} onChange={(e) => onField('clientNumber')(e.target.value)} />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="cl-name" labelText="Name" placeholder="e.g. Canfor"
              value={criteria.clientName ?? ''} onChange={(e) => onField('clientName')(e.target.value)} />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="cl-acronym" labelText="Acronym" placeholder="e.g. CANFOR"
              value={criteria.clientAcronym ?? ''} onChange={(e) => onField('clientAcronym')(e.target.value)} />
          </Column>
        </Grid>
        <div style={{ display: 'flex', gap: '0.5rem', marginTop: '1.5rem' }}>
          <Button type="submit" renderIcon={SearchIcon}>Search</Button>
          <Button type="button" kind="ghost" renderIcon={Reset} onClick={onReset}>Reset</Button>
        </div>
      </form>

      <AsyncBoundary loading={loading} error={error} onRetry={() => void runSearch(criteria)} loadingText="Searching…">
        {rows !== null && (
          <SearchResultsTable
            rows={rows.map((r, i) => ({ ...r, id: r.clientNumber ?? String(i) }))}
            headers={HEADERS}
            emptyTitle="No clients found"
            renderCell={(row, key) =>
              key === 'clientStatusCode'
                ? (row.clientStatusCode
                    ? <Tag type={row.clientStatusCode === 'ACT' ? 'green' : 'gray'}>{row.clientStatusCode}</Tag>
                    : '—')
                : undefined
            }
          />
        )}
      </AsyncBoundary>
    </PageLayout>
  );
};

export default ClientSearch;
