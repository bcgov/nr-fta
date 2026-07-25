package ca.bc.gov.nrs.fta.tenure.dto;

import java.util.List;

/**
 * Request body for assigning hauling timber marks to the cut blocks of a
 * cutting permit ({@code POST
 * /api/fta/cutting-permits/{cpId}/assign-marks}). Fields mirror the input
 * parameters of the legacy {@code FTA_908_HAULING_AUTH.save} procedure
 * (invoked per block via {@code mainline}/{@code assign_to_all}). The cutting
 * permit id comes from the path; the harvesting authority key and the
 * per-block assignments come from the body.
 */
public record AssignMarksRequest(
    String forestFileId,
    String hvaSkey,
    List<BlockAssignment> assignments) {

  /**
   * One block's mark assignment — mirrors the block-level params of the legacy
   * {@code save} procedure. {@code newTimberMark} is optional; when present it
   * replaces {@code timberMark} for the block (legacy: {@code IF
   * p_new_timber_mark IS NOT NULL THEN p_timber_mark := p_new_timber_mark}).
   */
  public record BlockAssignment(
      String cbSkey,
      String cutBlockId,
      String timberMark,
      String newTimberMark,
      String revisionCount) {}
}
