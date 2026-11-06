package ca.bc.gov.nrs.fta.apt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The body of {@code POST /api/fta/apt/commitments} and {@code PUT .../{id}}, bound to
 * {@code APT_COMMITMENT_INS_UPD}. APT2's field names; its {@code id}, {@code user} and
 * {@code appln} are gone: the id is the path's, the user is the caller's token, and the
 * application is always APT2's screen code.
 *
 * @param paptId            partition apportionment the commitment belongs to; null for one
 *                          entered against a licence
 * @param forestFileId      licence, required
 * @param aptPartitionSt    partition type code, required
 * @param commitTypeSt      commitment type code, required
 * @param aac               allowable annual cut, a whole number 0 to 999,999,999, required
 * @param inclInLicAacInd   {@code Y} or {@code N}, required
 * @param determinationDate required
 * @param effectiveDate     required
 * @param expiryDate        optional
 * @param reason            journal note; required to change an existing commitment
 */
public record AptCommitmentRequest(
    Long paptId,
    String forestFileId,
    String aptPartitionSt,
    String commitTypeSt,
    BigDecimal aac,
    String inclInLicAacInd,
    LocalDate determinationDate,
    LocalDate effectiveDate,
    LocalDate expiryDate,
    String reason) {}
