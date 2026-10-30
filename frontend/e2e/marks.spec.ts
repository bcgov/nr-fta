import { expect, test } from './fixtures';
import { gotoPage, PAGES, settled, walkTabs } from './helpers/nav';
import { openFirstResult } from './helpers/search';

/** Private mark applications, read only. */

test('a private mark opens from the list, with every tab loading', async ({ page }) => {
  await gotoPage(page, PAGES.markList);
  await openFirstResult(page, '#mk-district');
  await expect(page).toHaveURL(/\/marks\/[^/]+/);
  await settled(page);
  const opened = await walkTabs(page, 'Private mark sections');
  expect(opened[0]).toBe('Mark application');
  expect(opened.length).toBe(5);
});

test('the new application dialog opens and closes without saving', async ({ page }) => {
  await gotoPage(page, PAGES.markList);
  await page.locator('.fta-page-title').getByRole('button', { name: /New/ }).click();
  const dialog = page.getByRole('dialog', { name: 'New Mark Application' });
  await expect(dialog).toBeVisible();
  await dialog.getByRole('button', { name: 'Cancel' }).click();
  await expect(dialog).toBeHidden();
});
