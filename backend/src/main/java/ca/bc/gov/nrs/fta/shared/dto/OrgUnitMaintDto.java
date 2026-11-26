package ca.bc.gov.nrs.fta.shared.dto;

import java.util.List;

/**
 * What the Org Unit Maintenance screen (legacy SIL99) shows.
 *
 * @param userId           the signed-in user, whose default this is
 * @param orgUnits         the user's org units, one radio button each (code = ORG_UNIT_NO)
 * @param defaultOrgUnitNo the one checked, or null when none is
 * @param error            why the user has nothing to choose from, or null
 */
public record OrgUnitMaintDto(
    String userId, List<CodeOptionDto> orgUnits, String defaultOrgUnitNo, String error) {}
