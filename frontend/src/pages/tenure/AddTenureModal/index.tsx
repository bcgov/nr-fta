import { Button, DatePicker, DatePickerInput, Select, SelectItem, TextInput } from '@carbon/react';
import { useEffect, useState, type FC } from 'react';
import { useNavigate } from 'react-router-dom';

import DateField from '@/components/DateField';
import ManagementUnitComboBox from '@/components/ManagementUnitComboBox';
import { Modal } from '@/components/Modal';
import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import { useDefaultOrgUnit } from '@/hooks/useDefaultOrgUnit';
import { canEdit, holdsRangeRole, mayEditRangeDistrict } from '@/routes/access';
import {
  getAddNewFileTypes,
  getDistricts,
  getManagementUnits,
  getTenureAdminOrgUnits,
  orgUnitCodeOf,
  type CodeOption,
  type ManagementUnit,
} from '@/services/codeLists';
import {
  createRangeTenure,
  getRangeCreateTypes,
  getRangeZonesWithContact,
  type RangeZone,
} from '@/services/range_tenure_tab';
import { getTenureDetailsOptions } from '@/services/tenure_details';
import { createTenure } from '@/services/tenure_write';
import { parseTypedDate, TYPED_DATE_PATTERN } from '@/utils/typedDate';
import './AddTenureModal.scss';

interface Form {
  fileType: string;
  fileId: string;
  adminOrg: string;
  district: string;
  mgmtUnitType: string;
  mgmtUnitId: string;
  issueDate: string;
  purpose: string;
  zone: string;
  location: string;
  quotaType: string;
  initialArea: string;
  fupType: string;
  fupFnUsage: string;
  fupCedar: string;
  fupTreaty: string;
  bctsFund: string;
  paymentMethod: string;
  spatialLater: string;
  salvage: string;
  /** A range tenure's Status Date and File Name (FTA100R's Add New). */
  statusDate: string;
  fileName: string;
}

type Errors = Partial<Record<keyof Form, string>>;

const EMPTY: Form = {
  fileType: '',
  fileId: '',
  adminOrg: '',
  district: '',
  mgmtUnitType: '',
  mgmtUnitId: '',
  issueDate: '',
  purpose: '',
  zone: '',
  location: '',
  quotaType: '',
  initialArea: '',
  fupType: '',
  fupFnUsage: '',
  fupCedar: '',
  fupTreaty: '',
  bctsFund: '',
  paymentMethod: '',
  spatialLater: '',
  salvage: '',
  statusDate: '',
  fileName: '',
};

/**
 * The range types FTA010 handed to FTA100R's Add New (Fta010AddNewTenureAction): a new range
 * tenure is PA, in a district, with a management unit, a zone of that district and a file name;
 * its term is entered on the range tenure once it leaves PA.
 */
const RANGE_TYPES = new Set(['E01', 'E02', 'H01', 'H02']);

/*
 * Legacy FTA100's Add New rules by file type (the backend applies them again on create).
 */

/** The user types the Forest File ID; FTA assigns every other type's, B10's when left blank. */
const ENTERED_ID_TYPES = new Set(['A02', 'A06', 'A30', 'B40']);
/** FTA100 asks for Admin Org on file types starting A, U or P, except a new U1 or U2. */
const showsAdminOrg = (t: string) => /^[AUP]/.test(t) && t !== 'U1' && t !== 'U2';
/** A04, A44 and A11 files need a district as Admin Org, every other a region. */
const adminOrgIsDistrict = (t: string) => ['A04', 'A44', 'A11'].includes(t);
/** FTA100 hides District for file types starting A, and P01. */
const showsDistrict = (t: string) => t !== '' && !t.startsWith('A') && t !== 'P01';
const licenceToCut = (t: string) => ['B05', 'B06', 'B07'].includes(t);
const needsZone = (t: string) => licenceToCut(t) || t === 'B10';
/** Only a B07 asks BCTS Fund and Payment Method. */
const asksBctsFund = (t: string) => t === 'B07';
/** A01, A18 and A41 ask Salvage. */
const asksSalvage = (t: string) => ['A01', 'A18', 'A41'].includes(t);
/** GET_DEFAULTS: the management unit type a new file of this type starts with. */
const DEFAULT_MGMT_UNIT_TYPE: Record<string, string> = {
  A04: 'F',
  A44: 'F',
  A02: 'T',
  A28: 'C',
  A29: 'M',
};

/** Legacy FTA100's format checks for an entered ID. */
const fileIdFormatError = (fileType: string, id: string): string | undefined => {
  if (fileType === 'A06' && !/^T\d{4}$/.test(id))
    return 'File must start with letter T and be followed by 4 numbers.';
  if (fileType === 'A02' && !/^TFL\d{2}$/.test(id))
    return 'File must start with TFL and be followed by 2 numbers.';
  if (fileType === 'A30' && !/^T[A-Z]{2}\d{2}$/.test(id))
    return 'File must be in the format TAANN, where AA is the last 2 characters of a district within the Admin Org.';
  return undefined;
};

const YES_NO = [
  { code: 'Y', description: 'Yes' },
  { code: 'N', description: 'No' },
];

/** yyyy-mm-dd in local time. */
const toIsoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

interface Lists {
  fileTypes: CodeOption[];
  /** FTA010's range types, which FTA100R creates. */
  rangeTypes: CodeOption[];
  districts: CodeOption[];
  /** FTA100's Admin Org list: current districts, regions, then HQ units. */
  adminOrgs: CodeOption[];
  units: ManagementUnit[];
  details: Record<string, CodeOption[]>;
}

const NO_LISTS: Lists = {
  fileTypes: [],
  rangeTypes: [],
  districts: [],
  adminOrgs: [],
  units: [],
  details: {},
};

interface Props {
  open: boolean;
  onClose: () => void;
}

/**
 * FTA010 / FTA100 Add New — a new tenure, as a dialog over the current page. Opened from the
 * Tenures menu or the Tenure List; only for a role that may create a tenure (routes/access.ts).
 * It asks for what legacy had no default for, by file type; FTA assigns the ID (except for the
 * types whose ID is typed) and the rest is edited on the new file's Details tab.
 *
 * A range type (E01, E02, H01, H02) is created as FTA100R's Add New and opens the new range
 * tenure. A range role, which legacy let add only range types (FTA010's districtRangeUser), is
 * offered only those, in the districts it may edit.
 */
const AddTenureModal: FC<Props> = ({ open, onClose }) => {
  const notify = useNotification();
  const navigate = useNavigate();
  const [form, setForm] = useState<Form>(EMPTY);
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);
  const [lists, setLists] = useState<Lists>(NO_LISTS);
  const [rangeZones, setRangeZones] = useState<RangeZone[]>([]);
  const { user } = useAuth();
  const mayCreateTimber = canEdit(user);
  const mayCreateRange = mayCreateTimber || holdsRangeRole(user);

  useEffect(() => {
    if (!open) return;
    setForm(EMPTY);
    setErrors({});
    let cancelled = false;
    // A range role reads only what a range type needs.
    const timber = <T,>(load: () => Promise<T>, empty: T) =>
      mayCreateTimber ? load() : Promise.resolve(empty);
    Promise.allSettled([
      timber(getAddNewFileTypes, [] as CodeOption[]),
      getDistricts(),
      timber(getTenureAdminOrgUnits, [] as CodeOption[]),
      getManagementUnits(),
      timber(getTenureDetailsOptions, {} as Record<string, CodeOption[]>),
      mayCreateRange ? getRangeCreateTypes() : Promise.resolve([] as CodeOption[]),
    ]).then(([types, districts, adminOrgs, units, details, rangeTypes]) => {
      if (cancelled) return;
      const ok = <T,>(r: PromiseSettledResult<T>, empty: T) =>
        r.status === 'fulfilled' ? r.value : empty;
      setLists({
        fileTypes: ok(types, []),
        rangeTypes: ok(rangeTypes, []),
        districts: ok(districts, []),
        adminOrgs: ok(adminOrgs, []),
        units: ok(units, []),
        details: ok(details, {}),
      });
      const all = [types, districts, adminOrgs, units, details, rangeTypes];
      if (all.some((r) => r.status === 'rejected')) {
        notify.display({
          kind: 'error',
          title: 'Could not load the lists this dialog needs.',
          subtitle: 'Close the dialog and open it again to retry.',
          timeout: 6000,
        });
      }
    });
    return () => {
      cancelled = true;
    };
    // notify is stable for the page's life.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const set = (field: keyof Form, value: string) => {
    setForm((f) => ({ ...f, [field]: value }));
    setErrors((e) => ({ ...e, [field]: undefined }));
  };

  const t = form.fileType;
  const range = RANGE_TYPES.has(t);
  const entersId = ENTERED_ID_TYPES.has(t);
  const optionalId = t === 'B10';
  const fn = t === 'B10' && form.fupType === 'FN';
  // FTA100's list narrowed to the level Save accepts for this type (the backend checks it too).
  const adminOrgOptions = lists.adminOrgs.filter((o) =>
    orgUnitCodeOf(o).startsWith(adminOrgIsDistrict(t) ? 'D' : 'R'),
  );
  const purposeOptions = lists.details[t === 'B07' ? 'forLicenceToCut' : 'occLicenceToCut'] ?? [];
  // FTA100 accepts only A (not a cash sale) or C (a cash sale).
  const paymentOptions = (lists.details.paymentMethods ?? []).filter((o) =>
    ['A', 'C'].includes(o.code),
  );

  // FTA100's setDefaults, from the user's default org unit (Org Unit Maintenance): District is the
  // user's org unit; Admin Org is too for A04, A44, A06, A11 and A18 files, and otherwise the
  // user's region when the user's org unit is a district. Only where the type's list has it.
  const userDefault = useDefaultOrgUnit();
  const orgDefaults = (fileType: string): Pick<Form, 'district' | 'adminOrg'> => {
    const no = userDefault?.orgUnitNo;
    if (!no) return { district: '', adminOrg: '' };
    const ownOrg =
      ['A04', 'A44', 'A06', 'A11', 'A18'].includes(fileType) || userDefault.orgLevelCode !== 'D';
    const adminOrg = ownOrg ? no : userDefault.rollupRegionNo;
    const level = adminOrgIsDistrict(fileType) ? 'D' : 'R';
    return {
      district: showsDistrict(fileType) && lists.districts.some((o) => o.code === no) ? no : '',
      adminOrg:
        showsAdminOrg(fileType) &&
        lists.adminOrgs.some((o) => o.code === adminOrg && orgUnitCodeOf(o).startsWith(level))
          ? (adminOrg ?? '')
          : '',
    };
  };

  // The file types this user may add: FTA010's list, its range types FTA100R's.
  const fileTypeOptions = [...lists.fileTypes, ...lists.rangeTypes].sort((a, b) =>
    a.code.localeCompare(b.code),
  );
  // A range tenure's district: one the user may edit (FTA100R's district was the user's own).
  const rangeDistricts = lists.districts.filter((o) =>
    mayEditRangeDistrict(user, orgUnitCodeOf(o)),
  );

  /** The range zones of a district, for its Zone dropdown. */
  const loadRangeZones = (districtNo: string) => {
    setRangeZones([]);
    if (!districtNo) return;
    getRangeZonesWithContact(districtNo)
      .then(setRangeZones)
      .catch(() => setRangeZones([]));
  };

  const chooseRangeType = (fileType: string) => {
    // FTA100R's Add New defaults: the user's district, management unit type Q (GET_DEFAULTS),
    // Status PA and the Status Date today (setDefaults).
    const no = userDefault?.orgUnitNo ?? '';
    const district = rangeDistricts.some((o) => o.code === no) ? no : '';
    setForm({ ...EMPTY, fileType, district, mgmtUnitType: 'Q' });
    setErrors({});
    loadRangeZones(district);
  };

  const chooseType = (fileType: string) => {
    if (RANGE_TYPES.has(fileType)) {
      chooseRangeType(fileType);
      return;
    }
    // A new type asks different questions; keep only what every type shares, and start the
    // management unit at the type's default where it has one.
    const defaultMuType = DEFAULT_MGMT_UNIT_TYPE[fileType];
    setForm((f) => ({
      ...EMPTY,
      ...orgDefaults(fileType),
      fileType,
      mgmtUnitType: defaultMuType ?? f.mgmtUnitType,
      mgmtUnitId: defaultMuType ? '' : f.mgmtUnitId,
      issueDate: f.issueDate,
    }));
    setErrors({});
  };

  const validate = (): Errors => {
    const e: Errors = {};
    if (!t) e.fileType = 'Choose a file type.';
    if (range) {
      if (!form.district) e.district = 'District is mandatory.';
      if (!form.mgmtUnitType) e.mgmtUnitType = 'Mgmt Unit Type is mandatory.';
      if (!form.zone) e.zone = 'Zone is mandatory.';
      return e;
    }
    if (entersId) {
      const id = form.fileId.trim();
      e.fileId = id ? fileIdFormatError(t, id) : 'File is mandatory.';
    }
    if (showsAdminOrg(t) && !form.adminOrg) e.adminOrg = 'Choose the Admin Organization.';
    if (showsDistrict(t) && !form.district) e.district = 'Choose a district.';
    if (t && !form.mgmtUnitType) e.mgmtUnitType = 'Choose a management unit.';
    if (licenceToCut(t) && !form.purpose) e.purpose = 'Choose a purpose.';
    if (needsZone(t) && !form.zone.trim()) e.zone = 'Zone is mandatory.';
    if (t === 'B05' && !form.location.trim()) e.location = 'Geographic Location is mandatory.';
    if (licenceToCut(t)) {
      if (!form.quotaType) e.quotaType = `Quota type is mandatory if file type is ${t}.`;
      else if (form.purpose === 'PA' && form.quotaType !== 'P')
        e.quotaType = 'If purpose is PA then quota type can only be P.';
      else if (form.quotaType === 'D' && form.bctsFund !== 'Y')
        e.quotaType =
          'Quota type should be C-Forest Service Reserve if this sale is not BCTS funded.';
      else if (t === 'B07' && form.bctsFund === 'Y' && form.quotaType !== 'D')
        e.quotaType = 'If BCTS Fund is Y then Quota Type must be D.';
      if (!form.spatialLater) e.spatialLater = 'Submit Spatial Later is mandatory.';
    }
    if (asksBctsFund(t)) {
      if (!form.bctsFund) e.bctsFund = 'BCTS Fund is mandatory.';
      if (!form.paymentMethod) e.paymentMethod = 'Payment Method is mandatory.';
      else if (form.paymentMethod === 'C' && ['PA', 'FN'].includes(form.purpose))
        e.paymentMethod =
          'Cash Sales not allowed for PA-Pulpwood or FN-First Nations Forestry Licence To Cut.';
    }
    if (asksSalvage(t) && !form.salvage) e.salvage = 'Salvage is mandatory.';
    if (t === 'A30') {
      const a = form.initialArea.trim();
      if (!a) e.initialArea = 'Initial Area(ha) is mandatory.';
      else if (!/^\d{1,7}(\.\d)?$/.test(a))
        e.initialArea = 'Enter 0 to 9999999.9, with at most 1 decimal place.';
    }
    if (t === 'B10' && !form.fupType) e.fupType = 'FUP Type is mandatory.';
    if (fn && !form.fupFnUsage)
      e.fupFnUsage = 'First Nations Purpose is mandatory when FUP type is FN.';
    (Object.keys(e) as (keyof Form)[]).forEach((k) => e[k] === undefined && delete e[k]);
    return e;
  };

  const onSubmitRange = async () => {
    setSaving(true);
    try {
      const { forestFileId } = await createRangeTenure({
        fileTypeCode: t,
        forestDistrictNo: form.district,
        fileStatusDate: form.statusDate || null,
        mgmtUnitType: form.mgmtUnitType,
        mgmtUnitId: form.mgmtUnitId.trim() || null,
        districtAdminZone: form.zone,
        fileName: form.fileName.trim() || null,
      });
      notify.display({
        kind: 'success',
        title: 'Save successful.',
        subtitle: `Range tenure ${forestFileId} created.`,
        timeout: 5000,
      });
      onClose();
      navigate(`/range/${encodeURIComponent(forestFileId)}`);
    } catch (err) {
      notify.display({
        kind: 'error',
        title: 'Could not create the range tenure.',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  const onSubmit = async () => {
    const found = validate();
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    if (range) {
      await onSubmitRange();
      return;
    }
    setSaving(true);
    const v = (s: string) => s.trim() || null;
    try {
      const { forestFileId } = await createTenure({
        forestFileId: entersId || optionalId ? v(form.fileId) : null,
        fileTypeCode: t,
        adminOrgUnitNo: showsAdminOrg(t) ? v(form.adminOrg) : null,
        districtNo: showsDistrict(t) ? v(form.district) : null,
        mgmtUnitType: v(form.mgmtUnitType),
        mgmtUnitId: v(form.mgmtUnitId),
        issueDate: form.issueDate || null,
        purposeCode: licenceToCut(t) ? v(form.purpose) : null,
        districtAdminZone: needsZone(t) ? v(form.zone) : null,
        location: licenceToCut(t) ? v(form.location) : null,
        quotaTypeCode: licenceToCut(t) ? v(form.quotaType) : null,
        initialArea: t === 'A30' ? v(form.initialArea) : null,
        fupTypeCode: t === 'B10' ? v(form.fupType) : null,
        fupFnUsageCode: fn ? v(form.fupFnUsage) : null,
        fupCedarSpeciesInd: fn ? v(form.fupCedar) : null,
        fupTreatyPurposeInd: fn ? v(form.fupTreaty) : null,
        bctsFundInd: asksBctsFund(t) ? v(form.bctsFund) : null,
        paymentMethodCode: asksBctsFund(t) ? v(form.paymentMethod) : null,
        subSpatialLaterInd: licenceToCut(t) ? v(form.spatialLater) : null,
        salvageInd: asksSalvage(t) ? v(form.salvage) : null,
      });
      notify.display({
        kind: 'success',
        title: 'Tenure created.',
        subtitle: `File ${forestFileId} created.`,
        timeout: 5000,
      });
      onClose();
      navigate(`/tenures/${encodeURIComponent(forestFileId)}`);
    } catch (err) {
      notify.display({
        kind: 'error',
        title: 'Could not create tenure.',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 6000,
      });
    } finally {
      setSaving(false);
    }
  };

  const select = (
    field: keyof Form,
    label: string,
    options: CodeOption[],
    placeholder: string,
    full = false,
  ) => (
    <div className={full ? 'add-tenure__full' : undefined}>
      <Select
        id={`add-tenure-${field}`}
        labelText={label}
        value={form[field]}
        invalid={!!errors[field]}
        invalidText={errors[field]}
        disabled={saving}
        onChange={(e) => set(field, e.target.value)}
      >
        <SelectItem value="" text={placeholder} />
        {options.map((o) => (
          <SelectItem key={o.code} value={o.code} text={o.description} />
        ))}
      </Select>
    </div>
  );

  const text = (field: keyof Form, label: string, maxLength: number, upper = false) => (
    <TextInput
      id={`add-tenure-${field}`}
      labelText={label}
      value={form[field]}
      maxLength={maxLength}
      invalid={!!errors[field]}
      invalidText={errors[field]}
      disabled={saving}
      onChange={(e) => set(field, upper ? e.target.value.toUpperCase() : e.target.value)}
    />
  );

  return (
    <Modal
      open={open}
      // passiveModal + our own action row, as the New Mark Application dialog does.
      passiveModal
      className="add-tenure"
      size="md"
      modalHeading="Add New Tenure"
      onRequestClose={() => {
        if (!saving) onClose();
      }}
      preventCloseOnClickOutside
    >
      <p className="add-tenure__strap">All fields are required unless marked optional.</p>
      <div className="add-tenure__grid">
        <div className="add-tenure__full">
          <Select
            id="add-tenure-fileType"
            labelText="File Type"
            value={t}
            invalid={!!errors.fileType}
            invalidText={errors.fileType}
            disabled={saving}
            onChange={(e) => chooseType(e.target.value)}
          >
            <SelectItem value="" text="Choose a type" />
            {fileTypeOptions.map((ft) => (
              <SelectItem key={ft.code} value={ft.code} text={ft.description} />
            ))}
          </Select>
        </div>

        {range && (
          <>
            <TextInput id="add-tenure-status" labelText="Status" value="PA" readOnly />
            <DateField
              id="add-tenure-statusDate"
              labelText="Status Date (optional)"
              value={form.statusDate}
              disabled={saving}
              onChange={(value) => set('statusDate', value)}
            />
            <div className="add-tenure__full">
              <Select
                id="add-tenure-rangeDistrict"
                labelText="District"
                value={form.district}
                invalid={!!errors.district}
                invalidText={errors.district}
                disabled={saving}
                onChange={(e) => {
                  set('district', e.target.value);
                  set('zone', '');
                  loadRangeZones(e.target.value);
                }}
              >
                <SelectItem value="" text="Choose a district" />
                {rangeDistricts.map((o) => (
                  <SelectItem key={o.code} value={o.code} text={o.description} />
                ))}
              </Select>
            </div>
            <Select
              id="add-tenure-rangeZone"
              labelText="Zone"
              value={form.zone}
              invalid={!!errors.zone}
              invalidText={errors.zone}
              disabled={saving || !form.district}
              onChange={(e) => set('zone', e.target.value)}
            >
              <SelectItem value="" text="Choose a zone" />
              {rangeZones.map((z) => (
                <SelectItem
                  key={z.code}
                  value={z.code}
                  text={[z.code, z.description, z.contact].filter(Boolean).join(' - ')}
                />
              ))}
            </Select>
            {text('fileName', 'File Name (optional)', 100)}
          </>
        )}

        {(entersId || optionalId) &&
          text('fileId', optionalId ? 'Forest File ID (optional)' : 'Forest File ID', 10, true)}

        {showsAdminOrg(t) &&
          select(
            'adminOrg',
            'Admin Organization',
            adminOrgOptions,
            adminOrgIsDistrict(t) ? 'Choose a district' : 'Choose a region',
          )}

        {showsDistrict(t) &&
          !range &&
          select('district', 'District', lists.districts, 'Choose a district')}

        {t && (
          <div className="add-tenure__full">
            <ManagementUnitComboBox
              id="add-tenure-mgmtUnit"
              units={lists.units}
              typeCode={form.mgmtUnitType}
              unitId={form.mgmtUnitId}
              titleText="Management Unit"
              helperText=""
              typeEntrySuffix=""
              invalid={!!errors.mgmtUnitType}
              invalidText={errors.mgmtUnitType}
              disabled={saving}
              onChange={({ mgmtUnitType, mgmtUnitId }) => {
                setForm((f) => ({ ...f, mgmtUnitType, mgmtUnitId }));
                setErrors((e) => ({ ...e, mgmtUnitType: undefined }));
              }}
            />
          </div>
        )}

        {licenceToCut(t) && (
          <>
            {select('purpose', 'Purpose', purposeOptions, 'Choose a purpose')}
            {select(
              'quotaType',
              'Quota Type',
              lists.details.quotaTypes ?? [],
              'Choose a quota type',
            )}
            {asksBctsFund(t) && (
              <>
                {select('bctsFund', 'BCTS Fund', YES_NO, 'Choose')}
                {select('paymentMethod', 'Payment Method', paymentOptions, 'Choose a method')}
              </>
            )}
            {select('spatialLater', 'Submit Spatial Later', YES_NO, 'Choose')}
            <div className="add-tenure__full">
              {text(
                'location',
                t === 'B05' ? 'Geographic Location' : 'Geographic Location (optional)',
                50,
              )}
            </div>
          </>
        )}

        {needsZone(t) && text('zone', 'Zone', 4, true)}

        {asksSalvage(t) && select('salvage', 'Salvage', YES_NO, 'Choose')}

        {t === 'B10' && (
          <>
            {select('fupType', 'FUP Type', lists.details.fupTypes ?? [], 'Choose a FUP type')}
            {fn && (
              <>
                {select(
                  'fupFnUsage',
                  'First Nations Purpose',
                  lists.details.fupFnUsages ?? [],
                  'Choose a purpose',
                )}
                {select('fupCedar', 'Majority Volume Cedar Species', YES_NO, 'Choose')}
                {select('fupTreaty', 'First Nations Treaty Purpose (optional)', YES_NO, 'Choose')}
              </>
            )}
          </>
        )}

        {t === 'A30' && text('initialArea', 'Initial Area (ha)', 9)}

        {t && !range && (
          <div className="add-tenure__date">
            <DatePicker
              datePickerType="single"
              dateFormat="Y-m-d"
              value={form.issueDate}
              onChange={(dates: Date[]) => set('issueDate', dates[0] ? toIsoDate(dates[0]) : '')}
            >
              <DatePickerInput
                id="add-tenure-issueDate"
                labelText="Effective Date (optional)"
                placeholder="yyyy-mm-dd"
                pattern={TYPED_DATE_PATTERN}
                disabled={saving}
                onChange={(e) => {
                  const typed = e.target.value;
                  if (typed.trim() === '') set('issueDate', '');
                  else {
                    const parsed = parseTypedDate(typed);
                    if (parsed) set('issueDate', parsed);
                  }
                }}
              />
            </DatePicker>
          </div>
        )}
      </div>

      <div className="add-tenure__actions">
        <Button kind="tertiary" disabled={saving} onClick={onClose}>
          Cancel
        </Button>
        <Button kind="primary" disabled={saving} onClick={() => void onSubmit()}>
          {saving ? 'Creating…' : 'Create tenure'}
        </Button>
      </div>
    </Modal>
  );
};

export default AddTenureModal;
