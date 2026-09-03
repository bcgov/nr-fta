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
  // 60s is a generous ceiling for a single test against the shared, slower
  // TEST/preview infra — the proc-heavy flows still finish well under it.
  // Keeping it tight bounds the blast radius of any hang: with retries: 2 a
  // stuck test now costs ~3 min instead of ~9, and fails legibly rather than
  // masquerading as "slow CI".
  timeout: 60_000,
  testDir: './e2e',
  // Serial execution. We share one rotating refresh token across specs (see
  // e2e/fixtures.ts); parallel workers race that renewal and intermittently
  // leave some contexts stuck on the white `<Loading>` overlay.
  workers: 1,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: [['line'], ['list', { printSteps: true }], ['html', { open: 'never' }]],
  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [
    {
      name: 'setup',
      testMatch: /auth\.setup\.ts/,
      use: { ...devices['Desktop Chrome'] },
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
