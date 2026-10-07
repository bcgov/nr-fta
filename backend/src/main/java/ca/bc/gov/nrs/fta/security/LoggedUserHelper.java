package ca.bc.gov.nrs.fta.security;

import ca.bc.gov.nrs.fta.exception.UserNotFoundException;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Spring bean exposing authorization helpers for the currently authenticated user.
 *
 * <p>Registered as {@code @auth} for programmatic use in services and security configuration.
 *
 * <h3>Identity comes off the token now</h3>
 * Under Cognito the frontend sent an access token that carried {@code cognito:groups} but none
 * of the {@code custom:idp_*} profile claims, so this helper called the Cognito
 * {@code /oauth2/userInfo} endpoint on every request (behind a five-minute cache) and merged
 * the result into a synthetic claims map.
 *
 * <p>BC Gov SSO maps the profile claims onto the access token directly, so the whole
 * enrichment step — and the external HTTP call it made from the request path — is gone. The
 * claims are read straight off the JWT.
 */
@Component("auth")
public class LoggedUserHelper {

  // ─── Identity helpers ──────────────────────────────────────────────

  /**
   * Get the ID from the logged user (e.g. {@code IDIR\jsmith}).
   *
   * <p>Reads {@code idir_username} and {@code identity_provider} from the access token. The
   * provider is normalised to {@code IDIR} rather than passed through as {@code azureidir} —
   * see {@link JwtPrincipalUtil} for why that matters to the audit columns this value is
   * written to.
   */
  public String getLoggedUserId() {
    return JwtPrincipalUtil.getUserId(getPrincipal().getClaims());
  }

  // ─── Role / authority helpers (from client_roles on the access token) ──

  /**
   * Returns the set of authority strings for the current user (e.g. {@code FTA_ADMIN}).
   */
  public Set<String> getAuthorities() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return Set.of();
    }
    return authentication.getAuthorities()
        .stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toSet());
  }

  /**
   * Returns {@code true} if the user is an administrator, in any scope.
   *
   * <p>A district-scoped grant ({@code FTA_ADMIN_DISTRICT-DCC}) counts: the authority converter
   * derives the base {@code FTA_ADMIN} authority from it, which is also what lets such a user
   * past the URL rules. Use {@link #administersDistrict} where it matters which districts.
   */
  public boolean isAdmin() {
    return getAuthorities().contains(RoleConstants.ADMIN_AUTHORITY);
  }

  /**
   * Whether the user holds {@code FTA_ADMIN} with no scope at all — an administrator of every
   * district.
   *
   * <p>Read from the token's role names rather than the authorities, which carry
   * {@code FTA_ADMIN} for scoped administrators too.
   */
  public boolean isUnscopedAdmin() {
    return RoleScope.hasUnscoped(tokenRoles(), RoleConstants.ADMIN_AUTHORITY);
  }

  /**
   * The district org-unit codes the user administers under a district-scoped grant.
   *
   * <p>One role per scope value, so three districts arrive as three role names. Empty for an
   * unscoped administrator — that is not "no districts" but "not narrowed", which
   * {@link #isUnscopedAdmin()} distinguishes.
   */
  public List<String> adminDistricts() {
    return RoleScope.districtsFor(tokenRoles(), RoleConstants.ADMIN_AUTHORITY);
  }

  /**
   * Whether the user may administer a file whose administering district is the given one.
   *
   * <p>An unscoped administrator may act anywhere; a scoped one only within the districts
   * granted.
   */
  public boolean administersDistrict(String orgUnitCode) {
    if (isUnscopedAdmin()) {
      return true;
    }
    return orgUnitCode != null && adminDistricts().contains(orgUnitCode);
  }

  // ─── Internal helpers ─────────────────────────────────────────────

  /** The role names on the caller's token, scope suffixes intact. */
  private List<String> tokenRoles() {
    return TokenRoles.rolesFrom(getPrincipal());
  }

  /**
   * Returns the raw {@link Jwt} principal from the security context.
   */
  private Jwt getPrincipal() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication != null
        && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof Jwt jwtPrincipal) {
      return jwtPrincipal;
    }
    throw new UserNotFoundException();
  }


}
