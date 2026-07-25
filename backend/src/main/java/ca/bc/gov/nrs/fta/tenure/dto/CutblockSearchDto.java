package ca.bc.gov.nrs.fta.tenure.dto;

import java.time.LocalDate;

/**
 * Result row for a cut-block search.
 *
 * <p>Mirrors the {@code rec_cut_block_results} record returned by the legacy
 * Oracle package {@code THE.FTA_003_CUTBLK_SRCH} (see
 * {@code fta-archive/fta/database/ddl/pkg/FTA_003_CUTBLK_SRCH.PKS}). The legacy
 * record carries the disturbance dates as {@code VARCHAR2(10)} formatted
 * {@code YYYY-MM-DD}; here they are exposed as {@link LocalDate}.
 */
public record CutblockSearchDto(
    Long cbSkey,
    String orgUnitCode,
    String clientNumber,
    String clientName,
    String forestFileId,
    String cuttingPermitId,
    String timberMark,
    String cutBlockId,
    String blockStatusSt,
    LocalDate disturbanceStartDate,
    LocalDate disturbanceEndDate) {}
