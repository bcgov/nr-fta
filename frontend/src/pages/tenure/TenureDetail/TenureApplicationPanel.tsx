import { DocumentTasks, Map as MapIcon, Misuse, Stamp } from '@carbon/icons-react';
import {
  Button,
  InlineNotification,
  OverflowMenu,
  OverflowMenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TextInput,
} from '@carbon/react';
import { useCallback, useState, type FC, type ReactNode } from 'react';
import { Link } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { Modal } from '@/components/Modal';
import StatusTag from '@/components/StatusTag/StatusTag';
import UserName from '@/components/UserName';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import {
  getTenureAppProfDecs,
  getTenureAppTab,
  issueTenureAppPermit,
  type TenureAppColumns,
  type TenureAppProfDec,
  type TenureAppRow,
} from '@/services/tenure_tenureapp';
import { formatDate } from '@/utils/formatDate';

import UnavailableNotice from './UnavailableNotice';

import type { TenurePanelProps } from './panelProps';

const dash = (v: string | number | null | undefined): string | number =>
  v === null || v === undefined || v === '' ? '—' : v;
const date = (v: string | null | undefined) => dash(formatDate(v));
const yesNo = (b: boolean) => (b ? 'Yes' : 'No');
const num = (v: number | null, digits = 4) =>
  v === null || v === undefined
    ? '—'
    : v.toLocaleString('en-CA', { minimumFractionDigits: 0, maximumFractionDigits: digits });
const codeDesc = (code: string | null, desc: string | null) =>
  code && desc ? `${code} - ${desc}` : dash(desc ?? code);

/** Pill colours for TENURE_APPLICATION_STATE_CODE (Inbox, Approved, Issued, Rejected…). */
const STATE_VARIANTS: Record<string, string> = {
  INB: 'submitted',
  APP: 'approved',
  ISS: 'in-effect',
  REJ: 'rejected',
};

const MAX_URI = 2000;

/** A white card with an icon heading and a count, around one of the tab's lists. */
const Section: FC<{ title: string; icon: typeof Stamp; count: number; children: ReactNode }> = ({
  title,
  icon: Icon,
  count,
  children,
}) => (
  <section className="fsp-info__tile">
    <header className="fsp-info__tile-header">
      <h2 className="fsp-info__section-title">
        <Icon size={20} />
        <span>{title}</span>
      </h2>
      <span className="detail-tab__reason">
        {count} {count === 1 ? 'row' : 'rows'}
      </span>
    </header>
    <div className="bordered-table">
      <TableContainer>{children}</TableContainer>
    </div>
  </section>
);

/** One spatial-submission row's React key: legacy can list an application once per feature. */
const rowKey = (r: TenureAppRow, i: number) =>
  `${r.tenureAppId ?? 'x'}-${r.mapFeatureId ?? ''}-${r.hvaSkey ?? ''}-${i}`;

/**
 * The Tenure application tab of the tenure detail — legacy FTA950 (Tenure
 * Application List): the ESF tenure applications ("spatial submissions")
 * submitted for the file, the smart-form cutting-permit requests and the
 * rejected CP submissions.
 *
 * Issue Permit is ported (legacy's FTA_950X_TEN_APP_ISSUE_PERMIT runs on the
 * server, checks first): allowed for an approved or issued application, it
 * records the permit document's link — this app has no document-management
 * upload — and sets the application Issued. The Prof Dec flag opens the
 * application's professional declarations.
 */
const TenureApplicationPanel: FC<TenurePanelProps> = ({ tenure, canEdit, onTenureChanged }) => {
  const forestFileId = tenure.forestFileId;
  const { display } = useNotification();
  const fetcher = useCallback(() => getTenureAppTab(forestFileId), [forestFileId]);
  const { data, loading, error, reload } = useApiResource(fetcher, [forestFileId]);

  // Issue permit dialog
  const [issuing, setIssuing] = useState<TenureAppRow | null>(null);
  const [documentUri, setDocumentUri] = useState('');
  const [uriError, setUriError] = useState<string | undefined>();
  const [saving, setSaving] = useState(false);

  // Prof Dec dialog
  const [profDecFor, setProfDecFor] = useState<TenureAppRow | null>(null);
  const [profDecs, setProfDecs] = useState<TenureAppProfDec[] | null>(null);
  const [profDecError, setProfDecError] = useState<string | null>(null);

  const openIssue = (r: TenureAppRow) => {
    setIssuing(r);
    setDocumentUri('');
    setUriError(undefined);
  };

  const closeIssue = () => {
    if (!saving) setIssuing(null);
  };

  const submitIssue = async () => {
    if (!issuing || issuing.tenureAppId === null) return;
    const uri = documentUri.trim();
    if (!uri) {
      setUriError('Permit document is mandatory.');
      return;
    }
    if (uri.length > MAX_URI) {
      setUriError(`At most ${MAX_URI} characters.`);
      return;
    }
    setSaving(true);
    try {
      const res = await issueTenureAppPermit(forestFileId, issuing.tenureAppId, {
        hvaSkey: issuing.hvaSkey,
        documentUri: uri,
      });
      display({
        kind: 'success',
        title: `Permit issued for application ${issuing.tenureAppId}`,
        subtitle: res.message,
        timeout: 7000,
      });
      setIssuing(null);
      reload();
      // Issuing sets the file's status to HI.
      onTenureChanged();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not issue the permit',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 10000,
      });
    } finally {
      setSaving(false);
    }
  };

  const openProfDec = (r: TenureAppRow) => {
    if (r.tenureAppId === null) return;
    setProfDecFor(r);
    setProfDecs(null);
    setProfDecError(null);
    getTenureAppProfDecs(forestFileId, r.tenureAppId, r.cuttingPermitId, r.hvaSkey)
      .then(setProfDecs)
      .catch((err: unknown) =>
        setProfDecError(err instanceof Error ? err.message : 'Request failed'),
      );
  };

  const cpLink = (cpId: string, text: string) => (
    <Link
      to={`/harvesting-authority/${encodeURIComponent(cpId)}?forestFileId=${encodeURIComponent(
        forestFileId,
      )}`}
    >
      {text}
    </Link>
  );

  const markCell = (r: TenureAppRow, cols: TenureAppColumns) => {
    if (!r.timberMark) return '—';
    if (cols.cpLinksToPermit && r.cuttingPermitId) return cpLink(r.cuttingPermitId, r.timberMark);
    // Legacy linked a non-timber file's mark to that mark's own file (FTA100).
    if (!(data?.fileTypeCode ?? '').startsWith('A'))
      return <Link to={`/tenures/${encodeURIComponent(r.timberMark)}`}>{r.timberMark}</Link>;
    return r.timberMark;
  };

  const applicationsTable = (rows: TenureAppRow[], cols: TenureAppColumns) => {
    const headers: string[] = [
      'Submission ID',
      'Submitted',
      'Org',
      'Application ID',
      ...(cols.status ? ['Status'] : []),
      ...(cols.applicationType ? ['Type'] : []),
      'Description',
      'Purpose',
      ...(cols.featureType ? ['Feature type'] : []),
      ...(cols.chart ? ['Chart ID', 'Chart block ID'] : []),
      ...(cols.cuttingPermit ? ['CP/HVA ID'] : []),
      ...(cols.timberMark ? ['Mark'] : []),
      ...(cols.location ? ['Location'] : []),
      ...(cols.pointOfCommencement ? ['PofC'] : []),
      ...(cols.chart ? ['Chart volume (m³)'] : []),
      ...(cols.length ? ['Length (km)'] : []),
      ...(cols.area ? ['Area (ha)'] : []),
      'Decision date',
      'Issue date',
      'SNC',
      'Exhibit A',
      'Prof dec',
    ];
    return (
      <Table size="md" useZebraStyles>
        <TableHead>
          <TableRow>
            {headers.map((h) => (
              <TableHeader key={h}>{h}</TableHeader>
            ))}
            <TableHeader aria-label="Actions" />
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.length === 0 ? (
            <TableRow>
              <TableCell colSpan={headers.length + 1} className="fsp-info__empty-cell">
                No spatial submissions for this tenure.
              </TableCell>
            </TableRow>
          ) : (
            rows.map((r, i) => (
              <TableRow key={rowKey(r, i)}>
                <TableCell>{dash(r.submissionId)}</TableCell>
                <TableCell>{date(r.submissionDate)}</TableCell>
                <TableCell title={r.orgUnitName ?? undefined}>{dash(r.orgUnitCode)}</TableCell>
                <TableCell>{dash(r.tenureAppId)}</TableCell>
                {cols.status && (
                  <TableCell>
                    {r.statusCode ? (
                      <StatusTag
                        status={r.statusDesc ?? r.statusCode}
                        variant={STATE_VARIANTS[r.statusCode]}
                      />
                    ) : (
                      '—'
                    )}
                  </TableCell>
                )}
                {cols.applicationType && (
                  <TableCell>{codeDesc(r.applicationTypeCode, r.applicationTypeDesc)}</TableCell>
                )}
                <TableCell>{dash(r.description)}</TableCell>
                <TableCell>{dash(r.purposeDesc)}</TableCell>
                {cols.featureType && <TableCell>{dash(r.featureTypeDesc)}</TableCell>}
                {cols.chart && <TableCell>{dash(r.chartAreaId)}</TableCell>}
                {cols.chart && <TableCell>{dash(r.chartBlockId)}</TableCell>}
                {cols.cuttingPermit && (
                  <TableCell>
                    {r.cuttingPermitId
                      ? cols.cpLinksToPermit
                        ? cpLink(r.cuttingPermitId, r.cuttingPermitId)
                        : r.cuttingPermitId
                      : '—'}
                  </TableCell>
                )}
                {cols.timberMark && <TableCell>{markCell(r, cols)}</TableCell>}
                {cols.location && <TableCell>{dash(r.location)}</TableCell>}
                {cols.pointOfCommencement && <TableCell>{dash(r.pointOfCommencement)}</TableCell>}
                {cols.chart && <TableCell>{num(r.chartVolume)}</TableCell>}
                {cols.length && <TableCell>{num(r.objectLength)}</TableCell>}
                {cols.area && <TableCell>{num(r.objectArea)}</TableCell>}
                <TableCell>{date(r.decisionDate)}</TableCell>
                <TableCell>{date(r.issuanceDate)}</TableCell>
                <TableCell>{yesNo(r.statusNotificationClearance)}</TableCell>
                <TableCell>{yesNo(r.exhibitAImage)}</TableCell>
                <TableCell>
                  {r.professionalDeclaration ? (
                    <Button kind="ghost" size="sm" onClick={() => openProfDec(r)}>
                      View
                    </Button>
                  ) : (
                    'No'
                  )}
                </TableCell>
                <TableCell>
                  <OverflowMenu
                    size="sm"
                    flipped
                    iconDescription={`Application ${r.tenureAppId ?? ''} actions`}
                  >
                    <OverflowMenuItem
                      itemText="Issue permit"
                      disabled={!canEdit || !r.issuePermitAllowed}
                      title={canEdit ? (r.issuePermitReason ?? undefined) : undefined}
                      onClick={() => openIssue(r)}
                    />
                    <OverflowMenuItem
                      itemText="Professional declaration"
                      disabled={!r.professionalDeclaration}
                      onClick={() => openProfDec(r)}
                    />
                  </OverflowMenu>
                </TableCell>
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    );
  };

  const profDec = profDecs?.[0];

  return (
    <div className="fsp-info__tab-panel">
      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Loading tenure applications…"
      >
        {data && <UnavailableNotice notices={data.unavailable} />}
        {data &&
          (data.applications.length === 0 &&
          data.cpRequests.length === 0 &&
          data.cpRejections.length === 0 ? (
            <EmptyState
              icon={<DocumentTasks size={48} />}
              title="No tenure applications for this tenure"
              body="Applications submitted through ESF for this file appear here."
            />
          ) : (
            <>
              {data.warning && (
                <InlineNotification
                  kind="warning"
                  lowContrast
                  hideCloseButton
                  title="Note"
                  subtitle={data.warning.replace(/^NOTE:\s*/, '')}
                />
              )}
              <Section title="Spatial submissions" icon={MapIcon} count={data.applications.length}>
                {applicationsTable(data.applications, data.columns)}
              </Section>
              {(data.cpRequests.length > 0 || (data.fileTypeCode ?? '').startsWith('A')) && (
                <Section
                  title="Smart form applications"
                  icon={Stamp}
                  count={data.cpRequests.length}
                >
                  <Table size="md" useZebraStyles>
                    <TableHead>
                      <TableRow>
                        <TableHeader>Cutting Permit ID</TableHeader>
                        <TableHeader>Submitted</TableHeader>
                        <TableHeader>Request type</TableHeader>
                        <TableHeader>State</TableHeader>
                        <TableHeader>Accepted by</TableHeader>
                        <TableHeader>Completion date</TableHeader>
                        <TableHeader>Rationale</TableHeader>
                        <TableHeader>Rationale doc</TableHeader>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {data.cpRequests.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={8} className="fsp-info__empty-cell">
                            No smart form applications for this tenure.
                          </TableCell>
                        </TableRow>
                      ) : (
                        data.cpRequests.map((q, i) => (
                          <TableRow key={`${q.requestGuid ?? ''}-${i}`}>
                            <TableCell>
                              {q.cuttingPermitId
                                ? cpLink(q.cuttingPermitId, q.cuttingPermitId)
                                : '—'}
                            </TableCell>
                            <TableCell>{date(q.requestDate)}</TableCell>
                            <TableCell>{codeDesc(q.requestCode, q.requestDesc)}</TableCell>
                            <TableCell>
                              {q.statusCode ? (
                                <StatusTag status={q.statusDesc ?? q.statusCode} />
                              ) : (
                                '—'
                              )}
                            </TableCell>
                            <TableCell>
                              <UserName userId={q.acceptedUserId} />
                            </TableCell>
                            <TableCell>{date(q.acceptedDate)}</TableCell>
                            <TableCell className="detail-tab__long-text">
                              {dash(q.rationaleDetail)}
                            </TableCell>
                            <TableCell>{yesNo(q.rationaleDocument)}</TableCell>
                          </TableRow>
                        ))
                      )}
                    </TableBody>
                  </Table>
                </Section>
              )}
              {(data.cpRejections.length > 0 || (data.fileTypeCode ?? '').startsWith('A')) && (
                <Section
                  title="CP submission rejections"
                  icon={Misuse}
                  count={data.cpRejections.length}
                >
                  <Table size="md" useZebraStyles>
                    <TableHead>
                      <TableRow>
                        <TableHeader>Cutting Permit ID</TableHeader>
                        <TableHeader>Submission ID</TableHeader>
                        <TableHeader>Submitted</TableHeader>
                        <TableHeader>Rejected</TableHeader>
                        <TableHeader>Rejection message</TableHeader>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {data.cpRejections.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={5} className="fsp-info__empty-cell">
                            No rejected CP submissions for this tenure.
                          </TableCell>
                        </TableRow>
                      ) : (
                        data.cpRejections.map((j, i) => (
                          <TableRow key={`${j.submissionId ?? ''}-${i}`}>
                            <TableCell>{dash(j.cuttingPermitId)}</TableCell>
                            <TableCell>{dash(j.submissionId)}</TableCell>
                            <TableCell>{date(j.submissionDate)}</TableCell>
                            <TableCell>{date(j.rejectionDate)}</TableCell>
                            <TableCell className="detail-tab__long-text">
                              {dash(j.rejectionMessage)}
                            </TableCell>
                          </TableRow>
                        ))
                      )}
                    </TableBody>
                  </Table>
                </Section>
              )}
            </>
          ))}
      </AsyncBoundary>

      {/* Issue permit — legacy's Issue Permit popup, without its DM upload and e-mail. */}
      <Modal
        open={issuing !== null}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading="Issue permit"
        onRequestClose={closeIssue}
        preventCloseOnClickOutside
      >
        <Stack gap={5}>
          <p className="detail-dialog__subtitle">
            All fields are required unless marked optional. Issuing records the permit document
            against application {issuing?.tenureAppId}
            {issuing?.cuttingPermitId ? ` (CP ${issuing.cuttingPermitId})` : ''}, sets it to Issued
            and the tenure
            {issuing?.hvaSkey ? ' and its cutting permit' : ''} to HI.
          </p>
          <TextInput
            id="tenureapp-document-uri"
            labelText="Permit document link"
            helperText="Link to the issued permit PDF in document management."
            value={documentUri}
            maxLength={MAX_URI}
            invalid={!!uriError}
            invalidText={uriError}
            disabled={saving}
            onChange={(e) => {
              setDocumentUri(e.target.value);
              setUriError(undefined);
            }}
          />
        </Stack>
        <div className="detail-dialog__actions">
          <Button kind="tertiary" disabled={saving} onClick={closeIssue}>
            Cancel
          </Button>
          <Button kind="primary" disabled={saving} onClick={() => void submitIssue()}>
            {saving ? 'Issuing…' : 'Issue permit'}
          </Button>
        </div>
      </Modal>

      {/* Professional declaration — legacy's Prof Dec popup. */}
      <Modal
        open={profDecFor !== null}
        passiveModal
        size="md"
        className="detail-dialog"
        modalHeading="Professional declaration"
        onRequestClose={() => setProfDecFor(null)}
      >
        {profDecError ? (
          <p className="detail-tab__reason">{profDecError}</p>
        ) : !profDecs ? (
          <p className="detail-tab__reason">Loading…</p>
        ) : !profDec ? (
          <p className="detail-tab__reason">No professional declaration found.</p>
        ) : (
          <dl className="fsp-info__field-list">
            {(
              [
                [
                  'Declaration type',
                  codeDesc(profDec.declarationTypeCode, profDec.declarationTypeDesc),
                ],
                ['Declaration date', date(profDec.declarationDate)],
                ['Tenure', dash(profDec.forestFileId)],
                ['Cutting Permit ID', dash(profDecFor?.cuttingPermitId)],
                ['Cut block ID', dash(profDec.cutBlockIds)],
                ['Submission ID', dash(profDec.submissionId)],
                [
                  'Licensee',
                  dash(
                    [profDec.clientNumber, profDec.clientLocationCode].filter(Boolean).join(' '),
                  ),
                ],
                ['Name', dash(profDec.clientName)],
                ['Declarant name', dash(profDec.declarantName)],
                ['Identifier', dash(profDec.certificationReferenceId)],
                ['Designation', dash(profDec.professionalIdentifierCode)],
                ['Declarant comments', dash(profDec.declarantComments)],
              ] as [string, ReactNode][]
            ).map(([label, value]) => (
              <div
                key={label}
                className={`fsp-info__field${label === 'Declarant comments' ? ' fsp-info__field--wide' : ''}`}
              >
                <dt>{label}</dt>
                <dd>{value}</dd>
              </div>
            ))}
          </dl>
        )}
        <div className="detail-dialog__actions">
          <Button kind="primary" onClick={() => setProfDecFor(null)}>
            Close
          </Button>
        </div>
      </Modal>
    </div>
  );
};

export default TenureApplicationPanel;
