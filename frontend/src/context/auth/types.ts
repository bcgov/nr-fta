import type { JWT as AmplifyJWT } from '@aws-amplify/core';

export type JWT = AmplifyJWT;

/**
 * Recognized Cognito groups that map to FTA application roles. Mirrors the
 * backend's ca.bc.gov.nrs.fta.dto.Role enum — keep these two lists in sync.
 * Org-suffixed Cognito groups (e.g. FTA_ADMIN_DPG) canonicalise to their
 * root role; see authUtils.canonicalRoleFor.
 */
export const AVAILABLE_ROLES = ['FTA_ADMIN', 'FTA_VIEWER'] as const;

export type ROLE_TYPE = (typeof AVAILABLE_ROLES)[number];

type RoleValue = string[] | null;

export type USER_PRIVILEGE_TYPE = Partial<Record<ROLE_TYPE, RoleValue>>;

// FTA is an internal ministry application — IDIR only (no BCeID Business).
export const validIdpProviders = ['IDIR'] as const;

export type IdpProviderType = (typeof validIdpProviders)[number];

/**
 * Provider identifier passed to AuthProvider.login() — translated into the
 * Cognito custom identity_provider name (e.g. 'idir' → 'DEV-IDIR' in dev).
 */
export type LoginProvider = 'idir';

export type FamLoginUser = {
  providerUsername?: string;
  userName?: string;
  displayName?: string;
  email?: string;
  idpProvider?: IdpProviderType;
  roles?: ROLE_TYPE[];
  authToken?: string;
  exp?: number;
  privileges: USER_PRIVILEGE_TYPE;
  firstName?: string;
  lastName?: string;
};
