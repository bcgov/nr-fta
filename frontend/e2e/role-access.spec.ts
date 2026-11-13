import { expect, test, type Page } from '@playwright/test';

import { BLOCK_ID, FILE_ID, PERMIT_ID } from './helpers/mockApi';
import { signInAs } from './helpers/mockSession';
import { navSection, openNavSection, pageHeading, sideNav } from './helpers/nav';
import { gotoProtected, gotoWithRetry, installStaticAssetCache } from './utils';

/**
 * What each role may see and open, for all seven roles, from one test account: see
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
const APT = 'FTA_APT_ADMIN';
const VIEWER = 'FTA_VIEWER';

const ALL_ROLES = [ADMIN, HARVEST_SENIOR, HARVEST_DISTRICT, MARK_HQ, MARK_DISTRICT, APT, VIEWER];
const MARK_ROLES = [MARK_HQ, MARK_DISTRICT];
const WIDE_READ_ROLES = [HARVEST_SENIOR, HARVEST_DISTRICT, APT, VIEWER];
/** Everyone but FTA_ADMIN and FTA_HARVEST_AUTH_SENIOR_ADMIN (access.canManageHarvest). */
const NOT_HARVEST_MANAGERS = [HARVEST_DISTRICT, ...MARK_ROLES, APT, VIEWER];

const PERMIT_PATH = `/harvesting-authority/${PERMIT_ID}?forestFileId=${FILE_ID}`;
const BLOCK_PATH = `/cut-block/${BLOCK_ID}?forestFileId=${FILE_ID}&cuttingPermitId=${PERMIT_ID}`;

// A Carbon danger button's accessible name starts with a visually hidden "danger".
const dangerAware = (name: string) => new RegExp(`^(danger )?${name}$`);

const forbidden = (page: Page) => page.getByTestId('forbidden-title');

// Starts signed out: the session here is forged per test, not the saved one.
test.use({ storageState: { cookies: [], origins: [] } });

test.describe('without a role', () => {
  test('signed out, any page offers only the sign-in', async ({ page, context }) => {
    await installStaticAssetCache(context);
    await gotoWithRetry(page, '/search/tenure');
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
      await gotoWithRetry(page, '/search/tenure');
      await expect(page.getByTestId('unauthorized-title')).toBeVisible();
      await expect(sideNav(page)).toHaveCount(0);
    });
  }
});

test.describe('the menu', () => {
  const SECTIONS: Record<string, string[]> = {
    [ADMIN]: ['Search', 'Tenures', 'Private Marks', 'Apportionment', 'Administration'],
    [HARVEST_SENIOR]: ['Search', 'Tenures', 'Private Marks', 'Apportionment'],
    [HARVEST_DISTRICT]: ['Search', 'Tenures', 'Private Marks', 'Apportionment'],
    [APT]: ['Search', 'Tenures', 'Private Marks', 'Apportionment'],
    [VIEWER]: ['Search', 'Tenures', 'Private Marks', 'Apportionment'],
    [MARK_HQ]: ['Search', 'Private Marks'],
    [MARK_DISTRICT]: ['Search', 'Private Marks'],
  };

  const MARK_CREATORS = [ADMIN, MARK_HQ, MARK_DISTRICT];

  for (const role of ALL_ROLES) {
    test(`${role} sees its sections and allowed menu entries`, async ({ page, context }) => {
      await signInAs(context, [role]);
      await gotoProtected(page, '/welcome');
      await expect(sideNav(page).getByRole('button')).toHaveText(SECTIONS[role]);

      if (MARK_ROLES.includes(role)) {
        await openNavSection(page, 'Search');
        await expect(sideNav(page).getByRole('link')).toHaveText(['Tenures', 'Timber Marks']);
      }

      await openNavSection(page, 'Private Marks');
      await expect(page.getByTestId('side-nav-link-marks-application')).toHaveCount(
        MARK_CREATORS.includes(role) ? 1 : 0,
      );

      if (SECTIONS[role].includes('Tenures')) {
        await openNavSection(page, 'Tenures');
        await expect(page.getByTestId('side-nav-link-tenure-add')).toHaveCount(
          role === ADMIN ? 1 : 0,
        );
      }
    });
  }

  test('roles do not stack: the highest one decides', async ({ page, context }) => {
    await signInAs(context, [VIEWER, MARK_HQ]);
    await gotoProtected(page, '/welcome');
    await expect(navSection(page, 'Tenures')).toHaveCount(0);
  });

  test('an APT administrator outranked by a timber mark role keeps Apportionment', async ({
    page,
    context,
  }) => {
    await signInAs(context, [MARK_HQ, APT]);
    await gotoProtected(page, '/welcome');
    await expect(sideNav(page).getByRole('button')).toHaveText([
      'Search',
      'Private Marks',
      'Apportionment',
    ]);
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
    // Pages that only make a change are closed to a role that may not make it.
    { path: '/tenures/add', deniedTo: ALL_ROLES.filter((r) => r !== ADMIN) },
    {
      path: `/harvesting-authority/${PERMIT_ID}/assign-marks`,
      deniedTo: [...MARK_ROLES, APT, VIEWER],
    },
    {
      path: `/harvesting-authority/${PERMIT_ID}/suspend-blocks`,
      deniedTo: NOT_HARVEST_MANAGERS,
    },
    { path: `/cut-block/${BLOCK_ID}`, deniedTo: MARK_ROLES },
    { path: `/cut-block/${BLOCK_ID}/relabel`, deniedTo: NOT_HARVEST_MANAGERS },
    { path: `/cut-block/${BLOCK_ID}/suspension`, deniedTo: NOT_HARVEST_MANAGERS },
    { path: '/marks/application', deniedTo: [HARVEST_SENIOR, HARVEST_DISTRICT, APT, VIEWER] },
    { path: '/search/tenure', deniedTo: [] },
    { path: '/search/timber-mark', deniedTo: [] },
    { path: '/marks', deniedTo: [] },
    { path: `/tenures/${FILE_ID}`, deniedTo: [] },
    { path: PERMIT_PATH.split('?')[0], deniedTo: [] },
    { path: '/apt/management-units', deniedTo: MARK_ROLES },
    { path: '/apt/licences', deniedTo: MARK_ROLES },
    { path: '/apt/linkages', deniedTo: MARK_ROLES },
    { path: '/apt/reports', deniedTo: MARK_ROLES },
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

  for (const role of [...EDITORS, APT, VIEWER]) {
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

test.describe('apportionment actions', () => {
  // FTA_ADMIN and FTA_APT_ADMIN write APT, checked against every role held: a timber mark
  // role outranking FTA_APT_ADMIN does not take the writes away.
  for (const [name, roles, edits] of [
    [ADMIN, [ADMIN], true],
    [APT, [APT], true],
    [`${MARK_HQ} + ${APT}`, [MARK_HQ, APT], true],
    [HARVEST_SENIOR, [HARVEST_SENIOR], false],
    [VIEWER, [VIEWER], false],
  ] as const) {
    test(`${name} ${edits ? 'can' : 'cannot'} create management units and relationships`, async ({
      page,
      context,
    }) => {
      await signInAs(context, [...roles]);
      await gotoProtected(page, '/apt/management-units');
      await expect(pageHeading(page, 'Management Units')).toBeVisible();
      await expect(page.getByRole('button', { name: 'New management unit' })).toHaveCount(
        edits ? 1 : 0,
      );

      await gotoProtected(page, '/apt/linkages');
      await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible();
      await expect(page.getByRole('button', { name: 'Create relationship' })).toHaveCount(
        edits ? 1 : 0,
      );
    });
  }
});
