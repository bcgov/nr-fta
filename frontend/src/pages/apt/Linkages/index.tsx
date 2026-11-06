import { Enterprise, TrashCan } from '@carbon/icons-react';
import {
  IconButton,
  Search,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableExpandedRow,
  TableExpandHeader,
  TableExpandRow,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { Fragment, useMemo, useState, type FC } from 'react';

import AsyncBoundary from '@/components/AsyncBoundary';
import ConfirmationModal from '@/components/ConfirmationModal';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import SectionTile from '@/components/SectionTile';
import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import PageLayout from '@/pages/PageLayout';
import { canEditApt } from '@/routes/access';
import {
  APT_RELATIONSHIP,
  deleteAptRelatedClient,
  getAptRelatedClients,
  type AptRelatedClient,
} from '@/services/apt/linkages';

import { groupByParent, matchesFilter } from './grouping';
import NewRelationshipForm from './NewRelationshipForm';

import './Linkages.css';

const relationshipLabel = (code: string) =>
  code === APT_RELATIONSHIP.code ? APT_RELATIONSHIP.label : code;

/**
 * APT linkages (APT2's APTM9200 Related Clients): which corporation controls which licensee.
 * The relationships are listed by controlling corporation, each expanding to its licensees.
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

  const parents = useMemo(
    () => groupByParent((data ?? []).filter((r) => matchesFilter(r, filter))),
    [data, filter],
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
      title: 'Relationship deleted',
      subtitle: `${deleting.parentClientName ?? deleting.parentClientNumber} no longer controls ${
        deleting.clientName ?? deleting.clientNumber
      }.`,
      timeout: 6000,
    });
    refresh();
  };

  // When filtering, show every match rather than making the user open each group.
  const isOpen = (parentNumber: string) => filter.trim() !== '' || expanded.has(parentNumber);

  return (
    <PageLayout title="Linkages" subtitle="Corporations and the licensees they control.">
      {canEdit && <NewRelationshipForm onCreated={refresh} />}

      <SectionTile title="Related clients" icon={Enterprise}>
        <AsyncBoundary
          loading={loading}
          error={error}
          onRetry={reload}
          loadingText="Loading related clients…"
        >
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
          {parents.length === 0 ? (
            <EmptyState
              title={filter ? 'No matching relationships' : 'No relationships'}
              body={filter ? 'Change the filter and try again.' : undefined}
            />
          ) : (
            <TableContainer>
              <Table size="md" aria-label="Related clients by controlling corporation">
                <TableHead>
                  <TableRow>
                    <TableExpandHeader aria-label="Expand" />
                    <TableHeader>Client number</TableHeader>
                    <TableHeader>Corporation</TableHeader>
                    <TableHeader>Licensees</TableHeader>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {parents.map((p) => (
                    <Fragment key={p.clientNumber}>
                      <TableExpandRow
                        isExpanded={isOpen(p.clientNumber)}
                        onExpand={() => toggle(p.clientNumber)}
                        aria-label={`Show the licensees ${p.clientName ?? p.clientNumber} controls`}
                      >
                        <TableCell>{p.clientNumber}</TableCell>
                        <TableCell>{p.clientName ?? '—'}</TableCell>
                        <TableCell>{p.children.length}</TableCell>
                      </TableExpandRow>
                      {isOpen(p.clientNumber) && (
                        <TableExpandedRow colSpan={4}>
                          <Table size="sm" aria-label={`Licensees of ${p.clientNumber}`}>
                            <TableHead>
                              <TableRow>
                                <TableHeader>Client number</TableHeader>
                                <TableHeader>Licensee</TableHeader>
                                <TableHeader>Relationship</TableHeader>
                                {canEdit && (
                                  <TableHeader>
                                    <span className="cds--visually-hidden">Actions</span>
                                  </TableHeader>
                                )}
                              </TableRow>
                            </TableHead>
                            <TableBody>
                              {p.children.map((c) => (
                                <TableRow key={`${c.clientNumber}|${c.relationshipCode}`}>
                                  <TableCell>{c.clientNumber}</TableCell>
                                  <TableCell>{c.clientName ?? '—'}</TableCell>
                                  <TableCell>{relationshipLabel(c.relationshipCode)}</TableCell>
                                  {canEdit && (
                                    <TableCell className="apt-linkages__action-cell">
                                      <IconButton
                                        kind="ghost"
                                        size="sm"
                                        label="Delete relationship"
                                        align="left"
                                        onClick={() => setDeleting(c)}
                                      >
                                        <TrashCan />
                                      </IconButton>
                                    </TableCell>
                                  )}
                                </TableRow>
                              ))}
                            </TableBody>
                          </Table>
                        </TableExpandedRow>
                      )}
                    </Fragment>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
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
            Delete the relationship in which{' '}
            <strong>
              {deleting.parentClientName ?? '—'} ({deleting.parentClientNumber})
            </strong>{' '}
            {relationshipLabel(deleting.relationshipCode).toLowerCase()}{' '}
            <strong>
              {deleting.clientName ?? '—'} ({deleting.clientNumber})
            </strong>
            ?
          </p>
        </ConfirmationModal>
      )}
    </PageLayout>
  );
};

export default AptLinkages;
