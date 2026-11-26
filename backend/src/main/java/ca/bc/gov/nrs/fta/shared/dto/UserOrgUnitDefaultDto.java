package ca.bc.gov.nrs.fta.shared.dto;

/**
 * The signed-in user's default org unit ({@code GET /api/fta/org-unit-default}), which legacy
 * screens defaulted their district fields to ({@code SilUser.getUser_org_unit}).
 *
 * @param orgUnitNo      the default's ORG_UNIT_NO, or null when the user has none
 * @param orgUnitCode    its ORG_UNIT_CODE, or null
 * @param orgLevelCode   its ORG_LEVEL_CODE (D district, R region, H headquarters), or null
 * @param rollupRegionNo the ORG_UNIT_NO of the region it rolls up to
 *                       ({@code SilUser.getRollup_region_code}), or null
 */
public record UserOrgUnitDefaultDto(
    String orgUnitNo, String orgUnitCode, String orgLevelCode, String rollupRegionNo) {

  public static final UserOrgUnitDefaultDto NONE = new UserOrgUnitDefaultDto(null, null, null, null);
}
