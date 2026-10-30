import { expect, test } from './fixtures';
import { gotoPage, PAGES, settled } from './helpers/nav';

/**
 * The admin screens that read data. Their actions (archive, billing, transfer,
 * rates) change shared records, so they are only checked for being offered.
 */

test('the audit report searches', async ({ page }) => {
  await gotoPage(page, PAGES.auditReport);
  await page.getByLabel('Licensee').fill('E2E-NO-SUCH-LICENSEE');
  await page.getByRole('button', { name: 'Run report' }).click();
  await expect(page.getByText('Certificates').first()).toBeVisible({ timeout: 90_000 });
  await settled(page);
});

test('a timber mark transfer cannot be submitted until it is complete', async ({ page }) => {
  await gotoPage(page, PAGES.markTransfer);
  await expect(
    page
      .getByRole('main')
      .getByRole('button', { name: /Transfer|Save/ })
      .first(),
  ).toBeDisabled();
});

test('archive offers nothing to do until tenures are selected', async ({ page }) => {
  await gotoPage(page, PAGES.archiveTenures);
  await settled(page);
  await expect(
    page
      .getByRole('main')
      .getByRole('button', { name: /Archive/ })
      .first(),
  ).toBeDisabled();
});
