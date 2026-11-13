import { Search as SearchIcon, DocumentAdd, Time } from '@carbon/icons-react';
import {
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import EmptyState from '@/components/EmptyState/EmptyState';
import SectionTile from '@/components/SectionTile';
import { statusCodeVariant } from '@/components/StatusTag/statusCodes';
import StatusTag from '@/components/StatusTag/StatusTag';
import { useAuth } from '@/context/auth/useAuth';
import { loadRecentTenures } from '@/lib/recentTenures';
import PageLayout from '@/pages/PageLayout';
import { formatDate } from '@/utils/formatDate';

import type { FC } from 'react';

/** The visit's calendar day where the user is (viewedAt is a UTC timestamp). */
const localDay = (iso: string): string => {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
};

/**
 * Landing for the "Tenures" menu entry. In the legacy app you reach a tenure
 * via search; this page offers that entry point plus the tenures this user
 * last opened in this browser (lib/recentTenures), linking straight into the
 * detail (FTA100).
 */
const TenureLanding: FC = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  // Read once on mount: the list only changes by visiting a tenure.
  const [recent] = useState(() => loadRecentTenures(user?.userName));

  return (
    <PageLayout
      title="Tenures"
      subtitle="Search for a forest tenure file, add a new one, or jump back into a recent file."
      actions={
        <>
          <Button size="md" renderIcon={SearchIcon} onClick={() => navigate('/search/tenure')}>
            Tenure Search
          </Button>
          <Button
            size="md"
            kind="tertiary"
            renderIcon={DocumentAdd}
            onClick={() => navigate('/tenures/add')}
          >
            Add New Tenure
          </Button>
        </>
      }
    >
      <SectionTile
        title="Recently accessed tenures"
        icon={Time}
        description="The tenures you last opened in this browser"
      >
        {recent.length === 0 ? (
          <EmptyState
            icon={<Time size={48} />}
            title="No recent tenures"
            body="Tenures you open will be listed here."
          />
        ) : (
          <div className="bordered-table">
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableHeader>File ID</TableHeader>
                    <TableHeader>File Type</TableHeader>
                    <TableHeader>Status</TableHeader>
                    <TableHeader>Licensee</TableHeader>
                    <TableHeader>Organization Unit</TableHeader>
                    <TableHeader>Last Viewed</TableHeader>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {recent.map((t) => (
                    <TableRow key={t.fileId}>
                      <TableCell>
                        <Link to={`/tenures/${encodeURIComponent(t.fileId)}`}>{t.fileId}</Link>
                      </TableCell>
                      <TableCell>{t.fileType || '—'}</TableCell>
                      <TableCell>
                        {t.statusCode ? (
                          <StatusTag
                            status={t.statusDesc ?? t.statusCode}
                            variant={statusCodeVariant(t.statusCode)}
                          />
                        ) : (
                          '—'
                        )}
                      </TableCell>
                      <TableCell>{t.licensee || '—'}</TableCell>
                      <TableCell>{t.orgUnit || '—'}</TableCell>
                      <TableCell>{formatDate(localDay(t.viewedAt)) || '—'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </div>
        )}
      </SectionTile>
    </PageLayout>
  );
};

export default TenureLanding;
