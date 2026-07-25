import {
  Button,
  Column,
  Grid,
  Select,
  SelectItem,
  Tag,
  TextInput,
} from '@carbon/react';
import { Search as SearchIcon, Reset } from '@carbon/icons-react';
import { useState, type FC, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import AsyncBoundary from '@/components/AsyncBoundary';
import SearchResultsTable, { type ColumnDef } from '@/components/SearchResultsTable';
import PageLayout from '@/pages/PageLayout';
import { searchTenures, type TenureSearchParams, type TenureSummary } from '@/services/tenure';
import './TenureSearch.scss';

const HEADERS: ColumnDef[] = [
  { key: 'forestFileId', header: 'File ID' },
  { key: 'fileTypeCode', header: 'File Type' },
  { key: 'fileStatusDesc', header: 'Status' },
  { key: 'orgUnitCode', header: 'Org Unit' },
  { key: 'clientName', header: 'Licensee' },
  { key: 'issueDate', header: 'Issue Date' },
  { key: 'expiryDate', header: 'Expiry Date' },
];

/**
 * FTA001 — Tenure Search. Criteria form + results table backed by the backend
 * {@code GET /api/fta/tenures} endpoint (which ports THE.FTA_001_TENR_SRCH).
 * Establishes the search → results → detail pattern the other FTA search
 * screens follow.
 */
const TenureSearch: FC = () => {
  const [criteria, setCriteria] = useState<TenureSearchParams>({});
  const [rows, setRows] = useState<TenureSummary[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);

  const onField = (field: keyof TenureSearchParams) => (value: string) =>
    setCriteria((c) => ({ ...c, [field]: value }));

  const runSearch = async (params: TenureSearchParams) => {
    setLoading(true);
    setError(undefined);
    try {
      setRows(await searchTenures(params));
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
    <PageLayout title="Tenure Search">
      <form className="tenure-search__form" onSubmit={onSearch}>
        <Grid narrow>
          <Column sm={4} md={4} lg={4}>
            <TextInput
              id="ts-file-id"
              labelText="Forest File ID"
              placeholder="e.g. A19201"
              value={criteria.forestFileId ?? ''}
              onChange={(e) => onField('forestFileId')(e.target.value)}
            />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <TextInput
              id="ts-client"
              labelText="Licensee / Client"
              placeholder="e.g. West Fraser"
              value={criteria.clientName ?? ''}
              onChange={(e) => onField('clientName')(e.target.value)}
            />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <TextInput
              id="ts-org-unit"
              labelText="Org Unit"
              placeholder="District code"
              value={criteria.orgUnitCode ?? ''}
              onChange={(e) => onField('orgUnitCode')(e.target.value)}
            />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <Select
              id="ts-status"
              labelText="Status"
              value={criteria.fileStatus ?? ''}
              onChange={(e) => onField('fileStatus')(e.target.value)}
            >
              <SelectItem value="" text="Any" />
              <SelectItem value="ACT" text="Active" />
              <SelectItem value="PEN" text="Pending" />
              <SelectItem value="EXP" text="Expired" />
              <SelectItem value="ARC" text="Archived" />
            </Select>
          </Column>
        </Grid>
        <div className="tenure-search__actions">
          <Button type="submit" renderIcon={SearchIcon}>Search</Button>
          <Button type="button" kind="ghost" renderIcon={Reset} onClick={onReset}>Reset</Button>
        </div>
      </form>

      <AsyncBoundary loading={loading} error={error} onRetry={() => void runSearch(criteria)} loadingText="Searching…">
        {rows !== null && (
          <SearchResultsTable
            rows={rows.map((r) => ({ ...r, id: r.forestFileId }))}
            headers={HEADERS}
            emptyTitle="No tenures found"
            renderCell={(row, key) => {
              if (key === 'forestFileId') return <Link to={`/tenures/${row.forestFileId}`}>{row.forestFileId}</Link>;
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

export default TenureSearch;
