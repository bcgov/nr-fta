package ca.bc.gov.nrs.fta.security;

import org.springframework.security.core.Authentication;

/**
 * Who may write harvest authorizations, for the rules a URL cannot express — a field or a status
 * change on a save both roles may call. Which endpoints each role reaches is
 * {@link ApiAuthorizationCustomizer}.
 *
 * <p>Neither check looks at a district: harvest role holders may act on any district's permits.
 */
public final class HarvestAccess {

  private HarvestAccess() {}

  /** FTA_ADMIN or either harvest role: may add and edit cutting permits and cut blocks. */
  public static boolean canEdit(Authentication authentication) {
    return holds(authentication, RoleConstants.ADMIN_AUTHORITY)
        || holds(authentication, RoleConstants.HARVEST_AUTH_SENIOR_AUTHORITY)
        || holds(authentication, RoleConstants.HARVEST_AUTH_DISTRICT_AUTHORITY);
  }

  /**
   * FTA_ADMIN or the senior harvest role: legacy's Senior Admin, BCTS and Feature Admin rights.
   */
  public static boolean isSenior(Authentication authentication) {
    return holds(authentication, RoleConstants.ADMIN_AUTHORITY)
        || holds(authentication, RoleConstants.HARVEST_AUTH_SENIOR_AUTHORITY);
  }

  private static boolean holds(Authentication authentication, String authority) {
    return authentication != null && authentication.getAuthorities().stream()
        .anyMatch(a -> authority.equals(a.getAuthority()));
  }
}
