import {
  Button,
  DatePicker,
  DatePickerInput,
  Select,
  SelectItem,
  TextArea,
  TextInput,
} from '@carbon/react';
import { useEffect, useState, type FC } from 'react';
import { useNavigate } from 'react-router-dom';

import ClientComboBox from '@/components/ClientComboBox';
import ManagementUnitComboBox from '@/components/ManagementUnitComboBox';
import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import {
  getCascadeSplits,
  getDistrictDefaultCascades,
  getManagementUnits,
  getThreeLetterDistricts,
  orgUnitCodeOf,
  type CodeOption,
  type ManagementUnit,
} from '@/services/codeLists';
import { markDetailPath } from '@/services/mark_detail';
import { createMarkApplication } from '@/services/mark_write';
import { parseTypedDate, TYPED_DATE_PATTERN } from '@/utils/typedDate';

import { MAX, TERM_OPTIONS } from '../MarkDetail/markEditForm';
import './NewMarkApplicationModal.scss';

interface Form {
  applicationDate: string;
  tenureTerm: string;
  forestDistrict: string;
  cascadeSplitCode: string;
  clientNumber: string;
  clientLocnCode: string;
  /** Text typed in the client box without picking a client. */
  clientName: string;
  permitBlockLocn: string;
  permitBlockArea: string;
  bcaaFolioNumber: string;
  mgmtUnitTypeCode: string;
  mgmtUnitId: string;
  mapReferenceReg: string;
  mapReferenceComp: string;
  proofOfCrownOrLegal: string;
}

type Errors = Partial<Record<keyof Form, string>>;

/** yyyy-mm-dd in local time. */
const toIsoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

/**
 * Legacy's new-record defaults (ADD_NEW_DEFAULTS / Fta510PrivateMarkForm.setDefaults):
 * today's application date, area 0.0 and management unit type Z. The initial
 * term starts at 60 months, the usual choice.
 */
const emptyForm = (): Form => ({
  applicationDate: toIsoDate(new Date()),
  tenureTerm: '60',
  forestDistrict: '',
  cascadeSplitCode: '',
  clientNumber: '',
  clientLocnCode: '',
  clientName: '',
  permitBlockLocn: '',
  permitBlockArea: '0.0',
  bcaaFolioNumber: '',
  mgmtUnitTypeCode: 'Z',
  mgmtUnitId: '',
  mapReferenceReg: '',
  mapReferenceComp: '',
  proofOfCrownOrLegal: '',
});

/** The legacy form's "Save" checks for a new record; the backend repeats them. */
const validate = (f: Form): Errors => {
  const e: Errors = {};
  const blank = (v: string) => v.trim() === '';
  if (blank(f.applicationDate)) e.applicationDate = 'Application Date is required.';
  else if (f.applicationDate > toIsoDate(new Date()))
    e.applicationDate = 'Application Date cannot be later than today.';
  if (!TERM_OPTIONS.includes(f.tenureTerm)) e.tenureTerm = 'Choose an initial term.';
  if (blank(f.forestDistrict)) e.forestDistrict = 'District is required.';
  if (blank(f.cascadeSplitCode)) e.cascadeSplitCode = 'Cascade is required.';
  if (!blank(f.clientName) && blank(f.clientNumber))
    e.clientName = 'Pick a client from the list, or clear the field.';
  if (blank(f.permitBlockLocn)) e.permitBlockLocn = 'Geographic Location is required.';
  if (blank(f.permitBlockArea)) e.permitBlockArea = 'Area is required.';
  else if (!/^\d{1,4}(\.\d)?$/.test(f.permitBlockArea.trim()))
    e.permitBlockArea = '0 to 9999.9, at most one decimal place.';
  if (blank(f.bcaaFolioNumber)) e.bcaaFolioNumber = 'LTO PID is required.';
  if (blank(f.mgmtUnitTypeCode)) e.mgmtUnitTypeCode = 'Management Unit is required.';
  if (!blank(f.mapReferenceReg) && !/^\d{1,2}$/.test(f.mapReferenceReg.trim()))
    e.mapReferenceReg = '0 to 99.';
  if (!blank(f.mapReferenceComp) && !/^\d{1,3}$/.test(f.mapReferenceComp.trim()))
    e.mapReferenceComp = '0 to 999.';
  if (blank(f.proofOfCrownOrLegal)) e.proofOfCrownOrLegal = 'Legal is required.';
  return e;
};

/**
 * New applications in these districts default to this cascade, whatever the
 * district's tenure default (DIST_TENR_DEFLT) says: W is West of the Cascades.
 * Keyed by ORG_UNIT_CODE.
 */
const CASCADE_OVERRIDES: Record<string, string> = { DKM: 'W', DCK: 'W' };

const orNull = (v: string) => (v.trim() === '' ? null : v.trim());

interface Props {
  open: boolean;
  onClose: () => void;
}

/**
 * New private mark application — legacy FTA510 "Add New", as a modal: every
 * field legacy stores for a new application (ADD_NEW / create_certificate),
 * with its required ones required. Saved, the new application opens on its
 * detail page, keyed by the certificate number it was given.
 *
 * Marking Requirements and Marking Instrument are not asked for: legacy's form
 * did, but its create never stored them — they are set on the hauling
 * authority once the mark is issued, and edited on the detail page then.
 */
const NewMarkApplicationModal: FC<Props> = ({ open, onClose }) => {
  const navigate = useNavigate();
  // `display`, not the context object: the provider builds a new { display }
  // on every render, so depending on the object re-ran the list fetch (and,
  // in the modal, the form reset) after every render.
  const { display } = useNotification();
  const [form, setForm] = useState<Form>(emptyForm);
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);
  const [districts, setDistricts] = useState<CodeOption[]>([]);
  const [cascades, setCascades] = useState<CodeOption[]>([]);
  // District number to its default cascade (legacy FTA_GET_DEFAULT_CASCADE).
  const [defaultCascades, setDefaultCascades] = useState<Map<string, string>>(new Map());
  const [mgmtUnits, setMgmtUnits] = useState<ManagementUnit[]>([]);

  // A fresh form each time it opens — and only then.
  useEffect(() => {
    if (!open) return;
    setForm(emptyForm());
    setErrors({});
  }, [open]);

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    Promise.all([getThreeLetterDistricts(), getCascadeSplits(), getManagementUnits()])
      .then(([d, c, m]) => {
        if (cancelled) return;
        setDistricts(d);
        setCascades(c);
        setMgmtUnits(m);
      })
      .catch(() => {
        if (!cancelled) {
          display({
            kind: 'error',
            title: 'Could not load the form lists',
            subtitle: 'Close the form and try again.',
            timeout: 6000,
          });
        }
      });
    // Only a convenience: without it the user picks the cascade themselves.
    getDistrictDefaultCascades()
      .then((rows) => {
        if (!cancelled) setDefaultCascades(new Map(rows.map((r) => [r.code, r.description])));
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [open, display]);

  const set = <K extends keyof Form>(key: K, value: Form[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    setErrors((prev) => {
      if (!prev[key]) return prev;
      const next = { ...prev };
      delete next[key];
      return next;
    });
  };

  const onSubmit = async () => {
    const found = validate(form);
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    setSaving(true);
    try {
      const { certificate } = await createMarkApplication({
        applicationDate: form.applicationDate,
        tenureTerm: Number(form.tenureTerm),
        forestDistrict: form.forestDistrict,
        permitBlockLocn: form.permitBlockLocn.trim(),
        proofOfCrownOrLegal: form.proofOfCrownOrLegal.trim(),
        bcaaFolioNumber: form.bcaaFolioNumber.trim(),
        permitBlockArea: Number(form.permitBlockArea),
        mgmtUnitTypeCode: form.mgmtUnitTypeCode,
        mgmtUnitId: orNull(form.mgmtUnitId),
        cascadeSplitCode: form.cascadeSplitCode,
        mapReferenceReg: orNull(form.mapReferenceReg),
        mapReferenceComp: orNull(form.mapReferenceComp),
        clientNumber: orNull(form.clientNumber),
        clientLocnCode: orNull(form.clientLocnCode),
      });
      display({
        kind: 'success',
        title: 'Mark application created',
        subtitle: `Certificate ${certificate}.`,
        timeout: 6000,
      });
      onClose();
      navigate(markDetailPath(null, certificate) ?? '/marks');
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not create the mark application',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  const text = (key: keyof Form, label: string, maxLength?: number, helperText?: string) => (
    <TextInput
      id={`new-mark-${key}`}
      labelText={label}
      value={form[key]}
      maxLength={maxLength}
      helperText={helperText}
      invalid={!!errors[key]}
      invalidText={errors[key]}
      disabled={saving}
      onChange={(e) => set(key, e.target.value)}
    />
  );

  const select = (
    key: keyof Form,
    label: string,
    options: CodeOption[],
    onPicked?: (value: string) => void,
  ) => (
    <Select
      id={`new-mark-${key}`}
      labelText={label}
      value={form[key]}
      invalid={!!errors[key]}
      invalidText={errors[key]}
      disabled={saving}
      onChange={(e) => {
        set(key, e.target.value);
        onPicked?.(e.target.value);
      }}
    >
      <SelectItem value="" text="Choose…" />
      {options.map((o) => (
        <SelectItem key={o.code} value={o.code} text={o.description || o.code} />
      ))}
    </Select>
  );

  return (
    <Modal
      open={open}
      // passiveModal + our own action row, as nr-fsp-new's dialogs do: Carbon's
      // built-in footer renders the buttons as a full-width 50/50 pair.
      passiveModal
      className="new-mark"
      size="md"
      modalHeading="New Mark Application"
      onRequestClose={() => {
        if (!saving) onClose();
      }}
      preventCloseOnClickOutside
    >
      <p className="new-mark__strap">All fields are required unless marked optional.</p>
      <div className="new-mark__grid">
        <div className="new-mark__date">
          <DatePicker
            datePickerType="single"
            dateFormat="Y-m-d"
            value={form.applicationDate}
            maxDate={toIsoDate(new Date())}
            invalid={!!errors.applicationDate}
            onChange={(dates: Date[]) =>
              set('applicationDate', dates[0] ? toIsoDate(dates[0]) : '')
            }
          >
            <DatePickerInput
              id="new-mark-applicationDate"
              labelText="Application Date"
              placeholder="yyyy-mm-dd"
              invalidText={errors.applicationDate}
              disabled={saving}
              pattern={TYPED_DATE_PATTERN}
              onChange={(e) => {
                const text = e.target.value;
                if (text.trim() === '') set('applicationDate', '');
                else {
                  const typed = parseTypedDate(text);
                  if (typed) set('applicationDate', typed);
                }
              }}
            />
          </DatePicker>
        </div>
        {select(
          'tenureTerm',
          'Initial Term',
          TERM_OPTIONS.map((t) => ({ code: t, description: `${t} months` })),
        )}

        {select('forestDistrict', 'District', districts, (district) => {
          // The district's default cascade, as legacy defaults a new tenure's —
          // except where the private mark default differs (CASCADE_OVERRIDES).
          const option = districts.find((d) => d.code === district);
          const cascade =
            (option && CASCADE_OVERRIDES[orgUnitCodeOf(option)]) ?? defaultCascades.get(district);
          if (cascade && cascades.some((c) => c.code === cascade)) set('cascadeSplitCode', cascade);
        })}
        {select('cascadeSplitCode', 'Cascade', cascades)}

        <div className="new-mark__full">
          <ClientComboBox
            id="new-mark-client"
            titleText="Mark Holder (optional)"
            helperText="Pick the client and location; can be added later on the Associated clients tab."
            clientNumber={form.clientNumber}
            clientLocnCode={form.clientLocnCode}
            clientName={form.clientName}
            invalid={!!errors.clientName}
            invalidText={errors.clientName}
            disabled={saving}
            onChange={({ clientNumber, clientLocnCode, clientName }) => {
              set('clientNumber', clientNumber);
              set('clientLocnCode', clientLocnCode);
              set('clientName', clientName);
            }}
          />
        </div>

        {text('permitBlockLocn', 'Geographic Location', MAX.permitBlockLocn)}
        {text('bcaaFolioNumber', 'LTO PID', MAX.bcaaFolioNumber)}

        <ManagementUnitComboBox
          id="new-mark-mgmtUnit"
          units={mgmtUnits}
          typeCode={form.mgmtUnitTypeCode}
          unitId={form.mgmtUnitId}
          titleText="Management Unit"
          helperText="Z (outside managed units) unless the land is in one"
          typeEntrySuffix=""
          invalid={!!errors.mgmtUnitTypeCode}
          invalidText={errors.mgmtUnitTypeCode}
          disabled={saving}
          onChange={({ mgmtUnitType, mgmtUnitId }) => {
            set('mgmtUnitTypeCode', mgmtUnitType);
            set('mgmtUnitId', mgmtUnitId);
          }}
        />
        <div className="new-mark__trio">
          {text('permitBlockArea', 'Area (Hectares)', 6)}
          {/* One label for the pair, as on the detail page — two "(optional)"
              labels wrap in columns this narrow and push their inputs down. */}
          <fieldset className="new-mark__pair">
            <legend className="cds--label">Reg / Comp (optional)</legend>
            <div className="new-mark__pair-inputs">
              <TextInput
                id="new-mark-mapReferenceReg"
                labelText="Reg"
                hideLabel
                value={form.mapReferenceReg}
                maxLength={MAX.mapReferenceReg}
                invalid={!!errors.mapReferenceReg}
                invalidText={errors.mapReferenceReg}
                disabled={saving}
                onChange={(e) => set('mapReferenceReg', e.target.value)}
              />
              <span aria-hidden="true">/</span>
              <TextInput
                id="new-mark-mapReferenceComp"
                labelText="Comp"
                hideLabel
                value={form.mapReferenceComp}
                maxLength={MAX.mapReferenceComp}
                invalid={!!errors.mapReferenceComp}
                invalidText={errors.mapReferenceComp}
                disabled={saving}
                onChange={(e) => set('mapReferenceComp', e.target.value)}
              />
            </div>
          </fieldset>
        </div>

        <div className="new-mark__full">
          <TextArea
            id="new-mark-proofOfCrownOrLegal"
            labelText="Legal"
            helperText="The legal description of the land."
            rows={4}
            value={form.proofOfCrownOrLegal}
            maxLength={MAX.proofOfCrownOrLegal}
            invalid={!!errors.proofOfCrownOrLegal}
            invalidText={errors.proofOfCrownOrLegal}
            disabled={saving}
            onChange={(e) => set('proofOfCrownOrLegal', e.target.value)}
          />
        </div>
      </div>

      <div className="new-mark__actions">
        <Button kind="tertiary" disabled={saving} onClick={onClose}>
          Cancel
        </Button>
        <Button kind="primary" disabled={saving} onClick={() => void onSubmit()}>
          {saving ? 'Creating…' : 'Create application'}
        </Button>
      </div>
    </Modal>
  );
};

export default NewMarkApplicationModal;
