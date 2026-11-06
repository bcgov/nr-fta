package ca.bc.gov.nrs.fta.apt.dto;

import java.math.BigDecimal;

/**
 * A partition apportionment to create or update ({@code APT_PART_APRTMT_INS_UPD}). The id comes
 * from the path, the audit user from the token.
 *
 * @param partitionId          the partition; required on create, ignored on update (an
 *                             apportionment stays with its partition)
 * @param foaTypeCode          the form of agreement type code; required
 * @param allowableAnnualCut   AAC in cubic metres; required, whole, 0 to 999,999,999
 * @param allowableAnnualCutHa AAC in hectares; whole, 0 to 999,999,999
 * @param comments             up to 2000 characters
 */
public record AptPartitionApportionmentRequest(
    Long partitionId,
    String foaTypeCode,
    BigDecimal allowableAnnualCut,
    BigDecimal allowableAnnualCutHa,
    String comments) {}
