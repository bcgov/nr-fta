package ca.bc.gov.nrs.fta.range.dto;

import java.util.List;

/**
 * What an FTA630 write returns: the range unit's id (generated on an add) and any warning legacy
 * showed alongside "Save successful." (the As of date set to today).
 */
public record RangeUnitWriteResultDto(String rangeUnitId, List<String> warnings) {}
