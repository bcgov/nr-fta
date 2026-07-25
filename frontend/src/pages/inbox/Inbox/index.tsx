import { Button, Column, Grid, Select, SelectItem, Tag, TextInput } from '@carbon/react';
import { Search as SearchIcon, Reset } from '@carbon/icons-react';
import { useEffect, useState, type FC, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import AsyncBoundary from '@/components/AsyncBoundary';
import SearchResultsTable, { type ColumnDef } from '@/components/SearchResultsTable';
import PageLayout from '@/pages/PageLayout';
import { searchInbox, type InboxSearchParams, type InboxRow } from '@/services/inbox';
import './Inbox.scss';

const HEADERS: ColumnDef[] = [
  { key: 'submissionId', header: 'ESF ID' },
  { key: 'forestFileIdDisplay', header: 'File ID' },
  { key: 'tenureApplicationType', header: 'Type' },
  { key: 'licensee', header: 'Client' },
  { key: 'orgUnitName', header: 'Org Unit' },
  { key: 'adjudicationInd', header: 'Exhibit A' },
  { key: 'submissionDate', header: 'Submitted' },
  { key: 'currentAssignedTo', header: 'Assigned To' },
];

/**
 * FTA300 — Inbox. Worklist of ESF tenure applications with filtering and
 * links to the application detail (FTA952) for adjudication. Backed by the
 * backend {@code GET /api/fta/inbox} endpoint (which ports THE.FTA_300N_INBOX);
 * the full pending queue loads on mount and the filter form narrows it.
 */
const Inbox: FC = () => {
  const [criteria, setCriteria] = useState<InboxSearchParams>({});
  const [rows, setRows] = useState<InboxRow[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);

  const onField = (f: keyof InboxSearchParams) => (v: string) =>
    setCriteria((c) => ({ ...c, [f]: v }));

  const runSearch = async (params: InboxSearchParams) => {
    setLoading(true);
    setError(undefined);
    try {
      setRows(await searchInbox(params));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load the inbox');
      setRows(null);
    } finally {
      setLoading(false);
    }
  };

  // Show the full pending queue on load.
  useEffect(() => {
    void runSearch({});
  }, []);

  const onSearch = (e: FormEvent) => {
    e.preventDefault();
    void runSearch(criteria);
  };
  const onReset = () => {
    setCriteria({});
    void runSearch({});
  };

  return (
    <PageLayout title="Inbox">
      <form className="inbox__form" onSubmit={onSearch}>
        <Grid narrow>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="in-file" labelText="Forest File ID" placeholder="e.g. A19201"
              value={criteria.forestFileId ?? ''} onChange={(e) => onField('forestFileId')(e.target.value)} />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <TextInput id="in-client" labelText="Client Number" placeholder="e.g. 00001012"
              value={criteria.clientNumber ?? ''} onChange={(e) => onField('clientNumber')(e.target.value)} />
          </Column>
          <Column sm={4} md={4} lg={4}>
            <Select id="in-type" labelText="Application Type" value={criteria.applTypeCode ?? ''}
              onChange={(e) => onField('applTypeCode')(e.target.value)}>
              <SelectItem value="" text="Any" />
              <SelectItem value="CP" text="Cutting Permit" />
              <SelectItem value="RP" text="Road Permit" />
              <SelectItem value="TL" text="Timber Licence" />
              <SelectItem value="RNG" text="Range" />
            </Select>
          </Column>
          <Column sm={4} md={4} lg={4}>
            <Select id="in-exa" labelText="Exhibit A" value={criteria.exACleared ?? ''}
              onChange={(e) => onField('exACleared')(e.target.value)}>
              <SelectItem value="" text="Any" />
              <SelectItem value="Y" text="Cleared" />
              <SelectItem value="N" text="Not cleared" />
            </Select>
          </Column>
        </Grid>
        <div className="inbox__actions">
          <Button type="submit" renderIcon={SearchIcon}>Filter</Button>
          <Button type="button" kind="ghost" renderIcon={Reset} onClick={onReset}>Reset</Button>
        </div>
      </form>

      <AsyncBoundary loading={loading} error={error} onRetry={() => void runSearch(criteria)} loadingText="Loading inbox…">
        {rows !== null && (
          <SearchResultsTable
            title="Application worklist"
            rows={rows.map((r) => ({ ...r, id: String(r.tenureAppId ?? r.submissionId ?? r.forestFileId) }))}
            headers={HEADERS}
            emptyTitle="No applications in the queue"
            emptyBody="No ESF submissions match the current filters."
            renderCell={(row, key) => {
              if (key === 'submissionId') {
                const id = row.submissionId ?? row.tenureAppId;
                return id != null ? <Link to={`/inbox/${id}`}>{id}</Link> : '—';
              }
              if (key === 'forestFileIdDisplay') {
                const label = row.forestFileIdDisplay ?? row.forestFileId;
                return row.forestFileId ? <Link to={`/tenures/${row.forestFileId}`}>{label}</Link> : (label ?? '—');
              }
              if (key === 'adjudicationInd') {
                return row.adjudicationInd === 'Y'
                  ? <Tag type="teal">Cleared</Tag>
                  : <Tag type="blue">Pending</Tag>;
              }
              if (key === 'currentAssignedTo') return row.currentAssignedTo ?? <em>Unassigned</em>;
              return undefined;
            }}
          />
        )}
      </AsyncBoundary>
    </PageLayout>
  );
};

export default Inbox;
