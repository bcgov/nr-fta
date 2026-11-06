/**
 * Every APT (apportionment) page path, in one place so screens link to each other without
 * spelling paths out. The routes themselves are in App.tsx; access is routes/access.ts
 * (canEditApt, and the `/apt` exception in isPathAllowedForUser).
 */
export const aptPaths = {
  managementUnits: () => '/apt/management-units',
  newManagementUnit: () => '/apt/management-units/new',
  editManagementUnit: (muId: number | string) => `/apt/management-units/${muId}/edit`,
  managementUnitHistory: (muId: number | string) => `/apt/management-units/${muId}/history`,
  managementUnitDetail: (muId: number | string, historyId: number | string) =>
    `/apt/management-units/${muId}/history/${historyId}`,
  commitments: (
    muId: number | string,
    historyId: number | string,
    apportionmentId: number | string,
  ) =>
    `/apt/management-units/${muId}/history/${historyId}/apportionments/${apportionmentId}/commitments`,
  commitmentHistory: (commitmentId: number | string) => `/apt/commitments/${commitmentId}/history`,
  licences: () => '/apt/licences',
  licenceDetail: (forestFileId: string) => `/apt/licences/${encodeURIComponent(forestFileId)}`,
  linkages: () => '/apt/linkages',
  reports: () => '/apt/reports',
} as const;
