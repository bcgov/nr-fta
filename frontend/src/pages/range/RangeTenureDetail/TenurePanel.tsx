import { Calendar, Edit, Renew, Report, WarningAlt } from '@carbon/icons-react';
import { Button, InlineNotification, Select, SelectItem, TextInput } from '@carbon/react';
import { useEffect, useState, type FC, type ReactNode } from 'react';

import AsyncBoundary from '@/components/AsyncBoundary';
import ConfirmationModal from '@/components/ConfirmationModal';
import DateField from '@/components/DateField';
import DetailTile, { type DetailField } from '@/components/DetailTile';
import ManagementUnitComboBox from '@/components/ManagementUnitComboBox';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import { useOrgUnitName } from '@/hooks/useOrgUnitName';
import {
  getDistricts,
  getManagementUnits,
  type CodeOption,
  type ManagementUnit,
} from '@/services/codeLists';
import type { RangeTenureDetail } from '@/services/range_tenure_detail';
import {
  cancelRangeReplacement,
  getRangeTenureTab,
  getRangeZonesWithContact,
  retireRangeTenure,
  saveRangeTenureTab,
  type RangeTenureField,
  type RangeTenureTab,
  type RangeWriteResult,
  type RangeZone,
} from '@/services/range_tenure_tab';
import { formatDate } from '@/utils/formatDate';

import { dash, yesNo } from './format';
import TenureInvoicePreviewModal from './TenureInvoicePreviewModal';
import TenureSpecialConditions from './TenureSpecialConditions';

import type { RangePanelProps } from './panelProps';

/** range.legal.text.info, which legacy showed over the form. */
const LEGAL_TEXT = 'Any change requires new legal documentation to be produced.';

const isInt = (s: string) => /^\d{1,9}$/.test(s);

/**
 * The form's overrideReason condition (the backend decides again): a replacement term newly
 * entered on a tenure whose expiry year — entered, or effective year + term - 1 — has passed.
 */
const overrideRequired = (f: Record<string, string>, prevReplacement: string, year: number) => {
  const replacement = (f.replacementTermYears ?? '').trim();
  if (prevReplacement || !replacement || !isInt(replacement)) return false;
  const expiry = (f.expiryYear ?? '').trim();
  const term = (f.tenureTermYears ?? '').trim();
  const issue = (f.issueYear ?? '').trim();
  if (!term && !expiry) return false;
  let expiryYear: number;
  if (expiry && isInt(expiry)) expiryYear = Number(expiry);
  else if (isInt(term) && isInt(issue)) expiryYear = Number(issue) + Number(term) - 1;
  else return false;
  return year > expiryYear;
};

/** A stored code shown even when the current list no longer offers it. */
const withCurrent = (options: CodeOption[], code: string, label?: string) =>
  code && !options.some((o) => o.code === code)
    ? [{ code, description: label || code }, ...options]
    : options;

/**
 * The Tenure tab — legacy FTA100R (Range Tenure). The file's tenure fields (Historical and
 * Replacement only for the replaceable E01/H01 licences), edited in place by those who may edit
 * the file, with Retire, Cancel Replacement and Preview Invoice, then its special conditions.
 * What opens, the checks and the save are legacy's: the backend runs FTA_100RANGE_TENURE.
 */
const TenurePanel: FC<RangePanelProps> = ({ tenure, canEdit, onTenureChanged }) => {
  const forestFileId = tenure.forestFileId;
  const { data, loading, error, reload, refresh } = useApiResource(
    () => getRangeTenureTab(forestFileId),
    [forestFileId],
  );
  const onChanged = () => {
    refresh();
    onTenureChanged();
  };
  return (
    <AsyncBoundary
      loading={loading}
      error={error}
      onRetry={reload}
      loadingText="Loading range tenure…"
    >
      {data && (
        <TenureView
          tab={data}
          tenure={tenure}
          canEdit={canEdit && data.editable}
          onChanged={onChanged}
        />
      )}
    </AsyncBoundary>
  );
};

const TenureView: FC<{
  tab: RangeTenureTab;
  tenure: RangeTenureDetail;
  canEdit: boolean;
  onChanged: () => void;
}> = ({ tab, tenure, canEdit, onChanged }) => {
  const { display } = useNotification();
  const orgUnitName = useOrgUnitName();
  const { values, descriptions } = tab;
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<Record<string, string>>(values);
  const [saving, setSaving] = useState(false);
  const [confirmRetire, setConfirmRetire] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [previewing, setPreviewing] = useState(false);
  const [districts, setDistricts] = useState<CodeOption[]>([]);
  const [units, setUnits] = useState<ManagementUnit[]>([]);
  const [zones, setZones] = useState<RangeZone[]>(tab.zones);

  const opens = (key: RangeTenureField) => editing && tab.editableFields.includes(key);
  const v = (key: string) => values[key] ?? '';
  const f = (key: string) => form[key] ?? '';

  // The edit lists, fetched once the form opens.
  useEffect(() => {
    if (!editing) return;
    let cancelled = false;
    Promise.all([getManagementUnits(), getDistricts()])
      .then(([mgmtUnits, districtList]) => {
        if (cancelled) return;
        setUnits(mgmtUnits);
        setDistricts(districtList);
      })
      .catch(() => {
        if (!cancelled)
          display({
            kind: 'error',
            title: 'Could not load the edit lists.',
            subtitle: 'Some fields show only their current value.',
            timeout: 6000,
          });
      });
    return () => {
      cancelled = true;
    };
  }, [editing, display]);

  const set = (key: string, value: string) => setForm((prev) => ({ ...prev, [key]: value }));

  /** A new district lists its own zones; a zone it does not have is cleared. */
  const chooseDistrict = (districtNo: string) => {
    set('forestDistrictNo', districtNo);
    if (!districtNo) {
      setZones([]);
      set('districtAdminZone', '');
      return;
    }
    getRangeZonesWithContact(districtNo)
      .then((list) => {
        setZones(list);
        setForm((prev) =>
          list.some((z) => z.code === prev.districtAdminZone)
            ? prev
            : { ...prev, districtAdminZone: '' },
        );
      })
      .catch(() => setZones([]));
  };

  const year = new Date().getFullYear();
  const needsOverride = editing && overrideRequired(form, v('prevReplacementTermYears'), year);

  const onEdit = () => {
    setForm({ ...values, districtOverrideReason: '' });
    setZones(tab.zones);
    setEditing(true);
  };

  const onCancel = () => {
    setForm(values);
    setZones(tab.zones);
    setEditing(false);
  };

  const announce = (result: RangeWriteResult) =>
    display({
      kind: result.warnings.length > 0 ? 'warning' : 'success',
      title: result.message,
      subtitle: result.warnings.join(' ') || undefined,
      timeout: result.warnings.length > 0 ? 9000 : 5000,
    });

  const onSave = async () => {
    const sent: Record<string, string> = {};
    for (const key of tab.editableFields) sent[key] = f(key);
    if (needsOverride) sent.districtOverrideReason = f('districtOverrideReason');
    setSaving(true);
    try {
      announce(await saveRangeTenureTab(tab.forestFileId, tab.revisions, sent));
      setEditing(false);
      onChanged();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not save the range tenure.',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 12000,
      });
    } finally {
      setSaving(false);
    }
  };

  const onRetire = async () => {
    announce(await retireRangeTenure(tab.forestFileId, tab.revisions));
    onChanged();
  };

  const onCancelReplacement = async () => {
    setCancelling(true);
    try {
      announce(await cancelRangeReplacement(tab.forestFileId, tab.revisions));
      onChanged();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not cancel the replacement.',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 12000,
      });
    } finally {
      setCancelling(false);
    }
  };

  // ─── Inputs ────────────────────────────────────────────────────────────────

  const text = (key: string, label: string, maxLength: number, size: 'cell' | 'xs' = 'cell') => (
    <div className={`detail-edit__input detail-edit__input--${size}`}>
      <TextInput
        id={`range-tenure-${key}`}
        labelText={label}
        hideLabel
        value={f(key)}
        maxLength={maxLength}
        disabled={saving}
        onChange={(e) => set(key, e.target.value)}
      />
    </div>
  );

  const select = (
    key: string,
    label: string,
    options: CodeOption[],
    blank: boolean,
    onChange: (value: string) => void = (value) => set(key, value),
  ) => (
    <div className="detail-edit__input detail-edit__input--cell">
      <Select
        id={`range-tenure-${key}`}
        labelText={label}
        hideLabel
        value={f(key)}
        disabled={saving}
        onChange={(e) => onChange(e.target.value)}
      >
        {blank && <SelectItem value="" text="" />}
        {withCurrent(options, f(key), descriptions[key]).map((o) => (
          <SelectItem key={o.code} value={o.code} text={o.description || o.code} />
        ))}
      </Select>
    </div>
  );

  /** A year after legacy's fixed "January 1," or "December 31,". */
  const yearInput = (key: string, label: string, prefix: string) => (
    <div className="detail-edit__pair">
      <span>{prefix}</span>
      {text(key, label, 4, 'xs')}
    </div>
  );

  /** A field: its input when the user may change it and the form is open, else its value. */
  const field = (
    label: string,
    key: RangeTenureField,
    view: ReactNode,
    editor: () => ReactNode,
    opts: { optional?: boolean; span2?: boolean } = {},
  ): DetailField => {
    const open = opens(key);
    return {
      label: open && opts.optional ? `${label} (optional)` : label,
      value: open ? editor() : view,
      span2: opts.span2 && open,
    };
  };
  const show = (label: string, value: ReactNode): DetailField => ({ label, value });

  // ─── Views ─────────────────────────────────────────────────────────────────

  const jan1 = (y: string) => (y ? `January 1, ${y}` : '—');
  const dec31 = (y: string) => (y ? `December 31, ${y}` : '—');
  const years = (n: string) => (n ? `${n} yr` : '—');
  const mgmtUnitView = v('mgmtUnitType')
    ? [v('mgmtUnitType') + v('mgmtUnitId'), v('mgmtUnitName') && `— ${v('mgmtUnitName')}`]
        .filter(Boolean)
        .join(' ')
    : '—';
  const zoneCode = editing ? f('districtAdminZone') : v('districtAdminZone');
  const zone = (editing ? zones : tab.zones).find((z) => z.code === zoneCode);
  const zoneView = v('districtAdminZone')
    ? [v('districtAdminZone'), descriptions.zoneDescription, descriptions.zoneContact]
        .filter(Boolean)
        .join(' — ')
    : '—';
  const zoneOptions: CodeOption[] = zones.map((z) => ({ code: z.code, description: z.code }));

  const summary: DetailField[] = [
    show('File', tab.forestFileId),
    field('Status', 'fileStatusSt', dash(descriptions.fileStatusSt || v('fileStatusSt')), () =>
      select('fileStatusSt', 'Status', tab.statuses, true),
    ),
    field('Status Date', 'fileStatusDate', dash(formatDate(v('fileStatusDate'))), () => (
      <div className="detail-edit__input detail-edit__input--cell">
        <DateField
          id="range-tenure-fileStatusDate"
          labelText="Status Date"
          hideLabel
          value={f('fileStatusDate')}
          disabled={saving}
          onChange={(value) => set('fileStatusDate', value)}
        />
      </div>
    )),
    field('Type', 'fileTypeCode', dash(descriptions.fileTypeCode || v('fileTypeCode')), () =>
      select('fileTypeCode', 'Type', tab.fileTypes, false),
    ),
    show('Admin Org', dash(orgUnitName(v('adminOrgUnitNo')))),
    field('District', 'forestDistrictNo', dash(orgUnitName(v('forestDistrictNo'))), () =>
      select('forestDistrictNo', 'District', districts, true, chooseDistrict),
    ),
    show('Client', dash(tenure.licensee)),
    show('Sec. Licensee', yesNo(tenure.secLicenseeInd)),
    field(
      'Mgmt Unit',
      'mgmtUnitType',
      mgmtUnitView,
      () => (
        <div className="detail-edit__input detail-edit__input--cell">
          <ManagementUnitComboBox
            id="range-tenure-mgmtUnit"
            units={units}
            typeCode={f('mgmtUnitType')}
            unitId={f('mgmtUnitId')}
            titleText="Mgmt Unit"
            hideLabel
            helperText=""
            typeEntrySuffix=""
            disabled={saving}
            onChange={({ mgmtUnitType, mgmtUnitId }) =>
              setForm((prev) => ({ ...prev, mgmtUnitType, mgmtUnitId }))
            }
          />
        </div>
      ),
      { span2: true },
    ),
    field('Zone', 'districtAdminZone', zoneView, () => (
      <>
        {select('districtAdminZone', 'Zone', zoneOptions, true)}
        {zone && (zone.description || zone.contact) && (
          <span>{[zone.description, zone.contact].filter(Boolean).join(' — ')}</span>
        )}
      </>
    )),
    field('File Name', 'fileName', dash(v('fileName')), () => text('fileName', 'File Name', 100), {
      optional: true,
    }),
  ];

  const historical: DetailField[] = tab.replaceable
    ? [
        field('Original Start Date', 'originalIssueYear', jan1(v('originalIssueYear')), () =>
          yearInput('originalIssueYear', 'Original Start Year', 'January 1,'),
        ),
      ]
    : [];

  const current: DetailField[] = [
    field('Start Date', 'issueYear', jan1(v('issueYear')), () =>
      yearInput('issueYear', 'Start Year', 'January 1,'),
    ),
    field('Term', 'tenureTermYears', years(v('tenureTermYears')), () => (
      <div className="detail-edit__pair">
        {text('tenureTermYears', 'Term (yr)', 2, 'xs')}
        <span>yr</span>
      </div>
    )),
    field('Initial Expiry Date', 'expiryYear', dec31(v('expiryYear')), () =>
      yearInput('expiryYear', 'Initial Expiry Year', 'December 31,'),
    ),
    show('Replacement Count', dash(v('replacementCount'))),
  ];

  const replacement: DetailField[] = tab.replaceable
    ? [
        field(
          'Replacement Term',
          'replacementTermYears',
          years(v('replacementTermYears')),
          () => (
            <div className="detail-edit__pair">
              {text('replacementTermYears', 'Replacement Term (yr)', 2, 'xs')}
              <span>yr</span>
            </div>
          ),
          { optional: true },
        ),
        ...(v('replacementLabel') ? [show('Replacement', v('replacementLabel'))] : []),
      ]
    : [];

  return (
    <div className={editing ? 'fsp-info__tab-panel detail-edit' : 'fsp-info__tab-panel'}>
      <div className="detail-edit__toolbar">
        {!editing && (
          <>
            <Button
              kind="tertiary"
              size="sm"
              renderIcon={Edit}
              disabled={!canEdit}
              onClick={onEdit}
            >
              Edit
            </Button>
            <Button
              kind="tertiary"
              size="sm"
              disabled={!canEdit || !tab.retirable}
              onClick={() => setConfirmRetire(true)}
            >
              Retire
            </Button>
            <Button kind="tertiary" size="sm" onClick={() => setPreviewing(true)}>
              Preview Invoice
            </Button>
          </>
        )}
      </div>

      {editing && <InlineNotification kind="info" lowContrast hideCloseButton title={LEGAL_TEXT} />}

      <div className="fsp-info__tile-row">
        <DetailTile title="Tenure" icon={Report} fields={summary} />
        <DetailTile title="Current" icon={Calendar} fields={current} />
      </div>
      {(historical.length > 0 || replacement.length > 0) && (
        <div className="fsp-info__tile-row">
          {historical.length > 0 && (
            <DetailTile title="Historical" icon={Calendar} fields={historical} />
          )}
          {replacement.length > 0 && (
            <DetailTile
              title="Replacement"
              icon={Renew}
              fields={replacement}
              action={
                !editing ? (
                  <Button
                    kind="tertiary"
                    size="sm"
                    disabled={!canEdit || !tab.cancelReplacement || cancelling}
                    onClick={() => void onCancelReplacement()}
                  >
                    {cancelling ? 'Cancelling…' : 'Cancel Replacement'}
                  </Button>
                ) : undefined
              }
            />
          )}
        </div>
      )}
      {needsOverride && (
        <DetailTile
          title="District override"
          icon={WarningAlt}
          fields={[
            {
              label: 'District Override Reason',
              value: text('districtOverrideReason', 'District Override Reason', 60),
              wide: true,
            },
          ]}
        />
      )}

      {editing && (
        <div className="detail-edit__actions">
          <Button kind="tertiary" size="md" disabled={saving} onClick={onCancel}>
            Cancel
          </Button>
          <Button kind="primary" size="md" disabled={saving} onClick={() => void onSave()}>
            {saving ? 'Saving…' : 'Save changes'}
          </Button>
        </div>
      )}

      <TenureSpecialConditions
        forestFileId={tab.forestFileId}
        conditions={tenure.specialConditions}
        codes={tab.conditionCodes}
        canEdit={canEdit}
        locked={editing}
        onChanged={onChanged}
      />

      <ConfirmationModal
        open={confirmRetire}
        onClose={() => setConfirmRetire(false)}
        heading="Retire range tenure"
        confirmLabel="Retire"
        errorTitle="Could not retire the range tenure."
        onConfirm={onRetire}
      >
        {`Are you sure you want to retire file ${tab.forestFileId}?`}
      </ConfirmationModal>

      {previewing && (
        <TenureInvoicePreviewModal
          forestFileId={tab.forestFileId}
          onClose={() => setPreviewing(false)}
        />
      )}
    </div>
  );
};

export default TenurePanel;
