import { readFileSync, writeFileSync } from 'node:fs';

import { test as base, expect } from '@playwright/test';

import { SESSION_STORAGE_STATE, STORAGE_STATE } from './utils';

/** The saved sessionStorage snapshot, or null when there isn't one yet. */
const readSessionSnapshot = (): Record<string, string> | null => {
  try {
    return JSON.parse(readFileSync(SESSION_STORAGE_STATE, 'utf-8')) as Record<string, string>;
  } catch {
    return null;
  }
};

/**
 * The only non-GET API calls this suite may send. Both read: resolving audit ids
 * to display names, and generating a report.
 */
const READ_ONLY_POSTS = [/^\/api\/fta\/users\/resolve$/, /^\/api\/reports\//];

/**
 * Shared E2E test object. Use this instead of importing `test` straight from
 * `@playwright/test` so every spec gets the token persistence below.
 *
 * WHY: BC Gov SSO issues a **rotating** refresh token — each renewal mints a new
 * one and invalidates its predecessor. Every test context is created from the
 * same saved state (see playwright.config.ts), so without this, test 1 rotates
 * the shared refresh token and test 2 starts from the now-stale snapshot →
 * renewal refused → cascading auth failure across the whole suite.
 *
 * There are TWO halves to that state, and only one of them is Playwright's:
 *
 *   1. `storageState` (cookies + localStorage) — handled by `context.storageState`.
 *   2. **sessionStorage**, which is where oidc-client-ts keeps the tokens.
 *      Playwright does not capture it at all, so it is snapshotted by hand:
 *      injected with `addInitScript` before any page script runs, and written
 *      back after each test.
 *
 * Safe because the suite runs serially (workers: 1) — no two contexts ever hold
 * the same refresh token concurrently. The `auth.setup` project is unaffected;
 * it saves both halves itself and imports the base `test`.
 *
 * It also holds every test to four rules, so a screen that merely looks fine
 * still fails:
 *
 *   - **No writes.** The suite shares Oracle with everyone on TEST, and a cutting
 *     permit it added could not be deleted again (only an EE permit can be). Any
 *     other non-GET API call is aborted, whatever a selector ends up clicking.
 *   - **No 5xx.** The native SQL has no other coverage against a real schema.
 *   - **No 401 or 403.** The test user holds FTA_ADMIN, which may read everything.
 *     A refusal means that role is missing or the URL rules are wrong.
 *   - **No uncaught exception.** There is no error boundary, so a render crash
 *     otherwise shows only as a blank page.
 */
export const test = base.extend<{ persistRotatedTokens: void }>({
  // Overridden rather than handled in an auto fixture: the init script has to
  // be registered on the context BEFORE the page exists, and overriding `page`
  // is the only way to guarantee that ordering.
  //
  // The second parameter is Playwright's `use`, renamed: it is passed
  // positionally, and calling it `use` inside a function named `page` makes
  // eslint's react-hooks rule read it as React 19's `use` hook and fail the
  // build. Nothing here is React.
  page: async ({ context }, runTest) => {
    const snapshot = readSessionSnapshot();
    if (snapshot) {
      await context.addInitScript((entries: Record<string, string>) => {
        // Runs in every frame, and a sandboxed one refuses sessionStorage outright.
        try {
          for (const [key, value] of Object.entries(entries)) {
            window.sessionStorage.setItem(key, value);
          }
        } catch {
          /* not the app's frame */
        }
      }, snapshot);
    }

    const problems: string[] = [];

    await context.route(
      (url) => url.pathname.startsWith('/api/'),
      async (route) => {
        const request = route.request();
        const { pathname } = new URL(request.url());
        if (
          request.method() === 'GET' ||
          READ_ONLY_POSTS.some((allowed) => allowed.test(pathname))
        ) {
          await route.continue();
          return;
        }
        problems.push(`blocked write: ${request.method()} ${pathname}`);
        await route.abort();
      },
    );

    const page = await context.newPage();
    page.on('response', (response) => {
      const { pathname } = new URL(response.url());
      const status = response.status();
      if (pathname.startsWith('/api/') && (status >= 500 || status === 401 || status === 403)) {
        problems.push(`${status}: ${response.request().method()} ${pathname}`);
      }
    });
    page.on('pageerror', (error) => {
      problems.push(`uncaught exception: ${error.message}`);
    });

    await runTest(page);

    expect(problems, 'the app misbehaved during this test').toEqual([]);
  },

  persistRotatedTokens: [
    async ({ page, context }, use) => {
      await use();

      // Both may fail if a test tore its own context down; ignore and let the
      // next test fall back to the prior snapshot.
      // Only oidc-client-ts's own entries: the screens keep their last search in
      // sessionStorage too, and would otherwise re-run it in the next test.
      const session = await page
        .evaluate(() =>
          JSON.stringify(
            Object.fromEntries(
              Object.entries(window.sessionStorage).filter(([key]) => key.startsWith('oidc.')),
            ),
          ),
        )
        .catch(() => null);
      if (session) {
        writeFileSync(SESSION_STORAGE_STATE, session);
      }

      await context.storageState({ path: STORAGE_STATE }).catch(() => undefined);
    },
    { auto: true },
  ],
});

export { expect } from '@playwright/test';
export type { Page, Locator, Route } from '@playwright/test';
