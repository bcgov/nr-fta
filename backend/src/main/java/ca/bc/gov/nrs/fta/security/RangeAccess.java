package ca.bc.gov.nrs.fta.security;

import java.util.List;

/**
 * Who may act on range, by district — the rules {@link LoggedUserHelper}'s range helpers apply,
 * kept free of the security context so they can be tested on plain role names.
 *
 * <p>Every method takes the role names on the token with their FAM scope suffixes intact
 * ({@link TokenRoles#rolesFrom}), because the authorities alone cannot tell an unscoped grant from
 * a scoped one.
 *
 * <ul>
 *   <li><b>Range admin screens</b> (631 Manage Zone, 640 Archive Tenures, range billing, 699
 *       Range Rates): {@code FTA_ADMIN} in its districts, or {@code FTA_RANGE_HEADQUARTERS_ADMIN}
 *       everywhere — {@link #administersDistrict}, {@link #actsForEveryDistrict}.</li>
 *   <li><b>Range file edits</b>: those, plus {@code FTA_RANGE_DISTRICT_ADMIN} in its districts
 *       (every district when unscoped) — {@link #mayEditDistrict}. Legacy's range tech edited
 *       only files administered by its own district.</li>
 * </ul>
 */
public final class RangeAccess {

  private RangeAccess() {}

  /** Whether the roles include {@code FTA_RANGE_HEADQUARTERS_ADMIN}, in any scope. */
  public static boolean isHeadquarters(List<String> roles) {
    return roles.stream()
        .filter(r -> !RoleScope.isSidecar(r))
        .anyMatch(r ->
            RoleConstants.RANGE_HEADQUARTERS_AUTHORITY.equals(RoleScope.parse(r).baseRole()));
  }

  /**
   * Unscoped {@code FTA_ADMIN} or {@code FTA_RANGE_HEADQUARTERS_ADMIN}: may act on every district on
   * the range admin screens (and pick "every district" where a screen offers it), and save
   * Range Rates.
   */
  public static boolean actsForEveryDistrict(List<String> roles) {
    return RoleScope.hasUnscoped(roles, RoleConstants.ADMIN_AUTHORITY) || isHeadquarters(roles);
  }

  /**
   * May use a range admin screen on the given district: {@link #actsForEveryDistrict}, or a
   * district-scoped {@code FTA_ADMIN} holding it.
   */
  public static boolean administersDistrict(List<String> roles, String orgUnitCode) {
    if (actsForEveryDistrict(roles)) {
      return true;
    }
    return orgUnitCode != null
        && RoleScope.districtsFor(roles, RoleConstants.ADMIN_AUTHORITY).contains(orgUnitCode);
  }

  /**
   * May edit a range file administered by the given district: {@link #administersDistrict}, or
   * {@code FTA_RANGE_DISTRICT_ADMIN} unscoped or scoped to that district.
   */
  public static boolean mayEditDistrict(List<String> roles, String orgUnitCode) {
    if (administersDistrict(roles, orgUnitCode)
        || RoleScope.hasUnscoped(roles, RoleConstants.RANGE_DISTRICT_AUTHORITY)) {
      return true;
    }
    return orgUnitCode != null
        && RoleScope.districtsFor(roles, RoleConstants.RANGE_DISTRICT_AUTHORITY)
            .contains(orgUnitCode);
  }
}
