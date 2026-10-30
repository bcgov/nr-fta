import { expect, test, type Page } from './fixtures';
import { gotoPage, PAGES, pageHeading, settled, walkTabs } from './helpers/nav';
import { chooseOption, openFirstResult } from './helpers/search';

/**
 * The harvest authorization screens against the real backend, as FTA_ADMIN: each
 * opens and loads, and each dialog opens and closes. None of them saves, see
 * e2e/fixtures.ts. What another role may do is in role-access.spec.ts.
 */

const openAPermit = async (page: Page) => {
  await gotoPage(page, PAGES.harvestingSearch);
  await openFirstResult(page, '#ha-district', () => chooseOption(page, '#ha-cp-status', 'HI'));
  await expect(pageHeading(page, 'Cutting Permit')).toBeVisible({ timeout: 60_000 });
  await settled(page);
};

const openABlock = async (page: Page) => {
  await gotoPage(page, PAGES.cutBlockSearch);
  await openFirstResult(page, '#cb-district');
  await expect(pageHeading(page, /^Cut Block /)).toBeVisible({ timeout: 60_000 });
  await settled(page);
};

test.describe('cutting permit', () => {
  test('opens from search, with every tab loading', async ({ page }) => {
    await openAPermit(page);
    expect(await walkTabs(page, 'Cutting permit sections')).toEqual([
      'Details',
      'Cut blocks',
      'Harvest history',
      'Conditions',
      'Postponements',
    ]);
  });

  test('the edit dialog opens and closes without saving', async ({ page }) => {
    await openAPermit(page);
    const edit = page.getByRole('button', { name: 'Edit permit' });
    test.skip(await edit.isDisabled(), 'This permit is not editable in its status.');
    await edit.click();

    const dialog = page.getByRole('dialog', { name: 'Edit cutting permit' });
    await expect(dialog.getByLabel('Status')).toBeVisible();
    await dialog.getByRole('button', { name: 'Cancel' }).click();
    await expect(dialog).toBeHidden();
  });

  test('Delete asks before it does anything', async ({ page }) => {
    await openAPermit(page);
    // A Carbon danger button's accessible name starts with a visually hidden "danger".
    const remove = page.getByRole('button', { name: /^(danger )?Delete$/ });
    test.skip(await remove.isDisabled(), 'This permit cannot be deleted in its status.');
    await remove.click();

    const dialog = page.getByRole('dialog', { name: 'Delete cutting permit' });
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', { name: 'Cancel' }).click();
    await expect(dialog).toBeHidden();
  });

  for (const [link, heading] of [
    ['Assign marks to blocks', /Assign/i],
    ['Suspend blocks', /Suspend/i],
  ] as const) {
    test(`${link} opens and loads`, async ({ page }) => {
      await openAPermit(page);
      const action = page.getByRole('link', { name: link, exact: true });
      test.skip((await action.count()) === 0, `This permit does not offer ${link}.`);
      await action.click();
      await expect(pageHeading(page, heading)).toBeVisible({ timeout: 60_000 });
      await settled(page);
    });
  }
});

test.describe('cut block', () => {
  test('opens from search, with every tab loading', async ({ page }) => {
    await openABlock(page);
    expect(await walkTabs(page, 'Cut block sections')).toEqual([
      'Details',
      'Amendments',
      'Suspensions',
    ]);
  });

  test('the edit dialog opens and closes without saving', async ({ page }) => {
    await openABlock(page);
    const edit = page.getByRole('button', { name: 'Edit block' });
    test.skip((await edit.count()) === 0, 'This block is not editable in its status.');
    await edit.click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', { name: 'Cancel' }).click();
    await expect(dialog).toBeHidden();
  });

  for (const [link, heading] of [
    ['Re-label', /label/i],
    ['Suspend', /suspen/i],
    ['Edit suspension', /suspen/i],
  ] as const) {
    test(`${link} opens and loads when offered`, async ({ page }) => {
      await openABlock(page);
      // Suspend is a danger button: see the note on Delete above.
      const action = page.getByRole('link', { name: new RegExp(`^(danger )?${link}$`) });
      test.skip((await action.count()) === 0, `This block does not offer ${link}.`);
      await action.click();
      await expect(pageHeading(page, heading)).toBeVisible({ timeout: 60_000 });
      await settled(page);
    });
  }
});

test('a timber mark search opens its private mark or tenure', async ({ page }) => {
  await gotoPage(page, PAGES.timberMarkSearch);
  await openFirstResult(page, '#tm-district');
  await expect(page).toHaveURL(/\/(marks|tenures)\//);
  await settled(page);
});
