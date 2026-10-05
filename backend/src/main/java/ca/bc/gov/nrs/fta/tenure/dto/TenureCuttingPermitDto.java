package ca.bc.gov.nrs.fta.tenure.dto;

import java.time.LocalDate;

/**
 * One cutting permit (harvesting authority) of a tenure — a row of the legacy FTA901 Cutting
 * Permit List ({@code THE.FTA_901_CUT_PERM_LST.rec_cutting_permit}).
 *
 * @param orgUnitCode        the permit's district (ORG_UNIT_CODE)
 * @param cuttingPermitId    the CP, or for a Fort St. John authority (harvest type F) its
 *                           harvesting authority id
 * @param timberMark         the primary mark; for an FSJ authority with several, "…" is
 *                           appended, as legacy did
 * @param statusCode         HARVEST_AUTH_STATUS_CODE
 * @param statusDesc         "<code> - <description>"
 * @param issueDate          ISSUE_DATE
 * @param expiryDate         EXPIRY_DATE
 * @param extendDate         EXTEND_DATE
 * @param salvageTypeCode    SALVAGE_TYPE_CODE
 * @param hvaSkey            HARVESTING_AUTHORITY key
 * @param fsj                whether a Fort St. John authority (harvest type F)
 */
public record TenureCuttingPermitDto(
    String orgUnitCode,
    String cuttingPermitId,
    String timberMark,
    String statusCode,
    String statusDesc,
    LocalDate issueDate,
    LocalDate expiryDate,
    LocalDate extendDate,
    String salvageTypeCode,
    Long hvaSkey,
    boolean fsj) {}
