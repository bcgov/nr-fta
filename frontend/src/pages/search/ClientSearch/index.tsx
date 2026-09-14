import { Search as SearchIcon, Reset } from '@carbon/icons-react';
import { Button, Column, Grid, Tag, TextInput } from '@carbon/react';
import { useState, type FC, type FormEvent } from 'react';

import AsyncBoundary from '@/components/AsyncBoundary';
import SearchResultsTable, { type ColumnDef } from '@/components/SearchResultsTable';
import SectionTile from '@/components/SectionTile';
import PageLayout from '@/pages/PageLayout';
import {
  searchClients,
  type ClientSearchParams,
  type ClientSearchResult,
} from '@/services/client_search';

import './ClientSearch.scss';

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
    <PageLayout
      title="Client Search"
      subtitle="Find a forest client by client number, name or acronym"
    >
      <SectionTile title="Search criteria" icon={SearchIcon}>
        <form className="client-search__form" onSubmit={onSearch}>
          <Grid narrow>
            <Column sm={4} md={4} lg={4}>
              <TextInput
                id="cl-num"
                labelText="Client Number"
                placeholder="e.g. 00001012"
                value={criteria.clientNumber ?? ''}
                onChange={(e) => onField('clientNumber')(e.target.value)}
              />
            </Column>
            <Column sm={4} md={4} lg={4}>
              <TextInput
                id="cl-name"
                labelText="Name"
                placeholder="e.g. Canfor"
                value={criteria.clientName ?? ''}
                onChange={(e) => onField('clientName')(e.target.value)}
              />
            </Column>
            <Column sm={4} md={4} lg={4}>
              <TextInput
                id="cl-acronym"
                labelText="Acronym"
                placeholder="e.g. CANFOR"
                value={criteria.clientAcronym ?? ''}
                onChange={(e) => onField('clientAcronym')(e.target.value)}
              />
            </Column>
          </Grid>
          <div className="client-search__actions">
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
              rows={rows.map((r, i) => ({ ...r, id: r.clientNumber ?? String(i) }))}
              headers={HEADERS}
              emptyTitle="No clients found"
              renderCell={(row, key) =>
                key === 'clientStatusCode' ? (
                  row.clientStatusCode ? (
                    <Tag type={row.clientStatusCode === 'ACT' ? 'green' : 'gray'}>
                      {row.clientStatusCode}
                    </Tag>
                  ) : (
                    '—'
                  )
                ) : undefined
              }
            />
          </div>
        )}
      </AsyncBoundary>
    </PageLayout>
  );
};

export default ClientSearch;
