package ca.bc.gov.nrs.fta.tenure.tab.details;

import java.util.List;

/**
 * A successful save: legacy's "Save successful." plus the package's warnings, if any (e.g.
 * "This licence to cut requires spatial if area &gt;= 1 ha").
 */
public record TenureDetailsSaveResult(List<String> warnings) {}
