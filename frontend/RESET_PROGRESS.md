# FTA Frontend Reset — Progress Tracker

> **Historical tracker, not current documentation.** It records the July 2026
> frontend reset. Notably, auth has since moved from FAM/Cognito to BC Gov SSO
> (Keycloak) — see [../docs/architecture.md](../docs/architecture.md) and
> [README.md](README.md) for how authentication actually works now.

Goal: reset the FTA frontend to mirror **nr-fsp-new**'s shell/styling exactly, and
re-implement the **fta-archive** (legacy Struts/JSP) screens as Carbon React pages
driven by **mock data** (no backend APIs yet).

Reference repos (read-only):
- Shell/style source: `/Users/marcovilleneuve/IdeaProjects/nr-fsp-new/frontend`
- Screen/UX source: `/Users/marcovilleneuve/IdeaProjects/fta-archive` (JSPs under `fta/source/ear/src/main/webapp`)

Backup of pre-reset WIP: git branch `backup/pre-reset-wip` (commit `6ab705f`).

Alignment with new Spring Boot backend:
- Roles: `FTA_ADMIN` (full CRUD), `FTA_VIEWER` (read-only). IDIR-only (no BCeID).

## Phase 0 — Reset & foundation ✅ DONE
- [x] Backup current WIP to `backup/pre-reset-wip`
- [x] Copy FSP frontend as base, prune FSP-domain files
- [x] Rebrand auth (types, authUtils, AuthProvider) → FTA roles, IDIR-only
- [x] Rebrand shell (LayoutHeader app name, HeaderPanelProfile drop org, theme storage key)
- [x] main.tsx / App.tsx (drop OrgProvider, FTA routes)
- [x] routePaths.ts / access.ts → full FTA nav IA + role gating
- [x] Core pages: Landing (IDIR login), Welcome/Home, NotFound, Forbidden, Unauthorized
- [x] index.html / package.json rebrand
- [x] npm install + typecheck + build green + vite dev boots

## Phase 1 — Mock data + page pattern (exemplars) ✅ DONE
- [x] Mock-data convention: `src/mocks/tenures.ts`
- [x] Search→list pattern: Tenure Search (`src/pages/search/TenureSearch`, FTA001)
- [x] Tabbed-detail pattern: Tenure Detail (`src/pages/tenure/TenureDetail`, FTA100)
- [x] Scaffold pattern: `src/components/ScreenPlaceholder` (every nav route resolves)
- [x] Admin-gated pattern established via `/admin/*` routes + `canEdit()`

### Patterns to follow when porting the remaining screens
- Page shell: `PageLayout title=...` wrapper; co-located `*.scss`.
- Search screen: criteria `<Grid>`/`<TextInput>`/`<Select>` + `<Button>` → Carbon
  `<DataTable>` results; row links to a detail route; `<EmptyState>` on no rows.
- Detail screen: tombstone `<section>` of key facts + Carbon `<Tabs>` per sub-entity.
- Mock data lives in `src/mocks/<domain>.ts` with typed records + `search*/find*` fns.
- Edit affordances gated by `canEdit(user)` from `@/routes/access`.
- Roles: `FTA_ADMIN` / `FTA_VIEWER` only. IDIR-only auth.

## Phase 2 — Tenure/Harvest line (priority: deep) — IN PROGRESS
Reusable building blocks built: `SearchResultsTable`, `Tombstone`, `DefinitionGrid`.
Mock data: `src/mocks/tenures.ts`, `src/mocks/harvesting.ts`.

Sub-batch 1 (DONE — navigable end-to-end, typecheck+build green):
- [x] Tenure Search (FTA001) → Tenure Detail (FTA100)
- [x] Harvesting Authority Search (FTA005) → Cutting Permit Detail (FTA902)
- [x] Cut Block Search (FTA003) → Cut Block Detail (FTA904)
- [x] Tenure detail tabs link through to CP + cut-block details

Sub-batch 2 (DONE — typecheck+build green):
- [x] Inbox worklist (FTA300) + nav entry → Application Detail (FTA952) w/ adjudication actions
- [x] Add New Tenure (FTA010) create form (write gated to FTA_ADMIN)
- [x] Tenure landing (/tenures) — entry points + recent list
- [x] Mock: `src/mocks/applications.ts`

Sub-batch 3 (DONE — typecheck+build green):
- [x] Road Section detail (FTA131) + Segments (FTA907) + Tenure History (FTA906) tabs
- [x] Tenure Detail expanded: Roads, Associated Files (FTA910), Sale Info (FTA940) tabs
- [x] AAC covered by tenure AAC tab (FTA930)

Sub-batch 4 (DONE — typecheck+build green): Exhibit-A spatial
- [x] Exhibit A tenure map (FTA304) + Feature List (FTA316) + Conflicts (FTA315) — Leaflet
- [x] Upload Exhibit A (FTA307)
- [x] Linked from Application Detail's Exhibit A tab; mock: `src/mocks/spatial.ts`

Sub-batch 5 (DONE — typecheck+build green): Harvesting / cut-block ops
- [x] Assign Marks to Blocks / Hauling Authority (FTA908)
- [x] Suspend Multiple Blocks within permit (FTA912)
- [x] Cut-block Amendment (FTA905) / Suspend (FTA914) / Re-label (FTA231) — parameterized CutBlockAction
- [x] Ops wired as admin-gated action buttons on CP + Cut Block detail

Tenure/Harvest line — considered COMPLETE for mock-data pass (18 screens).
Minor deferrals (low value / covered elsewhere):
- FTA990 Harvest History → covered by CP detail tab
- FTA901/903/980 standalone lists → covered by tenure detail tabs
- FTA909 Oil & Gas CP variant, FTA302 adjudication-comments screen, FTA305/306/313 → deferred

## Phase 3 — Private Marks + Range — DONE (typecheck+build green)

Private Marks (mock: `src/mocks/marks.ts`):
- [x] Mark Application/Amendment List (FTA500) → Mark Detail
- [x] Mark Detail — tabs: Mark Application (FTA510), Land Index (FTA511), Assoc Clients (FTA513), Amendments; Amend action (FTA512)
- [x] New Mark Application create form (FTA510)

Range (mock: `src/mocks/range.ts`):
- [x] Range Tenure Search (FTA001R) → Range Tenure Detail
- [x] Range Tenure Detail (FTA100R) — tabs: Range Usage (FTA613R), Rotations (FTA611/612), Land Base (FTA615R), Usage History
- [x] Range Unit / Pasture Search (FTA006) → Range Unit Detail (FTA630) w/ pasture breakdown

## Phase 4 — Remaining searches + Admin/Billing — DONE (typecheck+build green)
Recreation line DROPPED per ADR-0001 (nav + routes removed).

Searches (all real now):
- [x] Timber Mark Search (FTA002), Client Search (SIL21), Management Unit Search (SIL004), Application Metrics Export (FTA008)
- [x] Mock: `src/mocks/reference.ts` (clients, mgmt units, rates, zones, org units)

Admin / Billing / Reports (all FTA_ADMIN-gated; mock: `src/mocks/billing.ts`):
- [x] Audit Report (FTA675)
- [x] Rates & Fees Maintenance (FTA699)
- [x] Manage Range Zone (FTA631R)
- [x] Org Unit Maintenance (SIL99)
- [x] Timber Mark Transfer (FTA240)
- [x] Archive Tenures (FTA640)
- [x] Billing flows via shared BillingReportScreen: Rents & Fees Prep (FTA670), Billing Instructions (FTA680), Pre/Post Billing (FTA685/686), Approval (FTA690), Invoice Preview (FTA695)

## STATUS: every side-nav route resolves to a real Carbon screen on mock data.
36 page components. ScreenPlaceholder scaffold removed (no longer needed).
Remaining (deferred, low value): niche detail screens FTA909 (Oil & Gas CP),
FTA302/305/306/313 (extra Exhibit-A variants), FTA990/901/903/980 (covered by tabs).

## Phase 3 — Other lines (deferred until Tenure line done)
- [ ] Search screens: Timber Mark (FTA002), Range Tenure (FTA001R), Range Unit (FTA006), Recreation (FTA007), Metrics (FTA008), Client (SIL21), Mgmt Unit (SIL004)
- [ ] Tenure detail tabs (FTA100/910/920/930/940/970/…)
- [ ] Cutting Permits / Harvesting Authority (FTA901/902/908/909/912/990)
- [ ] Cut Blocks / Roads (FTA903/904/905/914/231/980/140/131/133/906/907)
- [ ] Timber Marks (FTA101/240)
- [ ] Inbox / Exhibit A spatial workflow (FTA300–316)
- [ ] Tenure Applications / Smart Forms (FTA950/952/953)
- [ ] Private Marks (FTA500/510/511/512/513)
- [ ] Range (FTA611/612/613/615/616/630/631)
- [ ] Recreation (FTA701–708)
- [ ] Oil & Gas (FTA945/977)
- [ ] Admin / Billing / Reports (FTA670/675/680/685/686/690/695/699, SIL99)

## Phase 5 — Backend wiring (legacy PROC-grounded) — DONE (mvn compile + tsc + build green)
Approach: each screen ports its legacy Oracle PL/SQL package (fta-archive/.../ddl/pkg/FTA_xxx)
to a Spring Boot vertical slice — DTO (from the package record type) + @Service using
NamedParameterJdbcTemplate with native SQL against the shared `THE` schema (mirroring the
package body) + @RestController (params from the package mainline). Frontend gets a typed
service in `src/services/<screen>.ts` (via services/http.ts) and the page reads live data
through AsyncBoundary + useApiResource. Ran as a 20-agent workflow, then serial verify/fix.

Wired (21 screens incl. tenure exemplar): tenure search+detail, harvesting search, cutting-permit
detail, cut-block search+detail, road detail, timber-mark search, mark list+detail, range tenure
search+detail, range unit search+detail, client search, mgmt-unit search, inbox, application detail,
admin audit/rates/manage-zone. 76 backend Controller/Service/Dto files; 22 frontend service modules.
Removed obsolete placeholder MarkController/RangeController (resolved a duplicate /api/fta/marks mapping).

Caveats: no Oracle here → verified compile/typecheck/build/boot only, NOT that the SQL returns correct
rows (native queries derived from the package specs/bodies need validation against a real THE schema).
App won't boot locally without a DataSource (Oracle creds in application-local.yml).

Still on mocks (POST/action flows, not in this pass): Add Tenure, Mark Application/Transfer, Suspend
Blocks, Assign Marks, Cut Block actions, Upload Exhibit A, Org Unit Maint, Archive Tenures, Billing
submissions, Application Metrics. These are write endpoints for a follow-up pass.

## Phase 6 — Backend WRITE flows — DONE (mvn compile + tsc + build + boot green)
Same PROC-grounded approach, POST/PUT: each write screen ports its legacy package's
insert/update proc → Request DTO + @Service (jdbc.update vs THE) + @Post/@PutMapping
controller (audit user from JWT via JwtPrincipalUtil.getIdpUsername). Frontend action
handlers now call apiPost/apiPut with saving state + success/error toasts. All write
endpoints FTA_ADMIN-gated (POST/PUT /api/** → FTA_ADMIN in ApiAuthorizationCustomizer).
Ran as a 12-agent workflow (9 first pass, 3 re-run after a transient classifier block).

Wired writes: Add Tenure (exemplar), Mark Application, Mark Transfer, Suspend Blocks,
Assign Marks/Hauling, Cut Block amend/suspend/relabel, Upload Exhibit A, Application
adjudication (assign/clarify/hold/clear/reject), Archive Tenures, Org Unit default,
Rates save (PUT), Manage Zone add, Billing submissions. 13 controllers with @Post/@PutMapping.

Same caveat: verified compile/typecheck/build/boot only — the INSERT/UPDATE SQL is
derived from the package specs/bodies and NOT validated against a real THE schema.

## OVERALL STATUS: full-stack app. All read screens + all action screens wired to
Spring Boot endpoints ported from the legacy FTA PL/SQL packages. Remaining work is
DB validation (needs a reachable THE schema) and any per-screen SQL/business-rule fidelity.
