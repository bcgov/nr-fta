package ca.bc.gov.nrs.fta.mark.dto;

/**
 * Request body for a timber-mark transfer ({@code POST
 * /api/fta/marks/transfer}). Fields mirror the {@code IN} parameters of the
 * legacy {@code FTA_230_MARKTRANFER.save} procedure, which re-points a source
 * file/CP timber mark (and its associated tenure data) onto a target file/CP
 * and records the move in {@code THE.MARK_TRANSFER}.
 *
 * <p>{@code transferEffDate} is the effective date in {@code YYYY-MM-DD} form;
 * the legacy package concatenates it with the current {@code HH24MISS} before
 * storing it as a {@code DATE} (see {@link
 * ca.bc.gov.nrs.fta.mark.service.MarkTransferWriteService}).
 */
public record MarkTransferRequest(
    String sourceForestFileId,
    String sourceCuttingPermitId,
    String timberMark,
    String targetForestFileId,
    String targetCuttingPermitId,
    String transferEffDate,
    String userOrgNo) {}
