package ca.bc.gov.nrs.fta.billing.dto;

import java.util.List;

/**
 * FTA680 Prepare: the search the rows came from, and the instruction chosen for
 * each. The server re-runs the search and refuses if the list has changed.
 *
 * @param criteria     the criteria of the last Go
 * @param instructions one entry per row shown
 */
public record PrepareInstructionsRequest(
    BillingInstructionsCriteria criteria, List<Instruction> instructions) {

  /**
   * @param forestFileId    the row's tenure
   * @param instructionCode the instruction chosen, blank for none
   */
  public record Instruction(String forestFileId, String instructionCode) {}
}
