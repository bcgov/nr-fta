/// <reference types="vite/client" />

interface ImportMetaEnv {
  // BC Gov SSO (Keycloak) via CSS. Two variables, not five: oidc-client-ts
  // discovers the authorize / token / end-session endpoints from the issuer,
  // which is why the three VITE_LOGOUT_*-equivalent values (here, the single
  // VITE_REDIRECT_SIGN_OUT) were deleted rather than renamed. The redirect URIs
  // are derived from window.location at runtime — see services/keycloak.ts.
  readonly VITE_KEYCLOAK_URL: string;
  // Must equal the backend's KEYCLOAK_CLIENT_ID: it is checked there as the
  // token's `azp`, so a mismatch refuses every request.
  readonly VITE_KEYCLOAK_CLIENT_ID: string;
  readonly VITE_BASE_PATH: string;
  // Backend API base path; usually '/api' so Caddy reverse-proxies to the
  // same-zone backend Service.
  readonly VITE_API_BASE_URL: string;
  // Display.
  readonly VITE_APP_NAME: string;
  // GBS (the government billing system) that FTA680's G.B.S. button opens; legacy's
  // gbs-invoice-url, set per environment. Blank disables the button (lib/externalUrls.ts).
  readonly VITE_GBS_URL?: string;
  // SNC (Status and Clearance) web that the Tenure Application tab's SNC link opens; legacy's
  // sncsc-web.url, set per environment. Blank shows SNC unlinked (lib/externalUrls.ts).
  readonly VITE_SNC_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
