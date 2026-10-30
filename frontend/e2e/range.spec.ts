import { expect, test } from './fixtures';
import { gotoPage, PAGES, settled, walkTabs } from './helpers/nav';
import { chooseOption, openFirstResult } from './helpers/search';

/** Range tenures and range units, read only. */

test('a range tenure opens from search, with every tab loading', async ({ page }) => {
  await gotoPage(page, PAGES.rangeTenureSearch);
  await openFirstResult(page, '#rt-org-unit', () => chooseOption(page, '#rt-status'));
  await expect(page).toHaveURL(/\/range\/[^/]+$/);
  await settled(page);
  expect(await walkTabs(page, 'Range tenure sections')).toEqual([
    'Range Usage',
    'Rotations',
    'Land Base',
    'Usage History',
  ]);
});

test('a range unit opens from search and loads', async ({ page }) => {
  await gotoPage(page, PAGES.rangeUnitSearch);
  await openFirstResult(page, '#ru-org-unit');
  await expect(page).toHaveURL(/\/range-unit\/[^/]+$/);
  await settled(page);
});
