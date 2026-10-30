package ca.bc.gov.nrs.fta.tenure.dto;

import java.time.LocalDate;

/** One {@code CUT_BLOCK_SUSPENSION} row of a cut block. */
public record CutBlockSuspensionDto(
    Long suspensionId,
    String underPartition,
    String orderNumber,
    LocalDate startDate,
    LocalDate endDate) {}
