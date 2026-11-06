import {
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
  TextArea,
} from '@carbon/react';
import { useState, type FC } from 'react';
import { Link } from 'react-router-dom';

import ConfirmationModal from '@/components/ConfirmationModal';
import DefinitionGrid from '@/components/DefinitionGrid';
import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import CommitmentHistoryView from '@/pages/apt/CommitmentHistory/CommitmentHistoryView';
import { aptPaths } from '@/routes/aptPaths';
import {
  COMMITMENT_REASON_MAX,
  deleteAptCommitment,
  type AptCommitment,
} from '@/services/apt/commitments';
import { formatDate } from '@/utils/formatDate';

const nf = new Intl.NumberFormat('en-CA');
const dash = (v: string | null | undefined) => (v && v.trim() ? v : '—');
const num = (v: number | null | undefined) => (v == null ? '—' : nf.format(v));

/** Total AAC, and the part of it counted towards the licences' AAC — APT2's two totals. */
function commitmentTotals(rows: AptCommitment[]) {
  return rows.reduce(
    (t, c) => {
      const aac = c.allowableAnnualCut ?? 0;
      t.total += aac;
      if (c.includeInLicAac === 'Y') t.included += aac;
      return t;
    },
    { total: 0, included: 0 },
  );
}

interface Props {
  rows: AptCommitment[];
  /**
   * `apportionment` lists each commitment's licence (APT2's APTM6000); `licence` its
   * partition (APT2's APTM4000).
   */
  variant: 'apportionment' | 'licence';
  /** Whether Edit and Delete are offered — an APT administrator only. */
  canEdit: boolean;
  onEdit: (c: AptCommitment) => void;
  /** Called after a delete, to re-read the list. */
  onDeleted: () => void;
}

/**
 * A list of commitments with APT2's totals beneath, and each row's History, Edit and Delete.
 * History opens the journal in a dialog, as APT2 did; its full page is linked from there.
 */
const CommitmentsTable: FC<Props> = ({ rows, variant, canEdit, onEdit, onDeleted }) => {
  const { display } = useNotification();
  const [historyOf, setHistoryOf] = useState<AptCommitment | null>(null);
  const [deleting, setDeleting] = useState<AptCommitment | null>(null);
  const [reason, setReason] = useState('');
  const totals = commitmentTotals(rows);
  const reasonTooLong = reason.trim().length > COMMITMENT_REASON_MAX;

  return (
    <Stack gap={5}>
      <div className="bordered-table">
        <TableContainer>
          <Table size="md" useZebraStyles>
            <TableHead>
              <TableRow>
                {variant === 'apportionment' ? (
                  <>
                    <TableHeader>Forest File ID</TableHeader>
                    <TableHeader>Licensee</TableHeader>
                  </>
                ) : (
                  <TableHeader>Partition</TableHeader>
                )}
                <TableHeader>Type</TableHeader>
                <TableHeader>AAC (m³)</TableHeader>
                <TableHeader>Determination Date</TableHeader>
                <TableHeader>Effective Date</TableHeader>
                <TableHeader>Expiry Date</TableHeader>
                <TableHeader>Incl in AAC</TableHeader>
                <TableHeader aria-label="Actions" />
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((c) => (
                <TableRow key={c.id}>
                  {variant === 'apportionment' ? (
                    <>
                      <TableCell>
                        <Link to={aptPaths.licenceDetail(c.forestFileId)}>{c.forestFileId}</Link>
                      </TableCell>
                      <TableCell>{dash(c.clientName)}</TableCell>
                    </>
                  ) : (
                    <TableCell>{dash(c.partitionDescription ?? c.partitionCode)}</TableCell>
                  )}
                  <TableCell>{dash(c.type)}</TableCell>
                  <TableCell>{num(c.allowableAnnualCut)}</TableCell>
                  <TableCell>{dash(formatDate(c.determinationDate))}</TableCell>
                  <TableCell>{dash(formatDate(c.effectiveDate))}</TableCell>
                  <TableCell>{dash(formatDate(c.expiryDate))}</TableCell>
                  <TableCell>{c.includeInLicAac === 'Y' ? 'Yes' : 'No'}</TableCell>
                  <TableCell>
                    <OverflowMenu size="sm" flipped iconDescription={`Commitment ${c.id} actions`}>
                      <OverflowMenuItem itemText="History" onClick={() => setHistoryOf(c)} />
                      {canEdit && <OverflowMenuItem itemText="Edit" onClick={() => onEdit(c)} />}
                      {canEdit && (
                        <OverflowMenuItem
                          itemText="Delete"
                          isDelete
                          hasDivider
                          onClick={() => {
                            setReason('');
                            setDeleting(c);
                          }}
                        />
                      )}
                    </OverflowMenu>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </div>
      <DefinitionGrid
        items={[
          { label: 'Total AAC (m³)', value: nf.format(totals.total) },
          { label: 'Total Incl in AAC (m³)', value: nf.format(totals.included) },
        ]}
      />

      <Modal
        open={historyOf != null}
        passiveModal
        size="lg"
        className="detail-dialog"
        modalHeading={`Commitment history — ${historyOf?.forestFileId ?? ''}`}
        onRequestClose={() => setHistoryOf(null)}
      >
        {historyOf && (
          <Stack gap={5}>
            <CommitmentHistoryView commitmentId={historyOf.id} />
            <Link to={aptPaths.commitmentHistory(historyOf.id)}>Open as a page</Link>
          </Stack>
        )}
      </Modal>

      {canEdit && (
        <ConfirmationModal
          open={deleting != null}
          onClose={() => setDeleting(null)}
          heading="Delete commitment"
          confirmLabel="Delete"
          danger
          errorTitle="Could not delete the commitment"
          onConfirm={async () => {
            if (!deleting) return;
            if (reasonTooLong)
              throw new Error(`Reason cannot exceed ${COMMITMENT_REASON_MAX} characters.`);
            await deleteAptCommitment(deleting.id, reason);
            display({ kind: 'success', title: 'Commitment deleted', timeout: 5000 });
            onDeleted();
          }}
        >
          <Stack gap={5}>
            <p>
              Delete the {deleting?.type} commitment of {deleting?.forestFileId}? It and its history
              will be ignored for reporting. Only a commitment entered in error should be deleted.
            </p>
            <TextArea
              id="commitment-delete-reason"
              labelText="Reason (optional)"
              rows={3}
              maxCount={COMMITMENT_REASON_MAX}
              enableCounter
              value={reason}
              invalid={reasonTooLong}
              invalidText={`Reason cannot exceed ${COMMITMENT_REASON_MAX} characters.`}
              onChange={(e) => setReason(e.target.value)}
            />
          </Stack>
        </ConfirmationModal>
      )}
    </Stack>
  );
};

export default CommitmentsTable;
