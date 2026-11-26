package ca.bc.gov.nrs.fta.shared.dto;

/**
 * Org Unit Maintenance's Save ({@code POST /api/fta/admin/org-unit-default}): the org unit whose
 * radio button the user checked, by its ORG_UNIT_NO — legacy's {@code p_user_org_default}.
 */
public record OrgUnitMaintRequest(String orgUnitNo) {}
