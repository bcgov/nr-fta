# FTA Frontend (SPA)

The Forest Tenure Administration single-page app — a Carbon rebuild of the
legacy Struts/JSP FTA screens.

- **React 19** + **TypeScript**, built with **Vite 6**
- **BC Gov Carbon Design System** (`@carbon/react`) — the BCGov theme is
  inlined under `src/styles/bcgov/`
- **oidc-client-ts** for BC Gov SSO (Keycloak) auth via CSS — IDIR only
- `react-router-dom` for routing

It talks to the [backend API](../backend/README.md) with a BC Gov SSO access
token; see [../docs/architecture.md](../docs/architecture.md) for the big
picture.

## Local development

```bash
npm install
npm run dev          # Vite dev server → http://localhost:3000
```

Requires `frontend/.env` (copy `.env.example` and fill in `VITE_KEYCLOAK_URL`,
`VITE_KEYCLOAK_CLIENT_ID` and the API base). Vite proxies `/api/*` to the
backend at `:8080` (see `vite.config.ts`). Or run the whole stack from the repo
root with `docker compose up`.

## Scripts

| Command | What it does |
|---------|--------------|
| `npm run dev` / `start` | Vite dev server (HMR) |
| `npm run build` | Production build |
| `npm run preview` | Serve the production build locally |
| `npm run typecheck` | `tsc --noEmit` (the real type gate — CI runs this) |
| `npm run e2e` | Playwright end-to-end suite (see [e2e/README.md](e2e/README.md)) |
| `npm run lint` | placeholder (no ESLint config yet) |
| `npm test` | placeholder (no unit tests yet — coverage is e2e) |

> There is no unit-test or lint setup yet; `typecheck` + the Playwright e2e
> suite are the current quality gates.

## Structure

```
src/
├── main.tsx, App.tsx       entry + top-level router
├── pages/                  grouped by domain: search/, tenure/, harvesting/,
│                           inbox/, marks/, range/, admin/, plus Welcome,
│                           LandingPage, AuthCallback and the error pages.
│                           Each is named after its legacy FTA screen id
│                           (TenureSearch = FTA001, TenureDetail = FTA100, …)
├── components/             shared UI — SearchResultsTable, Tombstone,
│                           DefinitionGrid, AsyncBoundary, Layout, modals
├── routes/                 routePaths.ts (nav model) + access.ts (role capabilities)
├── services/               one typed module per backend controller
│                           → http.ts → apiFetch.ts (bearer token + CSRF +
│                           ProblemDetail parsing)
├── context/                auth, layout, theme, notifications
├── hooks/                  useApiResource (fetch + loading/error state)
├── styles/                 Carbon + inlined BCGov theme (styles/bcgov/)
├── mocks/                  mock records for screens not yet wired to the API
├── lib/, utils/            error-message sanitising, date/download helpers
└── env.ts                  typed environment access (VITE_* vars)
```

Two screen patterns cover most of the app: **search → list** (criteria grid →
Carbon `DataTable` → row links to a detail route) and **tabbed detail**
(tombstone of key facts + one tab per sub-entity). Exhibit-A spatial screens add
Leaflet.

## Routing & access

Four roles, and a user resolves to exactly one of them — no stacking. When
several are granted, the highest in this table wins:

| Role | Menus | Capability |
|---|---|---|
| `FTA_ADMIN` | all, including Admin | full CRUD |
| `FTA_TIMBER_MARK_HEADQUARTERS_ADMIN` | Tenure Search, Timber Mark Search, Private Marks (plus the tenure, cutting-permit and mark details they open) | private-mark writes; read-only elsewhere |
| `FTA_TIMBER_MARK_DISTRICT_ADMIN` | as `FTA_TIMBER_MARK_HEADQUARTERS_ADMIN` | as `FTA_TIMBER_MARK_HEADQUARTERS_ADMIN`; new applications start PA and go to Headquarters with Submit to HQ (PA → PI); printing a mark's certificate also marks it issued (HN → HI) |
| `FTA_VIEWER` | all except Admin | read-only |

- `routes/access.ts` is the **source of truth for page access** —
  `isPathAllowedForRole` / `isPathAllowedForUser` drive both the route guard
  and the nav. The two timber mark roles share an allow-list of page patterns, so a
  new screen is hidden from it until listed there.
- `routes/routePaths.ts` is the **nav model**. An entry is shown only when the
  role may open its page; entries may also carry an extra `roles` gate (only
  the Admin branch does, `['FTA_ADMIN']`).
- `routes/access.ts` also holds the **capability predicates** — `effectiveRole`,
  `isAdministrator`, `canEdit`, `canEditMarks`, `defaultRouteForUser`. Screens
  gate their edit affordances on `canEdit`; the private-mark screens use
  `canEditMarks`, which also admits both timber mark roles.

Whatever the UI hides, the backend independently enforces via URL-level
authority checks — see
[`ApiAuthorizationCustomizer`](../backend/src/main/java/ca/bc/gov/nrs/fta/security/ApiAuthorizationCustomizer.java)
and [../docs/architecture.md](../docs/architecture.md).

## Auth

Authorization Code + PKCE against the BC Gov SSO standard realm, via
`oidc-client-ts`. `services/keycloak.ts` owns the `UserManager`; the whole
client is configured from one issuer URI, which it uses to discover the
authorize, token and end-session endpoints. Tokens live in **sessionStorage**.

Sign-in redirects to `/authCallback`, which performs the code exchange — that
route is in the **public** route table and sits above the `*` catch-all, which
would otherwise swallow the callback before it could run.

`context/auth` exposes the session. `services/apiFetch.ts` renews the token if
it is near expiry, attaches it as a `Bearer` header on every API call, and
normalizes error bodies (RFC 7807 `ProblemDetail` → `detail`/`message`/`title`)
into a clean message for toasts. `services/http.ts` sits on top of it and adds
the `X-XSRF-TOKEN` header on every write — service modules call `apiGet` /
`apiPost` / `apiPut` / `apiDelete` from there, never `apiFetch` directly.

## Deployment

Built to a static bundle and served by **Caddy**, which reverse-proxies
`/api/*` to the backend Service on OpenShift (CI/CD in
[`.github/workflows/`](../.github/workflows/)). Redirect-URI slots are managed
per PR preview — see the CI workflows. That bucketing was forced by Cognito's
refusal of wildcard callback URLs and may be removable now that the URIs live on
the CSS integration.
