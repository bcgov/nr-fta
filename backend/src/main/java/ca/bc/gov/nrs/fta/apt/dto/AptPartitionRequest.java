package ca.bc.gov.nrs.fta.apt.dto;

import java.math.BigDecimal;

/**
 * A partition to create or update ({@code APT_PARTITION_INS_UPD}). The id comes from the path,
 * the audit user from the token.
 *
 * @param muhId                the management unit history; required on create, ignored on update
 *                             (a partition stays with its determination)
 * @param partitionCode        the partition type code; required
 * @param allowableAnnualCut   AAC in cubic metres; required, whole, 0 to 999,999,999
 * @param allowableAnnualCutHa AAC in hectares; whole, 0 to 999,999,999
 * @param comments             up to 2000 characters
 */
public record AptPartitionRequest(
    Long muhId,
    String partitionCode,
    BigDecimal allowableAnnualCut,
    BigDecimal allowableAnnualCutHa,
    String comments) {}
