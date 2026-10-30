package ca.bc.gov.nrs.fta.tenure.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Legacy FTA908: a Fort St. John authorization, its blocks and the marks they can take.
 *
 * @param marks the timber marks on the file's issued Fort St. John authorizations
 */
public record AssignMarksDto(
    long hvaSkey,
    String forestFileId,
    String authorizationId,
    String statusCode,
    LocalDate approvalDate,
    LocalDate expiryDate,
    String licensee,
    String higherLevelPlanReference,
    List<Block> blocks,
    List<String> marks) {

  /** A cut block, as FTA908's list shows it. */
  public record Block(long cbSkey, String cutBlockId, String timberMark, long revisionCount) {}
}
