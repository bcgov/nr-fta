package ca.bc.gov.nrs.fta.billing.dto;

/**
 * One row of FTA680 Tenure Billing Instructions: a range tenure, the billing
 * instruction it would get, and what has been invoiced for it in the year.
 *
 * @param forestFileId      the tenure
 * @param fileTypeCode      its range file type (E01–E03, H01–H03)
 * @param clientName        its main ('A') client, or "NOT FOUND"
 * @param instructionCode   the instruction shown, blank for none
 * @param invoiceNumber     the RBS invoice for the year, "GBS" when billed
 *                          through the General Billing System, or blank
 * @param certifiedDate     when the latest instruction was certified (YYYY-MM-DD HH24:MI)
 * @param certifiedBy       who certified it
 * @param locked            true when the instruction cannot be changed
 *                          (legacy {@code protect_ind = 'Y'})
 */
public record BillingInstructionRowDto(
    String forestFileId,
    String fileTypeCode,
    String clientName,
    String instructionCode,
    String invoiceNumber,
    String certifiedDate,
    String certifiedBy,
    boolean locked) {}
