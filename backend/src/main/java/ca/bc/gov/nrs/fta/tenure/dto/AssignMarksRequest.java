package ca.bc.gov.nrs.fta.tenure.dto;

import java.util.List;

/**
 * Request body for assigning timber marks to an authorization's cut blocks ({@code POST
 * /api/fta/cutting-permits/{cpId}/assign-marks}), legacy FTA908. Either {@code assignToAllMark}
 * names an existing mark for FTA908's Assign Mark to All, or {@code assignments} lists blocks
 * individually.
 */
public record AssignMarksRequest(
    String forestFileId,
    Long hvaSkey,
    String assignToAllMark,
    List<BlockAssignment> assignments) {

  /**
   * One block's mark: an existing {@code timberMark}, or a {@code newTimberMark} to create, not
   * both. {@code revisionCount} is the block's when it was read.
   */
  public record BlockAssignment(
      Long cbSkey,
      String timberMark,
      String newTimberMark,
      Long revisionCount) {}
}
