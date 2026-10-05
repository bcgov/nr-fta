import {
  Calendar,
  Campsite,
  Edit,
  Information,
  Location,
  Notebook,
  Report,
  Tree,
  Archive,
} from '@carbon/icons-react';
import {
  Button,
  DatePicker,
  DatePickerInput,
  Select,
  SelectItem,
  TextArea,
  TextInput,
} from '@carbon/react';
import { useCallback, useEffect, useState, type FC, type ReactNode } from 'react';

import AsyncBoundary from '@/components/AsyncBoundary';
import DetailTile, { type DetailField } from '@/components/DetailTile';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import type { CodeOption } from '@/services/codeLists';
import {
  getRecProject,
  getRecProjectLookups,
  saveRecProject,
  type RecProject,
  type RecProjectLookups,
  type RecProjectSaveRequest,
} from '@/services/tenure_recproject';
import { formatDate } from '@/utils/formatDate';

import RecProjectSections from './RecProjectSections';

import type { TenurePanelProps } from './panelProps';

const nf = new Intl.NumberFormat('en-CA', { maximumFractionDigits: 4 });

const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;
const date = (v: string | null | undefined) => dash(formatDate(v));
const num = (v: number | null | undefined) => (v == null ? '—' : nf.format(v));
const yesNo = (v: string | null | undefined) => (v === 'Y' ? 'Yes' : v === 'N' ? 'No' : '—');
const coded = (code: string | null, desc: string | null) =>
  code ? (desc ? `${code} - ${desc}` : code) : '—';

/** yyyy-mm-dd in local time — what the backend's LocalDate expects. */
const toIsoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

/**
 * Options for a code select: the list, plus the stored value when the list no
 * longer carries it (an expired code), so the select never shows a value it
 * cannot hold. The backend accepts the stored code unchanged.
 */
const withCurrent = (options: CodeOption[], code: string, label: string) =>
  code && !options.some((o) => o.code === code)
    ? [{ code, description: label }, ...options]
    : options;

/** Legacy FTA701's field limits (input maxlengths and the form's validators). */
const MAX = {
  projectName: 100,
  siteLocation: 500,
  bordenNo: 200,
  aiaComment: 2000,
  siteDescription: 500,
} as const;

interface Form {
  projectName: string;
  riskRatingCode: string;
  projectEstablishedDate: string;
  siteLocation: string;
  utmZone: string;
  utmEasting: string;
  utmNorthing: string;
  rightOfWay: string;
  featureCode: string;
  userDaysCode: string;
  maintainStdCode: string;
  campHostInd: string;
  overflowCampsites: string;
  lowMobilityAccessInd: string;
  recreationViewInd: string;
  resourceFeatureInd: string;
  controlAccessCode: string;
  lastRecInspectionDate: string;
  lastHzrdTreeAssessDate: string;
  archImpactAssessInd: string;
  archImpactDate: string;
  bordenNo: string;
  aiaComment: string;
  siteDescription: string;
}

type Errors = Partial<Record<keyof Form, string>>;

const s = (v: string | number | null | undefined) => (v == null ? '' : String(v));

const createForm = (p: RecProject): Form => ({
  projectName: s(p.projectName),
  riskRatingCode: s(p.riskRatingCode),
  projectEstablishedDate: s(p.projectEstablishedDate),
  siteLocation: s(p.siteLocation),
  utmZone: s(p.utmZone),
  utmEasting: s(p.utmEasting),
  utmNorthing: s(p.utmNorthing),
  rightOfWay: s(p.rightOfWay),
  featureCode: s(p.featureCode),
  userDaysCode: s(p.userDaysCode),
  maintainStdCode: s(p.maintainStdCode),
  campHostInd: s(p.campHostInd),
  overflowCampsites: s(p.overflowCampsites),
  // Legacy's selects default these to N (Resource Feature to Y) on a new project.
  lowMobilityAccessInd: s(p.lowMobilityAccessInd) || 'N',
  recreationViewInd: s(p.recreationViewInd) || 'N',
  resourceFeatureInd: s(p.resourceFeatureInd) || 'Y',
  controlAccessCode: s(p.controlAccessCode),
  lastRecInspectionDate: s(p.lastRecInspectionDate),
  lastHzrdTreeAssessDate: s(p.lastHzrdTreeAssessDate),
  archImpactAssessInd: s(p.archImpactAssessInd),
  archImpactDate: s(p.archImpactDate),
  bordenNo: s(p.bordenNo),
  aiaComment: s(p.aiaComment),
  siteDescription: s(p.siteDescription),
});

const INTEGER = /^\d+$/;

/** The legacy form's checks (Fta701MaintainProjectForm, Save); the backend repeats them. */
const validate = (f: Form, trail: boolean): Errors => {
  const e: Errors = {};
  if (!f.projectName.trim()) e.projectName = 'Project Name is required.';
  else if (f.projectName.trim().length > MAX.projectName)
    e.projectName = `At most ${MAX.projectName} characters.`;
  const int = (
    key: 'overflowCampsites' | 'utmZone' | 'utmEasting' | 'utmNorthing',
    max: number,
  ) => {
    const v = f[key].trim();
    if (!v) return;
    if (!INTEGER.test(v)) e[key] = 'Must be a whole number.';
    else if (Number(v) > max) e[key] = `Must be between 0 and ${max}.`;
  };
  int('overflowCampsites', 99999);
  int('utmZone', 99999);
  int('utmEasting', 9999999999);
  int('utmNorthing', 9999999999);
  if (f.recreationViewInd === 'Y') {
    const msg = 'Required when Display to Website is Yes.';
    if (!f.utmZone.trim()) e.utmZone ??= msg;
    if (!f.utmEasting.trim()) e.utmEasting ??= msg;
    if (!f.utmNorthing.trim()) e.utmNorthing ??= msg;
  }
  if (
    !e.utmZone &&
    !e.utmEasting &&
    !e.utmNorthing &&
    f.utmEasting.trim() &&
    f.utmNorthing.trim()
  ) {
    const zone = Number(f.utmZone.trim());
    if (!f.utmZone.trim() || zone < 7 || zone > 11) e.utmZone = 'BC UTM zones are 7 to 11.';
  }
  if (trail) {
    const v = f.rightOfWay.trim();
    if (!v) e.rightOfWay = 'Right of Way is required for a trail.';
    else if (!/^\d+(\.\d)?$/.test(v)) e.rightOfWay = 'Enter metres, with at most 1 decimal place.';
    else if (Number(v) > 99999.9) e.rightOfWay = 'Must be between 0.0 and 99999.9.';
  }
  if (f.siteLocation.trim().length > MAX.siteLocation)
    e.siteLocation = `At most ${MAX.siteLocation} characters.`;
  if (f.bordenNo.trim().length > MAX.bordenNo) e.bordenNo = `At most ${MAX.bordenNo} characters.`;
  if (f.aiaComment.trim().length > MAX.aiaComment)
    e.aiaComment = `At most ${MAX.aiaComment} characters.`;
  if (f.siteDescription.trim().length > MAX.siteDescription)
    e.siteDescription = `At most ${MAX.siteDescription} characters.`;
  return e;
};

const orNull = (v: string) => (v.trim() ? v.trim() : null);

const toRequest = (f: Form, p: RecProject): RecProjectSaveRequest => ({
  revisionCount: p.exists ? p.revisionCount : null,
  projectName: f.projectName.trim(),
  riskRatingCode: orNull(f.riskRatingCode),
  projectEstablishedDate: orNull(f.projectEstablishedDate),
  siteLocation: orNull(f.siteLocation),
  utmZone: orNull(f.utmZone),
  utmEasting: orNull(f.utmEasting),
  utmNorthing: orNull(f.utmNorthing),
  rightOfWay: p.trailProject ? orNull(f.rightOfWay) : null,
  featureCode: orNull(f.featureCode),
  userDaysCode: orNull(f.userDaysCode),
  maintainStdCode: orNull(f.maintainStdCode),
  campHostInd: orNull(f.campHostInd),
  overflowCampsites: orNull(f.overflowCampsites),
  lowMobilityAccessInd: orNull(f.lowMobilityAccessInd),
  recreationViewInd: orNull(f.recreationViewInd),
  resourceFeatureInd: orNull(f.resourceFeatureInd),
  controlAccessCode: orNull(f.controlAccessCode),
  lastRecInspectionDate: orNull(f.lastRecInspectionDate),
  lastHzrdTreeAssessDate: orNull(f.lastHzrdTreeAssessDate),
  archImpactAssessInd: orNull(f.archImpactAssessInd),
  archImpactDate: orNull(f.archImpactDate),
  bordenNo: orNull(f.bordenNo),
  aiaComment: orNull(f.aiaComment),
  siteDescription: orNull(f.siteDescription),
});

const YES_NO = [
  { code: 'Y', description: 'Yes' },
  { code: 'N', description: 'No' },
];

interface DetailsProps {
  project: RecProject;
  forestFileId: string;
  canEdit: boolean;
  onEditingChange: (editing: boolean) => void;
  onSaved: () => void;
}

/**
 * The project details of FTA701, as the private-mark Mark application tab:
 * tiles of read-only values; one Edit button above them opens every field in
 * place, with Cancel / Save changes beneath. A file with no project record yet
 * shows an empty state whose button opens the same form to create it (legacy's
 * Save adds the RECREATION_PROJECT row when there is none).
 */
const RecProjectDetails: FC<DetailsProps> = ({
  project: p,
  forestFileId,
  canEdit,
  onEditingChange,
  onSaved,
}) => {
  const { display } = useNotification();
  const rules = p.rules;
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<Form>(() => createForm(p));
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);
  const [codes, setCodes] = useState<RecProjectLookups | null>(null);

  useEffect(() => onEditingChange(editing), [editing, onEditingChange]);

  // The dropdowns' lists, once the form opens (cached for the session).
  useEffect(() => {
    if (!editing || codes) return;
    let cancelled = false;
    getRecProjectLookups()
      .then((l) => {
        if (!cancelled) setCodes(l);
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
  }, [editing, codes, display]);

  const set = <K extends keyof Form>(key: K, value: Form[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    setErrors((prev) => {
      if (!prev[key]) return prev;
      const next = { ...prev };
      delete next[key];
      return next;
    });
  };

  const onEdit = () => {
    setForm(createForm(p));
    setErrors({});
    setEditing(true);
  };

  const onCancel = () => {
    setForm(createForm(p));
    setErrors({});
    setEditing(false);
  };

  const onSave = async () => {
    const found = validate(form, p.trailProject);
    if (Object.keys(found).length > 0) {
      setErrors(found);
      display({ kind: 'error', title: 'Check the highlighted fields', timeout: 6000 });
      return;
    }
    setSaving(true);
    try {
      const result = await saveRecProject(forestFileId, toRequest(form, p));
      setEditing(false);
      display({
        kind: 'success',
        title: p.exists ? 'Project details saved' : 'Project details created',
        subtitle: result?.warnings?.length ? result.warnings.join(' ') : undefined,
        timeout: result?.warnings?.length ? 9000 : 5000,
      });
      onSaved();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not save the project details',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  // ─── Field builders: the read-only value, or its input while editing ──────

  const field = (
    label: string,
    view: ReactNode,
    editor: (() => ReactNode) | null,
    opts: { optional?: boolean; wide?: boolean; rowStart?: boolean; span2?: boolean } = {},
  ): DetailField => {
    const open = editing && editor !== null;
    return {
      label: open && opts.optional ? `${label} (optional)` : label,
      value: open ? editor() : view,
      wide: opts.wide,
      rowStart: opts.rowStart,
      span2: opts.span2 && editing,
    };
  };

  const text = (
    key: keyof Form,
    label: string,
    maxLength?: number,
    size: 'cell' | 'sm' = 'cell',
  ) => (
    <div className={`detail-edit__input detail-edit__input--${size}`}>
      <TextInput
        id={`rec-edit-${key}`}
        labelText={label}
        hideLabel
        value={form[key]}
        maxLength={maxLength}
        invalid={!!errors[key]}
        invalidText={errors[key]}
        disabled={saving}
        onChange={(e) => set(key, e.target.value)}
      />
    </div>
  );

  const select = (
    key: keyof Form,
    label: string,
    options: CodeOption[],
    blank: string | null = 'None',
  ) => (
    <div className="detail-edit__input detail-edit__input--cell">
      <Select
        id={`rec-edit-${key}`}
        labelText={label}
        hideLabel
        value={form[key]}
        invalid={!!errors[key]}
        invalidText={errors[key]}
        disabled={saving}
        onChange={(e) => set(key, e.target.value)}
      >
        {blank !== null && <SelectItem value="" text={blank} />}
        {options.map((o) => (
          <SelectItem key={o.code} value={o.code} text={o.description || o.code} />
        ))}
      </Select>
    </div>
  );

  const codeSelect = (
    key: keyof Form,
    label: string,
    list: keyof RecProjectLookups,
    storedCode: string | null,
    storedDesc: string | null,
  ) =>
    select(
      key,
      label,
      withCurrent(
        (codes?.[list] as CodeOption[] | undefined) ?? [],
        storedCode ?? '',
        coded(storedCode, storedDesc),
      ),
    );

  const dateInput = (key: keyof Form, label: string) => (
    <div className="detail-edit__input detail-edit__input--cell">
      <DatePicker
        datePickerType="single"
        dateFormat="Y-m-d"
        value={form[key]}
        // `invalid` belongs on the wrapper: Carbon clones it onto the input.
        invalid={!!errors[key]}
        onChange={(dates: Date[]) => set(key, dates[0] ? toIsoDate(dates[0]) : '')}
      >
        <DatePickerInput
          id={`rec-edit-${key}`}
          labelText={label}
          hideLabel
          placeholder="yyyy-mm-dd"
          invalidText={errors[key]}
          disabled={saving}
          // Emptying the text does not reliably fire the picker's onChange.
          onChange={(e) => {
            if (e.target.value.trim() === '') set(key, '');
          }}
        />
      </DatePicker>
    </div>
  );

  const area = (key: keyof Form, label: string, maxLength: number, rows = 3) => (
    <TextArea
      id={`rec-edit-${key}`}
      labelText={label}
      hideLabel
      rows={rows}
      className="detail-edit__textarea"
      value={form[key]}
      maxLength={maxLength}
      invalid={!!errors[key]}
      invalidText={errors[key]}
      disabled={saving}
      onChange={(e) => set(key, e.target.value)}
    />
  );

  // ─── Sections ─────────────────────────────────────────────────────────────

  const projectType = coded(p.projectTypeCode, p.projectTypeDesc);

  const projectFields: DetailField[] = [
    field('Project Name', dash(p.projectName), () =>
      text('projectName', 'Project Name', MAX.projectName),
    ),
    { label: 'Project Type', value: projectType },
    field(
      'Risk Rating',
      coded(p.riskRatingCode, p.riskRatingDesc),
      () =>
        codeSelect(
          'riskRatingCode',
          'Risk Rating',
          'riskRatings',
          p.riskRatingCode,
          p.riskRatingDesc,
        ),
      { optional: true },
    ),
    field(
      'Project Established',
      date(p.projectEstablishedDate),
      () => dateInput('projectEstablishedDate', 'Project Established'),
      { optional: true },
    ),
  ];

  const locationFields: DetailField[] = [
    field(
      'Closest Community',
      dash(p.siteLocation),
      () => text('siteLocation', 'Closest Community', MAX.siteLocation),
      { optional: true },
    ),
    field('UTM Zone', dash(p.utmZone), () => text('utmZone', 'UTM Zone', 5, 'sm'), {
      optional: form.recreationViewInd !== 'Y',
    }),
    field('UTM Easting', dash(p.utmEasting), () => text('utmEasting', 'UTM Easting', 10), {
      optional: form.recreationViewInd !== 'Y',
    }),
    field('UTM Northing', dash(p.utmNorthing), () => text('utmNorthing', 'UTM Northing', 10), {
      optional: form.recreationViewInd !== 'Y',
    }),
  ];

  const featureFields: DetailField[] = [
    { label: 'Total Trail Length (km)', value: num(p.projectLength) },
    { label: 'Total Area (ha)', value: num(p.projectArea) },
    // Entered (and required) only on a trail; otherwise shown as stored.
    field(
      'Right of Way Width (m)',
      num(p.rightOfWay),
      p.trailProject ? () => text('rightOfWay', 'Right of Way Width (m)', 7, 'sm') : null,
    ),
    field(
      'Significant Recreation Feature',
      coded(p.featureCode, p.featureDesc),
      () =>
        codeSelect(
          'featureCode',
          'Significant Recreation Feature',
          'features',
          p.featureCode,
          p.featureDesc,
        ),
      { optional: true, rowStart: true },
    ),
    field(
      'User Days',
      coded(p.userDaysCode, p.userDaysDesc),
      () => codeSelect('userDaysCode', 'User Days', 'userDays', p.userDaysCode, p.userDaysDesc),
      { optional: true },
    ),
    field(
      'Maintenance Standard',
      coded(p.maintainStdCode, p.maintainStdDesc),
      () =>
        codeSelect(
          'maintainStdCode',
          'Maintenance Standard',
          'maintainStandards',
          p.maintainStdCode,
          p.maintainStdDesc,
        ),
      { optional: true },
    ),
  ];

  const campsiteFields: DetailField[] = [
    { label: 'Defined Campsites', value: p.definedCampsites },
    field(
      'Camp Host/Operator',
      yesNo(p.campHostInd),
      () => select('campHostInd', 'Camp Host/Operator', YES_NO),
      { optional: true },
    ),
    field(
      'Overflow Campsites',
      dash(p.overflowCampsites),
      () => text('overflowCampsites', 'Overflow Campsites', 5, 'sm'),
      { optional: true },
    ),
    field('Low Mobility Access', yesNo(p.lowMobilityAccessInd), () =>
      select('lowMobilityAccessInd', 'Low Mobility Access', YES_NO, null),
    ),
  ];

  const additionalFields: DetailField[] = [
    field('Display to Website', yesNo(p.recreationViewInd), () =>
      select('recreationViewInd', 'Display to Website', YES_NO, null),
    ),
    field('Resource Feature', yesNo(p.resourceFeatureInd), () =>
      select('resourceFeatureInd', 'Resource Feature', YES_NO, null),
    ),
    { label: 'Associated Files', value: p.associatedFiles ? 'Yes' : 'No' },
    field(
      'Controlled Access Type',
      coded(p.controlAccessCode, p.controlAccessDesc),
      () =>
        codeSelect(
          'controlAccessCode',
          'Controlled Access Type',
          'controlAccess',
          p.controlAccessCode,
          p.controlAccessDesc,
        ),
      { optional: true },
    ),
  ];

  const inspectionFields: DetailField[] = [
    field(
      'Last Rec. Inspection',
      date(p.lastRecInspectionDate),
      () => dateInput('lastRecInspectionDate', 'Last Rec. Inspection'),
      { optional: true },
    ),
    field(
      'Last Hazard Tree Assessment',
      date(p.lastHzrdTreeAssessDate),
      () => dateInput('lastHzrdTreeAssessDate', 'Last Hazard Tree Assessment'),
      { optional: true },
    ),
  ];

  const aiaFields: DetailField[] = [
    field(
      'AIA Indicator',
      yesNo(p.archImpactAssessInd),
      () => select('archImpactAssessInd', 'AIA Indicator', YES_NO),
      { optional: true },
    ),
    field('AIA Date', date(p.archImpactDate), () => dateInput('archImpactDate', 'AIA Date'), {
      optional: true,
    }),
    field(
      'Borden #',
      <span className="detail-tab__long-text">{dash(p.bordenNo)}</span>,
      () => area('bordenNo', 'Borden #', MAX.bordenNo, 2),
      { optional: true, wide: true },
    ),
    field(
      'Comment',
      <span className="detail-tab__long-text">{dash(p.aiaComment)}</span>,
      () => area('aiaComment', 'Comment', MAX.aiaComment, 4),
      { optional: true, wide: true },
    ),
  ];

  const noteFields: DetailField[] = [
    field(
      'Field Note',
      <span className="detail-tab__long-text">{dash(p.siteDescription)}</span>,
      () => area('siteDescription', 'Field Note', MAX.siteDescription, 4),
      { optional: true, wide: true },
    ),
  ];

  // Why the project can't be saved — not shown when it is the role that can't.
  const reason = canEdit && !rules.project ? rules.projectReason : null;

  if (!p.exists && !editing) {
    return (
      <EmptyState
        icon={<Campsite size={48} />}
        title="No project details yet"
        body={
          <>
            {p.projectTypeCode ? `Project type ${projectType}. ` : ''}
            Save the project details before adding fees, access types, districts and establishment
            orders.
          </>
        }
        action={
          <div className="detail-tab__empty-action">
            <Button kind="primary" disabled={!canEdit || !rules.project} onClick={onEdit}>
              Add project details
            </Button>
            {reason && <p className="detail-tab__reason">{reason}</p>}
          </div>
        }
      />
    );
  }

  return (
    <div className={editing ? 'detail-edit' : undefined}>
      {canEdit && (
        <div className="detail-edit__toolbar">
          {editing ? (
            <p className="detail-edit__strap">All fields are required unless marked optional.</p>
          ) : (
            <>
              {reason && <p className="detail-edit__reason">{reason}</p>}
              <Button
                kind="tertiary"
                size="sm"
                renderIcon={Edit}
                disabled={!rules.project}
                onClick={onEdit}
              >
                Edit
              </Button>
            </>
          )}
        </div>
      )}

      <div className="fsp-info__tile-row">
        <DetailTile title="Project" icon={Report} fields={projectFields} />
        <DetailTile title="Location" icon={Location} fields={locationFields} />
      </div>
      <div className="fsp-info__tile-row">
        <DetailTile title="Features" icon={Tree} fields={featureFields} />
        <DetailTile title="Campsites" icon={Campsite} fields={campsiteFields} />
      </div>
      <div className="fsp-info__tile-row">
        <DetailTile title="Additional info" icon={Information} fields={additionalFields} />
        <DetailTile
          title="Inspection / assessment dates"
          icon={Calendar}
          fields={inspectionFields}
        />
      </div>
      <DetailTile title="Archaeological impact assessment" icon={Archive} fields={aiaFields} />
      <DetailTile title="Field notes" icon={Notebook} fields={noteFields} />

      {editing && (
        <div className="detail-edit__actions">
          <Button kind="tertiary" size="md" disabled={saving} onClick={onCancel}>
            Cancel
          </Button>
          <Button kind="primary" size="md" disabled={saving} onClick={() => void onSave()}>
            {saving ? 'Saving…' : p.exists ? 'Save changes' : 'Create project'}
          </Button>
        </div>
      )}
    </div>
  );
};

/**
 * The Rec project tab of the tenure detail — legacy FTA701 (Recreation Project
 * Details): the project details (tiles with in-place edit, or create when the
 * file has none), then its recreation districts, fees, access types and
 * establishment orders. Applies to recreation files (RECnnnn) only; any other
 * file gets an empty state saying so.
 */
const RecProjectPanel: FC<TenurePanelProps> = ({ tenure, canEdit }) => {
  const forestFileId = tenure.forestFileId;
  const fetcher = useCallback(() => getRecProject(forestFileId), [forestFileId]);
  const { data, loading, error, reload } = useApiResource(fetcher, [forestFileId]);
  const [editing, setEditing] = useState(false);

  return (
    <div className="fsp-info__tab-panel">
      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Loading the recreation project…"
      >
        {data &&
          (!data.applicable ? (
            <EmptyState
              icon={<Campsite size={48} />}
              title="Not a recreation project"
              body={data.notApplicableReason ?? 'This file has no recreation project.'}
            />
          ) : (
            <>
              <RecProjectDetails
                project={data}
                forestFileId={forestFileId}
                canEdit={canEdit}
                onEditingChange={setEditing}
                onSaved={reload}
              />
              {data.exists && (
                <RecProjectSections
                  project={data}
                  forestFileId={forestFileId}
                  canEdit={canEdit}
                  locked={editing}
                  onChanged={reload}
                />
              )}
            </>
          ))}
      </AsyncBoundary>
    </div>
  );
};

export default RecProjectPanel;
