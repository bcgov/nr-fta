import { Calendar, Edit, Report, Stamp, TreeView, WarningAlt } from '@carbon/icons-react';
import {
  Button,
  DatePicker,
  DatePickerInput,
  InlineNotification,
  Select,
  SelectItem,
  TextInput,
} from '@carbon/react';
import { useEffect, useState, type FC, type ReactNode } from 'react';

import AsyncBoundary from '@/components/AsyncBoundary';
import DetailTile, { type DetailField } from '@/components/DetailTile';
import ManagementUnitComboBox from '@/components/ManagementUnitComboBox';
import { statusCodeVariant } from '@/components/StatusTag/statusCodes';
import StatusTag from '@/components/StatusTag/StatusTag';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import { getManagementUnits, type CodeOption, type ManagementUnit } from '@/services/codeLists';
import {
  getTenureDetails,
  getTenureDetailsOptions,
  updateTenureDetails,
  type TenureDetails,
} from '@/services/tenure_details';
import { formatDate } from '@/utils/formatDate';
import { parseTypedDate, TYPED_DATE_PATTERN } from '@/utils/typedDate';

import type { TenurePanelProps } from './panelProps';

const nf = new Intl.NumberFormat('en-CA', { maximumFractionDigits: 4 });

const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;
const date = (v: string | null | undefined) => dash(formatDate(v));
/** A number as legacy stored it, grouped for reading; non-numbers pass through. */
const num = (v: string, suffix = '') =>
  v === '' ? '—' : Number.isFinite(Number(v)) ? `${nf.format(Number(v))}${suffix}` : v;
const yesNo = (v: string) => (v === 'Y' ? 'Yes' : v === 'N' ? 'No' : '—');

const YES_NO: CodeOption[] = [
  { code: 'Y', description: 'Yes' },
  { code: 'N', description: 'No' },
];

/** Options for a code select, plus the stored value when the list no longer has it. */
const withCurrent = (options: CodeOption[], code: string, label?: string) =>
  code && !options.some((o) => o.code === code)
    ? [{ code, description: label || code }, ...options]
    : options;

/** yyyy-mm-dd in local time — what the package parses. */
const toIsoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

/** The fields whose change on a held file needs a District Override Reason. */
const TERM_KEYS = [
  'awardDate',
  'tenureTermYears',
  'tenureTermMonths',
  'extensionCount',
  'expiryDate',
];

const FUP_FN_KEYS = ['fupFnUsageCode', 'fupCedarSpeciesInd', 'fupTreatyPurposeInd'];

/**
 * The Details tab of the tenure page — legacy FTA100 (Tenure): every field the
 * legacy screen shows for this file, laid out as the private-mark Mark
 * application tab (Tenure summary and Term side by side, the licence and mark
 * sections full width beneath). One Edit button opens every field the backend's
 * rules allow in place; Cancel / Save changes close it. The rules, validations
 * and save are legacy's — the backend runs `FTA_100_TENURE` itself.
 */
const DetailsPanel: FC<TenurePanelProps> = ({ tenure, canEdit, onTenureChanged }) => {
  const forestFileId = tenure.forestFileId;
  const { data, loading, error, reload } = useApiResource(
    () => getTenureDetails(forestFileId),
    [forestFileId],
  );

  return (
    <AsyncBoundary
      loading={loading}
      error={error}
      onRetry={reload}
      loadingText="Loading tenure details…"
    >
      {data && (
        <DetailsView
          details={data}
          canEdit={canEdit}
          onSaved={() => {
            reload();
            onTenureChanged();
          }}
        />
      )}
    </AsyncBoundary>
  );
};

const DetailsView: FC<{ details: TenureDetails; canEdit: boolean; onSaved: () => void }> = ({
  details,
  canEdit,
  onSaved,
}) => {
  const { display } = useNotification();
  const { values, descriptions, layout, rules } = details;
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<Record<string, string>>(values);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [options, setOptions] = useState<Record<string, CodeOption[]>>({});
  const [units, setUnits] = useState<ManagementUnit[]>([]);

  // The dropdowns' lists, fetched once the form opens (cached by the backend).
  useEffect(() => {
    if (!editing) return;
    let cancelled = false;
    Promise.all([getTenureDetailsOptions(), getManagementUnits()])
      .then(([lists, mgmtUnits]) => {
        if (!cancelled) {
          setOptions(lists);
          setUnits(mgmtUnits);
        }
      })
      .catch(() => {
        if (!cancelled) {
          display({
            kind: 'error',
            title: 'Could not load the edit lists',
            subtitle: 'Some dropdowns show only their current value.',
            timeout: 6000,
          });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [editing, display]);

  const v = (key: string) => values[key] ?? '';
  const f = (key: string) => form[key] ?? '';
  const desc = (key: string) => descriptions[key] || v(key);

  const set = (key: string, value: string) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    if (errors[key]) {
      setErrors((prev) => {
        const next = { ...prev };
        delete next[key];
        return next;
      });
    }
  };

  /** Whether a key opens: the rules' list, or a conditional one whose condition holds. */
  const opens = (key: string) => {
    if (rules.fields.includes(key)) return true;
    if (!rules.conditionalFields.includes(key)) return false;
    if (FUP_FN_KEYS.includes(key)) return f('fupTypeCode') === 'FN';
    if (key === 'pulpwoodFile') return f(layout.purposeField) === 'PA';
    return false;
  };
  const open = (key: string) => editing && rules.editable && opens(key);

  const prevStatus = v('prevFileStatusSt');
  const needsOverride =
    editing &&
    ((f('fileStatusSt') !== v('fileStatusSt') &&
      rules.overrideStatuses.includes(f('fileStatusSt'))) ||
      (prevStatus.startsWith('H') &&
        prevStatus !== 'HN' &&
        TERM_KEYS.some((k) => (form[k] ?? '').trim() !== v(k).trim())));

  const onEdit = () => {
    setForm({ ...values, districtOverrideReason: '' });
    setErrors({});
    setEditing(true);
  };

  const onCancel = () => {
    setForm(values);
    setErrors({});
    setEditing(false);
  };

  const onSave = async () => {
    if (needsOverride && !f('districtOverrideReason').trim()) {
      setErrors({ districtOverrideReason: 'District Override Reason is mandatory.' });
      display({ kind: 'error', title: 'Check the highlighted fields', timeout: 6000 });
      return;
    }
    const sent: Record<string, string> = {};
    for (const key of [...rules.fields, ...rules.conditionalFields]) {
      if (opens(key)) sent[key] = f(key);
    }
    if (needsOverride) sent.districtOverrideReason = f('districtOverrideReason');
    setSaving(true);
    try {
      const result = await updateTenureDetails(details.forestFileId, details.revisions, sent);
      setEditing(false);
      display({
        kind: result.warnings.length > 0 ? 'warning' : 'success',
        title: 'Save successful.',
        subtitle: result.warnings.join(' ') || undefined,
        timeout: result.warnings.length > 0 ? 9000 : 5000,
      });
      onSaved();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not save the tenure',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 12000,
      });
    } finally {
      setSaving(false);
    }
  };

  // ─── Inputs ────────────────────────────────────────────────────────────────

  const text = (
    key: string,
    label: string,
    maxLength?: number,
    size: 'cell' | 'sm' | 'xs' = 'cell',
  ) => (
    <div className={`detail-edit__input detail-edit__input--${size}`}>
      <TextInput
        id={`details-edit-${key}`}
        labelText={label}
        hideLabel
        value={f(key)}
        maxLength={maxLength}
        invalid={!!errors[key]}
        invalidText={errors[key]}
        disabled={saving}
        onChange={(e) => set(key, e.target.value)}
      />
    </div>
  );

  const select = (key: string, label: string, list: CodeOption[], blank = true) => (
    <div className="detail-edit__input detail-edit__input--cell">
      <Select
        id={`details-edit-${key}`}
        labelText={label}
        hideLabel
        value={f(key)}
        invalid={!!errors[key]}
        invalidText={errors[key]}
        disabled={saving}
        onChange={(e) => set(key, e.target.value)}
      >
        {blank && <SelectItem value="" text="" />}
        {withCurrent(list, f(key), descriptions[key]).map((o) => (
          <SelectItem key={o.code} value={o.code} text={o.description || o.code} />
        ))}
      </Select>
    </div>
  );

  /** A code select over one of the backend's lists. */
  const codes = (key: string, label: string, list: string, blank = true) =>
    select(key, label, options[list] ?? [], blank);

  const dateInput = (key: string, label: string) => (
    <div className="detail-edit__input detail-edit__input--cell">
      <DatePicker
        datePickerType="single"
        dateFormat="Y-m-d"
        value={f(key)}
        invalid={!!errors[key]}
        onChange={(dates: Date[]) => set(key, dates[0] ? toIsoDate(dates[0]) : '')}
      >
        <DatePickerInput
          id={`details-edit-${key}`}
          labelText={label}
          hideLabel
          placeholder="yyyy-mm-dd"
          invalidText={errors[key]}
          disabled={saving}
          pattern={TYPED_DATE_PATTERN}
          onChange={(e) => {
            const text = e.target.value;
            if (text.trim() === '') set(key, '');
            else {
              const typed = parseTypedDate(text);
              if (typed) set(key, typed);
            }
          }}
        />
      </DatePicker>
    </div>
  );

  // ─── Fields ────────────────────────────────────────────────────────────────

  /** A field: its input when the key opens, otherwise the read-only value. */
  const field = (
    label: string,
    key: string,
    view: ReactNode,
    editor: () => ReactNode,
    opts: { optional?: boolean; wide?: boolean; span2?: boolean } = {},
  ): DetailField => {
    const editable = open(key);
    return {
      label: editable && opts.optional ? `${label} (optional)` : label,
      value: editable ? editor() : view,
      wide: opts.wide,
      span2: opts.span2 && editing,
    };
  };
  const show = (label: string, value: ReactNode, opts: { wide?: boolean } = {}): DetailField => ({
    label,
    value,
    wide: opts.wide,
  });
  const codeField = (label: string, key: string, list: string, opts = { optional: true }) =>
    field(label, key, dash(desc(key)), () => codes(key, label, list), opts);
  const ynField = (label: string, key: string, opts = { optional: false }) =>
    field(label, key, yesNo(v(key)), () => select(key, label, YES_NO, opts.optional), opts);
  /** Legacy's Y / N / blank ("U") selects. */
  const uynField = (label: string, key: string) =>
    field(label, key, yesNo(v(key)), () => (
      <div className="detail-edit__input detail-edit__input--cell">
        <Select
          id={`details-edit-${key}`}
          labelText={label}
          hideLabel
          value={f(key) || 'U'}
          disabled={saving}
          onChange={(e) => set(key, e.target.value)}
        >
          <SelectItem value="U" text="" />
          <SelectItem value="Y" text="Yes" />
          <SelectItem value="N" text="No" />
        </Select>
      </div>
    ));
  const textField = (
    label: string,
    key: string,
    maxLength: number,
    view: ReactNode = dash(v(key)),
    opts: { optional?: boolean; wide?: boolean } = { optional: true },
  ) => field(label, key, view, () => text(key, label, maxLength), opts);
  const dateField = (label: string, key: string, optional = true) =>
    field(label, key, date(v(key)), () => dateInput(key, label), { optional });

  const typeCode = v('fileTypeCode');
  const statusView = v('fileStatusSt') ? (
    <StatusTag status={desc('fileStatusSt')} variant={statusCodeVariant(v('fileStatusSt'))} />
  ) : (
    '—'
  );
  const mgmtUnitView = v('mgmtUnitType')
    ? [v('mgmtUnitType'), v('mgmtUnitId'), v('mgmtUnitName') && `— ${v('mgmtUnitName')}`]
        .filter(Boolean)
        .join(' ')
    : '—';
  const purposeLabel = 'Purpose';

  // Tenure summary
  const summary: (DetailField | false)[] = [
    show('File ID', details.forestFileId),
    show('File Type', dash(desc('fileTypeCode'))),
    field('Status', 'fileStatusSt', statusView, () =>
      codes('fileStatusSt', 'Status', rules.statusList, false),
    ),
    dateField('As of', 'fileStatusDate', false),
    show('Admin Organization', dash(desc('adminOrgUnitNo'))),
    layout.showDistrict &&
      codeField('District', 'forestDistrictNo', 'districts', { optional: false }),
    !layout.recreation && show(layout.licenseeLabel, dash(v('licensee'))),
    !layout.recreation && show('Secondary Licensee', yesNo(v('secLicenseeInd'))),
    {
      ...field('Management Unit', 'mgmtUnitType', mgmtUnitView, () => (
        <div className="detail-edit__input detail-edit__input--cell">
          <ManagementUnitComboBox
            id="details-edit-mgmtUnit"
            units={units}
            typeCode={f('mgmtUnitType')}
            unitId={f('mgmtUnitId')}
            titleText="Management Unit"
            hideLabel
            helperText=""
            typeEntrySuffix=""
            disabled={saving}
            onChange={({ mgmtUnitType, mgmtUnitId }) => {
              set('mgmtUnitType', mgmtUnitType);
              set('mgmtUnitId', mgmtUnitId);
            }}
          />
        </div>
      )),
      span2: editing && open('mgmtUnitType'),
    },
    layout.showFnAgreement && uynField('Direct Award FN Agreement', 'fnAgreement'),
    layout.showMapNotation && codeField('Map Notation Type', 'mapNotnTypeCd', 'mapNotationTypes'),
    layout.showZone && textField('Zone', 'districtAdminZone', 4),
    layout.showSalvage && ynField('Salvage', 'salvageInd'),
    layout.showSfl && show('SFL', yesNo(v('sflInd'))),
    layout.showFup && codeField('FUP Type', 'fupTypeCode', 'fupTypes'),
    layout.showFup &&
      (f('fupTypeCode') === 'FN' || v('fupTypeCode') === 'FN') &&
      codeField('First Nations Purpose', 'fupFnUsageCode', 'fupFnUsages', { optional: false }),
    layout.showFup &&
      (f('fupTypeCode') === 'FN' || v('fupTypeCode') === 'FN') &&
      ynField('Majority Volume Cedar Species', 'fupCedarSpeciesInd'),
    layout.showFup &&
      (f('fupTypeCode') === 'FN' || v('fupTypeCode') === 'FN') &&
      ynField('First Nations Treaty Purpose', 'fupTreatyPurposeInd', { optional: true }),
    v('notesLabel').trim() !== '' && show('Notes', v('notesLabel').trim()),
  ];

  // Term
  const replaceable = f('licenceReplaceableInd') === 'Y';
  const termView =
    v('tenureTermYears') || v('tenureTermMonths')
      ? [
          v('tenureTermYears') && `${v('tenureTermYears')} yr`,
          v('tenureTermMonths') && `${v('tenureTermMonths')} mo`,
        ]
          .filter(Boolean)
          .join(' ')
      : '—';
  const term: (DetailField | false)[] = layout.showTerm
    ? [
        dateField(
          'Effective Date',
          'awardDate',
          !(f('fileStatusSt').startsWith('H') && f('fileStatusSt') !== 'HN'),
        ),
        layout.showTenureTerm &&
          field(
            'Term',
            'tenureTermYears',
            termView,
            () => (
              <div className="detail-edit__pair">
                {text('tenureTermYears', 'Term (yr)', 2, 'xs')}
                <span>yr</span>
                {text('tenureTermMonths', 'Term (mo)', 2, 'xs')}
                <span>mo</span>
              </div>
            ),
            { optional: true },
          ),
        dateField('Initial Expiry Date', 'expiryDate'),
        layout.showReplaceable && ynField('Replaceable', 'licenceReplaceableInd'),
        layout.showReplaceable &&
          textField(
            'Maximum Harvest Volume',
            'maximumHarvestVolume',
            10,
            num(v('maximumHarvestVolume'), ' m³'),
          ),
        layout.showReplaceable &&
          textField(
            'Maximum Revenue Share Volume (AAC)',
            'maximumRevenueVolume',
            10,
            num(v('maximumRevenueVolume'), ' m³'),
          ),
        layout.showExtension &&
          textField(
            replaceable ? 'Rep. Count' : layout.extensionCountLabel,
            'extensionCount',
            2,
            dash(v('extensionCount')),
          ),
        layout.showExtension &&
          typeCode !== 'A11' &&
          replaceable &&
          dateField('Replaced Date', 'replacedDate'),
        layout.showExtension &&
          typeCode !== 'A11' &&
          dateField('Current Expiry Date', 'extendedDate'),
        layout.showExtension &&
          !replaceable &&
          codeField('Extension Reason', 'extensionReason', 'extendReasons'),
        layout.showExtension &&
          layout.showDesignate &&
          show('Mark Designate', dash(v('markDesignate'))),
        layout.showExtension &&
          layout.showDesignate &&
          !layout.showMark &&
          codeField('FN Held Amt', 'fnHeldLevelCode', 'fnHeldLevels'),
      ]
    : [];

  // Licence details (the screen's middle sections)
  const purposeField = (key: string) =>
    field(purposeLabel, key, dash(desc(key)), () => codes(key, purposeLabel, rules.purposeList));
  const areaLabel = layout.grossArea ? 'Gross Area' : 'Initial Area';
  const otherMarks = [1, 2, 3, 4].map((i) => v(`otherMarksUsed${i}`)).filter(Boolean);
  const showPulpwoodFile =
    layout.showPulpwoodAgreement ||
    (editing && opens('pulpwoodFile') && f(layout.purposeField) === 'PA');
  const licence: (DetailField | false)[] = [
    layout.showEstTotalVol && show('Est. Total Vol.', num(v('estTotalAreaVol'), ' m³')),
    layout.showPulpwoodAac &&
      textField("Sched. 'B' AAC", 'p01TotalAac', 9, num(v('p01TotalAac'), ' m³')),
    layout.showPulpwoodAac &&
      textField('Coniferous', 'coniferousAac', 9, num(v('coniferousAac'), ' m³')),
    layout.showPulpwoodAac &&
      textField('Deciduous', 'deciduousAac', 9, num(v('deciduousAac'), ' m³')),
    layout.showLocationArea &&
      textField('Location', 'permitBlockLocation', 50, dash(v('permitBlockLocation')), {
        optional: true,
        wide: false,
      }),
    layout.showLocationArea &&
      textField('Sched B', 'permitBlockArea', 16, num(v('permitBlockArea'), ' ha')),
    layout.showDepositPurpose && purposeField(layout.purposeField),
    layout.showDepositPurpose &&
      layout.annualRentLabel !== '' &&
      textField(layout.annualRentLabel, 'annualRentFee', 10, num(v('annualRentFee'))),
    layout.showDeposits &&
      codeField('Security Deposit Type', 'securityDepositCode', 'depositTypes'),
    layout.showDeposits &&
      textField(
        'Security Deposit Amount',
        'securityDepositAmount',
        12,
        num(v('securityDepositAmount')),
      ),
    layout.showDeposits && codeField('Other Deposit Type', 'otherDepositCode', 'depositTypes'),
    layout.showDeposits &&
      textField('Other Deposit Amount', 'otherDepositAmount', 12, num(v('otherDepositAmount'))),
    layout.showTimberLicenceAreas &&
      textField(areaLabel, 'initLicenceArea', 9, num(v('initLicenceArea'), ' ha'), {
        optional: false,
      }),
    layout.showTimberLicenceAreas &&
      textField('Obligated Area', 'obligationArea', 9, num(v('obligationArea'), ' ha')),
    layout.showTimberLicenceAreas &&
      textField('Eliminated Area', 'eliminatedArea', 9, num(v('eliminatedArea'), ' ha')),
    layout.showTimberLicenceAreas &&
      !layout.grossArea &&
      textField('Other Area', 'otherArea', 9, num(v('otherArea'), ' ha')),
    layout.showMinor && show('Licence', num(v('tslLicenceHa'), ' ha')),
    layout.showMinor && show('Active', num(v('tslActiveHa'), ' ha')),
    layout.showBctsFund && ynField('BCTS Fund', 'bctsFundInd'),
    layout.showBctsFund && codeField('BCTS Org', 'bctsOrgUnit', 'bctsOrgUnits'),
    layout.showPaymentMethod &&
      codeField('Payment Method', 'paymentMethodCd', 'paymentMethods', { optional: false }),
    layout.showSpatialLater && ynField('Submit Spatial Later', 'subSpatialLaterInd'),
    layout.showAac && show('Licence Area', num(v('licenceArea'), ' ha')),
    layout.showAac && show('Private / Schedule A', num(v('schedAAac'))),
    layout.showAac && show('Crown / Schedule B', num(v('schedBAac'))),
    layout.showAac && show('Revenue Share', num(v('revenueShareVolume'))),
    layout.showAac &&
      show(
        `Total${v('harvestUnitOfMeasureCode') ? ` (${v('harvestUnitOfMeasureCode')})` : ''}`,
        num(v('totalAac')),
      ),
    layout.showOccupancy &&
      show('Occupation Authorities Granted By', dash(v('occupationAuthorityFiles'))),
    layout.showOccupancy &&
      field(
        'Other Marks Used by this Licence',
        'otherMarksUsed1',
        otherMarks.length ? otherMarks.join(', ') : '—',
        () => (
          <div className="detail-edit__pair">
            {[1, 2, 3, 4].map((i) => (
              <div key={i}>{text(`otherMarksUsed${i}`, `Other Mark ${i}`, 6, 'xs')}</div>
            ))}
          </div>
        ),
        { optional: true, span2: true },
      ),
    showPulpwoodFile &&
      textField('Associated with Pulpwood Agreement', 'pulpwoodFile', 10, dash(v('pulpwoodFile'))),
    ...(layout.recreation
      ? layout.oldRecreation
        ? [
            textField('File Name', 'recProjectName', 30, dash(v('recProjectName')), {
              optional: false,
            }),
            show('Last Updated', date(v('recProjUpdateDate'))),
            show('Total Area', num(v('recSiteArea'), ' ha')),
            show(
              'UTM Coordinates',
              v('utmZone')
                ? `Zone ${v('utmZone')} · E ${v('utmEasting')} · N ${v('utmNorthing')}`
                : '—',
            ),
          ]
        : [
            show('Project Type', dash(v('recProjectType'))),
            show('Project Name', dash(v('recProjectName'))),
            show('Last Updated', date(v('recProjUpdateDate'))),
            show('Total Area', num(v('recSiteArea'), ' ha')),
            show('Total Length', num(v('recSiteLength'), ' km')),
            show('Old Project Reference', dash(v('oldRecProject'))),
          ]
      : []),
  ];

  // Mark
  const mark: (DetailField | false)[] = layout.showMark
    ? [
        layout.showMarkPurpose && purposeField(layout.purposeField),
        show('Mark', dash(layout.markDisplay)),
        layout.showWasteAssess && uynField("Waste Assessment Req'd", 'wasteAssessReqdInd'),
        layout.showFrz && uynField('Within Fibre Recovery Zone', 'isInFrz'),
        codeField('FN Held Amt', 'fnHeldLevelCode', 'fnHeldLevels'),
        layout.showMarkAdditional && show('Issue Date', date(v('markAwardDate'))),
        layout.showMarkAdditional && show('Expires', date(v('markExpiryDate'))),
        layout.showMarkAdditional && show('Ext. Count', dash(v('markExtensionCount'))),
        layout.showMarkAdditional && show('Extend Date', date(v('markExtendedDate'))),
        layout.showMarkAdditional && show('Reason', dash(v('markExtensionReason'))),
        layout.showQuotaType && codeField('Quota Type', 'quotaTypeCode', 'quotaTypes'),
        ynField('Catastrophic', 'catastrophicInd'),
        ynField('Deciduous', 'deciduousInd'),
        ynField(layout.cruiseBasedLabel, 'cruiseBasedInd'),
        codeField('Compliance Method', 'markingMethodCd', 'markingMethods', { optional: false }),
        codeField('Instrument', 'markngInstrmntCd', 'markingInstruments'),
        codeField('Land Region', 'landsRegion', 'landRegions'),
      ]
    : [];

  const present = (fields: (DetailField | false)[]) => fields.filter((x): x is DetailField => !!x);
  const summaryFields = present(summary);
  const termFields = present(term);
  const licenceFields = present(licence);
  const markFields = present(mark);

  return (
    <div className={editing ? 'fsp-info__tab-panel detail-edit' : 'fsp-info__tab-panel'}>
      {canEdit && (
        <div className="detail-edit__toolbar">
          {editing ? (
            <p className="detail-edit__strap">All fields are required unless marked optional.</p>
          ) : (
            <>
              <Button
                kind="tertiary"
                size="sm"
                renderIcon={Edit}
                disabled={!rules.editable}
                onClick={onEdit}
              >
                Edit
              </Button>
            </>
          )}
        </div>
      )}

      {details.notices
        .filter((n) => n !== rules.reason)
        .map((notice) => (
          <InlineNotification
            key={notice}
            kind="warning"
            lowContrast
            hideCloseButton
            title={notice}
          />
        ))}

      <div className="fsp-info__tile-row">
        <DetailTile title="Tenure summary" icon={Report} fields={summaryFields} />
        {termFields.length > 0 && <DetailTile title="Term" icon={Calendar} fields={termFields} />}
      </div>
      {licenceFields.length > 0 && (
        <DetailTile title="Licence details" icon={TreeView} fields={licenceFields} />
      )}
      {markFields.length > 0 && <DetailTile title="Mark" icon={Stamp} fields={markFields} />}
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
    </div>
  );
};

export default DetailsPanel;
