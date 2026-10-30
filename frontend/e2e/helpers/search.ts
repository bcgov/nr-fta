import { expect, test, type Locator, type Page } from '@playwright/test';

/** How many options of a select to try before deciding there is no data to open. */
const MAX_OPTIONS_TRIED = 8;

/** A search on a slow Oracle table, over VPN, can take most of a minute. */
const SEARCH_TIMEOUT_MS = 90_000;

/** The result rows, which open their record when clicked. */
export const resultRows = (page: Page): Locator => page.locator('.fsp-search__row--selectable');

/** The search form on the page. The menu has a button called Search too. */
const searchForm = (page: Page): Locator => page.locator('form.fsp-search__form');

/** A select's real options (the first is its "All ..." placeholder), once loaded. */
const optionValues = async (select: Locator): Promise<string[]> => {
  await expect.poll(() => select.locator('option').count(), { timeout: 60_000 }).toBeGreaterThan(1);
  const values = await select
    .locator('option')
    .evaluateAll((options) => options.map((o) => (o as HTMLOptionElement).value));
  return values.filter(Boolean);
};

/** Chooses `preferred` when the select offers it, otherwise its first real option. */
export const chooseOption = async (
  page: Page,
  selector: string,
  preferred?: string,
): Promise<void> => {
  const select = page.locator(`select${selector}`);
  const values = await optionValues(select);
  await select.selectOption(preferred && values.includes(preferred) ? preferred : values[0]);
};

/**
 * Submits the search and waits for it to finish, with rows or with its empty
 * state. A failed search is an error here, not "no results".
 *
 * @return how many rows came back on this page of results
 */
export const submitSearch = async (page: Page): Promise<number> => {
  await searchForm(page).getByRole('button', { name: 'Search', exact: true }).click();

  const rows = resultRows(page).first();
  // Client and Management Unit results have no row to click: they are read in place.
  const anyRow = page.locator('.fsp-search__table tbody tr').first();
  const empty = page.getByText(/^No .+ found$/);
  const failed = page.getByText('Search failed');
  // The table's skeleton is a tbody too, so wait for "Searching" to clear first.
  await expect(page.locator('.fsp-search__results-count--searching')).toHaveCount(0, {
    timeout: SEARCH_TIMEOUT_MS,
  });
  await expect(rows.or(anyRow).or(empty).or(failed).first()).toBeVisible({
    timeout: SEARCH_TIMEOUT_MS,
  });
  await expect(failed, 'the search failed').toHaveCount(0);

  return page.locator('.fsp-search__table tbody tr').count();
};

/**
 * Searches one option of `selector` at a time until one finds something, then
 * opens the first result — a data-agnostic way to reach a real record, as no
 * district is sure to hold any on a given schema. Skips the test when none does:
 * that says the schema has no such data, not that the screen is broken.
 *
 * @param prepare sets the rest of the search's criteria, before each attempt
 */
export const openFirstResult = async (
  page: Page,
  selector: string,
  prepare?: () => Promise<void>,
): Promise<void> => {
  const select = page.locator(`select${selector}`);
  const values = (await optionValues(select)).slice(0, MAX_OPTIONS_TRIED);

  for (const value of values) {
    await select.selectOption(value);
    if (prepare) await prepare();
    if ((await submitSearch(page)) > 0) {
      await expect(resultRows(page).first()).toBeVisible();
      await resultRows(page).first().click();
      return;
    }
  }
  test.skip(true, `No results for the first ${values.length} options of ${selector}.`);
};
