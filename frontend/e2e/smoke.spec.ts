import { expect, test } from './fixtures';
import { gotoPage, navSection, PAGES, pageHeading, settled } from './helpers/nav';
import { gotoProtected } from './utils';

/**
 * Every page in the menu renders for the real test user against the real backend,
 * its code lists and first queries included. The fixture fails a test on any 5xx,
 * 401, 403 or uncaught exception, which is most of what this is here to catch.
 */

test('root responds with 200', async ({ page }) => {
  const response = await page.goto('/');
  expect(response?.status(), 'root should return 200').toBe(200);
});

// First, because every other failure in a run reads differently once this one is known.
test('the test user holds FTA_ADMIN', async ({ page }) => {
  await gotoProtected(page, PAGES.welcome.path);
  await expect(
    navSection(page, 'Administration'),
    'no Administration menu: grant FTA_ADMIN to the test user in CSS, then log in again',
  ).toBeVisible();
});

for (const [name, def] of Object.entries(PAGES)) {
  test(`${name} renders`, async ({ page }) => {
    await gotoPage(page, def);
    await settled(page);
  });
}

test('an unknown route shows the not-found page', async ({ page }) => {
  await gotoProtected(page, '/this-route-does-not-exist');
  await expect(pageHeading(page, 'Page not found')).toBeVisible();
});
