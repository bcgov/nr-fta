import { expect, test } from './fixtures';
import { gotoPage, PAGES, settled, walkTabs } from './helpers/nav';
import { chooseOption, openFirstResult } from './helpers/search';

/** Range tenures and range units, read only. */

test('a range tenure opens from search, with every tab loading', async ({ page }) => {
  await gotoPage(page, PAGES.rangeTenureSearch);
  await openFirstResult(page, '#rt-org-unit', () => chooseOption(page, '#rt-status'));
  await expect(page).toHaveURL(/\/range\/[^/]+$/);
  await settled(page);
  // Legacy FtaRangeTabs, in its order and with its labels.
  expect(await walkTabs(page, 'Range tenure sections')).toEqual([
    'Tenure',
    'Assoc. Files',
    'Assoc. Client',
    'Tenure Appl.',
    'Notes',
    'Range Usage',
    'Assoc. Land Base',
  ]);

  // The usage history is Range Usage's View History window, as in legacy FTA613R.
  await page.getByRole('tab', { name: 'Range Usage' }).click();
  await page.getByRole('button', { name: 'View history' }).click();
  await expect(page.getByRole('dialog', { name: 'Usage History' })).toBeVisible();
});

test('a range unit opens from search and loads', async ({ page }) => {
  await gotoPage(page, PAGES.rangeUnitSearch);
  await openFirstResult(page, '#ru-org-unit');
  await expect(page).toHaveURL(/\/range-unit\/[^/]+$/);
  await settled(page);
});
