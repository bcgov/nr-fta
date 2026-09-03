# FTA end-to-end tests

Playwright suite that drives the real SPA against a real backend (BC Gov SSO
auth + Oracle). One `setup` project authenticates via IDIR and saves the
session; every other project boots from it already signed in.

## Running

```bash
# 1. Point at a target (no default — fail fast beats a stale URL)
export E2E_BASE_URL=http://localhost:3000      # or a deployed slot

# 2. Log in once (interactive, headed) — caches the session under e2e/.auth/
npm run e2e:login

# 3. Run
npm run e2e                # chromium
npm run e2e:ui             # Playwright UI mode
npm run e2e:all-browsers   # every project
```

In CI, `auth.setup.ts` logs in programmatically from `E2E_IDIR_USER` /
`E2E_IDIR_PASSWORD` (see `.github/workflows/reusable-tests.yml`).

## The session is two files, not one

This is the part that surprises people. `npm run e2e:login` writes **both**:

| File | Holds | Written by |
|---|---|---|
| `e2e/.auth/user.json` | cookies + localStorage | Playwright's `context.storageState` |
| `e2e/.auth/session-storage.json` | **the OIDC tokens** | by hand, in `auth.setup.ts` |

Playwright's `storageState` does not capture sessionStorage, and that is exactly
where `oidc-client-ts` keeps the tokens (see `src/services/keycloak.ts`). So
`user.json` on its own is not a session.

`e2e/fixtures.ts` restores the second half — it overrides the `page` fixture to
register an `addInitScript` on the context *before* the page exists, and writes
the snapshot back after every test. **Import `test` from `./fixtures`, not from
`@playwright/test`**, or your spec starts signed out.

Writing back after each test is not tidiness: BC Gov SSO issues a **rotating**
refresh token, so a renewal in one spec invalidates the token the next spec
would have started from. That is also why the suite runs with `workers: 1`.

Re-run `npm run e2e:login` when the cached session expires — symptom: specs
bouncing to the login domain, or 401s. **That window is 30 minutes**, half of
what the Cognito refresh token allowed.

### Programmatic login and MFA

FTA signs in through the standard realm's **IDIR - MFA** integration, which
federates to Azure AD and can require a second factor — something a stored
password cannot satisfy. The programmatic path works only for an account whose
MFA is met without interaction (a CI service account with a conditional-access
exemption). If CI starts timing out at the Microsoft sign-in page, that is why,
and no selector change will fix it.

## What's covered

| Spec | Backend | Notes |
|------|---------|-------|
| `smoke.spec.ts` | real | root returns 200 |

That is the whole suite today. It is deliberately a placeholder: it lets
`reusable-tests.yml`'s chromium project pass on a fresh PR rather than failing
with "No tests found". Authenticated flows are not yet covered.

## Helpers

- `utils.ts` — `baseURL`, the two storage-state paths, `gotoProtected` (navigate
  and wait for the Layout header, with a diagnostic dump on timeout), and small
  test-data helpers.
- `helpers/nav.ts` — `PAGES`, every SideNav-reachable route with its `<h1>` and
  `data-testid` suffix, plus `gotoPage`. Mirrors `NAV` in
  `src/routes/routePaths.ts`; keep the two in step. Admin entries carry
  `role: 'FTA_ADMIN'` — a `FTA_VIEWER` session gets `ForbiddenPage` for those.

## Prerequisites

- **A role on the token.** A signed-in account with no `FTA_ADMIN` /
  `FTA_VIEWER` role lands on `UnauthorizedPage`, and every page assertion will
  fail. Roles are assigned per environment in the CSS console.
- **`FTA_ADMIN` for the Admin section and any write flow.** `FTA_VIEWER` has its
  edit affordances hidden in the UI *and* its writes rejected with 403 by the
  backend, so an admin storageState is needed for those.
- **A reachable Oracle.** The backend needs a datasource to boot at all; without
  VPN and credentials the app will not come up for the suite to drive.
