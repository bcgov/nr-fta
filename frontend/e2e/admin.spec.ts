import { expect, test } from './fixtures';
import { gotoPage, PAGES, settled } from './helpers/nav';

/**
 * The admin screens that read data. Their actions (archive, billing, transfer,
 * rates) change shared records, so they are only checked for being offered.
 */

test('the audit report cannot be submitted until a report, region and district are chosen', async ({
  page,
}) => {
  await gotoPage(page, PAGES.auditReport);
  await settled(page);
  const submit = page.getByRole('main').getByRole('button', { name: 'Submit' });
  await expect(submit).toBeDisabled();
  await page.locator('#audit-report').selectOption('FTA403');
  await page.locator('#audit-region').selectOption('*');
  await page.locator('#audit-district').selectOption('*');
  await expect(submit).toBeEnabled();
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

test('archive cannot count or archive until a region and year are chosen', async ({ page }) => {
  await gotoPage(page, PAGES.archiveTenures);
  await settled(page);
  const main = page.getByRole('main');
  await expect(main.getByRole('button', { name: 'Go' })).toBeDisabled();
  // Nothing is offered for archiving before a count has run.
  await expect(main.getByRole('button', { name: /^Archive/ })).toHaveCount(0);
});

test('billing instructions cannot be searched or prepared until a district and year are chosen', async ({
  page,
}) => {
  await gotoPage(page, PAGES.billingTenure);
  await settled(page);
  const main = page.getByRole('main');
  await expect(main.getByRole('button', { name: 'Go' })).toBeDisabled();
  await expect(main.getByRole('button', { name: 'Prepare' })).toHaveCount(0);
});

test('a billing run cannot be submitted until a district and year are chosen', async ({ page }) => {
  await gotoPage(page, PAGES.billingApproval);
  await settled(page);
  await expect(page.getByRole('main').getByRole('button', { name: 'Submit' })).toBeDisabled();
});

test('the invoice preview reports an unknown tenure', async ({ page }) => {
  await gotoPage(page, PAGES.billingInvoice);
  await page.getByLabel('Tenure Number').fill('E2ENOSUCH');
  await page.getByRole('main').getByRole('button', { name: 'Go' }).click();
  await expect(page.getByText('Invalid Tenure Number.')).toBeVisible({ timeout: 15_000 });
});

test('the billing reports cannot be submitted until their criteria are chosen', async ({
  page,
}) => {
  for (const target of [PAGES.billingPre, PAGES.billingPost]) {
    await gotoPage(page, target);
    await settled(page);
    await expect(page.getByRole('main').getByRole('button', { name: 'Submit' })).toBeDisabled();
  }
});
