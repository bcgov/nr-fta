# Analysis: multiple PIDs on a private mark application

**Status:** analysis, for decision · **Date:** 2026-10-05

**Question:** we want to allow entry of multiple PIDs for a private mark application. What will it take?

## Short answer

Today a private mark holds **one** PID, in a single 23-character column. Allowing several means one of three things:

1. packing them into that column (2 at most);
2. widening the column;
3. adding a child table.

The recommended option is the child table: about **7–10 dev-days** in this app, plus a schema change in `nr-mof-db` and sign-off from the owners of the reports and the PMT replication that read the current column.

## Where the PID lives today

- **Column:** the "LTO PID" field is stored in `THE.PRIVATE_MARK_CERTIFICATE.BCAA_FOLIO_NUMBER`, a `VARCHAR2(23 BYTE)`. The column comment says it holds either a BC Assessment folio number or an LTO PID (`nnn-nnn-nnn`).
- **Required:** legacy FTA510 requires it, and so does the new app (create and edit, max 23 characters).
- **Editable after issue:** it can be changed later, as one of the location fields.
- **Unrelated namesake:** `HARVESTING_AUTHORITY` and `TIMBER_MARK` have a column with the same name, used for Crown permits. The private mark's value is not copied there; the certificate sync trigger `FTA_SYNC_PMC_TM` doesn't touch it.

## What else reads the column

| Consumer | Impact of a change |
|---|---|
| `FTARPT_P12_PRIVATE_MARK_VW`, `FTARPT_P23_PRIV_MARK_RGISTR_VW` (report views) | They show one value per mark. Extra PIDs appear only if the views are changed. |
| `PROXY_FTA_PMT_REPLICATION` (has read grants on about 90 THE tables, apparently replicating them elsewhere) | Widening the column or adding a table means the replicated copy must change too. Its owner needs to know. |
| `FTA_510_PRIVATE_MARK` package (legacy FTA510) | Passes the value through. Harmless once legacy is retired. |
| E-submission (`FTA_XML_PROCESS`) | The PID lines are commented out. No impact. |
| Certificate PDF, Timber Mark Search | Neither uses the PID today. |

## Touchpoints in the new app

- **Backend:**
  - `MarkApplicationRequest`, `MarkUpdateRequest`;
  - `MarkFieldChecks`;
  - `MarkApplicationWriteService`, `MarkUpdateService`;
  - `MarkDetailService` / `MarkDetailDto`.
- **Frontend:**
  - `NewMarkApplicationModal`;
  - `MarkDetail/markEditForm.ts`, `MarkDetail/MarkApplicationPanel.tsx`;
  - `services/mark_detail.ts`, `services/mark_write.ts`.

## Options

### A. Several PIDs in the existing column (no schema change)

- **Capacity:** 23 characters holds at most 2 hyphenated PIDs; `123-456-789,234-567-890` is exactly 23.
- **Effort:** about 1–2 days, for a multi-value input, validation, and splitting and joining the text.
- **Reports:** they show the comma-joined text.
- **Not recommended:** a hard cap of 2, and a delimited list in one column.

### B. Widen the column and store a delimited list

- **Change:** a one-line `ALTER` in `nr-mof-db` (e.g. `VARCHAR2(500)`), plus the app work from A.
- **Effort:** about 2–3 days.
- **Checks needed:** the replication target, and any report with a fixed-width layout.
- **Still a delimited list:** PIDs can't be searched, validated one by one, or given their own details.

### C. New child table `PRIVATE_MARK_PARCEL` (recommended)

- **Shape:** one row per PID, keyed by certificate, with the usual audit columns. This mirrors how `MARK_LAND_INDEX` already models a mark's multiple land references.
- **Compatibility:** keep `BCAA_FOLIO_NUMBER` filled with the first PID. The report views, replication and legacy keep working unchanged, and the extra PIDs are additive.

| Work | Estimate |
|---|---|
| Schema: table, sequence, foreign key with delete cascade (or update `FTA_DELETE_CERTIFICATE` / `FTA_DELETE_FF_CP_CB`), grants, ideally the report views | 1–2 days, plus DBA review |
| Backend: list / add / remove endpoints (or a list in the create and update requests), validation, keeping the first PID in the old column, tests | 3–4 days |
| Frontend: multi-PID entry on the new application form and the edit form, display on the detail page | 2–3 days |
| Migrate existing values into the table | About 1 day; depends on the current data (see below) |

## Decisions needed before building

1. **Format:** PIDs only (`nnn-nnn-nnn`, strictly checked), or BC Assessment folio numbers too, as the column comment allows?
2. **Required:** at least one PID, as today? Is there a maximum?
3. **After issue:** can PIDs still be added or removed? Is that an amendment, recorded in the mark's amendment history?
4. **Duplicates:** should the same PID on another active mark warn or block?
5. **Downstream:** do the report owners and the PMT replication owner need the full list, or is the first PID enough?
6. **Legacy coexistence:** if legacy FTA runs alongside the new app for a while, it will see only the first PID.

## Not yet verified

- **Existing data:** whether users already squeeze several PIDs into the column. Run:

  ```sql
  SELECT bcaa_folio_number
    FROM the.private_mark_certificate
   WHERE bcaa_folio_number LIKE '%,%'
      OR bcaa_folio_number LIKE '%;%'
      OR bcaa_folio_number LIKE '% %';
  ```

- **Replication target:** what system `PROXY_FTA_PMT_REPLICATION` feeds. Its owner needs to confirm either schema change.
