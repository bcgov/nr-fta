/**
 * Canned API answers for e2e/role-access.spec.ts, which runs as roles the test
 * user does not hold, so it cannot use the real backend. Only what the role
 * checks read. Every rule flag is true, so what a role is denied in these tests is
 * the frontend's doing — the backend's half is HarvestAuthorizationRulesTest.
 */

export const FILE_ID = 'A12345';
export const PERMIT_ID = 'P01';
export const BLOCK_ID = 'B1';
const CB_SKEY = 1001;

const permit = {
  hvaSkey: 2001,
  forestFileId: FILE_ID,
  cuttingPermitId: PERMIT_ID,
  timberMark: 'ABC123',
  fileTypeCode: 'A01',
  fileTypeDescription: 'Forest Licence',
  adminOrgCode: 'DCC',
  licensee: 'E2E Licensee',
  statusCode: 'HI',
  statusDesc: 'Issued',
  revisionCount: 1,
  cutBlocks: [],
  harvestHistory: [],
  rules: {
    canSave: true,
    termsLocked: false,
    canEditExtend: true,
    canEditArea: true,
    canEditPurpose: true,
    canEditMarking: true,
    canDelete: true,
    canRetire: true,
    canManageConditions: true,
    canPostpone: true,
    canChangePostponements: true,
    canRescindAnytime: true,
    canAssignMarks: true,
    canSuspendBlocks: true,
  },
};

const block = {
  cbSkey: CB_SKEY,
  cutBlockId: BLOCK_ID,
  forestFileId: FILE_ID,
  cuttingPermitId: PERMIT_ID,
  timberMark: 'ABC123',
  blockStatus: 'HI',
  blockStatusDescription: 'Issued',
  revisionCount: 1,
  amendments: [],
  suspensions: [],
  rules: { canEdit: true, canRelabel: true, canSuspend: true, canEditSuspension: true },
};

const tenure = {
  forestFileId: FILE_ID,
  fileTypeCode: 'A01',
  fileTypeDescription: 'Forest Licence',
  orgUnitCode: 'DCC',
  orgUnitName: 'Cariboo-Chilcotin',
  fileStatus: 'HI',
  fileStatusDescription: 'Issued',
};

const tenureDetails = {
  values: {},
  descriptions: {},
  rules: {},
  revisions: {},
  notices: [],
  layout: {},
};

const blockSuspension = {
  cbSkey: CB_SKEY,
  forestFileId: FILE_ID,
  cuttingPermitId: PERMIT_ID,
  cutBlockId: BLOCK_ID,
  statusCode: 'HI',
  statusDescription: 'Issued',
  suspension: null,
  canSuspend: true,
  canEdit: false,
  partitionCodes: [],
  postStatusCodes: [],
};

/** The answer for a GET, by pathname. Anything not listed is an empty list. */
export const mockGet = (pathname: string): unknown => {
  if (pathname === `/api/fta/cutting-permits/${PERMIT_ID}`) return permit;
  if (pathname === `/api/fta/cut-blocks/${BLOCK_ID}`) return block;
  if (pathname === `/api/fta/cut-blocks/${BLOCK_ID}/suspensions`) return blockSuspension;
  if (pathname === `/api/fta/tenures/${FILE_ID}`) return tenure;
  if (pathname === `/api/fta/tenures/${FILE_ID}/details`) return tenureDetails;
  if (pathname === '/api/fta/tenure-lookups/cutting-permit') {
    return { quotaTypes: [], extendReasons: [], crownLandsRegions: [], conditionThemes: [] };
  }
  return [];
};
