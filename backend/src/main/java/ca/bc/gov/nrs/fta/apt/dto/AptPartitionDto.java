package ca.bc.gov.nrs.fta.apt.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A partition of a management unit's AAC determination, a row of {@code APT_PARTITIONS_GET}.
 *
 * @param id                      {@code APT_PARTITIONS.ID}
 * @param muhId                   the management unit history (determination) it belongs to
 * @param partitionCode           the partition type code
 * @param partitionDescription    the partition type's description
 * @param allowableAnnualCut      AAC in cubic metres
 * @param allowableAnnualCutHa    AAC in hectares
 * @param comments                free text
 * @param addTimestamp            when the row was added
 * @param addUserid               who added it
 * @param updateTimestamp         when it was last updated
 * @param updateUserid            who last updated it
 * @param totalAac                AAC (m³) summed over the partitions the query matched
 * @param totalAacHa              AAC (ha) summed over the partitions the query matched
 * @param totalApportionmentAac   AAC (m³) summed over this partition's apportionments
 * @param totalApportionmentAacHa AAC (ha) summed over this partition's apportionments
 */
public record AptPartitionDto(
    Long id,
    Long muhId,
    String partitionCode,
    String partitionDescription,
    BigDecimal allowableAnnualCut,
    BigDecimal allowableAnnualCutHa,
    String comments,
    LocalDateTime addTimestamp,
    String addUserid,
    LocalDateTime updateTimestamp,
    String updateUserid,
    BigDecimal totalAac,
    BigDecimal totalAacHa,
    BigDecimal totalApportionmentAac,
    BigDecimal totalApportionmentAacHa) {}
