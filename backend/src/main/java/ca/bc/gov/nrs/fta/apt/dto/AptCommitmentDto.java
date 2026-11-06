package ca.bc.gov.nrs.fta.apt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One commitment (APT_COMMITMENTS row): AAC committed to a licence within a partition. Read from
 * {@code APT_COMMITMENT_GET}, {@code APT_COMMITMENTS_GET_BY_MU} and
 * {@code APT_COMMITMENTS_GET_BY_FFID}. Field names are APT2's.
 *
 * @param id                   commitment id
 * @param forestFileId         the licence it is committed to
 * @param clientName           the licensee
 * @param partitionCode        partition type code ({@code APT_PARTITION_ST})
 * @param partitionDescription partition type description
 * @param type                 commitment type code ({@code COMMIT_TYPE_ST})
 * @param allowableAnnualCut   committed AAC (m³)
 * @param includeInLicAac      {@code Y} when it counts towards the licence's AAC
 * @param determinationDate    when it was determined
 * @param effectiveDate        when it takes effect
 * @param expiryDate           when it ends, if it does
 * @param paptId               the partition apportionment it belongs to, if any
 * @param totalAac             the result set's total AAC; null from {@code APT_COMMITMENT_GET}
 * @param totalIncludedAac     the result set's total AAC counted towards the licence; null from
 *                             {@code APT_COMMITMENT_GET}
 */
public record AptCommitmentDto(
    Long id,
    String forestFileId,
    String clientName,
    String partitionCode,
    String partitionDescription,
    String type,
    BigDecimal allowableAnnualCut,
    String includeInLicAac,
    LocalDate determinationDate,
    LocalDate effectiveDate,
    LocalDate expiryDate,
    Long paptId,
    BigDecimal totalAac,
    BigDecimal totalIncludedAac) {}
