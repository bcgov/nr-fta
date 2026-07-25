import { Button, Column, Grid, Select, SelectItem, Tag, TextInput } from '@carbon/react';
import { Search as SearchIcon, Reset } from '@carbon/icons-react';
import { useState, type FC, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import AsyncBoundary from '@/components/AsyncBoundary';
import SearchResultsTable, { type ColumnDef } from '@/components/SearchResultsTable';
import PageLayout from '@/pages/PageLayout';
import {
  searchCutBlocks,
  type CutblockSearchParams,
  type CutblockSearchResult,
} from '@/services/cutblock_search';
import './CutBlockSearch.scss';

const HEADERS: ColumnDef[] = [
  { key: 'cutBlockId', header: 'Block' },
  { key: 'cuttingPermitId', header: 'Cutting Permit' },
  { key: 'forestFileId', header: 'File ID' },
  { key: 'timberMark', header: 'Timber Mark' },
  { key: 'blockStatusSt', header: 'Status' },
  { key: 'clientName', header: 'Licensee' },
  { key: 'orgUnitCode', header: 'Org Unit' },
];

/**
 * FTA003 — Cut Block Search. Criteria form + results table backed by the
 * backend {@code GET /api/fta/cut-blocks} endpoint (which ports
 * THE.FTA_003_CUTBLK_SRCH). Each block links to its Cut Block detail (FTA904).
 */
const CutBlockSearch: FC = () => {
  const [criteria, setCriteria] = useState<CutblockSearchParams>({});
  const [rows, setRows] = useState<CutblockSearchResult[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);

  const onField = (f: keyof CutblockSearchParams) => (v: string) =>
    setCriteria((c) => ({ ...c, [f]: v }));

  const runSearch = async (params: CutblockSearchParams) => {
    setLoading(true);
    setError(undefined);
    try {
      setRows(await searchCutBlocks(params));
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
    <PageLayout title="Cut Block Search">
      <form className="cb-search__form" onSubmit={onSearch}>
        <Grid narrow>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="cb-block" labelText="Block" placeholder="e.g. BLK-001"
              value={criteria.cutBlockId ?? ''} onChange={(e) => onField('cutBlockId')(e.target.value)} />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="cb-cp" labelText="Cutting Permit" placeholder="e.g. CP-01"
              value={criteria.cuttingPermitId ?? ''} onChange={(e) => onField('cuttingPermitId')(e.target.value)} />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="cb-file" labelText="Forest File ID" placeholder="e.g. A19201"
              value={criteria.forestFileId ?? ''} onChange={(e) => onField('forestFileId')(e.target.value)} />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <Select id="cb-status" labelText="Status" value={criteria.blockStatusSt ?? ''}
              onChange={(e) => onField('blockStatusSt')(e.target.value)}>
              <SelectItem value="" text="Any" />
              <SelectItem value="Active" text="Active" />
              <SelectItem value="Harvested" text="Harvested" />
              <SelectItem value="Suspended" text="Suspended" />
              <SelectItem value="Amended" text="Amended" />
            </Select>
          </Column>
        </Grid>
        <div className="cb-search__actions">
          <Button type="submit" renderIcon={SearchIcon}>Search</Button>
          <Button type="button" kind="ghost" renderIcon={Reset} onClick={onReset}>Reset</Button>
        </div>
      </form>

      <AsyncBoundary loading={loading} error={error} onRetry={() => void runSearch(criteria)} loadingText="Searching…">
        {rows !== null && (
          <SearchResultsTable
            rows={rows.map((r, i) => ({ ...r, id: r.cutBlockId ?? String(r.cbSkey ?? i) }))}
            headers={HEADERS}
            emptyTitle="No cut blocks found"
            renderCell={(row, key) => {
              if (key === 'cutBlockId') {
                return row.cutBlockId
                  ? <Link to={`/cut-block/${row.cutBlockId}`}>{row.cutBlockId}</Link>
                  : '—';
              }
              if (key === 'cuttingPermitId') {
                return row.cuttingPermitId
                  ? <Link to={`/harvesting-authority/${row.cuttingPermitId}`}>{row.cuttingPermitId}</Link>
                  : '—';
              }
              if (key === 'forestFileId') return <Link to={`/tenures/${row.forestFileId}`}>{row.forestFileId}</Link>;
              if (key === 'blockStatusSt') {
                return row.blockStatusSt ? <Tag type="green">{row.blockStatusSt}</Tag> : '—';
              }
              if (key === 'clientName') return row.clientName ?? '—';
              if (key === 'timberMark') return row.timberMark ?? '—';
              if (key === 'orgUnitCode') return row.orgUnitCode ?? '—';
              return undefined;
            }}
          />
        )}
      </AsyncBoundary>
    </PageLayout>
  );
};

export default CutBlockSearch;
