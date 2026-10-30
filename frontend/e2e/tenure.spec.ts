import { expect, test } from './fixtures';
import { gotoPage, PAGES, settled, walkTabs } from './helpers/nav';
import { chooseOption, openFirstResult } from './helpers/search';

/** From a search to a real tenure, and through everything a tenure holds. */

const openATenure = async (page: Parameters<typeof gotoPage>[0]) => {
  await gotoPage(page, PAGES.tenureSearch);
  await openFirstResult(page, '#ts-org-unit');
  await expect(page).toHaveURL(/\/tenures\/[^/]+$/);
  await settled(page);
};

test('a tenure opens from search, with every tab loading', async ({ page }) => {
  await openATenure(page);
  const opened = await walkTabs(page, 'Tenure sections');
  // These apply to every file type; TL blocks and Harvest history depend on it.
  expect(opened).toEqual(
    expect.arrayContaining([
      'Details',
      'Cutting permit / mark',
      'Cut block',
      'Associated files',
      'Associated clients',
      'AAC',
      'Sale info',
      'Tenure application',
      'Notes',
      'CP/CB amendments',
    ]),
  );
});

test('the back link returns to the search with its results', async ({ page }) => {
  await openATenure(page);
  await page.getByRole('link', { name: /Tenure Search/ }).click();
  await expect(page).toHaveURL(/\/search\/tenure$/);
  await expect(page.locator('.fsp-search__row--selectable').first()).toBeVisible({
    timeout: 90_000,
  });
});

test('an unknown tenure says so', async ({ page }) => {
  await gotoPage(page, { path: '/tenures/E2E-NO-SUCH-FILE', heading: /.+/ });
  await expect(page.getByRole('tablist', { name: 'Tenure sections' })).toHaveCount(0);
});

test('adding a cutting permit validates before it saves', async ({ page }) => {
  await gotoPage(page, PAGES.tenureSearch);
  // Only a timber tenure, one whose file type starts with A, takes a permit.
  await openFirstResult(page, '#ts-org-unit', () => chooseOption(page, '#ts-tenure-type', 'A'));
  await settled(page);

  await page.getByRole('tab', { name: 'Cutting permit / mark' }).click();
  const add = page.getByRole('button', { name: 'Add cutting permit' }).first();
  await expect(add).toBeVisible({ timeout: 60_000 });
  test.skip(await add.isDisabled(), 'This tenure does not take cutting permits.');
  await add.click();

  const dialog = page.getByRole('dialog', { name: 'Add cutting permit' });
  await dialog.getByRole('button', { name: 'Add cutting permit' }).click();

  // Still open and flagged. A save would have been aborted by the fixture, and failed the test.
  await expect(dialog.locator('[data-invalid="true"]').first()).toBeVisible();

  await dialog.getByRole('button', { name: 'Cancel' }).click();
  await expect(dialog).toBeHidden();
});

test('adding a tenure validates before it saves', async ({ page }) => {
  await gotoPage(page, PAGES.addTenure);
  await settled(page);
  await page.getByRole('button', { name: 'Create tenure' }).click();
  // The browser's own required-field check stops the submit.
  await expect(page.locator('form.add-tenure__form :invalid').first()).toBeAttached();
  await expect(page).toHaveURL(/\/tenures\/add$/);
});
