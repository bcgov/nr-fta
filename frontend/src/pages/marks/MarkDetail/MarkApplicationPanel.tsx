import { Calendar, Camera, Edit, Printer, Renew, Report, SendAlt } from '@carbon/icons-react';
import {
  Button,
  DatePicker,
  DatePickerInput,
  Select,
  SelectItem,
  TextArea,
  TextInput,
} from '@carbon/react';
import { useEffect, useState, type FC, type ReactNode } from 'react';

import DetailTile, { type DetailField } from '@/components/DetailTile';
import ManagementUnitComboBox from '@/components/ManagementUnitComboBox';
import { Modal } from '@/components/Modal';
import { statusCodeVariant } from '@/components/StatusTag/statusCodes';
import StatusTag from '@/components/StatusTag/StatusTag';
import { useNotification } from '@/context/notification/useNotification';
import {
  getCascadeSplits,
  getDistricts,
  getManagementUnits,
  getMarkingInstruments,
  getMarkingMethods,
  getPrivateMarkAmendStatuses,
  getPrivateMarkTypes,
  getPrivateMarkStatuses,
  type CodeOption,
  type ManagementUnit,
} from '@/services/codeLists';
import {
  getMarkSnapshot,
  getNextTimberMark,
  skipTimberMark,
  printMarkCertificate,
  submitMark,
  updateMark,
  type MarkDetail,
} from '@/services/mark_detail';
import { triggerBrowserDownload } from '@/utils/download';
import { formatDate } from '@/utils/formatDate';
import { parseTypedDate, TYPED_DATE_PATTERN } from '@/utils/typedDate';

import {
  createForm,
  issuing,
  MAX,
  TERM_OPTIONS,
  toRequest,
  validate,
  type MarkEditErrors,
  type MarkEditForm,
} from './markEditForm';
import './MarkDetail.scss';

const nf = new Intl.NumberFormat('en-CA');

const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;
const date = (v: string | null | undefined) => dash(formatDate(v));

interface CodeLists {
  districts: CodeOption[];
  markingMethods: CodeOption[];
  markingInstruments: CodeOption[];
  cascades: CodeOption[];
  statuses: CodeOption[];
  amendStatuses: CodeOption[];
  markTypes: CodeOption[];
  mgmtUnits: ManagementUnit[];
}

const NO_CODES: CodeLists = {
  districts: [],
  markingMethods: [],
  markingInstruments: [],
  cascades: [],
  statuses: [],
  amendStatuses: [],
  markTypes: [],
  mgmtUnits: [],
};

/**
 * Options for a code select: the list, plus the stored value when the list no
 * longer carries it (an expired code), so the select never shows a value it
 * cannot hold. Legacy keeps an expired code on a record that already has it.
 */
const withCurrent = (options: CodeOption[], code: string, label?: string | null) =>
  code && !options.some((o) => o.code === code)
    ? [{ code, description: label || code }, ...options]
    : options;

/** yyyy-mm-dd in local time — what the backend's LocalDate expects. */
const toIsoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

/**
 * The Mark summary card's fields, in pairs: two to a row, left then right. Reg /
 * Comp has a row of its own, and Legal spans the card.
 */
const MARK_SUMMARY_ORDER = [
  'Timber Mark',
  'File / Certificate',
  'Client Number',
  'Mark Holder',
  'District',
  'Region',
  'Marking Requirements',
  'Marking Instrument',
  'Application Date',
  'Geographic Location',
  'LTO PID',
  'Area (Hectares)',
  'Management Unit',
  'Cascade',
  'Reg / Comp',
  'Legal',
];

/**
 * `fields` sorted by their label's place in `order`; unlisted ones go last.
 * Edit mode adds " (optional)" to some labels, which the match ignores.
 */
const inOrder = (fields: DetailField[], order: string[]) => {
  const rank = (f: DetailField) => {
    const i = order.indexOf(f.label.replace(/ \(optional\)$/, ''));
    return i === -1 ? order.length : i;
  };
  return [...fields].sort((a, b) => rank(a) - rank(b));
};

interface Props {
  mark: MarkDetail;
  /** The detail route's id: a timber mark, or a certificate with `byCertificate`. */
  id: string;
  byCertificate: boolean;
  /** Whether the user's role may edit private marks (the rules decide the rest). */
  canEdit: boolean;
  /** Tells the page edit mode opened or closed, so it can lock the other tabs. */
  onEditingChange: (editing: boolean) => void;
  /** Called after a save, to re-read the mark. */
  onSaved: () => void;
}

/**
 * The Mark application tab of the private-mark detail (FTA510): Mark summary
 * (location fields included) and Administration side by side. One Edit button above them opens
 * every field the user may change in place — the nr-fsp-new FSP Information
 * pattern: inputs replace values inside the same sections, Cancel and Save
 * changes close it, and nothing else on the page can be used meanwhile.
 *
 * Which fields open is `mark.editRules`, computed by the backend from the legacy
 * FTA510 protection rules; the backend applies the same rules to the save.
 */
const MarkApplicationPanel: FC<Props> = ({
  mark,
  id,
  byCertificate,
  canEdit,
  onEditingChange,
  onSaved,
}) => {
  // `display`, not the context object: the provider builds a new { display }
  // on every render, so depending on the object re-ran the list fetch (and,
  // in the modal, the form reset) after every render.
  const { display } = useNotification();
  const rules = mark.editRules;
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<MarkEditForm>(() => createForm(mark));
  const [errors, setErrors] = useState<MarkEditErrors>({});
  const [saving, setSaving] = useState(false);
  const [codes, setCodes] = useState<CodeLists>(NO_CODES);

  useEffect(() => onEditingChange(editing), [editing, onEditingChange]);

  // The dropdowns' lists, fetched once the form opens (all cached app-wide).
  useEffect(() => {
    if (!editing) return;
    let cancelled = false;
    Promise.all([
      getDistricts(),
      getMarkingMethods(),
      getMarkingInstruments(),
      getCascadeSplits(),
      getPrivateMarkStatuses(),
      getPrivateMarkAmendStatuses(),
      getManagementUnits(),
      getPrivateMarkTypes(),
    ])
      .then(
        ([
          districts,
          markingMethods,
          markingInstruments,
          cascades,
          statuses,
          amend,
          units,
          types,
        ]) => {
          if (!cancelled) {
            setCodes({
              districts,
              markingMethods,
              markingInstruments,
              cascades,
              statuses,
              amendStatuses: amend,
              mgmtUnits: units,
              markTypes: types,
            });
          }
        },
      )
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

  const set = <K extends keyof MarkEditForm>(key: K, value: MarkEditForm[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    if (errors[key]) {
      setErrors((prev) => {
        const next = { ...prev };
        delete next[key];
        return next;
      });
    }
  };

  const onEdit = () => {
    setForm(createForm(mark));
    setErrors({});
    setEditing(true);
  };

  const onCancel = () => {
    setForm(createForm(mark));
    setErrors({});
    setEditing(false);
  };

  // A Mark Type chosen for a mark that has none: Save generates the timber mark
  // for it and issues the mark (legacy's Assign Mark and Save as one step).
  const willIssue = editing && issuing(form, mark);

  // Print — the FTA402 certificate. For a district user the backend also marks
  // the mark issued (HN → HI), so the mark is re-read afterwards.
  const [printing, setPrinting] = useState(false);
  const onPrint = async () => {
    // Open the tab now, inside the click: a window opened after the await is no
    // longer user-initiated, and browsers block it as a popup. It is pointed at
    // the PDF when that arrives, or closed if the print fails.
    const tab = window.open('', '_blank');
    setPrinting(true);
    try {
      const pdf = await printMarkCertificate(id, byCertificate);
      const url = URL.createObjectURL(pdf);
      if (tab) {
        tab.location.href = url;
      } else {
        // Popups blocked outright: fall back to a download so the certificate isn't lost.
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `timber-mark-certificate-${mark.timberMark ?? id}.pdf`;
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
      }
      // The tab needs the URL until its viewer has loaded the file; release it later.
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
      onSaved();
    } catch (err) {
      tab?.close();
      display({
        kind: 'error',
        title: 'Could not print the certificate',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setPrinting(false);
    }
  };
  // Snapshot — the whole mark as a PDF stamped with now and the user, for the
  // record. Read-only, so it's there for anyone who can see the mark.
  const [snapping, setSnapping] = useState(false);
  const onSnapshot = async () => {
    setSnapping(true);
    try {
      const pdf = await getMarkSnapshot(id, byCertificate);
      const d = new Date();
      const pad = (n: number) => String(n).padStart(2, '0');
      const stamp = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}-${pad(
        d.getHours(),
      )}${pad(d.getMinutes())}`;
      triggerBrowserDownload(pdf, `mark-${mark.timberMark ?? id}-snapshot-${stamp}.pdf`);
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not take the snapshot',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSnapping(false);
    }
  };

  // Legacy offered Print once the status began with H (HN, HI, HX…).
  const printable = (mark.markStatusCode ?? '').startsWith('H');

  // Submit to HQ — a district's PA application goes to Headquarters as PI. The
  // button is always there, disabled when submitting isn't allowed.
  const [confirmSubmit, setConfirmSubmit] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const onSubmit = async () => {
    setSubmitting(true);
    try {
      await submitMark(id, mark.revisionCount, byCertificate);
      display({
        kind: 'success',
        title: 'Submitted to Headquarters',
        subtitle: 'The application is now PI - Pending Issuance.',
        timeout: 6000,
      });
      setConfirmSubmit(false);
      onSaved();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not submit the application',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSubmitting(false);
    }
  };

  // A save that issues the mark first shows the timber mark it will be given, to
  // confirm: issuing can't be undone. `mark` is null when it couldn't be previewed.
  const [issueConfirm, setIssueConfirm] = useState<{ mark: string | null } | null>(null);

  const onSave = async () => {
    if (!rules) return;
    const found = validate(form, mark, rules);
    if (Object.keys(found).length > 0) {
      setErrors(found);
      display({
        kind: 'error',
        title: 'Check the highlighted fields',
        timeout: 6000,
      });
      return;
    }
    if (willIssue) {
      setSaving(true);
      const next = await getNextTimberMark(form.fileTypeCode);
      setSaving(false);
      setIssueConfirm({ mark: next });
      return;
    }
    await save(null);
  };

  // "Generate another": the shown mark is passed over for good and the next one shown —
  // for a mark that spells something it shouldn't.
  const [skipping, setSkipping] = useState(false);
  const onSkip = async () => {
    setSkipping(true);
    try {
      const next = await skipTimberMark(form.fileTypeCode);
      setIssueConfirm({ mark: next });
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not generate another mark',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSkipping(false);
    }
  };

  /** The save itself; `expected` is the timber mark the user confirmed, when issuing. */
  const save = async (expected: string | null) => {
    if (!rules) return;
    setSaving(true);
    try {
      const saved = await updateMark(id, toRequest(form, mark), byCertificate);
      setEditing(false);
      setIssueConfirm(null);
      if (expected && saved.timberMark && saved.timberMark !== expected) {
        // Another issue took the previewed number between the preview and the save.
        display({
          kind: 'warning',
          title: `Mark issued as ${saved.timberMark}`,
          subtitle: `${expected} was issued to another mark in the meantime.`,
          timeout: 12000,
        });
      } else {
        display({
          kind: 'success',
          title:
            saved.timberMark && expected ? `Mark ${saved.timberMark} issued` : 'Private mark saved',
          timeout: 5000,
        });
      }
      onSaved();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not save the mark',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  // ─── Field builders: the read-only value, or its input while editing ──────

  const open = (rule: boolean | undefined) => editing && !!rules?.editable && !!rule;

  /** A field; `optional` marks it so in edit mode, as the FSP form does. */
  const field = (
    label: string,
    view: ReactNode,
    editable: boolean,
    editor: () => ReactNode,
    opts: { optional?: boolean; wide?: boolean } = {},
  ): DetailField => ({
    label: editable && opts.optional ? `${label} (optional)` : label,
    value: editable ? editor() : view,
    wide: opts.wide,
  });

  // Each input sits in a plain wrapper that carries its width: a class on the
  // Carbon component itself lands on the very element Carbon sizes, and its own
  // widths win (the date picker's 18rem spilled into the next column).
  const text = (
    key: keyof MarkEditForm,
    label: string,
    maxLength?: number,
    size: 'cell' | 'sm' | 'xs' = 'cell',
  ) => (
    <div className={`detail-edit__input detail-edit__input--${size}`}>
      <TextInput
        id={`detail-edit-${key}`}
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
    key: keyof MarkEditForm,
    label: string,
    options: { code: string; description: string }[],
    placeholder = true,
  ) => (
    <div className="detail-edit__input detail-edit__input--cell">
      <Select
        id={`detail-edit-${key}`}
        labelText={label}
        hideLabel
        value={form[key]}
        invalid={!!errors[key]}
        invalidText={errors[key]}
        disabled={saving}
        onChange={(e) => set(key, e.target.value)}
      >
        {placeholder && <SelectItem value="" text="Choose…" />}
        {options.map((o) => (
          <SelectItem key={o.code} value={o.code} text={o.description || o.code} />
        ))}
      </Select>
    </div>
  );

  const dateInput = (key: keyof MarkEditForm, label: string, helperText?: string) => (
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
          id={`detail-edit-${key}`}
          labelText={label}
          hideLabel
          placeholder="yyyy-mm-dd"
          helperText={helperText}
          invalidText={errors[key]}
          disabled={saving}
          // Emptying the text does not reliably fire the picker's onChange.
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

  // ─── Read-only values ─────────────────────────────────────────────────────

  // Legacy shows the unit as type, id and description ("U 24 Prince George TSA").
  const managementUnit = mark.mgmtUnitTypeCode
    ? [mark.mgmtUnitTypeCode, mark.mgmtUnitId, mark.mgmtUnitDesc && `— ${mark.mgmtUnitDesc}`]
        .filter(Boolean)
        .join(' ')
    : '—';
  const regComp =
    mark.mapReferenceReg || mark.mapReferenceComp
      ? `${mark.mapReferenceReg ?? '—'} / ${mark.mapReferenceComp ?? '—'}`
      : '—';
  const statusTag = (code: string | null, desc: string | null) =>
    code ? <StatusTag status={desc ?? code} variant={statusCodeVariant(code)} /> : '—';

  const statusOptions = (rules?.statusOptions ?? []).map((code) => ({
    code,
    description:
      code === mark.markStatusCode
        ? (mark.markStatusDesc ?? code)
        : (codes.statuses.find((o) => o.code === code)?.description ?? code),
  }));
  const amendOptions = (rules?.amendmentStatusOptions ?? []).map((code) => ({
    code,
    description: codes.amendStatuses.find((o) => o.code === code)?.description ?? code,
  }));

  // ─── Sections ─────────────────────────────────────────────────────────────

  const summaryFields: DetailField[] = [
    {
      label: 'Timber Mark',
      value: willIssue ? (
        <span className="detail-edit__pending">Assigned on save</span>
      ) : (
        dash(mark.timberMark)
      ),
    },
    { label: 'File / Certificate', value: dash(mark.certificate) },
    field('Application Date', date(mark.markApplicationDate), open(rules?.applicationDate), () =>
      dateInput('applicationDate', 'Application Date'),
    ),
    { label: 'Mark Holder', value: dash(mark.clientName) },
    {
      label: 'Client Number',
      value: mark.clientNumber
        ? `${mark.clientNumber}${mark.clientLocnCode ? ` / ${mark.clientLocnCode}` : ''}`
        : '—',
    },
    field(
      'Marking Requirements',
      dash(mark.markingMethodDesc ?? mark.markingMethodCode),
      open(rules?.marking || willIssue),
      () =>
        select(
          'markingMethodCode',
          'Marking Requirements',
          withCurrent(codes.markingMethods, mark.markingMethodCode ?? '', mark.markingMethodDesc),
        ),
    ),
    field(
      'Marking Instrument',
      dash(mark.markingInstrumentDesc ?? mark.markingInstrumentCode),
      open(rules?.marking || willIssue),
      () =>
        select(
          'markingInstrumentCode',
          'Marking Instrument',
          withCurrent(
            codes.markingInstruments,
            mark.markingInstrumentCode ?? '',
            mark.markingInstrumentDesc,
          ),
        ),
    ),
  ];

  const administrationFields: DetailField[] = [
    field('Mark Type', dash(mark.fileTypeDesc ?? mark.fileTypeCode), open(rules?.markType), () => (
      // Legacy FTA510's Mark Type dropdown. Saving a type issues the mark: a timber
      // mark is generated for it (E…, N…, IR…) and the status goes to HN.
      <div className="detail-edit__input detail-edit__input--cell">
        <Select
          id="detail-edit-fileTypeCode"
          labelText="Mark Type"
          hideLabel
          value={form.fileTypeCode}
          helperText={willIssue ? 'Saving issues the mark' : undefined}
          invalid={!!errors.fileTypeCode}
          invalidText={errors.fileTypeCode}
          disabled={saving}
          onChange={(e) => set('fileTypeCode', e.target.value)}
        >
          <SelectItem value="" text="Choose…" />
          {codes.markTypes.map((o) => (
            <SelectItem key={o.code} value={o.code} text={o.description || o.code} />
          ))}
        </Select>
      </div>
    )),
    willIssue
      ? {
          label: 'Timber Mark Status',
          value: (
            <span>
              <StatusTag
                status={codes.statuses.find((o) => o.code === 'HN')?.description ?? 'HN'}
                variant={statusCodeVariant('HN')}
              />
              <span className="detail-edit__pending"> on save</span>
            </span>
          ),
        }
      : field(
          'Timber Mark Status',
          statusTag(mark.markStatusCode, mark.markStatusDesc),
          open(rules?.status),
          () => select('markStatusCode', 'Timber Mark Status', statusOptions, false),
        ),
    field(
      'Initial Term',
      mark.tenureTerm != null ? `${mark.tenureTerm} months` : '—',
      open(rules?.term),
      () =>
        select(
          'tenureTerm',
          'Initial Term',
          TERM_OPTIONS.map((t) => ({ code: t, description: `${t} months` })),
        ),
    ),
    field('Issued', date(mark.markIssueDate), open(rules?.branch), () =>
      dateInput('markIssueDate', 'Issued'),
    ),
    field(
      'Expired',
      date(mark.markExpiryDate),
      open(rules?.branch),
      () => dateInput('markExpiryDate', 'Expired', 'Left blank, set from Issued and Initial Term'),
      { optional: true },
    ),
    field(
      'Extended',
      date(mark.markExtendDate),
      open(rules?.branch),
      () => dateInput('markExtendDate', 'Extended'),
      { optional: true },
    ),
    { label: 'Extension Count', value: dash(mark.markExtendCount ?? 0) },
    field(
      'Cancelled',
      date(mark.markCancelDate),
      open(rules?.branch),
      () => dateInput('markCancelDate', 'Cancelled'),
      { optional: true },
    ),
    field(
      'Crown Granted Date',
      date(mark.grantedAcqrdDate),
      open(rules?.branch),
      () => dateInput('grantedAcqrdDate', 'Crown Granted Date'),
      { optional: true },
    ),
    field(
      'Crown Granted Description',
      dash(mark.crownGrantedAcqDesc),
      open(rules?.branch),
      () => text('crownGrantedAcqDesc', 'Crown Granted Description', MAX.crownGrantedAcqDesc, 'sm'),
      { optional: true },
    ),
    field(
      'Amendment Status',
      statusTag(mark.outstandingAmendStatus, mark.outstandingAmendStatusDesc),
      open(rules?.amendmentStatus),
      () => select('amendStatusCode', 'Amendment Status', amendOptions),
    ),
    { label: 'Amended Date', value: date(mark.markAmendDate) },
    { label: 'Amendment Count', value: mark.amendments.length },
  ];

  const locationOpen = open(rules?.location);
  const locationFields: DetailField[] = [
    // Full width beside a full-width District, so the pairs below stay paired.
    { label: 'Region', value: dash(mark.regionDesc), span2: editing },
    {
      ...field(
        'District',
        dash(mark.districtDesc ?? mark.orgUnitCode ?? mark.forestDistrict),
        locationOpen,
        () =>
          select(
            'forestDistrict',
            'District',
            withCurrent(codes.districts, mark.forestDistrict ?? '', mark.districtDesc),
          ),
      ),
      // District names are long ("DVA - Stuart Nechako Natural Resource
      // District"); one column truncates them in the select.
      span2: editing,
    },
    field('Geographic Location', dash(mark.permitBlockLocn), locationOpen, () =>
      text('permitBlockLocn', 'Geographic Location', MAX.permitBlockLocn),
    ),
    field(
      'Area (Hectares)',
      mark.permitBlockArea != null ? nf.format(mark.permitBlockArea) : '—',
      locationOpen,
      () => text('permitBlockArea', 'Area (Hectares)', 6, 'sm'),
    ),
    field('LTO PID', dash(mark.bcaaFolioNumber), locationOpen, () =>
      text('bcaaFolioNumber', 'LTO PID', MAX.bcaaFolioNumber),
    ),
    {
      ...field('Management Unit', managementUnit, locationOpen, () => (
        <div className="detail-edit__input detail-edit__input--cell">
          <ManagementUnitComboBox
            id="detail-edit-mgmtUnit"
            units={codes.mgmtUnits}
            typeCode={form.mgmtUnitTypeCode}
            unitId={form.mgmtUnitId}
            titleText="Management Unit"
            hideLabel
            helperText=""
            typeEntrySuffix=""
            invalid={!!errors.mgmtUnitTypeCode}
            invalidText={errors.mgmtUnitTypeCode}
            disabled={saving}
            onChange={({ mgmtUnitType, mgmtUnitId }) => {
              set('mgmtUnitTypeCode', mgmtUnitType);
              set('mgmtUnitId', mgmtUnitId);
            }}
          />
        </div>
      )),
      // Unit names run long ("U 24 — Prince George TSA"), as District's do.
      span2: editing,
    },
    {
      ...field('Cascade', dash(mark.cascadeSplitDesc ?? mark.cascadeSplitCode), locationOpen, () =>
        select(
          'cascadeSplitCode',
          'Cascade',
          withCurrent(codes.cascades, mark.cascadeSplitCode ?? '', mark.cascadeSplitDesc),
        ),
      ),
      // Full width beside a full-width Management Unit, as Region is beside District.
      span2: editing,
    },
    field(
      'Reg / Comp',
      regComp,
      locationOpen,
      () => (
        <div className="detail-edit__pair">
          {text('mapReferenceReg', 'Reg', MAX.mapReferenceReg, 'xs')}
          <span aria-hidden="true">/</span>
          {text('mapReferenceComp', 'Comp', MAX.mapReferenceComp, 'xs')}
        </div>
      ),
      { optional: true },
    ),
    field(
      'Legal',
      dash(mark.proofOfCrownOrLegal),
      locationOpen,
      () => (
        <TextArea
          id="detail-edit-proofOfCrownOrLegal"
          labelText="Legal"
          hideLabel
          rows={4}
          className="detail-edit__textarea"
          value={form.proofOfCrownOrLegal}
          // A counter would render beside the hidden label, on a line of its own.
          maxLength={MAX.proofOfCrownOrLegal}
          invalid={!!errors.proofOfCrownOrLegal}
          invalidText={errors.proofOfCrownOrLegal}
          disabled={saving}
          onChange={(e) => set('proofOfCrownOrLegal', e.target.value)}
        />
      ),
      { wide: true },
    ),
  ];

  return (
    <div className={editing ? 'fsp-info__tab-panel detail-edit' : 'fsp-info__tab-panel'}>
      <div className="detail-edit__toolbar">
        {editing ? (
          <p className="detail-edit__strap">All fields are required unless marked optional.</p>
        ) : (
          <>
            <Button
              kind="tertiary"
              size="sm"
              renderIcon={Camera}
              disabled={snapping}
              onClick={() => void onSnapshot()}
            >
              {snapping ? 'Taking snapshot…' : 'Snapshot'}
            </Button>
            {canEdit && rules && (
              <>
                <Button
                  kind="tertiary"
                  size="sm"
                  renderIcon={SendAlt}
                  disabled={!rules.submit || printing || submitting}
                  onClick={() => setConfirmSubmit(true)}
                >
                  Submit to Headquarters
                </Button>
                <Button
                  kind="tertiary"
                  size="sm"
                  renderIcon={Printer}
                  disabled={!printable || printing || submitting}
                  onClick={() => void onPrint()}
                >
                  {printing ? 'Printing…' : 'Print'}
                </Button>
                <Button
                  kind="tertiary"
                  size="sm"
                  renderIcon={Edit}
                  disabled={!rules.editable || printing || submitting}
                  onClick={onEdit}
                >
                  Edit
                </Button>
              </>
            )}
          </>
        )}
      </div>

      {/* Side by side, each as wide as its fields need; they wrap onto
          separate rows when the pane is too narrow. */}
      <div className="fsp-info__tile-row">
        {/* The summary and location fields together, in MARK_SUMMARY_ORDER. */}
        <DetailTile
          title="Mark summary"
          icon={Report}
          fields={inOrder([...summaryFields, ...locationFields], MARK_SUMMARY_ORDER)}
        />
        <DetailTile title="Administration" icon={Calendar} fields={administrationFields} />
      </div>

      {editing && (
        <div className="detail-edit__actions">
          <Button kind="tertiary" size="md" disabled={saving} onClick={onCancel}>
            Cancel
          </Button>
          <Button kind="primary" size="md" disabled={saving} onClick={onSave}>
            {saving ? 'Saving…' : 'Save changes'}
          </Button>
        </div>
      )}

      {/* Issuing confirmation: the timber mark the save will give, and its type. */}
      <Modal
        open={issueConfirm !== null}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading="Issue timber mark"
        onRequestClose={() => {
          if (!saving && !skipping) setIssueConfirm(null);
        }}
        preventCloseOnClickOutside
      >
        <dl className="issue-confirm">
          <dt className="issue-confirm__label">Timber Mark</dt>
          <dd className="issue-confirm__mark">{issueConfirm?.mark ?? 'Assigned on save'}</dd>
          <dt className="issue-confirm__label">Mark Type</dt>
          <dd className="issue-confirm__type">
            {codes.markTypes.find((o) => o.code === form.fileTypeCode)?.description ??
              form.fileTypeCode}
          </dd>
        </dl>
        <p className="detail-dialog__subtitle">
          Saving issues this mark to certificate {mark.certificate}. This can&apos;t be undone.
        </p>
        <div className="detail-dialog__actions">
          <Button
            kind="tertiary"
            disabled={saving || skipping}
            onClick={() => setIssueConfirm(null)}
          >
            Cancel
          </Button>
          {issueConfirm?.mark && (
            <Button
              kind="secondary"
              renderIcon={Renew}
              disabled={saving || skipping}
              onClick={() => void onSkip()}
            >
              {skipping ? 'Generating…' : 'Generate another'}
            </Button>
          )}
          <Button
            kind="primary"
            disabled={saving || skipping}
            onClick={() => void save(issueConfirm?.mark ?? null)}
          >
            {saving ? 'Issuing…' : 'Issue mark'}
          </Button>
        </div>
      </Modal>

      {/* Submit to HQ confirmation — the tabs' small dialog shape. */}
      <Modal
        open={confirmSubmit}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading="Submit to Headquarters"
        onRequestClose={() => {
          if (!submitting) setConfirmSubmit(false);
        }}
        preventCloseOnClickOutside
      >
        <p className="detail-dialog__subtitle">
          Certificate {mark.certificate} goes to Headquarters for approval and becomes PI - Pending
          Issuance.
        </p>
        <div className="detail-dialog__actions">
          <Button kind="tertiary" disabled={submitting} onClick={() => setConfirmSubmit(false)}>
            Cancel
          </Button>
          <Button kind="primary" disabled={submitting} onClick={() => void onSubmit()}>
            {submitting ? 'Submitting…' : 'Submit to Headquarters'}
          </Button>
        </div>
      </Modal>
    </div>
  );
};

export default MarkApplicationPanel;
