package ca.bc.gov.nrs.fta.tenure.tab.details;

import java.util.Map;

/**
 * {@code PUT /api/fta/tenures/{id}/details}.
 *
 * @param revisions the {@code revisions} the GET returned, unchanged — the optimistic lock
 * @param values    the edited values by key (see {@link TenureDetailsDto#values()}); only the
 *                  keys the rules open are applied, the rest are ignored. Includes
 *                  {@code districtOverrideReason} when the change needs one.
 */
public record TenureDetailsUpdateRequest(
    Map<String, String> revisions, Map<String, String> values) {}
