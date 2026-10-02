package ca.bc.gov.nrs.fta.mark.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for saving a private mark ({@code PUT /api/fta/marks/{id}}) — the FTA510
 * "Save" button. Carries every editable field; the server keeps the stored value for any
 * field {@link MarkEditRules} does not open, so a client cannot widen what it may change.
 *
 * <p>{@code revisionCount} and {@code amendRevisionCount} are the values the client read,
 * for optimistic locking, as legacy's {@code p_tm_revision_count} / {@code p_amd_revision_count}.
 */
public record MarkUpdateRequest(
    Long revisionCount,
    Long amendRevisionCount,
    LocalDate applicationDate,
    Integer tenureTerm,
    /** ORG_UNIT_NO of the district. */
    String forestDistrict,
    String markingMethodCode,
    String markingInstrumentCode,
    String permitBlockLocn,
    String proofOfCrownOrLegal,
    String bcaaFolioNumber,
    BigDecimal permitBlockArea,
    String mgmtUnitTypeCode,
    String mgmtUnitId,
    String cascadeSplitCode,
    String mapReferenceReg,
    String mapReferenceComp,
    LocalDate markIssueDate,
    LocalDate markExpiryDate,
    LocalDate markExtendDate,
    LocalDate markCancelDate,
    LocalDate grantedAcqrdDate,
    String crownGrantedAcqDesc,
    String markStatusCode,
    String amendStatusCode,
    /**
     * Mark Type. Set (on a mark that has none) means "issue the mark": the save generates the
     * timber mark for it and issues it — legacy's Assign Mark and Save as one step (PI to HN).
     */
    String fileTypeCode) {}
