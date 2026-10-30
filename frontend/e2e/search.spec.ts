import { expect, test, type Page } from './fixtures';
import { gotoPage, PAGES, type PageDef } from './helpers/nav';
import { chooseOption, submitSearch } from './helpers/search';

/**
 * Each search runs against the real schema. Whether it finds anything depends on
 * the data, so a run passes on rows or on the empty state; it fails on a rejected
 * or broken query, which is what these exist to catch.
 */
const SEARCHES: { name: string; def: PageDef; criteria: (page: Page) => Promise<void> }[] = [
  {
    name: 'tenures',
    def: PAGES.tenureSearch,
    criteria: (page) => chooseOption(page, '#ts-org-unit'),
  },
  {
    name: 'harvesting authorities',
    def: PAGES.harvestingSearch,
    // District plus one more: anything but a key search needs two criteria.
    criteria: async (page) => {
      await chooseOption(page, '#ha-district');
      await chooseOption(page, '#ha-cp-status', 'HI');
    },
  },
  {
    name: 'timber marks',
    def: PAGES.timberMarkSearch,
    criteria: (page) => chooseOption(page, '#tm-district'),
  },
  {
    name: 'cut blocks',
    def: PAGES.cutBlockSearch,
    criteria: (page) => chooseOption(page, '#cb-district'),
  },
  {
    name: 'range tenures',
    def: PAGES.rangeTenureSearch,
    criteria: async (page) => {
      await chooseOption(page, '#rt-org-unit');
      await chooseOption(page, '#rt-status');
    },
  },
  {
    name: 'range units',
    def: PAGES.rangeUnitSearch,
    criteria: (page) => chooseOption(page, '#ru-org-unit'),
  },
  {
    name: 'clients',
    def: PAGES.clientSearch,
    criteria: (page) => page.locator('#cl-last-name').fill('SMITH'),
  },
  {
    name: 'management units',
    def: PAGES.managementUnitSearch,
    criteria: (page) => page.locator('#mu-description').fill('Timber'),
  },
  {
    name: 'private marks',
    def: PAGES.markList,
    criteria: (page) => chooseOption(page, '#mk-district'),
  },
];

for (const { name, def, criteria } of SEARCHES) {
  test(`${name} search returns rows or its empty state`, async ({ page }) => {
    await gotoPage(page, def);
    await criteria(page);
    await submitSearch(page);
  });
}

test.describe('the searches refuse what legacy refused', () => {
  test('a harvesting authority search needs two criteria', async ({ page }) => {
    await gotoPage(page, PAGES.harvestingSearch);
    await chooseOption(page, '#ha-district');
    await page
      .locator('form.fsp-search__form')
      .getByRole('button', { name: 'Search', exact: true })
      .click();
    await expect(page.getByText('Please enter at least 2 search criteria.')).toBeVisible();
  });

  test('a client search needs a criterion', async ({ page }) => {
    await gotoPage(page, PAGES.clientSearch);
    await page
      .locator('form.fsp-search__form')
      .getByRole('button', { name: 'Search', exact: true })
      .click();
    await expect(page.getByText('Enter at least one search criterion.')).toBeVisible();
  });

  test('a range unit search needs an organization unit', async ({ page }) => {
    await gotoPage(page, PAGES.rangeUnitSearch);
    await page
      .locator('form.fsp-search__form')
      .getByRole('button', { name: 'Search', exact: true })
      .click();
    await expect(page.getByText('Administration organization unit is required.')).toBeVisible();
  });
});

test('Clear all empties the criteria and the results', async ({ page }) => {
  await gotoPage(page, PAGES.clientSearch);
  const lastName = page.locator('#cl-last-name');
  await lastName.fill('SMITH');
  await submitSearch(page);

  await page.getByRole('button', { name: 'Clear all' }).click();

  await expect(lastName).toHaveValue('');
  await expect(page.locator('.fsp-search__results')).toHaveCount(0);
});
