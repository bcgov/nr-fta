import { readFileSync } from 'node:fs';

import type { BrowserContext } from '@playwright/test';

import { SESSION_STORAGE_STATE, baseURL } from '../utils';

import { mockGet } from './mockApi';

/**
 * Where oidc-client-ts keeps the signed-in user, `oidc.user:<authority>:<client>`.
 * Read from the real session auth.setup.ts saved rather than rebuilt, because the
 * authority and client come from the deployment's own config. Nothing else is
 * taken from that session: its tokens never go near a forged one.
 */
const userStorageKey = (): string => {
  const session = JSON.parse(readFileSync(SESSION_STORAGE_STATE, 'utf-8')) as Record<
    string,
    string
  >;
  const key = Object.keys(session).find((k) => k.startsWith('oidc.user:'));
  if (!key) throw new Error(`No oidc.user entry in ${SESSION_STORAGE_STATE}.`);
  return key;
};

/**
 * Signs this browser context in as a user holding `roles`, with no backend.
 *
 * <p>The test user can hold only one effective role, and the realm cannot mint a
 * token for the others. The frontend's role rules read only the profile stored in
 * sessionStorage, so a forged one exercises them. That profile's token would be
 * refused by any backend, so API calls are answered from {@link mockGet} and
 * nothing leaves the app's origin: a write is refused here and the realm is never
 * asked to renew. This proves the screens, not the server's own checks.
 */
export const signInAs = async (context: BrowserContext, roles: string[]): Promise<void> => {
  const key = userStorageKey();
  const user = {
    access_token: 'forged-by-e2e',
    token_type: 'Bearer',
    scope: 'openid profile email',
    // Far enough off that the app never tries to renew it.
    expires_at: Math.floor(Date.now() / 1000) + 24 * 60 * 60,
    profile: {
      sub: 'e2e@azureidir',
      idir_username: 'E2EROLE',
      identity_provider: 'azureidir',
      display_name: 'E2E Role Check',
      given_name: 'E2E',
      family_name: 'Role Check',
      email: 'e2e@example.invalid',
      client_roles: roles,
    },
  };

  await context.addInitScript(
    ([k, v]) => {
      // Runs in every frame, and a sandboxed one refuses sessionStorage outright.
      try {
        window.sessionStorage.setItem(k, v);
      } catch {
        /* not the app's frame */
      }
    },
    [key, JSON.stringify(user)] as const,
  );

  const { origin } = new URL(baseURL);
  await context.route('**/*', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.origin !== origin) {
      await route.abort();
    } else if (!url.pathname.startsWith('/api/')) {
      await route.continue();
    } else if (request.method() === 'GET') {
      await route.fulfill({ json: mockGet(url.pathname) });
    } else {
      await route.fulfill({ status: 405, json: { message: 'No writes in the role checks.' } });
    }
  });
};
