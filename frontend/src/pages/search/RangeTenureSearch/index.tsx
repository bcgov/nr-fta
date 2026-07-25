import { Button, Column, Grid, Select, SelectItem, Tag, TextInput } from '@carbon/react';
import { Search as SearchIcon, Reset } from '@carbon/icons-react';
import { useState, type FC, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import AsyncBoundary from '@/components/AsyncBoundary';
import SearchResultsTable, { type ColumnDef } from '@/components/SearchResultsTable';
import PageLayout from '@/pages/PageLayout';
import {
  searchRangeTenures,
  type RangeTenureSearchParams,
  type RangeTenureSummary,
} from '@/services/range_tenure_search';
import './RangeTenureSearch.scss';

const HEADERS: ColumnDef[] = [
  { key: 'forestFileId', header: 'Agreement' },
  { key: 'fileTypeCode', header: 'Type' },
  { key: 'fileStatusDesc', header: 'Status' },
  { key: 'clientName', header: 'Holder' },
  { key: 'orgUnitCode', header: 'Org Unit' },
  { key: 'issueDate', header: 'Issue Date' },
  { key: 'expiryDate', header: 'Expiry Date' },
];

/**
 * FTA001R — Range Tenure Search. Criteria form + results table backed by the
 * backend {@code GET /api/fta/range-tenures} endpoint (which ports
 * THE.FTA_001R_TENR_SRCH). Each result links to its Range Tenure detail
 * (FTA100Range).
 */
const RangeTenureSearch: FC = () => {
  const [criteria, setCriteria] = useState<RangeTenureSearchParams>({});
  const [rows, setRows] = useState<RangeTenureSummary[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);

  const onField = (field: keyof RangeTenureSearchParams) => (value: string) =>
    setCriteria((c) => ({ ...c, [field]: value }));

  const runSearch = async (params: RangeTenureSearchParams) => {
    setLoading(true);
    setError(undefined);
    try {
      setRows(await searchRangeTenures(params));
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
    <PageLayout title="Range Tenure Search">
      <form className="range-search__form" onSubmit={onSearch}>
        <Grid narrow>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="rt-id" labelText="Agreement" placeholder="e.g. RAN076543"
              value={criteria.forestFileId ?? ''} onChange={(e) => onField('forestFileId')(e.target.value)} />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="rt-holder" labelText="Holder" placeholder="e.g. Meadow Ranch"
              value={criteria.clientName ?? ''} onChange={(e) => onField('clientName')(e.target.value)} />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="rt-org" labelText="Org Unit" placeholder="District code"
              value={criteria.orgUnitCode ?? ''} onChange={(e) => onField('orgUnitCode')(e.target.value)} />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <Select id="rt-status" labelText="Status" value={criteria.fileStatus ?? ''}
              onChange={(e) => onField('fileStatus')(e.target.value)}>
              <SelectItem value="" text="Any" />
              <SelectItem value="ACT" text="Active" />
              <SelectItem value="PEN" text="Pending" />
              <SelectItem value="EXP" text="Expired" />
              <SelectItem value="SUS" text="Suspended" />
            </Select>
          </Column>
        </Grid>
        <div className="range-search__actions">
          <Button type="submit" renderIcon={SearchIcon}>Search</Button>
          <Button type="button" kind="ghost" renderIcon={Reset} onClick={onReset}>Reset</Button>
        </div>
      </form>

      <AsyncBoundary loading={loading} error={error} onRetry={() => void runSearch(criteria)} loadingText="Searching…">
        {rows !== null && (
          <SearchResultsTable
            rows={rows.map((r) => ({ ...r, id: r.forestFileId }))}
            headers={HEADERS}
            emptyTitle="No range agreements found"
            renderCell={(row, key) => {
              if (key === 'forestFileId') return <Link to={`/range/${row.forestFileId}`}>{row.forestFileId}</Link>;
              if (key === 'fileStatusDesc') {
                const label = row.fileStatusDesc ?? row.fileStatusCode ?? '';
                return label ? <Tag type="green">{label}</Tag> : '—';
              }
              if (key === 'clientName') return row.clientName ?? '—';
              return undefined;
            }}
          />
        )}
      </AsyncBoundary>
    </PageLayout>
  );
};

export default RangeTenureSearch;
