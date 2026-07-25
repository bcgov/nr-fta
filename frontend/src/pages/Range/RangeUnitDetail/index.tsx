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
import { ArrowLeft, Edit } from '@carbon/icons-react';
import type { FC } from 'react';
import { Link, useParams } from 'react-router-dom';
import AsyncBoundary from '@/components/AsyncBoundary';
import DefinitionGrid from '@/components/DefinitionGrid';
import Tombstone from '@/components/Tombstone';
import { useAuth } from '@/context/auth/useAuth';
import { canEdit } from '@/routes/access';
import PageLayout from '@/pages/PageLayout';
import { useApiResource } from '@/hooks/useApiResource';
import { getRangeUnitDetail } from '@/services/range_unit_detail';

/**
 * FTA630 — Maintain Range Unit / Pasture. Tombstone + unit details and a
 * pasture breakdown. Backed by the backend {@code GET /api/fta/range-units/{unitId}}
 * endpoint (which ports THE.FTA_630_MN_RG_UN_PST GET).
 */
const RangeUnitDetail: FC = () => {
  const { unitId = '' } = useParams();
  const { user } = useAuth();
  const { data: unit, loading, error, reload } = useApiResource(
    () => getRangeUnitDetail(unitId),
    [unitId],
  );

  return (
    <PageLayout title={`Range Unit ${unitId}`}>
      <Link to="/search/range-unit" className="fta-back">
        <ArrowLeft size={16} /> Back to Range Unit Search
      </Link>

      <AsyncBoundary loading={loading} error={error} onRetry={reload} loadingText="Loading range unit…">
        {unit && (
          <>
            <Tombstone
              ariaLabel="Range unit summary"
              items={[
                { label: 'Unit ID', value: unit.rangeUnitId },
                { label: 'Name', value: unit.rangeUnitName ?? '—' },
                { label: 'Status', value: unit.statusDescription ?? unit.statusCode ?? '—' },
                { label: 'Admin Zone', value: unit.districtAdminZone ?? '—' },
                { label: 'Pastures', value: String(unit.pastures.length) },
                { label: 'District', value: unit.districtDescription ?? '—' },
              ]}
              action={
                canEdit(user) ? (
                  <Button size="sm" kind="tertiary" renderIcon={Edit}>Edit unit</Button>
                ) : undefined
              }
            />

            <DefinitionGrid
              items={[
                { label: 'Region', value: unit.regionDescription ?? '—' },
                { label: 'District', value: unit.districtDescription ?? '—' },
                { label: 'Admin Zone', value: unit.districtAdminZone ?? '—' },
                { label: 'Status', value: unit.statusDescription ?? unit.statusCode ?? '—' },
                { label: 'As Of', value: unit.statusDate ?? '—' },
                { label: 'Pastures', value: String(unit.pastures.length) },
              ]}
            />

            <TableContainer title="Pastures" description={`${unit.pastures.length} pasture(s)`}>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableHeader>Pasture ID</TableHeader>
                    <TableHeader>Name</TableHeader>
                    <TableHeader>Revision</TableHeader>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {unit.pastures.map((p) => (
                    <TableRow key={p.pastureId}>
                      <TableCell>{p.pastureId}</TableCell>
                      <TableCell>{p.pastureName ?? '—'}</TableCell>
                      <TableCell>{p.pastureRevisionCount ?? '—'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </>
        )}
      </AsyncBoundary>
    </PageLayout>
  );
};

export default RangeUnitDetail;
