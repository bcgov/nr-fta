import { expect, test, type Page } from '@playwright/test';

import { BLOCK_ID, FILE_ID, PERMIT_ID } from './helpers/mockApi';
import { signInAs } from './helpers/mockSession';
import { navSection, openNavSection, pageHeading, sideNav } from './helpers/nav';
import { gotoProtected } from './utils';

/**
 * What each role may see and open, for all six roles, from one test account: see
 * signInAs for how, and for why this covers the screens and not the server.
 *
 * The expectations are written out here rather than read from routes/access.ts, so
 * a change to who may do what has to be made twice, on purpose.
 */

const ADMIN = 'FTA_ADMIN';
const HARVEST_SENIOR = 'FTA_HARVEST_AUTH_SENIOR_ADMIN';
const HARVEST_DISTRICT = 'FTA_HARVEST_AUTH_DISTRICT_ADMIN';
const MARK_HQ = 'FTA_TIMBER_MARK_HEADQUARTERS_ADMIN';
const MARK_DISTRICT = 'FTA_TIMBER_MARK_DISTRICT_ADMIN';
const VIEWER = 'FTA_VIEWER';

const ALL_ROLES = [ADMIN, HARVEST_SENIOR, HARVEST_DISTRICT, MARK_HQ, MARK_DISTRICT, VIEWER];
const MARK_ROLES = [MARK_HQ, MARK_DISTRICT];
const WIDE_READ_ROLES = [HARVEST_SENIOR, HARVEST_DISTRICT, VIEWER];

const PERMIT_PATH = `/harvesting-authority/${PERMIT_ID}?forestFileId=${FILE_ID}`;
const BLOCK_PATH = `/cut-block/${BLOCK_ID}?forestFileId=${FILE_ID}&cuttingPermitId=${PERMIT_ID}`;

// A Carbon danger button's accessible name starts with a visually hidden "danger".
const dangerAware = (name: string) => new RegExp(`^(danger )?${name}$`);

const forbidden = (page: Page) => page.getByTestId('forbidden-title');

// Starts signed out: the session here is forged per test, not the saved one.
test.use({ storageState: { cookies: [], origins: [] } });

test.describe('without a role', () => {
  test('signed out, any page offers only the sign-in', async ({ page }) => {
    await page.goto('/search/tenure');
    await expect(page.getByTestId('landing-button__idir')).toBeVisible();
    await expect(sideNav(page)).toHaveCount(0);
  });

  for (const [name, roles] of [
    ['no role', []],
    ['only an unknown role', ['SOME_OTHER_APP_ADMIN']],
    ['only a FAM expiry marker', [`FAM:EXPIRES:2030-01-01:${ADMIN}`]],
  ] as const) {
    test(`signed in with ${name}, access is not granted`, async ({ page, context }) => {
      await signInAs(context, [...roles]);
      await page.goto('/search/tenure');
      await expect(page.getByTestId('unauthorized-title')).toBeVisible();
      await expect(sideNav(page)).toHaveCount(0);
    });
  }
});

test.describe('the menu', () => {
  const SECTIONS: Record<string, string[]> = {
    [ADMIN]: ['Search', 'Tenures', 'Private Marks', 'Administration'],
    [HARVEST_SENIOR]: ['Search', 'Tenures', 'Private Marks'],
    [HARVEST_DISTRICT]: ['Search', 'Tenures', 'Private Marks'],
    [VIEWER]: ['Search', 'Tenures', 'Private Marks'],
    [MARK_HQ]: ['Search', 'Private Marks'],
    [MARK_DISTRICT]: ['Search', 'Private Marks'],
  };

  for (const role of ALL_ROLES) {
    test(`${role} sees its sections`, async ({ page, context }) => {
      await signInAs(context, [role]);
      await gotoProtected(page, '/welcome');
      await expect(sideNav(page).getByRole('button')).toHaveText(SECTIONS[role]);
    });
  }

  for (const role of MARK_ROLES) {
    test(`${role} sees only its two searches`, async ({ page, context }) => {
      await signInAs(context, [role]);
      await gotoProtected(page, '/welcome');
      await openNavSection(page, 'Search');
      await expect(sideNav(page).getByRole('link')).toHaveText(['Tenures', 'Timber Marks']);
    });
  }

  for (const [role, offered] of [
    [ADMIN, true],
    [MARK_HQ, true],
    [MARK_DISTRICT, true],
    [HARVEST_SENIOR, false],
    [HARVEST_DISTRICT, false],
    [VIEWER, false],
  ] as const) {
    test(`${role} ${offered ? 'is' : 'is not'} offered a new mark application`, async ({
      page,
      context,
    }) => {
      await signInAs(context, [role]);
      await gotoProtected(page, '/welcome');
      await openNavSection(page, 'Private Marks');
      await expect(page.getByTestId('side-nav-link-marks-application')).toHaveCount(
        offered ? 1 : 0,
      );
    });
  }

  test('roles do not stack: the highest one decides', async ({ page, context }) => {
    await signInAs(context, [VIEWER, MARK_HQ]);
    await gotoProtected(page, '/welcome');
    await expect(navSection(page, 'Tenures')).toHaveCount(0);
  });

  test('a role scoped to a district still counts as that role', async ({ page, context }) => {
    await signInAs(context, [`${ADMIN}_DISTRICT-DCC`]);
    await gotoProtected(page, '/welcome');
    await expect(navSection(page, 'Administration')).toBeVisible();
  });
});

test.describe('the route guard', () => {
  const GUARDED: { path: string; heading?: RegExp; deniedTo: string[] }[] = [
    { path: '/admin/audit', deniedTo: [...WIDE_READ_ROLES, ...MARK_ROLES] },
    { path: '/admin/rates-fees', deniedTo: [...WIDE_READ_ROLES, ...MARK_ROLES] },
    { path: '/admin/billing/approval', deniedTo: [...WIDE_READ_ROLES, ...MARK_ROLES] },
    { path: '/search/harvesting-authority', deniedTo: MARK_ROLES },
    { path: '/search/cut-block', deniedTo: MARK_ROLES },
    { path: '/search/range-tenure', deniedTo: MARK_ROLES },
    { path: '/tenures', deniedTo: MARK_ROLES },
    { path: '/tenures/add', deniedTo: MARK_ROLES },
    { path: `/harvesting-authority/${PERMIT_ID}/assign-marks`, deniedTo: MARK_ROLES },
    { path: `/harvesting-authority/${PERMIT_ID}/suspend-blocks`, deniedTo: MARK_ROLES },
    { path: `/cut-block/${BLOCK_ID}`, deniedTo: MARK_ROLES },
    { path: `/cut-block/${BLOCK_ID}/relabel`, deniedTo: MARK_ROLES },
    { path: `/cut-block/${BLOCK_ID}/suspension`, deniedTo: MARK_ROLES },
    { path: '/search/tenure', deniedTo: [] },
    { path: '/search/timber-mark', deniedTo: [] },
    { path: '/marks', deniedTo: [] },
    { path: `/tenures/${FILE_ID}`, deniedTo: [] },
    { path: PERMIT_PATH.split('?')[0], deniedTo: [] },
  ];

  for (const role of ALL_ROLES) {
    test(`${role}`, async ({ page, context }) => {
      await signInAs(context, [role]);
      for (const { path, deniedTo } of GUARDED) {
        await gotoProtected(page, path);
        // Each page renders its own h1, the forbidden page its own, so this settles both ways.
        await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible();
        await expect(forbidden(page), path).toHaveCount(deniedTo.includes(role) ? 1 : 0);
      }
    });
  }
});

test.describe('harvest authorization actions', () => {
  const EDITORS = [ADMIN, HARVEST_SENIOR, HARVEST_DISTRICT];

  for (const role of [...EDITORS, VIEWER]) {
    const edits = EDITORS.includes(role);

    test(`${role} ${edits ? 'can' : 'cannot'} act on a cutting permit`, async ({
      page,
      context,
    }) => {
      await signInAs(context, [role]);
      await gotoProtected(page, PERMIT_PATH);
      await expect(pageHeading(page, 'Cutting Permit')).toBeVisible();
      await expect(page.getByRole('tablist', { name: 'Cutting permit sections' })).toBeVisible();

      for (const name of [
        'Edit permit',
        'Assign marks to blocks',
        'Suspend blocks',
        'Retire',
        'Delete',
      ]) {
        const action = page
          .locator('.fta-page-title')
          .getByRole(name.includes('blocks') ? 'link' : 'button', { name: dangerAware(name) });
        await expect(action, name).toHaveCount(edits ? 1 : 0);
      }
    });

    test(`${role} ${edits ? 'can' : 'cannot'} add a cutting permit to a tenure`, async ({
      page,
      context,
    }) => {
      await signInAs(context, [role]);
      await gotoProtected(page, `/tenures/${FILE_ID}`);
      await page.getByRole('tab', { name: 'Cutting permit / mark' }).click();
      const add = page.getByRole('button', { name: 'Add cutting permit' }).first();
      await expect(add).toBeVisible();
      if (edits) await expect(add).toBeEnabled();
      else await expect(add).toBeDisabled();
    });
  }

  // Relabel is the one block action the frontend also checks by role: the backend
  // computes the rest of the block's rules for the caller.
  for (const [role, relabels] of [
    [ADMIN, true],
    [HARVEST_SENIOR, true],
    [HARVEST_DISTRICT, false],
    [VIEWER, false],
  ] as const) {
    test(`${role} ${relabels ? 'can' : 'cannot'} re-label a cut block`, async ({
      page,
      context,
    }) => {
      await signInAs(context, [role]);
      await gotoProtected(page, BLOCK_PATH);
      await expect(pageHeading(page, `Cut Block ${BLOCK_ID}`)).toBeVisible();
      await expect(page.getByRole('link', { name: 'Edit suspension' })).toBeVisible();
      await expect(page.getByRole('link', { name: 'Re-label' })).toHaveCount(relabels ? 1 : 0);
    });
  }

  for (const role of MARK_ROLES) {
    test(`${role} reads a permit but cannot act on it or open its restricted tabs`, async ({
      page,
      context,
    }) => {
      await signInAs(context, [role]);
      await gotoProtected(page, PERMIT_PATH);
      await expect(pageHeading(page, 'Cutting Permit')).toBeVisible();
      await expect(page.getByRole('tab', { name: 'Details' })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Edit permit' })).toHaveCount(0);
      await expect(page.getByRole('tab', { name: 'Conditions' })).toBeDisabled();
      await expect(page.getByRole('tab', { name: 'Postponements' })).toBeDisabled();
    });
  }
});
