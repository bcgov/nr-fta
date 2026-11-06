package ca.bc.gov.nrs.fta.apt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One journal entry of a commitment ({@code APT_COMMITMENTS_JN}, read by
 * {@code APT_COMMITMENTS_JN_GET}): the commitment as it stood after an insert or update, or
 * before a delete. Field names are APT2's.
 *
 * @param rowId              the journal row's ROWID, which picks one entry
 * @param jnOperation        {@code INS}, {@code UPD} or {@code DEL}
 * @param jnOracleUser       who made the change ({@code IDIR\USERNAME} from FTA)
 * @param jnDatetime         when
 * @param jnNotes            the reason given
 * @param jnAppln            the screen it came from
 * @param jnSession          the database session, where recorded
 * @param id                 commitment id
 * @param forestFileId       licence
 * @param aptPartitionSt     partition type code
 * @param commitTypeSt       commitment type code
 * @param allowableAnnualCut AAC
 * @param includeInLicAacInd {@code Y} or {@code N}
 * @param determinationDate  determination date
 * @param effectiveDate      effective date
 * @param expiryDate         expiry date
 * @param paptId             partition apportionment, if any
 */
public record AptCommitmentHistoryDto(
    String rowId,
    String jnOperation,
    String jnOracleUser,
    LocalDateTime jnDatetime,
    String jnNotes,
    String jnAppln,
    Long jnSession,
    Long id,
    String forestFileId,
    String aptPartitionSt,
    String commitTypeSt,
    BigDecimal allowableAnnualCut,
    String includeInLicAacInd,
    LocalDate determinationDate,
    LocalDate effectiveDate,
    LocalDate expiryDate,
    Long paptId) {}
