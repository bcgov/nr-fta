package ca.bc.gov.nrs.fta.tenure.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for saving a cut block ({@code PUT /api/fta/cut-blocks/{cbSkey}}), legacy FTA904's
 * Save. The revision counts are the ones the user read, so a save over someone else's is refused.
 */
public record CutBlockEditRequest(
    String blockStatus,
    LocalDate blockStatusDate,
    String cutBlockDescription,
    String cutRegulationCode,
    String reforestDeclareTypeCode,
    String fireHarvestingReasonCode,
    String underPartitionOrder,
    LocalDate reportedFireDate,
    String wasteAssessmentRequired,
    BigDecimal plannedGrossBlockArea,
    BigDecimal plannedNetBlockArea,
    LocalDate plannedHarvestDate,
    BigDecimal disturbanceGrossArea,
    LocalDate disturbanceStartDate,
    LocalDate disturbanceEndDate,
    String spExemptInd,
    String overrideReason,
    long cbRevisionCount,
    long cboaRevisionCount) {}
