import { expect, test } from '@playwright/test';

import { signInAs } from './helpers/mockSession';
import {
  gotoPage,
  navSection,
  openNavSection,
  PAGES,
  pageHeading,
  type PageDef,
} from './helpers/nav';
import { gotoProtected } from './utils';

/**
 * Reaching pages through the menu, and how the menu itself behaves.
 * Uses a forged FTA_ADMIN session (see signInAs) so menu/router checks do not
 * depend on live Oracle queries or the rotating Keycloak refresh token.
 */
test.use({ storageState: { cookies: [], origins: [] } });

test.beforeEach(async ({ context }) => {
  await signInAs(context, ['FTA_ADMIN']);
});

test('every menu entry opens its page', async ({ page }) => {
  await gotoProtected(page, PAGES.welcome.path);

  for (const def of Object.values(PAGES) as PageDef[]) {
    if (!def.navId || !def.section) continue;
    await openNavSection(page, def.section);
    await page.getByTestId(`side-nav-link-${def.navId}`).click();
    await expect(page).toHaveURL(new RegExp(`${def.path}$`));
    await expect(pageHeading(page, def.heading)).toBeVisible();
  }
});

test('the section holding the current page opens on load, the others stay closed', async ({
  page,
}) => {
  await gotoPage(page, PAGES.markList);
  await expect(navSection(page, 'Private Marks')).toHaveAttribute('aria-expanded', 'true');
  await expect(navSection(page, 'Search')).toHaveAttribute('aria-expanded', 'false');
});

test('New Application opens its dialog over the current page', async ({ page }) => {
  await gotoPage(page, PAGES.tenureSearch);
  await openNavSection(page, 'Private Marks');
  await page.getByTestId('side-nav-link-marks-application').click();

  const dialog = page.getByRole('dialog', { name: 'New Mark Application' });
  await expect(dialog).toBeVisible();
  await expect(page).toHaveURL(new RegExp(`${PAGES.tenureSearch.path}$`));

  await dialog.getByRole('button', { name: 'Close' }).first().click();
  await expect(dialog).toBeHidden();
});

test('a deep link to the new application opens the dialog over the list', async ({ page }) => {
  await gotoProtected(page, '/marks/application');
  await expect(page.getByRole('dialog', { name: 'New Mark Application' })).toBeVisible();
});
