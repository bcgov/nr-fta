import { Add, Edit } from '@carbon/icons-react';
import {
  Button,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TextArea,
  TextInput,
} from '@carbon/react';
import { useState, type FC } from 'react';

import { EmptyState } from '@/components/EmptyState/EmptyState';
import { Modal } from '@/components/Modal';
import { statusCodeVariant } from '@/components/StatusTag/statusCodes';
import StatusTag from '@/components/StatusTag/StatusTag';
import UserName from '@/components/UserName';
import { useNotification } from '@/context/notification/useNotification';
import { addAmendment, type MarkAmendment } from '@/services/mark_detail';
import { formatDate } from '@/utils/formatDate';

const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;

/** Fta512MarkAmendForm's limits; the backend enforces them too. */
const MAX_CHANGES = 2000;
const MAX_AREA = 9999.9;
const AREA_PATTERN = /^\d{1,4}(\.\d)?$/;

interface Errors {
  area?: string;
  changes?: string;
}

const validate = (area: string, changes: string): Errors => {
  const e: Errors = {};
  if (!changes.trim()) e.changes = 'Requested amendment changes are required.';
  else if (changes.trim().length > MAX_CHANGES) e.changes = `At most ${MAX_CHANGES} characters.`;
  const a = area.trim();
  if (a && (!AREA_PATTERN.test(a) || Number(a) > MAX_AREA))
    e.area = 'Enter 0 to 9999.9, with at most one decimal place.';
  return e;
};

interface Props {
  /** The detail route's id: a timber mark, or a certificate with `byCertificate`. */
  id: string;
  byCertificate: boolean;
  rows: MarkAmendment[];
  /** Whether the user may request one (the mark's `editRules.amendments`). */
  canAdd: boolean;
  /** Why not, when `canAdd` is false — shown beside the disabled button. */
  disabledReason: string | null;
  /** Called after an add, to re-read the mark. */
  onAdded: () => void;
}

/**
 * The Amendments tab of the private-mark detail — legacy FTA512. Laid out as the
 * Land index and Associated clients tabs: an empty state, or the history table
 * with a Request button above it, and a small modal for the request.
 *
 * Who may request, and when, is the backend's `editRules.amendments` (FTA_512:
 * an HI mark with no amendment outstanding, not B15/B16). A request is saved as
 * PI; approving it is the Mark application tab's amendment status, and Print
 * issues it.
 */
const AmendmentsPanel: FC<Props> = ({
  id,
  byCertificate,
  rows,
  canAdd,
  disabledReason,
  onAdded,
}) => {
  const { display } = useNotification();
  const [open, setOpen] = useState(false);
  const [area, setArea] = useState('');
  const [changes, setChanges] = useState('');
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);

  const openDialog = () => {
    setArea('');
    setChanges('');
    setErrors({});
    setOpen(true);
  };

  const closeDialog = () => {
    if (!saving) setOpen(false);
  };

  const submit = async () => {
    const found = validate(area, changes);
    if (found.area || found.changes) {
      setErrors(found);
      return;
    }
    setSaving(true);
    try {
      await addAmendment(
        id,
        {
          permitBlockArea: area.trim() ? Number(area.trim()) : null,
          requestedChanges: changes.trim(),
        },
        byCertificate,
      );
      display({
        kind: 'success',
        title: 'Amendment requested',
        subtitle: 'Saved as PI - Pending Issuance.',
        timeout: 5000,
      });
      setOpen(false);
      onAdded();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not request the amendment',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fsp-info__tab-panel">
      {rows.length === 0 ? (
        <EmptyState
          icon={<Edit size={48} />}
          title="No amendments for this mark"
          body="Request an amendment to change an issued mark's area or legal description."
          action={
            // Always shown; disabled, with the reason, when a request isn't allowed.
            <div className="detail-tab__empty-action">
              <Button kind="primary" renderIcon={Add} disabled={!canAdd} onClick={openDialog}>
                Request amendment
              </Button>
              {!canAdd && disabledReason && <p className="detail-tab__reason">{disabledReason}</p>}
            </div>
          }
        />
      ) : (
        <div>
          <header className="detail-tab__actions">
            {!canAdd && disabledReason && <p className="detail-tab__reason">{disabledReason}</p>}
            <Button
              kind="tertiary"
              size="sm"
              renderIcon={Add}
              disabled={!canAdd}
              onClick={openDialog}
            >
              Request amendment
            </Button>
          </header>
          <div className="bordered-table">
            <TableContainer>
              <Table size="md" useZebraStyles>
                <TableHead>
                  <TableRow>
                    <TableHeader>Request Date</TableHeader>
                    <TableHeader>Status</TableHeader>
                    <TableHeader>Requested By</TableHeader>
                    <TableHeader>Area (ha)</TableHeader>
                    <TableHeader>Requested Changes</TableHeader>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((a, i) => (
                    <TableRow key={`${a.amendRequestDate ?? 'amd'}-${i}`}>
                      <TableCell>{dash(formatDate(a.amendRequestDate))}</TableCell>
                      <TableCell>
                        {a.prvMrkAmdStsSt ? (
                          <StatusTag
                            status={a.prvMrkAmdStsDesc ?? a.prvMrkAmdStsSt}
                            variant={statusCodeVariant(a.prvMrkAmdStsSt)}
                          />
                        ) : (
                          '—'
                        )}
                      </TableCell>
                      <TableCell>
                        <UserName userId={a.requestingUserid} />
                      </TableCell>
                      <TableCell>
                        {a.permitBlockArea === null ? '—' : a.permitBlockArea.toFixed(1)}
                      </TableCell>
                      <TableCell className="detail-tab__long-text">
                        {dash(a.requestedChanges)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </div>
        </div>
      )}

      {/* Request dialog — the Land index dialog's shape. */}
      <Modal
        open={open}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading="Request amendment"
        onRequestClose={closeDialog}
        preventCloseOnClickOutside
      >
        <Stack gap={5}>
          <p className="detail-dialog__subtitle">
            All fields are required unless marked optional. The request is saved as PI - Pending
            Issuance.
          </p>
          <TextInput
            id="amendment-area"
            labelText="Requested area (ha) (optional)"
            inputMode="decimal"
            value={area}
            helperText="0 to 9999.9, one decimal place."
            invalid={!!errors.area}
            invalidText={errors.area}
            disabled={saving}
            onChange={(e) => {
              setArea(e.target.value);
              if (errors.area) setErrors((prev) => ({ ...prev, area: undefined }));
            }}
          />
          <TextArea
            id="amendment-changes"
            labelText="Requested amendment changes"
            rows={6}
            value={changes}
            maxCount={MAX_CHANGES}
            enableCounter
            helperText="Saved in upper case, as in the legacy system."
            invalid={!!errors.changes}
            invalidText={errors.changes}
            disabled={saving}
            onChange={(e) => {
              setChanges(e.target.value);
              if (errors.changes) setErrors((prev) => ({ ...prev, changes: undefined }));
            }}
          />
        </Stack>
        <div className="detail-dialog__actions">
          <Button kind="tertiary" disabled={saving} onClick={closeDialog}>
            Cancel
          </Button>
          <Button kind="primary" disabled={saving} onClick={() => void submit()}>
            {saving ? 'Requesting…' : 'Request amendment'}
          </Button>
        </div>
      </Modal>
    </div>
  );
};

export default AmendmentsPanel;
