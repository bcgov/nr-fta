# Apportionment guide

For **Apportionment Administrators** and **Apportionment Viewers**.
Apportionment (APT) records how each management unit's allowable annual
cut (AAC) is determined, partitioned and apportioned to forms of agreement,
and how it is committed to licences. It also records which parent
corporations control which subsidiary corporations, and produces the
apportionment reports.

Apportionment Viewers can look at everything and run every report.
Apportionment Administrators can also create, edit and delete records. In
this guide, a task that changes a record says **Administrators only**.

## Getting started

Open FTA and select **Log in with IDIR**. After you sign in, the
**Welcome** page opens; select **Apportionment** there or use the left menu.
Your name and role are in the profile panel at the top right.

With an Apportionment role you see only the Welcome page and the
**Apportionment** screens. **Search › Management Units** elsewhere in the
menu is a different screen, not part of apportionment.

## Screens

| Where to find it | Use it to |
|---|---|
| **Apportionment › Management Units** | Find a management unit, and from it reach its AAC determinations, partitions, apportionments and commitments. Administrators also create management units here. |
| **Apportionment › Licences** | Find a licence, see its commitments and totals, and (Administrators) keep its FOA type, replacement details and commitments. |
| **Apportionment › Linkages** | See which parent corporations control which subsidiary corporations, and (Administrators) create or delete those relationships. |
| **Apportionment › Reports** | Run the apportionment reports as PDF, and two of them as CSV. |

## How the records fit together

1. A **management unit** (a TSA, TFL or woodlot) has one or more **AAC
   determinations**, each with an effective date.
2. A determination is split into **partitions** (for example
   conventional or deciduous), each with its own AAC.
3. A partition is **apportioned** to forms of agreement (FOA types, such as
   Forest Licences Replaceable).
4. An apportionment is **committed** to licences of that FOA type.
   Commitments can also be managed from the licence.

## Common tasks

### Find a management unit

1. Select **Apportionment › Management Units**.
2. Enter any of **MU type**, **MU no.** (digits only), **Name**, **Org
   unit**, **Source management unit**, **Status**, **Status effective date**
   and **ORCS file #**. All are optional; with none, every unit is listed.
3. Select **Search**.

The results show **Name**, **MU type**, **MU no.**, **Status**, **Effective
date**, **Org unit** and **ORCS file #**. Select a column heading to sort by
it. Select a row to open the unit's history. **Clear all** empties the form
and the results. FTA remembers your last search while the browser tab stays
open.

### Create a management unit

Administrators only.

1. On **Apportionment › Management Units**, select **New management unit**.
2. Choose the **Management unit** from the list. It lists the units that
   exist in FTA's code tables but not yet in apportionment. If every unit is
   already there, the page says so.
3. Choose the **Org unit** and **Status**, and enter the **Status effective
   date**. **Source management unit**, **ORCS file #** (up to 13 characters)
   and **Comments** are optional.
4. Select **Create**.

### Edit a management unit

Administrators only.

1. Open the unit and select **Edit management unit**.
2. Change the **Org unit**, **Source management unit**, **Status**, **Status
   effective date**, **ORCS file #** or **Comments**. The unit's type, number
   and name can't change.
3. Select **Save**, or **Cancel** to go back without saving.

### Review a management unit's AAC determinations

Open a unit from the search. The page shows the unit's details across the
top (**Name**, **MU type**, **MU no.**, **Status**, **Status effective**,
**Org unit**, **Source management unit** and **ORCS file #**). Select the
source management unit to open it.

The **AAC determinations** table lists each determination, newest first,
with its effective date, determination date, AAC in cubic metres and
hectares, apportionment effective date and comments. Select a row to show
that determination's full details in the tile below it: the AAC and
apportionment dates, inventory revision date, long term harvest level, the
total, productive, current net operating and future harvest landbase areas,
comments and who added it. The newest determination is shown until you
select another.

Select **Partitions and apportionments** on that tile to open the
determination's partitions.

### Add or edit an AAC determination

Administrators only.

1. On the unit's page, select **New determination**, or select a
   determination and then **Edit** on its tile.
2. Enter the **Effective date** and **AAC determination date** (both
   required) and any of the other dates, AAC and area figures and comments.
   AAC figures and the long term harvest level are whole numbers from 0 to
   999,999,999. Areas are from 0 to 9,999,999.99, with up to two decimals.
   Comments can be up to 2000 characters.
3. When adding a determination, you can tick **Copy the partitions,
   apportionments and BCTS distributions of the determination effective …**
   to start the new one from the determination currently selected.
4. Select **Save**.

### Work with partitions and apportionments

From a determination, select **Partitions and apportionments**. The page
shows the unit and the determination's dates and AAC, then two sections:

- **Partitions**: each partition's **Type**, **AAC (m³)**, **AAC (ha)**,
  **Apportioned (m³)** and **Comments**, with a **Total** row. A total that
  is more than the determination's AAC is flagged in red. Select a
  partition to show its apportionments.
- **Apportionments — *partition***: each apportionment's **Form of
  agreement / rights held**, **AAC (m³)**, **AAC (ha)**, **Distributed (m³)**
  and **Comments**, with a **Total** row. A total that is more than the
  partition's AAC is flagged in red.

Select **Commitments** on an apportionment to see its commitments. Select
**Back to Management Unit History** to return.

Administrators only:

- **New partition** adds a partition. Choose the **Partition type** and enter
  the **AAC (m³)** (required), **AAC (ha)** and **Comments**, then select
  **Save**.
- **New apportionment** adds an apportionment to the selected partition.
  Choose the **Form of agreement / rights held** and enter the **AAC (m³)**
  (required), **AAC (ha)** and **Comments**, then select **Save**.
- **Edit** on a row opens the same dialog for that partition or
  apportionment. Its **Delete** button removes it after you confirm.

### Review and manage an apportionment's commitments

Select **Commitments** on an apportionment. The page shows the management
unit, the determination, the partition and the apportionment with their
AAC, then the **Commitments** of every licence of that apportionment's FOA
type, with their totals.

Each commitment shows its **Forest File ID**, **Licensee**, **Type**, **AAC
(m³)**, **Incl in AAC**, **Determination Date**, **Effective Date** and
**Expiry Date**. Below the table, **Total AAC (m³)** adds every commitment
and **Total Incl in AAC (m³)** adds only those included in the licence's
AAC.

Open a commitment's row menu (under **Actions**) and select **History** to
see every change to it: the date and time, the change, the user and the
reason. Select an entry to see the commitment as that change left it; the
newest is shown first.

Administrators only:

1. Select **New commitment**, or **Edit** from a commitment's row menu.
2. Choose the **Forest File ID** (one of the unit's licences of this FOA
   type) and the **Type**, enter the **AAC (m³)** (a whole number up to
   999,999,999), choose **Incl in AAC**, and enter the **Determination Date**
   and **Effective Date**. **Expiry Date (optional)** can be left blank.
3. A **Reason** is required when you change a commitment. It is optional for
   a new one.
4. Select **Save**.

To delete a commitment, select **Delete** from its row menu. You can give a
**Reason (optional)**. The commitment and its history are removed.

### Find a licence

1. Select **Apportionment › Licences**.
2. Enter any of **Forest File ID**, **Licensee**, **Org. Unit Name**,
   **Management Unit Name**, **Management Unit Type** and **Management Unit
   Number**. At least one is needed. In **Forest File ID**, use `%` to match
   any characters, for example `A13%`. **Licensee**, **Org. Unit Name** and
   **Management Unit Name** match any part of the name.
3. Select **Search**.

A search that matches more than 10,000 rows is refused; add criteria to
narrow it.

The results show **Forest File ID**, **Licensee**, **Management Unit**,
**Org. Unit**, **File Status** and **File Status Date**. Select a column
heading to sort by it, and select a row to open the licence.

### Review a licence

The licence's page shows a summary across the top (**Forest File ID**,
**Licensee**, **Controlling Co.**, **Org. Unit**, **Management Unit**,
**File Status** and **FOA Type**), then:

- **Licence**: the management unit, file status date, legal effective,
  initial and current expiry dates, tenure term, FOA type, replacement date
  and term, comments, and who added and last updated it.
- **At a glance**: **Total AAC (m³)**, **Included in AAC (m³)** and the
  number of **Commitments**.
- **Commitments**: the licence's commitments, with their **Partition**
  instead of the licensee, and the same totals and row menu as on an
  apportionment.

### Edit a licence

Administrators only.

1. On the licence, select **Edit licence**.
2. Choose the **FOA Type** (required). **Replacement Date (optional)**,
   **Replacement Term (months, optional)** (0 to 999) and **Comments
   (optional)** can be left blank.
3. Select **Save**.

Commitments are added from the licence the same way as from an
apportionment: select **New commitment**, choose the **Partition** (one the
licence's management unit uses), and enter the rest. From a licence, a
**Reason** is required for a new commitment as well as for a change.

### Look up corporate linkages

1. Select **Apportionment › Linkages**. **Related clients** lists each
   parent corporation with its **Client number** and **Corporation** name,
   and a **Parent** tag under **Role**.
2. Select the arrow at the start of a row to show the subsidiary
   corporations it controls, listed under it with a **Subsidiary** tag.
   Select it again to hide them.
3. To narrow the list, type in **Filter by client number or name**. While
   filtering, every matching parent is shown open.
4. Select the **Client number** or **Corporation** heading to sort by it.

### Create or delete a linkage

Administrators only.

1. On **Apportionment › Linkages**, select **Create relationship** in the
   **Related clients** section. The **Create relationship** dialog opens.
2. In **Parent corporation**, type at least three characters of the
   controlling corporation's client number or name, and pick it from the
   list.
3. In **Subsidiary corporation**, pick the corporation it controls the same
   way.
4. Select **Create relationship**, or **Cancel** to close without saving.
   FTA confirms "Relationship created".

Both clients must be picked from the list; typed text that isn't a picked
client is refused.

To delete one, select **Delete relationship** (the red bin icon) under
**Actions** on the subsidiary's row and confirm.

### Run a report

1. Select **Apportionment › Reports**. The **Reports** list shows each
   report's code and name.
2. Select a report's row to open it. One report is open at a time.
3. Enter the inputs it asks for: a **Report date**, a **Management unit**
   (APTR011 lists only TSAs) or a **Region**. A report with no inputs says
   so.
4. Select **Generate PDF**. The report downloads as a PDF.

APTR041 and APTR013 also have **Generate CSV**, which downloads the report's
data as a CSV file. The CSV doesn't use the inputs. The reports are:

| Report | Name | Asks for |
|---|---|---|
| APTR011 | TSA AAC, Apportionment and Commitments | A TSA and a report date |
| APTR012 | Region AAC, Apport and Commit - by Partition | A region and a report date |
| APTR013 | Region AAC, Apportionment and Commitments - by TSA | A region and a report date |
| APTR017 | AAC Summary By TSA | Nothing |
| APTR021 | Tree Farm Licence Summary Report | A report date |
| APTR032 | Provincial Summary Report | A report date |
| APTR033 | Provincial Summary Report by Region | A report date |
| APTR041 | Linkages and Licences | Nothing |
| APTR043 | Licencees Annual Commitment | Nothing |

## What you can do

| Task | Apportionment Viewer | Apportionment Administrator |
|---|---|---|
| Search management units and licences; view determinations, partitions, apportionments, commitments and their history | Yes | Yes |
| View linkages | Yes | Yes |
| Run reports (PDF and CSV) | Yes | Yes |
| Create or edit a management unit | No | Yes |
| Add or edit AAC determinations | No | Yes |
| Add, edit or delete partitions and apportionments | No | Yes |
| Add, edit or delete commitments | No | Yes |
| Edit a licence's FOA type, replacement details and comments | No | Yes |
| Create or delete linkages | No | Yes |

Administrators, Viewers and the harvest authorization roles can also open
the Apportionment screens. Of those, only Administrators can change records.
