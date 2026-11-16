# Forest Tenure Administration — user guides

These guides are for the people who use FTA day to day: ministry staff who
sign in with IDIR. Each covers one area of the application for one role:
what you see on screen and how to get a job done.

| Guide | For | Covers | PDF |
|---|---|---|---|
| [Timber Marks for Headquarters Administrators](timber-marks-headquarters.md) | Timber Mark Headquarters Administrator, Administrator | Timber mark search; reviewing and issuing private marks, amendments, cancelling and reinstating, land index, clients and notes; timber mark transfer | `FTA-Timber-Marks-Headquarters-Guide.pdf` |
| [Timber Marks for District Administrators](timber-marks-district.md) | Timber Mark District Administrator | Timber mark search; entering private mark applications and submitting them to Headquarters, printing certificates, requesting amendments | `FTA-Timber-Marks-District-Guide.pdf` |
| [Harvest Authorities for Senior Administrators](harvest-authorities-senior.md) | Harvest Authorization Senior Administrator, Administrator | Everything in the district guide, plus issued permits' terms, conditions, postponements, suspensions, deleting and re-labelling | `FTA-Harvest-Authorities-Senior-Guide.pdf` |
| [Harvest Authorities for District Administrators](harvest-authorities-district.md) | Harvest Authorization District Administrator | Harvesting authority and cut block search, adding and editing cutting permits and cut blocks, retiring permits, assigning marks | `FTA-Harvest-Authorities-District-Guide.pdf` |
| [Apportionment](apportionment.md) | Apportionment Administrator, Apportionment Viewer | Management units and their AAC determinations, partitions, apportionments and commitments; licences; corporate linkages; apportionment reports | `FTA-Apportionment-Guide.pdf` |

## How to read these guides

- **Menu paths** are written as **Area › Menu item**, for example
  **Search › Timber Marks**. The areas are the headings in the left-hand
  menu.
- **You only see what your role allows.** If a menu item or button in a
  guide is missing for you, your role doesn't grant it. Each guide ends with
  a **What you can do** section.

## Keeping these guides current

<!-- Developer note (not in the PDFs). -->

The guides live in the repository next to the code and are updated in the
same change as the feature they describe. Conventions, the source → guide
mapping and the secrets and personal-data sweep are in the `update-manual`
skill (`.claude/skills/update-manual/SKILL.md`). A Claude Code Stop hook
(`.claude/hooks/check-manual.sh`) sends Claude back once when guided screens
change without their guide.

- Screenshots: `cd frontend && E2E_BASE_URL=http://localhost:3000 npm run manual:screenshots`
  (needs `npm run e2e:login` first). Written to `images/`.
- PDFs: `cd frontend && npm run manual:pdf`.
