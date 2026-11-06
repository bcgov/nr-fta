package ca.bc.gov.nrs.fta.apt.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A partition's apportionment to one form of agreement, a row of
 * {@code APT_PART_APPORTIONMENTS_GET}.
 *
 * @param id                      {@code APT_PART_APPORTIONMENTS.ID}
 * @param partitionId             the partition it belongs to
 * @param foaTypeCode             the form of agreement (FOA) type code
 * @param foaTypeName             that type's description
 * @param allowableAnnualCut      AAC in cubic metres
 * @param allowableAnnualCutHa    AAC in hectares
 * @param comments                free text
 * @param addTimestamp            when the row was added
 * @param addUserid               who added it
 * @param updateTimestamp         always null: the APT function does not return it
 * @param updateUserid            always null: the APT function does not return it
 * @param totalAac                AAC (m³) summed over the apportionments the query matched
 * @param totalAacHa              AAC (ha) summed over the apportionments the query matched
 * @param totalDistributionVolume this apportionment's distribution volumes, summed
 */
public record AptPartitionApportionmentDto(
    Long id,
    Long partitionId,
    String foaTypeCode,
    String foaTypeName,
    BigDecimal allowableAnnualCut,
    BigDecimal allowableAnnualCutHa,
    String comments,
    LocalDateTime addTimestamp,
    String addUserid,
    LocalDateTime updateTimestamp,
    String updateUserid,
    BigDecimal totalAac,
    BigDecimal totalAacHa,
    BigDecimal totalDistributionVolume) {}
