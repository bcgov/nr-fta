package ca.bc.gov.nrs.fta.billing.dto;

/**
 * FTA680's search header. Org unit and year are required; the rest narrow it.
 *
 * @param orgUnitNo    the administering district (ORG_UNIT_NO)
 * @param calendarYear the billing year, four digits
 * @param forestFileId one tenure only
 * @param fileTypeCode one range file type only
 * @param clientName   the start of the main client's name
 */
public record BillingInstructionsCriteria(
    Long orgUnitNo,
    String calendarYear,
    String forestFileId,
    String fileTypeCode,
    String clientName) {}
