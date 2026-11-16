---
name: update-manual
description: Update the FTA end-user guides in docs/manual/ (Timber Marks for Headquarters and District Administrators, Harvest Authorities for Senior and District Administrators, Apportionment) after a change to user-visible behaviour in those areas (screens, menu items, fields, buttons, dialogs, tabs, workflows, statuses, validation, roles, reports). Use whenever frontend/src or backend/src/main changed in a way a user of a guided area would notice, or when the check-manual Stop hook asks for it. Ends with a secrets and personal-data sweep of the changed text, screenshots and PDFs.
---

# Updating the FTA user guides

The guides in `docs/manual/` are for **end users**: ministry IDIR staff who
administer forest tenures. Not developers. Keep them in step with the code in
the same change.

There is one guide per area **and role**, each a single Markdown file built
into its own PDF. Each guide is written for its reader alone ("you"), so a
change in an area usually touches both of that area's guides:

| Guide | Reader | File | PDF |
|---|---|---|---|
| Timber Marks, Headquarters | Timber Mark Headquarters Administrator, Administrator | `docs/manual/timber-marks-headquarters.md` | `docs/manual/FTA-Timber-Marks-Headquarters-Guide.pdf` |
| Timber Marks, District | Timber Mark District Administrator | `docs/manual/timber-marks-district.md` | `docs/manual/FTA-Timber-Marks-District-Guide.pdf` |
| Harvest Authorities, Senior | Harvest Authorization Senior Administrator, Administrator | `docs/manual/harvest-authorities-senior.md` | `docs/manual/FTA-Harvest-Authorities-Senior-Guide.pdf` |
| Harvest Authorities, District | Harvest Authorization District Administrator | `docs/manual/harvest-authorities-district.md` | `docs/manual/FTA-Harvest-Authorities-District-Guide.pdf` |
| Apportionment | Apportionment Administrator and Apportionment Viewer (one guide; Administrator-only tasks are marked) | `docs/manual/apportionment.md` | `docs/manual/FTA-Apportionment-Guide.pdf` |

Put a task in a guide only if its reader can do it; a task the other role
does is mentioned in one line ("Headquarters does these") at most. Viewers
have no guide of their own.

Other areas (tenures, range, administration) have no guide
yet. A change there needs no manual update until its guide exists.

## 1. Decide whether a guide needs to change

List what changed: `git diff --name-only HEAD` plus untracked files.

**Update the guide** when a change in its area alters anything a user sees or
does:
- a screen, tab or dialog added, removed, renamed or moved in the menu
- search criteria, columns, form fields, required fields, defaults or
  validation messages
- a button or action added, removed or relabelled, or when it is available
  (role, record status)
- a workflow or status change (e.g. what Submit to HQ or Print does)
- a role or what a role may do
- a report or PDF added, removed or renamed

**Don't update a guide** for refactors, tests, performance work, logging,
build/CI/deploy changes, SQL changes with the same results, other areas, or
developer docs. In that case say in one sentence why no guided screen
changed, and stop.

## 2. Find the guide and section to edit

| Source | Guide |
|---|---|
| `frontend/src/pages/marks/**`, `pages/search/TimberMarkSearch/**`, `pages/admin/MarkTransfer/**`, `backend/.../mark/**` | Both Timber Marks guides (Transfer: Headquarters only) |
| `frontend/src/pages/harvesting/**`, `pages/search/HarvestingAuthoritySearch/**`, `pages/search/CutBlockSearch/**`, `pages/tenure/TenureDetail/{CuttingPermitsPanel,CutBlocksPanel,CpCbAmendmentsPanel}.tsx`, `backend/.../tenure/**` files about cutting permits, cut blocks, harvesting authorities, postponements, conditions, suspensions, marks assignment or harvest history | Both Harvest Authorities guides |
| `frontend/src/pages/apt/**`, `services/apt/**`, `routes/aptPaths.ts`, `backend/.../apt/**` (reports included) | Apportionment |
| `frontend/src/routes/access.ts`, `routes/routePaths.ts` (menu), `context/auth/types.ts`, `components/Layout/HeaderPanelProfile/**` (role labels), `backend/.../security/ApiAuthorizationCustomizer.java`, `RoleConstants.java` | The "What you can do" section of each guide, and any task whose role changed |

`backend/...` = `backend/src/main/java/ca/bc/gov/nrs/fta/`.

The screens are the source of truth for what users see: page titles,
labels, column headers, button text, dialog headings, tab names, validation
and notification messages. Read the component rather than guessing. Role
display names are `ROLE_LABEL` in
`frontend/src/components/Layout/HeaderPanelProfile/index.tsx`; what each role
may open and do is `frontend/src/routes/access.ts` (pages, `canEdit*`
helpers) and the backend `rules` objects the screens read (e.g. a permit's
`canSave`, `canRetire`).

## 3. Style

- Write for the user. Use the on-screen labels exactly, in **bold**:
  **Search**, **Edit permit**, **Search › Timber Marks**.
- Use role display names ("Timber Mark Headquarters Administrator"), never
  `FTA_TIMBER_MARK_HEADQUARTERS_ADMIN`.
- Never mention legacy screen numbers (FTA510, FTA902), SQL, procedures,
  class names, API paths or file paths.
- Each guide: a short intro (what the area is for, who uses it) →
  **Screens** table (where to find it → what it's for, plus a "Who" column
  where access differs) → **Common tasks** as numbered steps → **Statuses**
  where the area has them → **What you can do**.
- Document only menu screens in the Screens table. Mention screens reached
  from results (detail pages, tabs, dialogs) inside the task that uses them.
- Short sentences, active voice, present tense. Canadian spelling.
- Describe the app as it is in this change. Nothing planned or unbuilt, and
  no legacy behaviour the app doesn't have.

## 4. Screenshots and the PDFs

Screenshots live in `docs/manual/images/` (`tm-*.png` for Timber Marks,
`ha-*.png` for Harvest Authorities, `apt-*.png` for Apportionment; both guides of an area share them) and are referenced as
`![Caption](images/name.png)`. The caption is also the figure caption in the
PDF. They are produced by `frontend/e2e/manual.screenshots.spec.ts`
(read-only; it replaces the signed-in user's name and every displayed user
name with "Jordan Sample").

- If a change alters a screen that has a screenshot, or adds a screen worth
  showing, add or update its entry in that spec. Don't hand-edit images.
- Recapturing needs a signed-in e2e session and the app running locally, so
  ask the user before running:
  `cd frontend && E2E_BASE_URL=http://localhost:3000 npm run manual:screenshots`
  (after `npm run e2e:login` if the session has expired). If you can't
  recapture, say which images are now out of date.
- A screen the e2e account has no role for is skipped, never saved. Never
  commit a Forbidden page image or one showing a real person's name.
- Rebuild the PDFs after any guide change: `cd frontend && npm run manual:pdf`.

## 5. Secrets and personal-data sweep

The guides may be shared outside the team, so run this sweep on every guide
change before you report done: every changed guide, image and rebuilt PDF.
Fix anything found; never just mention it.

1. **Text.** Grep the changed guide files (and the screenshot spec or PDF
   script if touched) for credential values and identities:
   `password|secret|token|api[_-]?key|bearer|BEGIN .*PRIVATE|eyJ[A-Za-z0-9_-]{10,}|AKIA|ghp_|sk-|jdbc:|@gov\.bc\.ca|IDIR\\[A-Z]|E2E_[0-9]`.
   Matches that only name a concept, or the sample identity
   `jordan.sample@gov.bc.ca` / `JSAMPLE`, are fine; any real value is not.
   Never print a credential value you find, only the file and line.
2. **Screenshots: look at every new or changed image** (Read the PNG). Reject
   and recapture, or fix the spec, if it shows any of:
   - a real person's name, IDIR user id, email or initials (only
     "Jordan Sample" / `JSAMPLE` / "JS" are allowed), including the header,
     profile panel, "Welcome," line, and audit fields such as "Entered by",
     "Updated by", "Added by" or a note's author;
   - the e2e account name (`E2E_…`) or a Forbidden page;
   - a token, cookie, URL with query secrets, devtools, an error stack trace
     or an internal hostname other than the app itself.
   Client names and numbers, timber marks and file ids from TEST are
   acceptable but call them out to the user.
3. **Image metadata.** PNGs must carry no `tEXt`/`iTXt`/`zTXt`/`eXIf`
   chunks (Playwright writes none).
4. **PDF.** After `npm run manual:pdf`, check each PDF's decompressed
   streams hold no `/Users/`, `file://`, local usernames or `E2E_` strings,
   and that its Info has only Title/Creator/Producer.
5. Confirm `frontend/e2e/.auth/` is still gitignored
   (`git check-ignore frontend/e2e/.auth/user.json`).

Report the sweep result in your summary (clean, or what you fixed).

## 6. Finish

- Edit only the guide sections affected. Keep tables in step: a removed
  screen or button leaves no row behind.
- A new guide: add its file, its row in the table at the top of this skill
  and in `docs/manual/README.md`, its source paths in section 2 and in
  `.claude/hooks/check-manual.sh`, and its entry in
  `frontend/scripts/build-manual-pdf.mjs`.
- In your summary to the user, list which guide sections you changed.
