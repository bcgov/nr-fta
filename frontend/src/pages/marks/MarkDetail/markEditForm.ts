import type { MarkDetail, MarkEditRules, MarkUpdateRequest } from '@/services/mark_detail';

/**
 * The FTA510 edit form: every editable field as the string an input holds.
 * Which ones are open is `MarkEditRules`; the backend keeps the stored value
 * for any field the rules close, whatever is sent.
 */
export interface MarkEditForm {
  applicationDate: string;
  tenureTerm: string;
  forestDistrict: string;
  markingMethodCode: string;
  markingInstrumentCode: string;
  permitBlockLocn: string;
  proofOfCrownOrLegal: string;
  bcaaFolioNumber: string;
  permitBlockArea: string;
  mgmtUnitTypeCode: string;
  mgmtUnitId: string;
  cascadeSplitCode: string;
  mapReferenceReg: string;
  mapReferenceComp: string;
  markIssueDate: string;
  markExpiryDate: string;
  markExtendDate: string;
  markCancelDate: string;
  grantedAcqrdDate: string;
  crownGrantedAcqDesc: string;
  markStatusCode: string;
  amendStatusCode: string;
  /** Mark Type. Choosing one for a mark that has none issues it on save. */
  fileTypeCode: string;
}

export type MarkEditErrors = Partial<Record<keyof MarkEditForm, string>>;

/** Initial Term choices on the legacy screen, in months. */
export const TERM_OPTIONS = ['6', '12', '24', '36', '48', '60'];

/** Field limits, from the legacy form's maxlengths and validators. */
export const MAX = {
  permitBlockLocn: 50,
  proofOfCrownOrLegal: 4000,
  bcaaFolioNumber: 23,
  crownGrantedAcqDesc: 10,
  mapReferenceReg: 2,
  mapReferenceComp: 3,
} as const;

const s = (v: string | number | null | undefined) => (v == null ? '' : String(v));

export function createForm(mark: MarkDetail): MarkEditForm {
  return {
    applicationDate: s(mark.markApplicationDate),
    tenureTerm: s(mark.tenureTerm),
    forestDistrict: s(mark.forestDistrict),
    markingMethodCode: s(mark.markingMethodCode),
    markingInstrumentCode: s(mark.markingInstrumentCode),
    permitBlockLocn: s(mark.permitBlockLocn),
    proofOfCrownOrLegal: s(mark.proofOfCrownOrLegal),
    bcaaFolioNumber: s(mark.bcaaFolioNumber),
    permitBlockArea: s(mark.permitBlockArea),
    mgmtUnitTypeCode: s(mark.mgmtUnitTypeCode),
    mgmtUnitId: s(mark.mgmtUnitId),
    cascadeSplitCode: s(mark.cascadeSplitCode),
    mapReferenceReg: s(mark.mapReferenceReg),
    mapReferenceComp: s(mark.mapReferenceComp),
    markIssueDate: s(mark.markIssueDate),
    markExpiryDate: s(mark.markExpiryDate),
    markExtendDate: s(mark.markExtendDate),
    markCancelDate: s(mark.markCancelDate),
    grantedAcqrdDate: s(mark.grantedAcqrdDate),
    crownGrantedAcqDesc: s(mark.crownGrantedAcqDesc),
    markStatusCode: s(mark.markStatusCode),
    amendStatusCode: s(mark.outstandingAmendStatus),
    fileTypeCode: s(mark.fileTypeCode),
  };
}

const orNull = (v: string) => (v.trim() === '' ? null : v.trim());
const numOrNull = (v: string) => (v.trim() === '' ? null : Number(v));

export function toRequest(form: MarkEditForm, mark: MarkDetail): MarkUpdateRequest {
  return {
    revisionCount: mark.revisionCount,
    amendRevisionCount: mark.amendRevisionCount,
    applicationDate: orNull(form.applicationDate),
    tenureTerm: numOrNull(form.tenureTerm),
    forestDistrict: orNull(form.forestDistrict),
    markingMethodCode: orNull(form.markingMethodCode),
    markingInstrumentCode: orNull(form.markingInstrumentCode),
    permitBlockLocn: orNull(form.permitBlockLocn),
    proofOfCrownOrLegal: orNull(form.proofOfCrownOrLegal),
    bcaaFolioNumber: orNull(form.bcaaFolioNumber),
    permitBlockArea: numOrNull(form.permitBlockArea),
    mgmtUnitTypeCode: orNull(form.mgmtUnitTypeCode),
    mgmtUnitId: orNull(form.mgmtUnitId),
    cascadeSplitCode: orNull(form.cascadeSplitCode),
    mapReferenceReg: orNull(form.mapReferenceReg),
    mapReferenceComp: orNull(form.mapReferenceComp),
    markIssueDate: orNull(form.markIssueDate),
    markExpiryDate: orNull(form.markExpiryDate),
    markExtendDate: orNull(form.markExtendDate),
    markCancelDate: orNull(form.markCancelDate),
    grantedAcqrdDate: orNull(form.grantedAcqrdDate),
    crownGrantedAcqDesc: orNull(form.crownGrantedAcqDesc),
    markStatusCode: orNull(form.markStatusCode),
    amendStatusCode: orNull(form.amendStatusCode),
    // Only a newly chosen type is sent: on a mark that has none, it means "issue".
    fileTypeCode: mark.fileTypeCode ? null : orNull(form.fileTypeCode),
  };
}

/** A Mark Type chosen for a mark that has none: the save will issue it. */
export const issuing = (form: MarkEditForm, mark: MarkDetail) =>
  !mark.fileTypeCode && form.fileTypeCode !== '';

const today = () => new Date().toISOString().slice(0, 10);
const CROWN_GRANT_CUTOVER = '1906-03-12';

/**
 * The legacy form's "Save" checks, for fields the rules open — so mistakes show
 * against the field before a round trip. The backend repeats every one of them.
 * ISO dates compare correctly as strings.
 */
export function validate(
  form: MarkEditForm,
  mark: MarkDetail,
  rules: MarkEditRules,
): MarkEditErrors {
  const e: MarkEditErrors = {};
  const blank = (v: string) => v.trim() === '';

  if (rules.applicationDate) {
    if (blank(form.applicationDate)) e.applicationDate = 'Application Date is required.';
    else if (form.applicationDate > today())
      e.applicationDate = 'Application Date cannot be later than today.';
  }
  if (rules.term && !TERM_OPTIONS.includes(form.tenureTerm)) {
    e.tenureTerm = 'Choose an initial term.';
  }

  if (rules.location) {
    if (blank(form.forestDistrict)) e.forestDistrict = 'District is required.';
    if (blank(form.permitBlockLocn)) e.permitBlockLocn = 'Geographic Location is required.';
    if (blank(form.proofOfCrownOrLegal)) e.proofOfCrownOrLegal = 'Legal is required.';
    if (blank(form.bcaaFolioNumber)) e.bcaaFolioNumber = 'LTO PID is required.';
    if (blank(form.permitBlockArea)) e.permitBlockArea = 'Area is required.';
    else if (!/^\d{1,4}(\.\d)?$/.test(form.permitBlockArea.trim()))
      e.permitBlockArea = 'Area must be 0 to 9999.9, with at most one decimal place.';
    if (blank(form.mgmtUnitTypeCode)) e.mgmtUnitTypeCode = 'Management Unit is required.';
    if (blank(form.cascadeSplitCode)) e.cascadeSplitCode = 'Cascade is required.';
    if (!blank(form.mapReferenceReg) && !/^\d{1,2}$/.test(form.mapReferenceReg.trim()))
      e.mapReferenceReg = 'Reg must be a number from 0 to 99.';
    if (!blank(form.mapReferenceComp) && !/^\d{1,3}$/.test(form.mapReferenceComp.trim()))
      e.mapReferenceComp = 'Comp must be a number from 0 to 999.';
  }

  // Issuing creates the hauling authority, which takes the marking codes.
  if (rules.marking || issuing(form, mark)) {
    if (blank(form.markingMethodCode)) e.markingMethodCode = 'Marking Requirements is required.';
    if (blank(form.markingInstrumentCode))
      e.markingInstrumentCode = 'Marking Instrument is required.';
  }

  if (rules.branch) {
    const prev = mark.markStatusCode ?? '';
    if (prev.startsWith('H') && blank(form.markIssueDate))
      e.markIssueDate = `Issued is required when the status is ${prev}.`;
    if (!blank(form.markIssueDate) && !blank(form.markExpiryDate)) {
      if (form.markExpiryDate <= form.markIssueDate)
        e.markExpiryDate = 'Expired must be later than Issued.';
    }
    if (prev === 'PI' && !blank(form.markExtendDate))
      e.markExtendDate = 'Extended must be blank while the status is PI.';
    else if (!blank(form.markExtendDate) && !blank(form.markExpiryDate)) {
      if (form.markExtendDate <= form.markExpiryDate)
        e.markExtendDate = 'Extended must be later than Expired.';
    }

    // B08 marks are crown granted before the cut-over, B09 on or after it; they
    // take a date or a description (a year), exactly one. The type being assigned
    // counts too: legacy checked these on the Save that issued the mark.
    const type = form.fileTypeCode || mark.fileTypeCode;
    if (type === 'B08' || type === 'B09') {
      const hasDate = !blank(form.grantedAcqrdDate);
      const hasDesc = !blank(form.crownGrantedAcqDesc);
      if (hasDate === hasDesc) {
        e.grantedAcqrdDate =
          'Enter exactly one of Crown Granted Date or Crown Granted Description.';
      } else if (hasDate) {
        if (type === 'B08' && form.grantedAcqrdDate >= CROWN_GRANT_CUTOVER)
          e.grantedAcqrdDate = 'Must be before 1906-03-12 for mark type B08.';
        if (type === 'B09' && form.grantedAcqrdDate < CROWN_GRANT_CUTOVER)
          e.grantedAcqrdDate = 'Must be on or after 1906-03-12 for mark type B09.';
      } else if (/^\d{4}$/.test(form.crownGrantedAcqDesc.trim())) {
        const year = Number(form.crownGrantedAcqDesc.trim());
        if (type === 'B08' && year > 1906)
          e.crownGrantedAcqDesc = 'The year must be 1906 or earlier for mark type B08.';
        if (type === 'B09' && year < 1906)
          e.crownGrantedAcqDesc = 'The year must be 1906 or later for mark type B09.';
      }
    }
  }

  if (
    rules.status &&
    form.markStatusCode === 'HX' &&
    mark.markStatusCode !== 'HX' &&
    blank(form.markCancelDate)
  ) {
    e.markCancelDate = 'Cancelled is required to set the status to HX.';
  }
  if (rules.amendmentStatus && blank(form.amendStatusCode)) {
    e.amendStatusCode = 'Amendment Status is required.';
  }
  return e;
}
