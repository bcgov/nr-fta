import { Add, DocumentAdd } from '@carbon/icons-react';
import {
  Button,
  Select,
  SelectItem,
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
import { useEffect, useRef, useState, type FC } from 'react';

import { EmptyState } from '@/components/EmptyState/EmptyState';
import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import { getLandDistricts, getPrimaryIds, type CodeOption } from '@/services/codeLists';
import { addLandIndex, type MarkLandIndex } from '@/services/mark_detail';
import { formatDate } from '@/utils/formatDate';

const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;

/** The legacy add row's description maxlength. */
const MAX_DESC = 40;

interface Props {
  /** The detail route's id: a timber mark, or a certificate with `byCertificate`. */
  id: string;
  byCertificate: boolean;
  rows: MarkLandIndex[];
  /** Whether the user may add (the mark's `editRules.landIndex`). */
  canAdd: boolean;
  /** Called after an add, to re-read the mark. */
  onAdded: () => void;
}

/**
 * The Land index tab of the private-mark detail — legacy FTA511. Laid out as
 * nr-fsp-new's Attachments tab: an empty state that invites the first entry, or
 * the table with an Add button above it, and a small modal for the addition.
 *
 * Who may add, and when, is the backend's `editRules.landIndex` (FTA_511: not at
 * HX, DV or DD, not for B15/B16, Headquarters only); the backend enforces it too.
 */
const LandIndexPanel: FC<Props> = ({ id, byCertificate, rows, canAdd, onAdded }) => {
  const { display } = useNotification();
  const [open, setOpen] = useState(false);
  const [primary, setPrimary] = useState('');
  const [secondary, setSecondary] = useState('');
  const [description, setDescription] = useState('');
  const [showValidation, setShowValidation] = useState(false);
  const [saving, setSaving] = useState(false);
  const [districts, setDistricts] = useState<CodeOption[]>([]);
  const [primaryIds, setPrimaryIds] = useState<CodeOption[]>([]);
  const [listsLoading, setListsLoading] = useState(false);

  // The modal's lists, once it opens (cached app-wide).
  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setListsLoading(true);
    Promise.all([getLandDistricts(), getPrimaryIds()])
      .then(([d, p]) => {
        if (cancelled) return;
        setDistricts(d);
        setPrimaryIds(p);
      })
      .catch(() => {
        if (!cancelled) {
          display({
            kind: 'error',
            title: 'Could not load the land index lists',
            subtitle: 'Close the dialog and try again.',
            timeout: 6000,
          });
        }
      })
      .finally(() => {
        if (!cancelled) setListsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [open, display]);

  // Land District/Island takes the focus when the dialog opens and after "Add
  // additional" — once it's enabled again, as it's disabled while loading or
  // saving. Retried briefly: the dialog can't take focus until it has faded in.
  const primaryRef = useRef<HTMLSelectElement>(null);
  const [focusPrimary, setFocusPrimary] = useState(false);
  useEffect(() => {
    if (!open || !focusPrimary || saving || listsLoading) return;
    let tries = 0;
    const timer = setInterval(() => {
      const el = primaryRef.current;
      el?.focus();
      if ((el && document.activeElement === el) || ++tries >= 20) {
        clearInterval(timer);
        setFocusPrimary(false);
      }
    }, 50);
    return () => clearInterval(timer);
  }, [open, focusPrimary, saving, listsLoading]);

  const resetForm = () => {
    setPrimary('');
    setSecondary('');
    setDescription('');
    setShowValidation(false);
  };

  const openDialog = () => {
    resetForm();
    setFocusPrimary(true);
    setOpen(true);
  };

  const closeDialog = () => {
    if (!saving) setOpen(false);
  };

  // `another` keeps the dialog open, emptied for the next one.
  const submit = async (another: boolean) => {
    if (!primary) {
      setShowValidation(true);
      return;
    }
    setSaving(true);
    try {
      await addLandIndex(
        id,
        {
          primaryLandIndexCode: primary,
          secondaryLandIndexCode: secondary || null,
          markLandIndexDesc: description.trim() || null,
        },
        byCertificate,
      );
      display({ kind: 'success', title: 'Land index added', timeout: 5000 });
      if (another) {
        resetForm();
        setFocusPrimary(true);
      } else setOpen(false);
      onAdded();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not add the land index',
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
          icon={<DocumentAdd size={48} />}
          title="No land index for this mark"
          body="Add the land district and primary ID the timber mark may be used on."
          action={
            // Always shown; disabled when adding isn't allowed.
            <div className="detail-tab__empty-action">
              <Button kind="primary" renderIcon={Add} disabled={!canAdd} onClick={openDialog}>
                Add land index
              </Button>
            </div>
          }
        />
      ) : (
        <div>
          <header className="detail-tab__actions">
            <Button
              kind="tertiary"
              size="sm"
              renderIcon={Add}
              disabled={!canAdd}
              onClick={openDialog}
            >
              Add land index
            </Button>
          </header>
          <div className="bordered-table">
            <TableContainer>
              <Table size="md" useZebraStyles>
                <TableHead>
                  <TableRow>
                    <TableHeader>Land District/Island</TableHeader>
                    <TableHeader>Primary ID</TableHeader>
                    <TableHeader>Description</TableHeader>
                    <TableHeader>Deactivate Date</TableHeader>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((r) => (
                    <TableRow
                      key={
                        r.markLandIndexSkey ??
                        `${r.primaryLandIndexCode}-${r.secondaryLandIndexCode}-${r.markLandIndexDesc}`
                      }
                    >
                      <TableCell>
                        {dash(r.primaryLandIndexCodeDesc ?? r.primaryLandIndexCode)}
                      </TableCell>
                      <TableCell>
                        {/* The "code - desc" concatenation is " - " alone without a code. */}
                        {r.secondaryLandIndexCode
                          ? dash(r.secondaryLandIndexCodeDesc ?? r.secondaryLandIndexCode)
                          : '—'}
                      </TableCell>
                      <TableCell>{dash(r.markLandIndexDesc)}</TableCell>
                      <TableCell>{dash(formatDate(r.indexDeactivateDate))}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </div>
        </div>
      )}

      {/* Add dialog — nr-fsp-new's Add attachment shape: passive, small, a
          stacked form and its own Cancel / primary pair. */}
      <Modal
        open={open}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading="Add land index"
        onRequestClose={closeDialog}
        preventCloseOnClickOutside
      >
        <Stack gap={5}>
          <p className="detail-dialog__subtitle">All fields are required unless marked optional.</p>
          <Select
            id="land-index-primary"
            ref={primaryRef}
            labelText="Land District/Island"
            value={primary}
            invalid={showValidation && !primary}
            invalidText="Land District/Island is required."
            disabled={saving || listsLoading}
            onChange={(e) => setPrimary(e.target.value)}
          >
            <SelectItem value="" text={listsLoading ? 'Loading…' : '— Select land district —'} />
            {districts.map((o) => (
              <SelectItem key={o.code} value={o.code} text={o.description || o.code} />
            ))}
          </Select>
          <Select
            id="land-index-secondary"
            labelText="Primary ID (optional)"
            value={secondary}
            disabled={saving || listsLoading}
            onChange={(e) => setSecondary(e.target.value)}
          >
            <SelectItem value="" text={listsLoading ? 'Loading…' : '— None —'} />
            {primaryIds.map((o) => (
              <SelectItem key={o.code} value={o.code} text={o.description || o.code} />
            ))}
          </Select>
          <TextInput
            id="land-index-description"
            labelText="Description (optional)"
            value={description}
            maxLength={MAX_DESC}
            helperText={`Up to ${MAX_DESC} characters, e.g. a parcel or lot.`}
            disabled={saving}
            onChange={(e) => setDescription(e.target.value)}
          />
        </Stack>
        <div className="detail-dialog__actions">
          <Button kind="tertiary" disabled={saving} onClick={closeDialog}>
            Cancel
          </Button>
          <Button kind="secondary" disabled={saving} onClick={() => void submit(true)}>
            Add additional
          </Button>
          <Button kind="primary" disabled={saving} onClick={() => void submit(false)}>
            {saving ? 'Adding…' : 'Add land index'}
          </Button>
        </div>
      </Modal>
    </div>
  );
};

export default LandIndexPanel;
