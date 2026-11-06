# APT report templates

APT's nine PDF reports are rendered in the backend by `AptReportRenderer`
(`ca.bc.gov.nrs.fta.apt.service`) with the embedded JasperReports library. There is no
external report server.

Drop each JRXML template here as `<template>.jrxml`. The names are APT2's, listed in
`AptReportDefinition`:

| Report  | Template file                            | Parameters (besides `user`)        |
|---------|------------------------------------------|------------------------------------|
| APTR011 | `APTR011_TSA_AAC_Apt_Commits.jrxml`      | `in_rpt_date`, `IN_MGMT_UNIT_ID`   |
| APTR012 | `APTR012_RegionAAC_Etc.jrxml`            | `in_report_date`, `IN_ORG_UNIT_NO` |
| APTR013 | `APTR013_reg_aac_apt_comm_by_tsa.jrxml`  | `in_report_date`, `IN_ORG_UNIT_NO` |
| APTR017 | `APTR017_AAC_summary_by_tsa.jrxml`       | none                               |
| APTR021 | `APTR021_TreeFarmLicenceSummary.jrxml`   | `in_report_date`                   |
| APTR032 | `APTR032_prov_summary.jrxml`             | `in_rpt_date`                      |
| APTR033 | `APTR033_prov_summary_by_reg.jrxml`      | `in_report_date`                   |
| APTR041 | `APTR041_master.jrxml`                   | none                               |
| APTR043 | `APTR043_provincial_linkage_aac.jrxml`   | none                               |

Until a template is here, its report answers 501. Each template is filled against the
backend's database connection and runs its own query. Parameters arrive as text and are
converted to the type the template declares (dates as `YYYY-MM-DD`). If a template's
parameter names differ from the table, change `AptReportDefinition` to match the template.
