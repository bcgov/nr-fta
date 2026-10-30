package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.tenure.dto.AssignMarksRequest;
import ca.bc.gov.nrs.fta.tenure.dto.AssignMarksRequest.BlockAssignment;
import java.util.List;

/**
 * Legacy FTA908's form validators, which its package does not repeat, plus the Senior Admin check
 * its screen made by disabling Specify New Timber Mark.
 */
final class AssignMarksRules {

  /** FTA908's field length for a new mark. */
  private static final int MAX_MARK_LENGTH = 6;

  private AssignMarksRules() {}

  /** Null when the request can be attempted, else the problem. */
  static String problem(AssignMarksRequest q, boolean senior) {
    List<BlockAssignment> assignments = q.assignments() == null ? List.of() : q.assignments();
    boolean toAll = hasText(q.assignToAllMark());
    if (toAll && !assignments.isEmpty()) {
      return "Assign a mark to all blocks or to individual blocks, not both.";
    }
    if (!toAll && assignments.isEmpty()) {
      return "No changes made.";
    }
    for (BlockAssignment a : assignments) {
      boolean existing = hasText(a.timberMark());
      boolean created = hasText(a.newTimberMark());
      if (existing == created) {
        return "Choose a Timber Mark or specify a new one, not both.";
      }
      if (created && !senior) {
        return "Specify New Timber Mark requires the senior harvest role.";
      }
      if (created && a.newTimberMark().trim().length() > MAX_MARK_LENGTH) {
        return "Specified Timber Mark must be at most " + MAX_MARK_LENGTH + " characters.";
      }
      if (a.cbSkey() == null || a.revisionCount() == null) {
        return "Each assignment needs its block and revision count.";
      }
    }
    return null;
  }

  private static boolean hasText(String s) {
    return s != null && !s.isBlank();
  }
}
