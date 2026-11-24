import { Button, DatePicker, DatePickerInput, Select, SelectItem, TextInput } from '@carbon/react';
import { useEffect, useState, type FC } from 'react';
import { useNavigate } from 'react-router-dom';

import ManagementUnitComboBox from '@/components/ManagementUnitComboBox';
import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import {
  getDistricts,
  getFileTypes,
  getManagementUnits,
  getRegions,
  type CodeOption,
  type ManagementUnit,
} from '@/services/codeLists';
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
};

/*
 * Legacy FTA100's Add New rules by file type (the backend applies them again on create).
 */

/** The user types the Forest File ID; FTA assigns every other type's, B10's when left blank. */
const ENTERED_ID_TYPES = new Set(['A02', 'A06', 'A30', 'B40']);
/** FTA100 shows Admin Org for file types starting A, U or P. */
const showsAdminOrg = (t: string) => /^[AUP]/.test(t);
/** A04, A44 and A11 files need a district as Admin Org, every other a region. */
const adminOrgIsDistrict = (t: string) => ['A04', 'A44', 'A11'].includes(t);
/** FTA100 hides District for file types starting A, and P01. */
const showsDistrict = (t: string) => t !== '' && !t.startsWith('A') && t !== 'P01';
const licenceToCut = (t: string) => ['B05', 'B06', 'B07'].includes(t);
const needsZone = (t: string) => licenceToCut(t) || t === 'B10';

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
  districts: CodeOption[];
  regions: CodeOption[];
  units: ManagementUnit[];
  details: Record<string, CodeOption[]>;
}

const NO_LISTS: Lists = { fileTypes: [], districts: [], regions: [], units: [], details: {} };

interface Props {
  open: boolean;
  onClose: () => void;
}

/**
 * FTA010 / FTA100 Add New — a new tenure, as a dialog over the current page. Opened from the
 * Tenures menu or the Tenure List; only for a role that may create a tenure (routes/access.ts).
 * It asks for what legacy had no default for, by file type; FTA assigns the ID (except for the
 * types whose ID is typed) and the rest is edited on the new file's Details tab.
 */
const AddTenureModal: FC<Props> = ({ open, onClose }) => {
  const notify = useNotification();
  const navigate = useNavigate();
  const [form, setForm] = useState<Form>(EMPTY);
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);
  const [lists, setLists] = useState<Lists>(NO_LISTS);

  useEffect(() => {
    if (!open) return;
    setForm(EMPTY);
    setErrors({});
    let cancelled = false;
    Promise.allSettled([
      getFileTypes(),
      getDistricts(),
      getRegions(),
      getManagementUnits(),
      getTenureDetailsOptions(),
    ]).then(([types, districts, regions, units, details]) => {
      if (cancelled) return;
      const ok = <T,>(r: PromiseSettledResult<T>, empty: T) =>
        r.status === 'fulfilled' ? r.value : empty;
      setLists({
        fileTypes: ok(types, []),
        districts: ok(districts, []),
        regions: ok(regions, []),
        units: ok(units, []),
        details: ok(details, {}),
      });
      if ([types, districts, regions, units, details].some((r) => r.status === 'rejected')) {
        notify.display({
          kind: 'error',
          title: 'Could not load the lists this dialog needs',
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
  const entersId = ENTERED_ID_TYPES.has(t);
  const optionalId = t === 'B10';
  const fn = t === 'B10' && form.fupType === 'FN';
  const adminOrgOptions = adminOrgIsDistrict(t) ? lists.districts : lists.regions;
  const purposeOptions = lists.details[t === 'B07' ? 'forLicenceToCut' : 'occLicenceToCut'] ?? [];

  const chooseType = (fileType: string) =>
    // A new type asks different questions; keep only what every type shares.
    setForm((f) => ({
      ...EMPTY,
      fileType,
      mgmtUnitType: f.mgmtUnitType,
      mgmtUnitId: f.mgmtUnitId,
      issueDate: f.issueDate,
    }));

  const validate = (): Errors => {
    const e: Errors = {};
    if (!t) e.fileType = 'Choose a file type.';
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
    if ((t === 'B05' || t === 'B06') && !form.quotaType)
      e.quotaType = `Quota type is mandatory if file type is ${t}.`;
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

  const onSubmit = async () => {
    const found = validate();
    setErrors(found);
    if (Object.keys(found).length > 0) return;
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
      });
      notify.display({
        kind: 'success',
        title: 'Tenure created',
        subtitle: `File ${forestFileId} created.`,
        timeout: 5000,
      });
      onClose();
      navigate(`/tenures/${encodeURIComponent(forestFileId)}`);
    } catch (err) {
      notify.display({
        kind: 'error',
        title: 'Could not create tenure',
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
            {lists.fileTypes.map((ft) => (
              <SelectItem key={ft.code} value={ft.code} text={ft.description} />
            ))}
          </Select>
        </div>

        {(entersId || optionalId) &&
          text('fileId', optionalId ? 'Forest File ID (optional)' : 'Forest File ID', 10, true)}

        {showsAdminOrg(t) &&
          select(
            'adminOrg',
            'Admin Organization',
            adminOrgOptions,
            adminOrgIsDistrict(t) ? 'Choose a district' : 'Choose a region',
          )}

        {showsDistrict(t) && select('district', 'District', lists.districts, 'Choose a district')}

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
              t === 'B07' ? 'Quota Type (optional)' : 'Quota Type',
              lists.details.quotaTypes ?? [],
              'Choose a quota type',
            )}
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

        {t && (
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
