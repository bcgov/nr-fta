package ca.bc.gov.nrs.fta.apt.dto;

import java.time.LocalDate;

/**
 * The inputs an APT PDF report may take, as {@code GET /api/fta/apt/reports/{code}} receives
 * them. Which ones a report needs, and the report parameter each becomes, is
 * {@link ca.bc.gov.nrs.fta.apt.service.AptReportDefinition}'s business.
 *
 * @param reportDate       the "as of" date ({@code in_rpt_date} / {@code in_report_date})
 * @param managementUnitId {@code APT_MANAGEMENT_UNITS.ID} ({@code IN_MGMT_UNIT_ID}), positive
 * @param orgUnitNo        a region's {@code ORG_UNIT_NO} ({@code IN_ORG_UNIT_NO}), positive
 */
public record AptReportParams(
    LocalDate reportDate,
    Long managementUnitId,
    Integer orgUnitNo) {}
