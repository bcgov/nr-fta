import path from 'node:path';

import { expect, test, type Page } from './fixtures';
import { gotoPage, PAGES, pageHeading, settled } from './helpers/nav';
import { chooseOption, openFirstResult, resultRows, submitSearch } from './helpers/search';

/**
 * Screenshots for the user guides (docs/manual/images/). Not part of the test
 * suite: skipped unless MANUAL_SCREENSHOTS=1. Read-only: it opens screens, runs
 * searches and opens dialogs, never saves (e2e/fixtures.ts aborts any write).
 *
 *   cd frontend && E2E_BASE_URL=http://localhost:3000 npm run manual:screenshots
 *
 * The signed-in user's name, user id, initials and email, and every user name a
 * screen displays, are replaced with a sample identity. Result rows show the
 * environment's data: review the images before publishing a guide (the
 * update-manual skill's sweep).
 *
 * Then build the PDFs: `npm run manual:pdf`.
 */

test.skip(!process.env.MANUAL_SCREENSHOTS, 'Set MANUAL_SCREENSHOTS=1 to capture guide screenshots');
test.use({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1.5 });

const OUT = path.join(import.meta.dirname, '..', '..', 'docs', 'manual', 'images');

/** Swap every real person's details on screen for a sample identity. */
const anonymize = (page: Page) =>
  page.evaluate(() => {
    const set = (el: Element | null | undefined, text: string) => {
      if (el) el.textContent = text;
    };
    // The header's avatar and the profile panel.
    document.querySelectorAll('[data-testid="avatar-initials"]').forEach((el) => set(el, 'JS'));
    const data = document.querySelector('.user-data');
    if (data) {
      const name = data.querySelector('.user-name');
      const role = (name?.textContent ?? '').match(/\(([^)]*)\)\s*$/)?.[1];
      set(name, role ? `Jordan Sample (${role})` : 'Jordan Sample');
      data.querySelectorAll('p').forEach((p) => {
        const t = p.textContent ?? '';
        if (/^IDIR:/i.test(t)) set(p, 'IDIR: JSAMPLE');
        else if (t.startsWith('Email:')) set(p, 'Email: jordan.sample@gov.bc.ca');
      });
    }
    // Every user id a screen resolves to a name (audit fields, note authors, journals).
    document
      .querySelectorAll('.user-name__text')
      .forEach((el) => set(el, 'Jordan Sample (JSAMPLE)'));
    // Leaf text: the Welcome greeting, and any raw IDIR\USER id shown as is.
    document.querySelectorAll('main *').forEach((el) => {
      if (el.childElementCount !== 0) return;
      const t = el.textContent ?? '';
      if (/^Welcome, /.test(t)) set(el, 'Welcome, Jordan.');
      else if (/IDIR\\\w+/i.test(t)) set(el, t.replace(/IDIR\\\w+/gi, 'IDIR\\JSAMPLE'));
    });
  });

/** Screenshot from the top of the page, at most `maxHeight` CSS pixels tall. */
const snap = async (page: Page, name: string, maxHeight = 900) => {
  await settled(page);
  await page.evaluate(() => window.scrollTo(0, 0));
  // Let toasts, skeletons, name lookups and transitions settle.
  await page.waitForTimeout(1_000);
  await anonymize(page);
  const height = await page.evaluate(() => document.documentElement.scrollHeight);
  await page.screenshot({
    path: path.join(OUT, `${name}.png`),
    fullPage: true,
    clip: { x: 0, y: 0, width: 1440, height: Math.min(height, maxHeight) },
    animations: 'disabled',
  });
};

/** One viewport framed on the search results. */
const snapResults = async (page: Page, name: string) => {
  await settled(page);
  await page
    .locator('.fsp-search__table')
    .first()
    .evaluate((el) => window.scrollTo(0, el.getBoundingClientRect().top + window.scrollY - 260));
  await page.waitForTimeout(1_000);
  await anonymize(page);
  await page.screenshot({ path: path.join(OUT, `${name}.png`), animations: 'disabled' });
};

/** A dialog, open over its page. */
const snapDialog = async (page: Page, name: string, dialog: string) => {
  const d = page.getByRole('dialog', { name: dialog });
  await expect(d).toBeVisible({ timeout: 15_000 });
  await expect(d.getByText(/^Loading/))
    .toHaveCount(0, { timeout: 10_000 })
    .catch(() => undefined);
  await page.waitForTimeout(800);
  await anonymize(page);
  await page.screenshot({ path: path.join(OUT, `${name}.png`), animations: 'disabled' });
};

test.describe('Timber Marks guide', () => {
  test('timber mark search', async ({ page }) => {
    await gotoPage(page, PAGES.timberMarkSearch);
    await chooseOption(page, '#tm-district');
    await submitSearch(page);
    await snapResults(page, 'tm-timber-mark-search');
  });

  test('private marks', async ({ page }) => {
    await gotoPage(page, PAGES.markList);
    await chooseOption(page, '#mk-district');
    await submitSearch(page);
    await snapResults(page, 'tm-application-list');

    // An issued mark: it has a timber mark, land index and clients to show.
    await openFirstResult(page, '#mk-district', () => chooseOption(page, '#mk-status', 'HI'));
    await expect(page).toHaveURL(/\/marks\/[^/]+/);
    await snap(page, 'tm-mark-detail', 1300);

    for (const [tab, name] of [
      ['Associated clients', 'tm-associated-clients'],
      ['Amendments', 'tm-amendments'],
    ] as const) {
      await page.getByRole('tab', { name: tab }).click();
      await snap(page, name, 1100);
    }
  });

  test('new mark application', async ({ page }) => {
    await gotoPage(page, PAGES.markList);
    await page
      .getByRole('button', { name: /new (mark )?application/i })
      .first()
      .click();
    await snapDialog(page, 'tm-new-application', 'New Mark Application');
  });
});

test.describe('Harvest Authorities guide', () => {
  test('harvesting authorities and a cutting permit', async ({ page }) => {
    await gotoPage(page, PAGES.harvestingSearch);
    await chooseOption(page, '#ha-district');
    await chooseOption(page, '#ha-cp-status', 'HI');
    await submitSearch(page);
    await snapResults(page, 'ha-harvesting-authority-search');

    await openFirstResult(page, '#ha-district', () => chooseOption(page, '#ha-cp-status', 'HI'));
    await expect(pageHeading(page, 'Cutting Permit')).toBeVisible({ timeout: 15_000 });
    await snap(page, 'ha-cutting-permit', 1300);

    // The Edit cutting permit dialog (opened, never saved), when this permit allows it.
    const edit = page.getByRole('button', { name: 'Edit permit' });
    if (await edit.isEnabled()) {
      await edit.click();
      await snapDialog(page, 'ha-edit-cutting-permit', 'Edit cutting permit');
      await page
        .getByRole('dialog', { name: 'Edit cutting permit' })
        .getByRole('button', { name: 'Cancel' })
        .click();
    } else {
      test.info().annotations.push({ type: 'not captured', description: 'ha-edit-cutting-permit' });
    }

    // The Suspend Blocks page (opened, never saved).
    const suspend = page.getByRole('link', { name: 'Suspend blocks' });
    if (await suspend.isVisible()) {
      await suspend.click();
      await expect(page).toHaveURL(/\/suspend-blocks/);
      await snap(page, 'ha-suspend-blocks', 1100);
      await page.goBack();
      await expect(pageHeading(page, 'Cutting Permit')).toBeVisible({ timeout: 15_000 });
    } else {
      test.info().annotations.push({ type: 'not captured', description: 'ha-suspend-blocks' });
    }

    // The permit's tenure, and its Add cutting permit dialog (opened, never saved).
    await page.locator('main a[href^="/tenures/"]').first().click();
    await expect(page).toHaveURL(/\/tenures\/[^/]+/);
    await page.getByRole('tab', { name: 'Cutting permit / mark' }).click();
    await page.getByRole('button', { name: 'Add cutting permit' }).first().click();
    await snapDialog(page, 'ha-add-cutting-permit', 'Add cutting permit');
  });

  test('postponements', async ({ page }) => {
    // A permit under postponement (HP) has postponements to show.
    await gotoPage(page, PAGES.harvestingSearch);
    await openFirstResult(page, '#ha-district', () => chooseOption(page, '#ha-cp-status', 'HP'));
    await expect(pageHeading(page, 'Cutting Permit')).toBeVisible({ timeout: 15_000 });
    await page.getByRole('tab', { name: 'Postponements' }).click();
    await snap(page, 'ha-postponements', 1000);
  });

  test('cut blocks', async ({ page }) => {
    await gotoPage(page, PAGES.cutBlockSearch);
    await chooseOption(page, '#cb-district');
    await submitSearch(page);
    await snapResults(page, 'ha-cut-block-search');

    await openFirstResult(page, '#cb-district');
    await expect(pageHeading(page, /^Cut Block /)).toBeVisible({ timeout: 15_000 });
    await snap(page, 'ha-cut-block', 1300);
  });
});

test.describe('Apportionment guide', () => {
  test('management units down to commitments', async ({ page }) => {
    await page.goto('/apt/management-units');
    await submitSearch(page);
    await snapResults(page, 'apt-management-unit-search');

    // The first unit with an AAC determination, so its history, partitions and
    // commitments have something to show.
    const rows = resultRows(page);
    const count = Math.min(await rows.count(), 10);
    let found = false;
    for (let i = 0; i < count && !found; i++) {
      await rows.nth(i).click();
      await expect(page).toHaveURL(/\/history$/);
      await settled(page);
      found = (await page.getByText('No AAC determinations').count()) === 0;
      if (!found) await page.goBack();
    }
    test.skip(!found, 'No management unit with an AAC determination in the first results');
    await snap(page, 'apt-management-unit-history', 1300);

    await page.getByRole('link', { name: 'Partitions and apportionments' }).click();
    await expect(page.getByRole('heading', { name: 'Partitions' })).toBeVisible({
      timeout: 15_000,
    });
    await snap(page, 'apt-partitions', 1300);

    const commitments = page.getByRole('link', { name: 'Commitments', exact: true }).first();
    if (await commitments.isVisible()) {
      await commitments.click();
      await expect(page).toHaveURL(/\/commitments$/);
      await snap(page, 'apt-commitments', 1300);
    } else {
      test.info().annotations.push({ type: 'not captured', description: 'apt-commitments' });
    }
  });

  test('licences', async ({ page }) => {
    await page.goto('/apt/licences');
    await page.locator('#apt-lic-ffid').fill('A1%');
    await submitSearch(page);
    await snapResults(page, 'apt-licence-search');

    await resultRows(page).first().click();
    await expect(page.getByRole('heading', { name: 'Commitments' })).toBeVisible({
      timeout: 15_000,
    });
    await snap(page, 'apt-licence', 1400);
  });

  test('linkages and reports', async ({ page }) => {
    await page.goto('/apt/linkages');
    await expect(page.getByRole('heading', { name: 'Related clients' })).toBeVisible({
      timeout: 15_000,
    });
    await snap(page, 'apt-linkages', 1100);

    await page.goto('/apt/reports');
    await expect(page.getByRole('heading', { name: 'Reports', exact: true })).toBeVisible({
      timeout: 15_000,
    });
    await snap(page, 'apt-reports', 1100);
  });
});
