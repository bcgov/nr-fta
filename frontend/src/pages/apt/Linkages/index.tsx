import { Add, ChevronDown, ChevronUp, Enterprise, TrashCan } from '@carbon/icons-react';
import {
  Button,
  IconButton,
  Search,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { Fragment, useMemo, useState, type FC } from 'react';

import AsyncBoundary from '@/components/AsyncBoundary';
import ConfirmationModal from '@/components/ConfirmationModal';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import ExportCsvButton from '@/components/ExportCsvButton';
import SectionTile from '@/components/SectionTile';
import { StatusTag } from '@/components/StatusTag/StatusTag';
import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import { nextColumnSort, sortHeaderProps, sortRows } from '@/lib/columnSort';
import PageLayout from '@/pages/PageLayout';
import { canEditApt } from '@/routes/access';
import {
  APT_RELATIONSHIP,
  deleteAptRelatedClient,
  getAptRelatedClients,
  type AptRelatedClient,
} from '@/services/apt/linkages';
import { downloadRowsAsCsv } from '@/utils/csvDownload';

import CreateRelationshipModal from './CreateRelationshipModal';
import { groupByParent, matchesFilter } from './grouping';

import './Linkages.css';

const relationshipLabel = (code: string) =>
  code === APT_RELATIONSHIP.code ? APT_RELATIONSHIP.label : code;

/**
 * APT linkages (APT2's APTM9200 Related Clients): which parent corporation controls which
 * subsidiary corporation. Each parent's row opens to its subsidiaries, in the same table.
 * APT administrators create relationships and delete them; everyone else reads.
 */
const AptLinkages: FC = () => {
  const { user } = useAuth();
  const canEdit = canEditApt(user);
  const { display } = useNotification();
  const { data, loading, error, reload, refresh } = useApiResource(getAptRelatedClients, []);

  const [filter, setFilter] = useState('');
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [deleting, setDeleting] = useState<AptRelatedClient | null>(null);
  const [creating, setCreating] = useState(false);

  // Client number or corporation name; sorts the parents and, within each, its subsidiaries.
  const [sort, setSort] = useState<string | undefined>();
  const onSort = (key: string) => setSort((prev) => nextColumnSort(prev, key));
  const parents = useMemo(
    () =>
      sortRows(groupByParent((data ?? []).filter((r) => matchesFilter(r, filter))), sort).map(
        (g) => ({ ...g, children: sortRows(g.children, sort) }),
      ),
    [data, filter, sort],
  );

  const toggle = (parentNumber: string) =>
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(parentNumber)) next.delete(parentNumber);
      else next.add(parentNumber);
      return next;
    });

  const confirmDelete = async () => {
    if (!deleting) return;
    await deleteAptRelatedClient(deleting);
    display({
      kind: 'success',
      title: 'Relationship deleted.',
      subtitle: `${deleting.parentClientName ?? deleting.parentClientNumber} no longer controls ${
        deleting.clientName ?? deleting.clientNumber
      }.`,
      timeout: 6000,
    });
    refresh();
  };

  // One row per relationship, as filtered and sorted on screen.
  const exportCsv = () =>
    downloadRowsAsCsv(
      'apt-linkages',
      [
        'Parent client number',
        'Parent corporation',
        'Subsidiary client number',
        'Subsidiary corporation',
      ],
      parents.flatMap((p) =>
        p.children.map((c) => [p.clientNumber, p.clientName, c.clientNumber, c.clientName]),
      ),
    );

  // When filtering, show every match rather than making the user open each group.
  const isOpen = (parentNumber: string) => filter.trim() !== '' || expanded.has(parentNumber);

  return (
    <PageLayout
      title="Linkages"
      subtitle="Parent corporations and the subsidiary corporations they control."
    >
      <SectionTile
        title="Related clients"
        icon={Enterprise}
        actions={
          canEdit ? (
            <Button size="md" kind="tertiary" renderIcon={Add} onClick={() => setCreating(true)}>
              Create relationship
            </Button>
          ) : undefined
        }
      >
        <AsyncBoundary
          loading={loading}
          error={error}
          onRetry={reload}
          loadingText="Loading related clients…"
        >
          <div className="apt-linkages__toolbar">
            <div className="apt-linkages__filter">
              <Search
                id="apt-linkages-filter"
                size="md"
                labelText="Filter by client number or name"
                placeholder="Filter by client number or name"
                value={filter}
                onChange={(e) => setFilter(e.target.value)}
              />
            </div>
            {parents.length > 0 && <ExportCsvButton onExport={exportCsv} />}
          </div>
          {parents.length === 0 ? (
            <EmptyState
              title={filter ? 'No matching relationships' : 'No relationships'}
              body={filter ? 'Change the filter and try again.' : undefined}
            />
          ) : (
            <div className="bordered-table apt-linkages__table">
              <TableContainer>
                <Table size="md" aria-label="Related clients by parent corporation">
                  <TableHead>
                    <TableRow>
                      <TableHeader aria-label="Expand" className="apt-linkages__toggle-cell" />
                      <TableHeader {...sortHeaderProps(sort, 'clientNumber', onSort)}>
                        Client number
                      </TableHeader>
                      <TableHeader {...sortHeaderProps(sort, 'clientName', onSort)}>
                        Corporation
                      </TableHeader>
                      <TableHeader>Role</TableHeader>
                      {canEdit && <TableHeader>Actions</TableHeader>}
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {parents.map((p, i) => {
                      const open = isOpen(p.clientNumber);
                      // A group keeps its parent row's band, white or grey, when opened.
                      const band = i % 2 === 0 ? 'apt-linkages__band--a' : 'apt-linkages__band--b';
                      const name = p.clientName ?? p.clientNumber;
                      return (
                        <Fragment key={p.clientNumber}>
                          <TableRow
                            className={`${band}${open ? ' apt-linkages__parent--open' : ''}`}
                          >
                            <TableCell className="apt-linkages__toggle-cell">
                              <IconButton
                                kind="ghost"
                                size="sm"
                                align="right"
                                label={
                                  open
                                    ? `Hide the subsidiaries of ${name}`
                                    : `Show the subsidiaries of ${name}`
                                }
                                aria-expanded={open}
                                onClick={() => toggle(p.clientNumber)}
                              >
                                {open ? <ChevronUp /> : <ChevronDown />}
                              </IconButton>
                            </TableCell>
                            <TableCell>{p.clientNumber}</TableCell>
                            <TableCell>{p.clientName ?? '—'}</TableCell>
                            <TableCell>
                              <StatusTag status="Parent" variant="submitted" />
                            </TableCell>
                            {canEdit && <TableCell />}
                          </TableRow>
                          {open &&
                            p.children.map((c) => (
                              <TableRow
                                key={`${c.clientNumber}|${c.relationshipCode}`}
                                className={`${band} apt-linkages__child`}
                              >
                                <TableCell className="apt-linkages__toggle-cell" />
                                <TableCell className="apt-linkages__indent">
                                  {c.clientNumber}
                                </TableCell>
                                <TableCell className="apt-linkages__indent">
                                  {c.clientName ?? '—'}
                                </TableCell>
                                <TableCell>
                                  <StatusTag status="Subsidiary" variant="default" />
                                </TableCell>
                                {canEdit && (
                                  <TableCell className="apt-linkages__action-cell">
                                    <IconButton
                                      kind="ghost"
                                      size="sm"
                                      label="Delete relationship"
                                      align="left"
                                      className="icon-button--delete"
                                      onClick={() => setDeleting(c)}
                                    >
                                      <TrashCan />
                                    </IconButton>
                                  </TableCell>
                                )}
                              </TableRow>
                            ))}
                        </Fragment>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>
            </div>
          )}
        </AsyncBoundary>
      </SectionTile>

      {deleting && (
        <ConfirmationModal
          open
          danger
          heading="Delete relationship"
          confirmLabel="Delete"
          errorTitle="Could not delete the relationship"
          onClose={() => setDeleting(null)}
          onConfirm={confirmDelete}
        >
          <p>
            Delete the relationship in which parent corporation{' '}
            <strong>
              {deleting.parentClientName ?? '—'} ({deleting.parentClientNumber})
            </strong>{' '}
            {relationshipLabel(deleting.relationshipCode).toLowerCase()} subsidiary corporation{' '}
            <strong>
              {deleting.clientName ?? '—'} ({deleting.clientNumber})
            </strong>
            ?
          </p>
        </ConfirmationModal>
      )}
      {canEdit && (
        <CreateRelationshipModal
          open={creating}
          onClose={() => setCreating(false)}
          onCreated={refresh}
        />
      )}
    </PageLayout>
  );
};

export default AptLinkages;
