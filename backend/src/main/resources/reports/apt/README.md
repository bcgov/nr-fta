# APT report templates

APT's nine PDF reports are rendered in the backend by `AptReportRenderer`
(`ca.bc.gov.nrs.fta.apt.service`) with the embedded JasperReports library. There is no
external report server.

## Where these came from

These are APT2's own templates, exported from its JasperReports Server (the `JCRS/APT`
folder) and cleaned for in-backend rendering. Only the nine reports APT2 still offers are here,
with their subreports and the three shared pieces (`APT_RPT_HEADER`,
`APT_RPT_HEADER_landscape`, `APT_RPT_COPYRIGHT`). APT2 dropped APTR018, 044, 055, 060 and 065,
so they were not brought over.

The cleaning, and nothing else:

- iReport / Jaspersoft Studio designer properties removed (`ireport.*`,
  `com.jaspersoft.studio.*`), including the old server URLs they carried.
- JasperServer's `LoggedInUser` parameter removed (its class is JasperServer's own). The
  `User` it defaulted is now sent by `AptReportService`, alongside `user`.
- `SUBREPORT_DIR` / `SUBREPORT_EXT` defaults set to `""` / `".jrxml"` in place of the
  JasperServer `repo:` and `I:\…` network-drive paths. The renderer also passes them.
- Every query rewritten from a `{call apt_rpt…}` PL/SQL procedure to plain SQL on the tables
  (see below). Where the change needed it, the `ORACLE_REF_CURSOR` parameter and the
  subreport parameters that passed it on were removed. Nothing else in a template changed.

## How they run

| Report  | Template file                            | Parameters (besides `user` / `User`) |
|---------|------------------------------------------|--------------------------------------|
| APTR011 | `APTR011_TSA_AAC_Apt_Commits.jrxml`      | `in_rpt_date`, `IN_MGMT_UNIT_ID`     |
| APTR012 | `APTR012_RegionAAC_Etc.jrxml`            | `in_report_date`, `IN_ORG_UNIT_NO`   |
| APTR013 | `APTR013_reg_aac_apt_comm_by_tsa.jrxml`  | `in_report_date`, `IN_ORG_UNIT_NO`   |
| APTR017 | `APTR017_AAC_summary_by_tsa.jrxml`       | none                                 |
| APTR021 | `APTR021_TreeFarmLicenceSummary.jrxml`   | `in_report_date`                     |
| APTR032 | `APTR032_prov_summary.jrxml`             | `in_rpt_date`                        |
| APTR033 | `APTR033_prov_summary_by_reg.jrxml`      | `in_report_date`                     |
| APTR041 | `APTR041_master.jrxml`                   | none                                 |
| APTR043 | `APTR043_provincial_linkage_aac.jrxml`   | none                                 |

- Each template's query is plain SQL on `THE` tables, the same result set as the
  `THE.APT_RPTAPTR*` procedure it replaces (named in an XML comment above the query). APT2's
  views (`APT_*_V`) and functions (`APT_INDEP_SIZE`, `APT_RPT_COUNT_LICENCEES`,
  `APT_RPT_CAT_LICENSEE_NAME`, the `APTR` package's totals) are inlined. The SQL therefore
  needs only SELECT on the APT tables, which FTA's role is granted for the APT screens, and on
  FTA tables the backend already reads. It needs no EXECUTE grant.
- Cursor loops the procedures ran first (grand totals, the window of partition / TSA columns
  for the crosstabs) are expressed in SQL with CTEs and analytic functions.
- Two fields the templates declared but the procedures in nr-mof-db never returned were
  removed, rather than guessing at their data: APTR011's `EXTERNAL_COMMENTS` (and its COMMENT
  heading) and the Expiry Date row group of its commitments crosstab.
- Subreports are resolved by name only, to `<name>.jrxml` in this directory.
- The templates are laid out in DejaVu Sans, which comes from the `jasperreports-fonts`
  dependency.
- Parameters arrive as text and are converted to the type the template declares (dates
  as `YYYY-MM-DD`). `AptReportRendererTest` checks that every report compiles, that each
  subreport it names is here, and that each declares the parameters `AptReportDefinition`
  sends.
