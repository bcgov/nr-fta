import { createContext, type ReactNode } from 'react';

import type { FamLoginUser } from './types';

export type AuthContextType = {
  user: FamLoginUser | undefined;
  isLoggedIn: boolean;
  isLoading: boolean;
  /**
   * Redirect to BC Gov SSO to sign in. Takes no provider argument: the
   * Cognito version needed a per-environment identity_provider name
   * (`DEV-IDIR`, `TEST-IDIR`, `IDIR`), whereas the Keycloak IdP hint is the
   * constant `azureidir` everywhere (see services/keycloak.ts).
   */
  login: () => void;
  logout: () => void;
  userToken: () => string | undefined;
  /**
   * Checks the access token expiry and refreshes via the refresh token
   * if needed. Returns the current access token string, or undefined if
   * the session has expired (user is signed out automatically).
   */
  ensureFreshToken: () => Promise<string | undefined>;
  /**
   * Completes the authorization-code exchange after Keycloak redirects back.
   * Called only by the /authCallback route; rejects if the callback URL carries
   * no usable code or its state has already been consumed.
   */
  completeLogin: () => Promise<void>;
};

export type AuthProviderProps = {
  children: ReactNode;
};

export const AuthContext = createContext<AuthContextType | undefined>(undefined);
