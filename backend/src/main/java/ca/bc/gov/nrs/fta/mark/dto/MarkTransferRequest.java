package ca.bc.gov.nrs.fta.mark.dto;

/**
 * Request body for a timber-mark transfer ({@code POST /api/fta/marks/transfer}) — the
 * fields of legacy FTA230's form ({@code Fta230MarktranferForm}), all required.
 *
 * <p>There is no timber mark: as legacy's {@code FTA_230_MARKTRANFER.save}, the mark moved is
 * the source File/CP's primary mark. {@code transferEffDate} is the Legal Transfer Date in
 * {@code YYYY-MM-DD} form.
 */
public record MarkTransferRequest(
    String sourceForestFileId,
    String sourceCuttingPermitId,
    String targetForestFileId,
    String targetCuttingPermitId,
    String transferEffDate) {}
