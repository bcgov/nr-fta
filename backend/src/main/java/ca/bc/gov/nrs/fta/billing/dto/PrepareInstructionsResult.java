package ca.bc.gov.nrs.fta.billing.dto;

/**
 * @param created true when at least one new instruction was recorded
 */
public record PrepareInstructionsResult(boolean created) {}
