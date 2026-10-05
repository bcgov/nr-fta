import { Add, DocumentMultiple_01 as DocumentMultiple } from '@carbon/icons-react';
import {
  Button,
  DatePicker,
  DatePickerInput,
  OverflowMenu,
  OverflowMenuItem,
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
import { useCallback, useEffect, useState, type FC } from 'react';
import { Link } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import { getFileSources, type CodeOption } from '@/services/codeLists';
import {
  addTenureAssociatedFile,
  deleteTenureAssociatedFile,
  getFileAssociationTypes,
  getTenureAssociatedFiles,
  type TenureAssociatedFile,
} from '@/services/tenure_assocfiles';
import { formatDate } from '@/utils/formatDate';

import type { TenurePanelProps } from './panelProps';

const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;

/** yyyy-mm-dd in local time. */
const toIsoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

/** Legacy FTA910's input width. */
const MAX_FILE_ID = 25;
/** The FTAS source — the associated file is an FTA forest file. */
const FTAS = 'F';
/** The one association type that takes an end date. */
const AAC = 'AAC';

interface Form {
  associatedFileId: string;
  source: string;
  type: string;
  endDate: string;
}

type Errors = Partial<Record<keyof Form, string>>;

const EMPTY: Form = { associatedFileId: '', source: '', type: '', endDate: '' };

/** Fta910AssocFileForm's checks, with its texts; the backend repeats them and the PL/SQL's. */
const validate = (
  f: Form,
  forestFileId: string,
  awardDate: string | null,
  expiryDate: string | null,
): Errors => {
  const e: Errors = {};
  const id = f.associatedFileId.trim().toUpperCase();
  if (!id) e.associatedFileId = 'Associated File is mandatory.';
  else if (id === forestFileId.toUpperCase())
    e.associatedFileId = 'File may not be associated with itself.';
  if (!f.source) e.source = 'Source is mandatory.';
  if (f.type && f.source !== FTAS)
    e.type = 'Source must be Forest Tenure System for an Association Type to be present.';
  if (f.endDate) {
    if (f.type !== AAC)
      e.endDate = 'Association Type must be AAC for an Association End Date to be present.';
    else if ((awardDate && f.endDate < awardDate) || (expiryDate && f.endDate > expiryDate))
      e.endDate = "Must be within this tenure's term (and the associated tenure's).";
  }
  return e;
};

/**
 * The Associated files tab of the tenure detail — legacy FTA910 (Associated Files). Laid
 * out as the other tabs: an empty state, or the table with an Add button above it, a dialog
 * to add, and a row menu to delete. Legacy never edits an association (its fields are its
 * key), so there is no Edit.
 *
 * An FTA file (source F) links to its tenure page — legacy's Details button. The list also
 * shows the FTAS associations other files made to this one; deleting one of those removes
 * it in both directions, as legacy did.
 */
const AssociatedFilesPanel: FC<TenurePanelProps> = ({ tenure, canEdit }) => {
  const forestFileId = tenure.forestFileId;
  const { display } = useNotification();
  const fetcher = useCallback(() => getTenureAssociatedFiles(forestFileId), [forestFileId]);
  const { data, loading, error, reload } = useApiResource(fetcher, [forestFileId]);

  const allowed = canEdit && !!data?.canAdd;
  // A business reason only — a role that can't write just sees the button disabled.
  const disabledReason = canEdit && data && !data.canAdd ? data.addReason : null;

  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<Form>(EMPTY);
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);
  const [lists, setLists] = useState<{ sources: CodeOption[]; types: CodeOption[] } | null>(null);
  const [pendingDelete, setPendingDelete] = useState<TenureAssociatedFile | null>(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    if (!open || lists) return;
    let cancelled = false;
    Promise.all([getFileSources(), getFileAssociationTypes()])
      .then(([sources, types]) => {
        if (!cancelled) setLists({ sources, types });
      })
      .catch(() => {
        if (!cancelled) {
          display({
            kind: 'error',
            title: 'Could not load the source and association type lists',
            subtitle: 'Close the dialog and try again.',
            timeout: 6000,
          });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [open, lists, display]);

  const set = <K extends keyof Form>(key: K, value: Form[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    setErrors((prev) => {
      if (!prev[key]) return prev;
      const next = { ...prev };
      delete next[key];
      return next;
    });
  };

  const openDialog = () => {
    setForm(EMPTY);
    setErrors({});
    setOpen(true);
  };

  const closeDialog = () => {
    if (!saving) setOpen(false);
  };

  const submit = async () => {
    const found = validate(form, forestFileId, tenure.awardDate, tenure.expiryDate);
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    setSaving(true);
    try {
      const id = form.associatedFileId.trim().toUpperCase();
      const { warning } = await addTenureAssociatedFile(forestFileId, {
        associatedFileId: id,
        fileSourceCode: form.source,
        fileAssociationTypeCode: form.type || null,
        associationEndDate: form.endDate || null,
      });
      display({
        kind: warning ? 'warning' : 'success',
        title: `File ${id} associated`,
        subtitle: warning ?? undefined,
        timeout: warning ? 9000 : 6000,
      });
      setOpen(false);
      reload();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not add the association',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  const confirmDelete = async () => {
    if (!pendingDelete) return;
    setDeleting(true);
    try {
      await deleteTenureAssociatedFile(forestFileId, pendingDelete);
      display({
        kind: 'success',
        title: `Association with ${pendingDelete.associatedFileId} deleted`,
        timeout: 6000,
      });
      setPendingDelete(null);
      reload();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not delete the association',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setDeleting(false);
    }
  };

  const listsLoading = open && !lists;
  const ftas = form.source === FTAS;
  const aac = form.type === AAC;

  const addButton = (primary: boolean) => (
    <Button
      kind={primary ? 'primary' : 'tertiary'}
      size={primary ? undefined : 'sm'}
      renderIcon={Add}
      disabled={!allowed}
      onClick={openDialog}
    >
      Add associated file
    </Button>
  );

  const rows = data?.rows;

  return (
    <div className="fsp-info__tab-panel">
      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Loading associated files…"
      >
        {rows &&
          (rows.length === 0 ? (
            <EmptyState
              icon={<DocumentMultiple size={48} />}
              title="No associated files for this tenure"
              body="Associate another tenure, or a file from another system, with this one."
              action={
                <div className="detail-tab__empty-action">
                  {addButton(true)}
                  {disabledReason && <p className="detail-tab__reason">{disabledReason}</p>}
                </div>
              }
            />
          ) : (
            <div>
              <header className="detail-tab__actions">
                {disabledReason && <p className="detail-tab__reason">{disabledReason}</p>}
                {addButton(false)}
              </header>
              <div className="bordered-table">
                <TableContainer>
                  <Table size="md" useZebraStyles>
                    <TableHead>
                      <TableRow>
                        <TableHeader>Associated File</TableHeader>
                        <TableHeader>Source</TableHeader>
                        <TableHeader>Association Type</TableHeader>
                        <TableHeader>Association End Date</TableHeader>
                        <TableHeader aria-label="Actions" />
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {rows.map((r) => (
                        <TableRow key={`${r.associatedFileId}-${r.fileSourceCode}`}>
                          <TableCell>
                            {r.tenure ? (
                              <Link to={`/tenures/${encodeURIComponent(r.associatedFileId)}`}>
                                {r.associatedFileId}
                              </Link>
                            ) : (
                              r.associatedFileId
                            )}
                          </TableCell>
                          <TableCell>{dash(r.fileSourceDesc ?? r.fileSourceCode)}</TableCell>
                          <TableCell>{dash(r.fileAssociationTypeDesc)}</TableCell>
                          <TableCell>{dash(formatDate(r.associationEndDate))}</TableCell>
                          <TableCell>
                            <OverflowMenu
                              size="sm"
                              flipped
                              iconDescription={`Association with ${r.associatedFileId} actions`}
                            >
                              <OverflowMenuItem
                                itemText="Delete"
                                isDelete
                                disabled={!canEdit}
                                onClick={() => setPendingDelete(r)}
                              />
                            </OverflowMenu>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </div>
            </div>
          ))}
      </AsyncBoundary>

      {/* Add dialog. */}
      <Modal
        open={open}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading="Add associated file"
        onRequestClose={closeDialog}
        preventCloseOnClickOutside
      >
        <Stack gap={5}>
          <p className="detail-dialog__subtitle">All fields are required unless marked optional.</p>
          <div className="detail-dialog__pair">
            <TextInput
              id="assoc-file-id"
              labelText="Associated File"
              value={form.associatedFileId}
              maxLength={MAX_FILE_ID}
              helperText={ftas ? 'An FTA forest file ID.' : undefined}
              invalid={!!errors.associatedFileId}
              invalidText={errors.associatedFileId}
              disabled={saving}
              onChange={(e) => set('associatedFileId', e.target.value.toUpperCase())}
            />
            <Select
              id="assoc-file-source"
              labelText="Source"
              value={form.source}
              invalid={!!errors.source}
              invalidText={errors.source}
              disabled={saving || listsLoading}
              onChange={(e) => {
                set('source', e.target.value);
                // Legacy: a type (and so an end date) only with the FTAS source.
                if (e.target.value !== FTAS) {
                  set('type', '');
                  set('endDate', '');
                }
              }}
            >
              <SelectItem value="" text={listsLoading ? 'Loading…' : '— Select source —'} />
              {(lists?.sources ?? []).map((o) => (
                <SelectItem key={o.code} value={o.code} text={o.description || o.code} />
              ))}
            </Select>
          </div>
          <Select
            id="assoc-file-type"
            labelText="Association Type (optional)"
            value={form.type}
            helperText={!ftas ? 'Only for the Forest Tenure System source.' : undefined}
            invalid={!!errors.type}
            invalidText={errors.type}
            disabled={saving || listsLoading || !ftas}
            onChange={(e) => {
              set('type', e.target.value);
              if (e.target.value !== AAC) set('endDate', '');
            }}
          >
            <SelectItem value="" text={listsLoading ? 'Loading…' : '— None —'} />
            {(lists?.types ?? []).map((o) => (
              <SelectItem key={o.code} value={o.code} text={o.description || o.code} />
            ))}
          </Select>
          <DatePicker
            datePickerType="single"
            dateFormat="Y-m-d"
            className="detail-dialog__date"
            value={form.endDate}
            invalid={!!errors.endDate}
            onChange={(dates: Date[]) => set('endDate', dates[0] ? toIsoDate(dates[0]) : '')}
          >
            <DatePickerInput
              id="assoc-file-end"
              labelText="Association End Date (optional)"
              placeholder="yyyy-mm-dd"
              helperText={
                aac ? 'Within the terms of both tenures.' : 'Only for an AAC association.'
              }
              invalidText={errors.endDate}
              disabled={saving || !aac}
              onChange={(e) => {
                if (e.target.value.trim() === '') set('endDate', '');
              }}
            />
          </DatePicker>
        </Stack>
        <div className="detail-dialog__actions">
          <Button kind="tertiary" disabled={saving} onClick={closeDialog}>
            Cancel
          </Button>
          <Button kind="primary" disabled={saving} onClick={() => void submit()}>
            {saving ? 'Adding…' : 'Add associated file'}
          </Button>
        </div>
      </Modal>

      {/* Delete confirmation. */}
      <Modal
        open={!!pendingDelete}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading="Delete association?"
        onRequestClose={() => {
          if (!deleting) setPendingDelete(null);
        }}
        preventCloseOnClickOutside
      >
        {pendingDelete && (
          <p className="detail-dialog__subtitle">
            {pendingDelete.tenure
              ? `This removes the association between ${forestFileId} and ${pendingDelete.associatedFileId}, from both files.`
              : `This removes the association with ${pendingDelete.associatedFileId} (${
                  pendingDelete.fileSourceDesc ?? pendingDelete.fileSourceCode
                }).`}
          </p>
        )}
        <div className="detail-dialog__actions">
          <Button kind="tertiary" disabled={deleting} onClick={() => setPendingDelete(null)}>
            Cancel
          </Button>
          <Button kind="danger" disabled={deleting} onClick={() => void confirmDelete()}>
            {deleting ? 'Deleting…' : 'Delete'}
          </Button>
        </div>
      </Modal>
    </div>
  );
};

export default AssociatedFilesPanel;
