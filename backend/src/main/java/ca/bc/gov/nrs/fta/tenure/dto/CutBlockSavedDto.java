package ca.bc.gov.nrs.fta.tenure.dto;

import java.util.List;

/** The result of a cut block save: its label, and any warnings legacy would have shown. */
public record CutBlockSavedDto(String cutBlockId, List<String> warnings) {}
