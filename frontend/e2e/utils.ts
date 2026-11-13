import path from 'node:path';

import type { BrowserContext, Page, Response, Route } from '@playwright/test';

// E2E_BASE_URL is set by `.github/workflows/reusable-tests.yml` at step
// level (resolved from the PR slot or test/prod target). For local hand-runs,
// export it explicitly:
//   E2E_BASE_URL=http://localhost:3000 npm run e2e
// Failing fast here beats silently targeting a stale dev URL.
if (!process.env.E2E_BASE_URL) {
  throw new Error(
    'E2E_BASE_URL is not set. Export it before running Playwright ' +
      '(e.g. `E2E_BASE_URL=http://localhost:3000 npm run e2e`).',
  );
}

export const baseURL = process.env.E2E_BASE_URL;

/** Path to the saved auth state produced by auth.setup.ts. */
export const STORAGE_STATE = path.join(import.meta.dirname, '.auth', 'user.json');

/**
 * Path to the saved **sessionStorage** snapshot, which is where the OIDC tokens
 * actually live.
 *
 * Playwright's `storageState` captures cookies and localStorage — and nothing
 * else. Under Cognito that was enough, because Amplify was configured with
 * CookieStorage and the tokens were cookies. oidc-client-ts keeps them in
 * sessionStorage (see `src/services/keycloak.ts`, where that is a deliberate
 * choice), so `user.json` no longer carries a session on its own and this file
 * has to be saved and restored alongside it.
 */
export const SESSION_STORAGE_STATE = path.join(
  import.meta.dirname,
  '.auth',
  'session-storage.json',
);

/**
 * Unique-ish identifier suffix for test artifacts so concurrent runs and
 * leftover rows don't collide. Format: e2e-<base36 timestamp>-<rand>.
 */
export const uniqueSuffix = (): string => {
  const t = Date.now().toString(36);
  const r = Math.floor(Math.random() * 36 ** 4)
    .toString(36)
    .padStart(4, '0');
  return `e2e-${t}-${r}`;
};

type FulfillBody = NonNullable<NonNullable<Parameters<Route['fulfill']>[0]>['body']>;

interface CachedAsset {
  status: number;
  contentType: string;
  body: FulfillBody;
}

const staticAssetCache = new Map<string, CachedAsset>();

const STATIC_ASSET_PATH =
  /^\/(assets|fonts)\/|^\/config\.js$|\.(js|css|woff2?|ttf|otf|png|jpe?g|svg|ico)$/i;

/** True when `pathname` is a static frontend asset rather than an HTML route or API call. */
export const isStaticAssetPath = (pathname: string): boolean =>
  !pathname.startsWith('/api/') && STATIC_ASSET_PATH.test(pathname);

/**
 * Serve GET requests for static assets (and optionally the SPA HTML shell when
 * `cacheDocuments` is true) from an in-memory cache after the first fetch from
 * the deployment. Playwright creates a fresh incognito BrowserContext per test,
 * which otherwise wipes the browser HTTP cache and forces the 200m-CPU Caddy +
 * Coraza container to re-serve the full Vite bundle hundreds of times.
 */
export const fulfillWithAssetCache = async (
  route: Route,
  options: { cacheDocuments?: boolean } = {},
): Promise<void> => {
  const request = route.request();
  if (request.method() !== 'GET') {
    await route.continue();
    return;
  }

  const isDoc = request.resourceType() === 'document';
  if (isDoc && !options.cacheDocuments) {
    await route.continue();
    return;
  }

  const key = isDoc ? '__spa_document__' : new URL(request.url()).pathname;
  const cached = staticAssetCache.get(key);
  if (cached) {
    await route.fulfill({
      status: cached.status,
      contentType: cached.contentType,
      body: cached.body,
    });
    return;
  }

  try {
    const response = await route.fetch({ timeout: 10_000 });
    const body = await response.body();
    const status = response.status();
    const contentType = response.headers()['content-type'] ?? 'application/octet-stream';
    if (status === 200) {
      staticAssetCache.set(key, { status, contentType, body });
    }
    await route.fulfill({ status, contentType, body });
  } catch {
    await route.abort('timedout').catch(() => undefined);
  }
};

/**
 * Cache immutable static subresources (`/assets/*`, `/fonts/*`, `/config.js`,
 * images) across tests in this worker while leaving document and `/api/*`
 * requests live.
 */
export const installStaticAssetCache = async (context: BrowserContext): Promise<void> => {
  const { origin } = new URL(baseURL);
  await context.route(
    (url) => url.origin === origin && isStaticAssetPath(url.pathname),
    (route) => fulfillWithAssetCache(route),
  );
};

const TRANSIENT_NAV_ERROR =
  /TimeoutError|Timeout \d+ms exceeded|net::ERR_CONNECTION_REFUSED|net::ERR_CONNECTION_RESET|net::ERR_TIMED_OUT|net::ERR_NETWORK_CHANGED|net::ERR_ABORTED|ECONNREFUSED/;

const isTransientNavigationError = (err: unknown): boolean => {
  const message = err instanceof Error ? err.message : String(err);
  const name = err instanceof Error ? err.name : '';
  return TRANSIENT_NAV_ERROR.test(`${name}: ${message}`);
};

/**
 * Navigate to `path` with bounded retry/backoff when the edge router or pod
 * briefly refuses, drops, or stalls the navigation.
 */
export const gotoWithRetry = async (page: Page, path: string): Promise<Response | null> => {
  const maxAttempts = 4;
  const attemptTimeoutMs = 10_000;
  const backoffMs = [1_500, 3_000, 5_000];

  for (let attempt = 1; ; attempt++) {
    try {
      return await page.goto(path, { timeout: attemptTimeoutMs, waitUntil: 'domcontentloaded' });
    } catch (err) {
      if (attempt >= maxAttempts || !isTransientNavigationError(err)) {
        throw err;
      }
      await page.waitForTimeout(backoffMs[attempt - 1]);
    }
  }
};

/**
 * Navigate to a route on the protected side of the SPA and wait until the
 * Layout header has rendered — that means AuthProvider has finished
 * bootstrapping and we're past the white `<Loading withOverlay>` screen.
 *
 * Retries when either the document navigation or the initial bundle mount
 * stalls leaving `<div id="root"></div>` empty.
 */
export const gotoProtected = async (page: Page, path: string): Promise<void> => {
  // Capture browser console messages and page errors during this navigation.
  const consoleMessages: string[] = [];
  const pageErrors: string[] = [];
  const onConsole = (msg: { type(): string; text(): string }) => {
    consoleMessages.push(`[${msg.type()}] ${msg.text()}`);
  };
  const onPageError = (err: Error) => {
    pageErrors.push(err.message);
  };
  page.on('console', onConsole);
  page.on('pageerror', onPageError);

  const maxAttempts = 4;
  const backoffMs = [1_500, 3_000, 5_000];

  try {
    for (let attempt = 1; ; attempt++) {
      try {
        await page.goto(path, { timeout: 10_000, waitUntil: 'domcontentloaded' });
        await page.getByTestId('bc-header__header').waitFor({ timeout: 10_000 });
        break;
      } catch (err) {
        const currentUrl = page.url();
        const uncommitted =
          currentUrl === 'about:blank' || currentUrl.startsWith('chrome-error://');
        const rootEmpty = await page
          .evaluate(() => (document.getElementById('root')?.innerHTML.trim().length ?? 0) === 0)
          .catch(() => true);
        if (
          attempt >= maxAttempts ||
          (!uncommitted && !rootEmpty) ||
          !isTransientNavigationError(err)
        ) {
          throw err;
        }
        await page.waitForTimeout(backoffMs[attempt - 1]);
      }
    }
  } catch (err) {
    const url = page.url();
    const title = await page.title().catch(() => '(unavailable)');
    const readyState = await page.evaluate(() => document.readyState).catch(() => '(unavailable)');
    const bodyHTML = await page
      .evaluate(() => document.body?.innerHTML?.slice(0, 1500) ?? '(no body)')
      .catch(() => '(unavailable)');
    const rootContent = await page
      .evaluate(() => document.getElementById('root')?.innerHTML?.slice(0, 800) ?? '(no #root)')
      .catch(() => '(unavailable)');
    const loadingVisible = await page
      .getByTestId('loading')
      .isVisible()
      .catch(() => false);
    throw new Error(
      `gotoProtected("${path}") failed to find the Layout header.\n` +
        `  Current URL  : ${url}\n` +
        `  Page title   : ${title}\n` +
        `  readyState   : ${readyState}\n` +
        `  Loading?     : ${loadingVisible}\n` +
        `  #root excerpt: ${rootContent}\n` +
        `  body excerpt : ${bodyHTML}\n` +
        `  Console (${consoleMessages.length}): ${consoleMessages.slice(-15).join(' | ') || '(none)'}\n` +
        `  Page errors (${pageErrors.length}): ${pageErrors.join(' | ') || '(none)'}\n` +
        `  Original     : ${err instanceof Error ? err.message : String(err)}`,
      // Keep the original stack reachable — the message above is a summary,
      // not a replacement for where the failure actually came from.
      { cause: err },
    );
  } finally {
    page.off('console', onConsole);
    page.off('pageerror', onPageError);
  }
};
