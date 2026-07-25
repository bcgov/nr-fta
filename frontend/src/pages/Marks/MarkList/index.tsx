import { Button, Column, Grid, Select, SelectItem, Tag, TextInput } from '@carbon/react';
import { Search as SearchIcon, Reset, DocumentAdd } from '@carbon/icons-react';
import { useState, type FC, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import AsyncBoundary from '@/components/AsyncBoundary';
import SearchResultsTable, { type ColumnDef } from '@/components/SearchResultsTable';
import PageLayout from '@/pages/PageLayout';
import { listMarks, type MarkListParams, type MarkListRow } from '@/services/mark_list';
import './MarkList.scss';

const HEADERS: ColumnDef[] = [
  { key: 'timberMark', header: 'Mark Number' },
  { key: 'processType', header: 'Type' },
  { key: 'markStatusSt', header: 'Status' },
  { key: 'clientName', header: 'Holder' },
  { key: 'orgUnitCode', header: 'Org Unit' },
  { key: 'markApplDate', header: 'Issue Date' },
];

/**
 * FTA500 — Private Mark Application/Amendment List. Search + list of private
 * timber marks, linking each to its detail (FTA510). Also the entry point to
 * a new Mark Application. Backed by the backend {@code GET /api/fta/marks}
 * endpoint (which ports THE.FTA_500_MARK_LIST).
 */
const MarkList: FC = () => {
  const navigate = useNavigate();
  const [criteria, setCriteria] = useState<MarkListParams>({});
  const [rows, setRows] = useState<MarkListRow[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);

  const onField = (f: keyof MarkListParams) => (v: string) =>
    setCriteria((c) => ({ ...c, [f]: v }));

  const runSearch = async (params: MarkListParams) => {
    setLoading(true);
    setError(undefined);
    try {
      setRows(await listMarks(params));
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
    <PageLayout title="Private Marks">
      <div className="mark-list__topbar">
        <Button kind="tertiary" renderIcon={DocumentAdd} onClick={() => navigate('/marks/application')}>
          New Mark Application
        </Button>
      </div>

      <form className="mark-list__form" onSubmit={onSearch}>
        <Grid narrow>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="mk-num" labelText="Mark Number" placeholder="e.g. 12 3456"
              value={criteria.timberMark ?? ''} onChange={(e) => onField('timberMark')(e.target.value)} />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="mk-holder" labelText="Holder" placeholder="e.g. Meadow Ranch"
              value={criteria.clientName ?? ''} onChange={(e) => onField('clientName')(e.target.value)} />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="mk-org" labelText="Org Unit" placeholder="District code or name"
              value={criteria.orgUnitCode ?? ''} onChange={(e) => onField('orgUnitCode')(e.target.value)} />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <Select id="mk-status" labelText="Status" value={criteria.markStatusSt ?? ''}
              onChange={(e) => onField('markStatusSt')(e.target.value)}>
              <SelectItem value="" text="Any" />
              <SelectItem value="Active" text="Active" />
              <SelectItem value="Pending" text="Pending" />
              <SelectItem value="Amended" text="Amended" />
              <SelectItem value="Cancelled" text="Cancelled" />
            </Select>
          </Column>
        </Grid>
        <div className="mark-list__actions">
          <Button type="submit" renderIcon={SearchIcon}>Search</Button>
          <Button type="button" kind="ghost" renderIcon={Reset} onClick={onReset}>Reset</Button>
        </div>
      </form>

      <AsyncBoundary loading={loading} error={error} onRetry={() => void runSearch(criteria)} loadingText="Searching…">
        {rows !== null && (
          <SearchResultsTable
            rows={rows.map((r, i) => ({ ...r, id: `${r.processType ?? ''}-${r.certificate ?? ''}-${r.timberMark ?? ''}-${i}` }))}
            headers={HEADERS}
            emptyTitle="No marks found"
            renderCell={(row, key) => {
              if (key === 'timberMark') {
                const markId = row.timberMark ?? row.certificate;
                return markId ? (
                  <Link to={`/marks/${encodeURIComponent(markId)}`}>{markId}</Link>
                ) : (
                  '—'
                );
              }
              if (key === 'markStatusSt') {
                return row.markStatusSt ? <Tag type="blue">{row.markStatusSt}</Tag> : '—';
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

export default MarkList;
