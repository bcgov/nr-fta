import { ArrowLeft, Edit, Information, Location, Table as TableIcon } from '@carbon/icons-react';
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
import { Link, useParams } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import DefinitionGrid from '@/components/DefinitionGrid';
import SectionTile from '@/components/SectionTile';
import Tombstone from '@/components/Tombstone';
import { useAuth } from '@/context/auth/useAuth';
import { useApiResource } from '@/hooks/useApiResource';
import PageLayout from '@/pages/PageLayout';
import { canEdit } from '@/routes/access';
import { getRangeUnitDetail } from '@/services/range_unit_detail';

import type { FC } from 'react';

/**
 * FTA630 — Maintain Range Unit / Pasture. Tombstone + unit details and a
 * pasture breakdown. Backed by the backend {@code GET /api/fta/range-units/{unitId}}
 * endpoint (which ports THE.FTA_630_MN_RG_UN_PST GET).
 */
const RangeUnitDetail: FC = () => {
  const { unitId = '' } = useParams();
  const { user } = useAuth();
  const {
    data: unit,
    loading,
    error,
    reload,
  } = useApiResource(() => getRangeUnitDetail(unitId), [unitId]);

  return (
    <PageLayout
      title={`Range Unit ${unitId}`}
      subtitle="Range unit details and its pasture breakdown."
      actions={
        unit && canEdit(user) ? (
          <Button size="md" kind="tertiary" renderIcon={Edit}>
            Edit unit
          </Button>
        ) : undefined
      }
    >
      <Link to="/search/range-unit" className="back-link">
        <ArrowLeft size={16} /> Back to Range Unit Search
      </Link>

      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Loading range unit…"
      >
        {unit && (
          <>
            <SectionTile title="Range unit summary" icon={Location}>
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
              />
            </SectionTile>

            <SectionTile title="Unit details" icon={Information}>
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
            </SectionTile>

            <SectionTile
              title="Pastures"
              icon={TableIcon}
              description={`${unit.pastures.length} pasture(s)`}
            >
              <div className="bordered-table">
                <TableContainer>
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
              </div>
            </SectionTile>
          </>
        )}
      </AsyncBoundary>
    </PageLayout>
  );
};

export default RangeUnitDetail;
