import { Add, TrashCan } from '@carbon/icons-react';
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
  TextArea,
} from '@carbon/react';
import { useCallback, useEffect, useState, type FC } from 'react';

import AsyncBoundary from '@/components/AsyncBoundary';
import ConfirmationModal from '@/components/ConfirmationModal';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import type { CodeOption } from '@/services/codeLists';
import {
  addPermitCondition,
  deletePermitCondition,
  getPermitConditions,
  getPermitLookups,
  type PermitCondition,
} from '@/services/cutting_permit_detail';

const MAX_DESCRIPTION = 3000;

interface Props {
  hvaSkey: number;
  canManage: boolean;
}

/**
 * FTA902's Permit Issuance Conditions. Legacy has no Update for one, only Save (of a new one) and
 * Delete. FTA_902_CP_CONDITIONS requires a description when the theme is OTHER.
 */
const PermitConditions: FC<Props> = ({ hvaSkey, canManage }) => {
  const { display } = useNotification();
  const fetcher = useCallback(() => getPermitConditions(hvaSkey), [hvaSkey]);
  const { data: rows, loading, error, reload } = useApiResource(fetcher, [hvaSkey]);

  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [themes, setThemes] = useState<CodeOption[] | null>(null);
  const [theme, setTheme] = useState('');
  const [description, setDescription] = useState('');
  const [invalid, setInvalid] = useState<{ theme?: string; description?: string }>({});
  const [deleting, setDeleting] = useState<PermitCondition | null>(null);

  useEffect(() => {
    if (!open || themes) return;
    let cancelled = false;
    getPermitLookups()
      .then((l) => {
        if (!cancelled) setThemes(l.conditionThemes);
      })
      .catch((err) => {
        if (cancelled) return;
        display({
          kind: 'error',
          title: 'Could not load the conditions list',
          subtitle: err instanceof Error ? err.message : 'Request failed',
          timeout: 9000,
        });
      });
    return () => {
      cancelled = true;
    };
  }, [open, themes, display]);

  const openDialog = () => {
    setTheme('');
    setDescription('');
    setInvalid({});
    setOpen(true);
  };

  const submit = async () => {
    const found: typeof invalid = {};
    if (!theme) found.theme = 'Permit Issuance Condition is required.';
    if (theme === 'OTHER' && !description.trim())
      found.description = 'Condition Description is required for OTHER.';
    if (found.theme || found.description) {
      setInvalid(found);
      return;
    }
    setSaving(true);
    try {
      await addPermitCondition(hvaSkey, {
        themeCode: theme,
        description: description.trim() || null,
      });
      display({ kind: 'success', title: 'Condition added', timeout: 5000 });
      setOpen(false);
      reload();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not add the condition',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  const addButton = (
    <Button size="md" kind="primary" renderIcon={Add} disabled={!canManage} onClick={openDialog}>
      Add condition
    </Button>
  );

  return (
    <div className="fsp-info__tab-panel">
      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Loading conditions…"
      >
        {rows &&
          (rows.length === 0 ? (
            <EmptyState
              icon={<Add size={48} />}
              title="No permit issuance conditions"
              body="Conditions added to this permit appear here."
              action={<div className="detail-tab__empty-action">{addButton}</div>}
            />
          ) : (
            <div>
              <header className="detail-tab__actions">{addButton}</header>
              <div className="bordered-table">
                <TableContainer>
                  <Table size="md" useZebraStyles>
                    <TableHead>
                      <TableRow>
                        <TableHeader>Permit Issuance Condition</TableHeader>
                        <TableHeader>Condition Description</TableHeader>
                        <TableHeader aria-label="Actions" />
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {rows.map((c) => (
                        <TableRow key={c.id}>
                          <TableCell>{c.themeDescription}</TableCell>
                          <TableCell>{c.description ?? '—'}</TableCell>
                          <TableCell>
                            <Button
                              size="sm"
                              kind="ghost"
                              hasIconOnly
                              renderIcon={TrashCan}
                              iconDescription={`Delete condition ${c.themeDescription}`}
                              disabled={!canManage}
                              onClick={() => setDeleting(c)}
                            />
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

      <Modal
        open={open}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading="Add permit issuance condition"
        onRequestClose={() => !saving && setOpen(false)}
        preventCloseOnClickOutside
      >
        <Stack gap={5}>
          <Select
            id="cp-condition-theme"
            labelText="Permit Issuance Condition"
            value={theme}
            invalid={!!invalid.theme}
            invalidText={invalid.theme}
            disabled={saving || !themes}
            onChange={(e) => setTheme(e.target.value)}
          >
            <SelectItem value="" text={themes ? '— Select condition —' : 'Loading…'} />
            {(themes ?? []).map((o) => (
              <SelectItem key={o.code} value={o.code} text={o.description || o.code} />
            ))}
          </Select>
          <TextArea
            id="cp-condition-description"
            labelText={
              theme === 'OTHER' ? 'Condition Description' : 'Condition Description (optional)'
            }
            value={description}
            maxCount={MAX_DESCRIPTION}
            enableCounter
            invalid={!!invalid.description}
            invalidText={invalid.description}
            disabled={saving}
            onChange={(e) => setDescription(e.target.value)}
          />
        </Stack>
        <div className="detail-dialog__actions">
          <Button kind="tertiary" disabled={saving} onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button kind="primary" disabled={saving} onClick={() => void submit()}>
            {saving ? 'Adding…' : 'Add condition'}
          </Button>
        </div>
      </Modal>

      <ConfirmationModal
        open={deleting !== null}
        onClose={() => setDeleting(null)}
        heading="Delete condition"
        confirmLabel="Delete"
        danger
        errorTitle="Could not delete the condition"
        onConfirm={async () => {
          if (!deleting) return;
          await deletePermitCondition(hvaSkey, deleting.id);
          display({ kind: 'success', title: 'Condition deleted', timeout: 5000 });
          reload();
        }}
      >
        Delete the condition {deleting?.themeDescription}?
      </ConfirmationModal>
    </div>
  );
};

export default PermitConditions;
