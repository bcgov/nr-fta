import { defineConfig, devices } from '@playwright/test';

import { baseURL, STORAGE_STATE } from './e2e/utils';

/**
 * Playwright E2E config — mirrors nr-rept's structure.
 *
 * Auth flow:
 *   1. `npm run e2e:login` runs the `setup` project headed, parks at the IDIR
 *      login page, and once you sign in saves BOTH halves of the session:
 *      cookies + localStorage to e2e/.auth/user.json, and sessionStorage —
 *      where oidc-client-ts keeps the tokens, and which Playwright's
 *      storageState does not capture — to e2e/.auth/session-storage.json.
 *   2. All other projects start from that storageState, with the sessionStorage
 *      half restored by the `page` fixture in e2e/fixtures.ts, so each test
 *      boots already-authenticated. Import `test` from './fixtures', not from
 *      '@playwright/test', or the tokens will not be there.
 *
 * Override the target with E2E_BASE_URL (e.g. http://localhost:3000 for local).
 */
export default defineConfig({
  // The ceiling for one test. The slowest takes about 18s against a real backend
  // (a search plus its code lists), so this leaves it little room.
  timeout: 20_000,
  testDir: './e2e',
  // Serial execution. We share one rotating refresh token across specs (see
  // e2e/fixtures.ts); parallel workers race that renewal and intermittently
  // leave some contexts stuck on the white `<Loading>` overlay.
  workers: 1,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [['line'], ['list', { printSteps: true }], ['html', { open: 'never' }]],
  use: {
    baseURL,
    // Unset, a click on something that never appears waits out the whole test.
    actionTimeout: 10_000,
    navigationTimeout: 15_000,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [
    {
      name: 'setup',
      testMatch: /auth\.setup\.ts/,
      // Long enough for a person to sign in by hand with `npm run e2e:login`.
      timeout: 5 * 60_000,
      // A retry inside the same 30-second window would reuse a TOTP code Entra has
      // already refused, and repeated failures risk locking the account.
      retries: 0,
      // A trace records every call's arguments, so a retried login would carry
      // the password and TOTP code into the uploaded report. Screenshots of the
      // Microsoft pages add nothing worth that.
      use: {
        ...devices['Desktop Chrome'],
        actionTimeout: 30_000,
        navigationTimeout: 60_000,
        trace: 'off',
        screenshot: 'off',
      },
    },
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        storageState: STORAGE_STATE,
      },
      dependencies: ['setup'],
    },
    {
      name: 'Google Chrome',
      use: {
        ...devices['Desktop Chrome'],
        channel: 'chrome',
        storageState: STORAGE_STATE,
      },
      dependencies: ['setup'],
    },
    {
      name: 'firefox',
      use: {
        ...devices['Desktop Firefox'],
        storageState: STORAGE_STATE,
      },
      dependencies: ['setup'],
    },
    {
      name: 'safari',
      use: {
        ...devices['Desktop Safari'],
        storageState: STORAGE_STATE,
      },
      dependencies: ['setup'],
    },
    {
      name: 'Microsoft Edge',
      use: {
        ...devices['Desktop Edge'],
        channel: 'msedge',
        storageState: STORAGE_STATE,
      },
      dependencies: ['setup'],
    },
  ],
});
