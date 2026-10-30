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

In CI, `auth.setup.ts` logs in programmatically from `E2E_IDIR_USER`,
`E2E_IDIR_PASSWORD` and `E2E_IDIR_TOTP_SECRET` (see
`.github/workflows/reusable-tests.yml`).

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

### Unattended login (TOTP)

FTA signs in through the standard realm's **IDIR - MFA** integration, which
federates to Azure AD and requires a second factor. Set `E2E_IDIR_USER`,
`E2E_IDIR_PASSWORD` and `E2E_IDIR_TOTP_SECRET` and the setup project signs in on
its own, answering the prompt with a generated authenticator code (`totp.ts`).
CI reads the same three values from repository secrets of the same names.

This follows BC Gov SSO's
[Automating MFA with TOTP](https://bcgov.github.io/sso-docs/advanced/automating-mfa-totp)
guide, and should use a dedicated IDIR test account. To get the secret, sign in
as that account at <https://mysignins.microsoft.com/security-info>, then
**Add sign-in method → Microsoft Authenticator → Set up a different
authentication app → Can't scan QR code?** and copy the **Secret key**. Spaces
and case don't matter. Finish the registration by entering one generated code so
the method is actually enrolled, and make it the default sign-in method.

The secret is the second factor itself: keep it in a masked secret, never in the
repo or a committed `.env`.

```bash
E2E_IDIR_USER=... E2E_IDIR_PASSWORD=... E2E_IDIR_TOTP_SECRET=... npm run e2e:login
```

## The test user

**Grant `FTA_ADMIN`, and nothing else**, in the CSS console, in each environment
the suite runs against: DEV for PR previews and TEST on merge. Roles don't stack,
so a second role would change nothing. `smoke.spec.ts` names this as the problem
when the role is missing.

## What every test is held to

`fixtures.ts` fails a test, whatever its own assertions say, on:

- **A write.** The suite is read-only, because it shares Oracle with everyone on
  TEST and what it added could not all be removed: only an EE cutting permit can
  be deleted, and a new one starts as PE. Any non-GET API call other than name
  lookups and reports is aborted. Dialogs are opened and cancelled, never saved.
- **A 5xx**, which is how an untested native SQL query shows itself.
- **A 401 or 403**, which means the role is missing or the URL rules are wrong.
- **An uncaught exception.** There is no error boundary, so it is a blank page.

## What's covered

| Spec | Backend | Notes |
|------|---------|-------|
| `smoke.spec.ts` | real | the test user's role; every page in the menu renders; 404 |
| `navigation.spec.ts` | real | every menu entry opens its page; labels, icons, bold, open section; the New Application dialog |
| `search.spec.ts` | real | all nine searches run; the criteria rules legacy enforced; Clear all |
| `tenure.spec.ts` | real | search to tenure, every tab; back link; add-permit and add-tenure validation |
| `harvest.spec.ts` | real | permit and cut block: every tab, edit dialogs, Delete's confirmation, assign marks, suspend, re-label |
| `marks.spec.ts` | real | list to private mark, every tab; the new application dialog |
| `range.spec.ts` | real | range tenure with every tab; range unit |
| `admin.spec.ts` | real | audit report; transfer and archive stay disabled until they are ready |
| `role-access.spec.ts` | none | all six roles: menu, route guard, harvest actions; no role; signed out |

Searches are data-agnostic: `helpers/search.ts` tries the first few districts
until one returns a row. A test **skips** when none does, or when the record it
found doesn't offer the action under test, so read the skip count: a skip is a gap
in that run's coverage, not a pass.

### How `role-access.spec.ts` covers six roles with one account

The realm can't mint a token for a role the test user doesn't hold, but the
frontend's role rules read only the profile in sessionStorage. So
`helpers/mockSession.ts` forges one, and answers every API call from
`helpers/mockApi.ts` so that the forged token never leaves the browser. This proves
what each role is shown. It proves nothing about what the server would refuse:
that is `HarvestAuthorizationRulesTest` in the backend.

## Helpers

- `utils.ts` — `baseURL`, the two storage-state paths, `gotoProtected` (navigate
  and wait for the Layout header, with a diagnostic dump on timeout), and small
  test-data helpers.
- `helpers/nav.ts` — `PAGES`, every SideNav-reachable route with its `<h1>`,
  `data-testid` suffix and section, plus `gotoPage`, `openNavSection` (a section's
  entries are hidden until it is open) and `walkTabs`. Mirrors `NAV` in
  `src/routes/routePaths.ts`; keep the two in step.
- `helpers/search.ts` — running a search and opening its first result.
- `helpers/mockSession.ts`, `helpers/mockApi.ts` — see above.

## Prerequisites

- **`FTA_ADMIN` on the test user** — see [The test user](#the-test-user).
- **A reachable Oracle.** The backend needs a datasource to boot at all; without
  VPN and credentials the app will not come up for the suite to drive.
