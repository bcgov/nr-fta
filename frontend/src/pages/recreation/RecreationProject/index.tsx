import { ArrowLeft, Locked } from '@carbon/icons-react';
import {
  Button,
  Checkbox,
  InlineNotification,
  Loading,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  Tag,
  Tile,
} from '@carbon/react';
import { useEffect, useState, type FC, type ReactNode } from 'react';
import { Link, useParams } from 'react-router-dom';

import { EmptyState } from '@/components/EmptyState/EmptyState';
import { safeErrorMessage } from '@/lib/errorMessage';
import PageLayout from '@/pages/PageLayout';
import {
  FEE_DAYS,
  getRecreationProject,
  isTrailProject,
  type RecreationProjectDetail,
} from '@/services/recreation_project';
import './RecreationProject.scss';

const dash = (value: string | number | null | undefined) =>
  value === null || value === undefined || String(value).trim() === '' ? '—' : String(value);

const yesNo = (value: string | null | undefined) =>
  value === 'Y' ? 'Yes' : value === 'N' ? 'No' : '—';

/** One label/value pair in a section's grid. */
const Field: FC<{ label: string; children: ReactNode; wide?: boolean }> = ({
  label,
  children,
  wide,
}) => (
  <div className={wide ? 'rec-detail__field rec-detail__field--wide' : 'rec-detail__field'}>
    <span className="rec-detail__label">{label}</span>
    <span className="rec-detail__value">{children}</span>
  </div>
);

/** A titled block of fields. */
const Section: FC<{ title: string; children: ReactNode }> = ({ title, children }) => (
  <Tile className="rec-detail__section">
    <h2 className="rec-detail__section-title">{title}</h2>
    <div className="rec-detail__grid">{children}</div>
  </Tile>
);

/**
 * FTA701 — Recreation Project.
 *
 * <p>Read-only in this pass. Backed by `GET /api/fta/recreation/{fileId}`, which
 * ports the GET side of `THE.FTA_701_PROJECT_DETAILS` and
 * `THE.FTA_701_PROJECT_ACCESS` — legacy assembles this screen from four package
 * round-trips; here it is one request.
 *
 * <p>Sections follow the legacy screen's order: tombstone, Project, Location,
 * Features, Campsites, Additional Info, Inspection/Assessment Dates,
 * Archaeological Impact Assessment, Field Notes, then the four child lists
 * (Recreation Districts, Fees, Access, Establishment Order).
 *
 * <p>The permissions block is already computed server-side, so the screen can
 * say why editing is unavailable rather than simply offering nothing.
 */
const RecreationProject: FC = () => {
  const { fileId = '' } = useParams<{ fileId: string }>();
  const [detail, setDetail] = useState<RecreationProjectDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    getRecreationProject(fileId)
      .then((d) => {
        if (!cancelled) setDetail(d);
      })
      .catch((e) => {
        if (!cancelled) setError(safeErrorMessage(e));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [fileId]);

  if (loading) {
    return (
      <div className="fsp-search__loading" role="status" aria-live="polite">
        <Loading description="Loading…" withOverlay={false} />
      </div>
    );
  }

  if (error || !detail) {
    return (
      <PageLayout title="Recreation Project" subtitle={fileId}>
        <EmptyState
          title="Could not load this recreation project"
          body={error ?? 'No data was returned for this file.'}
          action={
            <Button as={Link} to="/search/recreation" renderIcon={ArrowLeft} kind="tertiary">
              Back to search
            </Button>
          }
        />
      </PageLayout>
    );
  }

  const { tombstone, project, districts, fees, access, attachments, permissions } = detail;
  const trail = isTrailProject(project);

  return (
    <PageLayout
      title={project?.projectName || fileId}
      subtitle={`Recreation project ${fileId}`}
      actions={
        <Button as={Link} to="/search/recreation" renderIcon={ArrowLeft} kind="tertiary" size="md">
          Back to search
        </Button>
      }
    >
      {/* Tombstone — the file header strip shared with the other file screens. */}
      <Tile className="rec-detail__tombstone">
        <div className="rec-detail__grid">
          <Field label="File">{dash(fileId)}</Field>
          <Field label="Status">
            {tombstone.fileStatusCode ? (
              <Tag type="blue">
                {tombstone.fileStatusCode}
                {tombstone.fileStatusDesc ? ` — ${tombstone.fileStatusDesc}` : ''}
              </Tag>
            ) : (
              '—'
            )}
          </Field>
          <Field label="As of">{dash(tombstone.fileStatusDate)}</Field>
          <Field label="Type">
            {dash(
              tombstone.fileTypeCode && tombstone.fileTypeDesc
                ? `${tombstone.fileTypeCode} — ${tombstone.fileTypeDesc}`
                : tombstone.fileTypeCode,
            )}
          </Field>
          <Field label="Admin org">
            {dash(
              tombstone.adminOrgCode && tombstone.adminOrgName
                ? `${tombstone.adminOrgCode} — ${tombstone.adminOrgName}`
                : tombstone.adminOrgCode,
            )}
          </Field>
        </div>
      </Tile>

      {!permissions.parentSaveEnabled && (
        <InlineNotification
          kind="info"
          lowContrast
          hideCloseButton
          title="Read-only"
          subtitle={
            tombstone.fileStatusCode !== 'HI'
              ? `This project cannot be edited because the file status is ${dash(tombstone.fileStatusCode)}. Only files in HI status may be changed.`
              : 'You do not have edit rights for the district administering this file.'
          }
        />
      )}

      {project === null ? (
        <EmptyState
          icon={<Locked size={64} />}
          title="No project details yet"
          body="This recreation file exists but has no project record. Project details must be saved before fees and access types can be added."
        />
      ) : (
        <>
          <Section title="Project">
            <Field label="Project name" wide>
              {dash(project.projectName)}
            </Field>
            <Field label="Project type">{dash(project.projectType)}</Field>
            <Field label="Risk rating">{dash(project.riskRatingCode)}</Field>
            <Field label="Project established">
              {dash(project.projectEstablishedDate)}
              {permissions.establishedFieldsLocked && (
                <Locked size={16} className="rec-detail__lock" aria-label="Headquarters only" />
              )}
            </Field>
          </Section>

          <Section title="Location">
            <Field label="Closest community" wide>
              {dash(project.siteLocation)}
            </Field>
            <Field label="UTM zone">{dash(project.utmZone)}</Field>
            <Field label="UTM easting">{dash(project.utmEasting)}</Field>
            <Field label="UTM northing">{dash(project.utmNorthing)}</Field>
          </Section>

          <Section title="Features">
            <Field label="Total trail length (km)">{dash(project.projectLength)}</Field>
            <Field label="Total area (ha)">{dash(project.projectArea)}</Field>
            <Field label={`Right of way (m)${trail ? '' : ' — not a trail project'}`}>
              {dash(project.rightOfWay)}
            </Field>
            <Field label="Significant recreation feature">{dash(project.featureCode)}</Field>
            <Field label="User days">{dash(project.userDaysCode)}</Field>
            <Field label="Maintenance standard">{dash(project.maintainStdCode)}</Field>
          </Section>

          <Section title="Campsites">
            <Field label="Defined campsites">{dash(project.definedCampsites)}</Field>
            <Field label="Overflow campsites">{dash(project.overflowCampsites)}</Field>
            <Field label="Camp host / operator">{yesNo(project.campHostInd)}</Field>
            <Field label="Low mobility access">{yesNo(project.lowMobilityAccessInd)}</Field>
          </Section>

          <Section title="Additional info">
            <Field label="Display to website">{yesNo(project.recreationViewInd)}</Field>
            <Field label="Resource feature">{yesNo(project.resourceFeatureInd)}</Field>
            <Field label="Associated files">{yesNo(project.assocFilesExist)}</Field>
            <Field label="Controlled access type">{dash(project.controlAccessCode)}</Field>
          </Section>

          <Section title="Inspection / assessment dates">
            <Field label="Last recreation inspection">{dash(project.lastRecInspectionDate)}</Field>
            <Field label="Last hazard tree assessment">
              {dash(project.lastHzrdTreeAssessDate)}
            </Field>
          </Section>

          <Section title="Archaeological impact assessment">
            <Field label="AIA indicator">{yesNo(project.archImpactAssessInd)}</Field>
            <Field label="AIA date">{dash(project.archImpactDate)}</Field>
            <Field label="Borden #" wide>
              {dash(project.bordenNo)}
            </Field>
            <Field label="Comment" wide>
              {dash(project.aiaComment)}
            </Field>
          </Section>

          <Section title="Field notes">
            <Field label="Field note" wide>
              {dash(project.siteDescription)}
            </Field>
          </Section>
        </>
      )}

      {/* ── Child lists ─────────────────────────────────────────────── */}

      <Tile className="rec-detail__section">
        <h2 className="rec-detail__section-title">Recreation districts</h2>
        {districts.length === 0 ? (
          <p className="rec-detail__empty">No districts recorded.</p>
        ) : (
          <TableContainer>
            <Table size="md">
              <TableHead>
                <TableRow>
                  <TableHeader>District</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {districts.map((d) => (
                  <TableRow key={d.districtCode ?? ''}>
                    <TableCell>{dash(d.description ?? d.districtCode)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Tile>

      <Tile className="rec-detail__section">
        <h2 className="rec-detail__section-title">Fees</h2>
        {fees.length === 0 ? (
          <p className="rec-detail__empty">No fees recorded.</p>
        ) : (
          <TableContainer>
            <Table size="md">
              <TableHead>
                <TableRow>
                  <TableHeader>Fee type</TableHeader>
                  <TableHeader>Amount</TableHeader>
                  <TableHeader>Start date</TableHeader>
                  <TableHeader>End date</TableHeader>
                  <TableHeader>Days</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {fees.map((f) => (
                  <TableRow key={f.feeId ?? ''}>
                    <TableCell>
                      {dash(
                        f.feeCode && f.feeDescription
                          ? `${f.feeCode} — ${f.feeDescription}`
                          : f.feeCode,
                      )}
                    </TableCell>
                    <TableCell>{f.feeAmount === null ? '—' : f.feeAmount.toFixed(2)}</TableCell>
                    <TableCell>{dash(f.feeStartDate)}</TableCell>
                    <TableCell>{dash(f.feeEndDate)}</TableCell>
                    <TableCell>
                      <div className="rec-detail__days">
                        {FEE_DAYS.map((day) => (
                          <Checkbox
                            key={day.key}
                            id={`fee-${f.feeId}-${day.key}`}
                            labelText={day.label}
                            checked={f[day.key] === 'Y'}
                            disabled
                          />
                        ))}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Tile>

      <Tile className="rec-detail__section">
        <h2 className="rec-detail__section-title">Access</h2>
        {access.length === 0 ? (
          <p className="rec-detail__empty">No access routes recorded.</p>
        ) : (
          <TableContainer>
            <Table size="md">
              <TableHead>
                <TableRow>
                  <TableHeader>Access type</TableHeader>
                  <TableHeader>Access sub type</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {access.map((a) => (
                  <TableRow key={`${a.accessCode}-${a.subAccessCode}`}>
                    <TableCell>{dash(a.accessDescription ?? a.accessCode)}</TableCell>
                    <TableCell>{dash(a.subAccessDescription ?? a.subAccessCode)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Tile>

      <Tile className="rec-detail__section">
        <h2 className="rec-detail__section-title">Establishment order</h2>
        {attachments.length === 0 ? (
          <p className="rec-detail__empty">No establishment order attached.</p>
        ) : (
          <TableContainer>
            <Table size="md">
              <TableHead>
                <TableRow>
                  <TableHeader>File name</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {attachments.map((at) => (
                  <TableRow key={at.attachmentId ?? ''}>
                    <TableCell>{dash(at.fileName)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Tile>
    </PageLayout>
  );
};

export default RecreationProject;
