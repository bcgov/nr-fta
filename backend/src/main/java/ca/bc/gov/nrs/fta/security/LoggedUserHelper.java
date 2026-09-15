package ca.bc.gov.nrs.fta.security;

import ca.bc.gov.nrs.fta.exception.UserNotFoundException;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

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
   * Returns {@code true} if the user holds the {@code FTA_ADMIN} authority.
   *
   * <p>Matches the bare authority only. A <em>district-scoped</em> grant reaches
   * the token as {@code FTA_ADMIN_DISTRICT-DCC} and will not satisfy this — use
   * {@link #isAdminAnywhere()} where a scoped grant should also count, and
   * {@link #adminDistricts()} where it matters which districts.
   */
  public boolean isAdmin() {
    return getAuthorities().contains(RoleConstants.ADMIN_AUTHORITY);
  }

  /**
   * Whether the user administers at least one district, or holds the role
   * unscoped.
   *
   * <p>FAM puts the scope in the role name and nowhere else, so a scoped holder
   * never carries the bare code. Anything that asks "may this user edit
   * <em>something</em>" has to accept both spellings.
   */
  public boolean isAdminAnywhere() {
    return isAdmin() || !adminDistricts().isEmpty();
  }

  /**
   * The district org-unit codes the user administers.
   *
   * <p>One role per scope value, so three districts arrive as three role names.
   * Empty for an unscoped administrator — that is not "no districts" but "not
   * narrowed", which {@link #isAdmin()} distinguishes.
   */
  public java.util.List<String> adminDistricts() {
    return RoleScope.districtsFor(getAuthorities(), RoleConstants.ADMIN_AUTHORITY);
  }

  /**
   * Whether the user may act on a file administered by the given district.
   *
   * <p>An unscoped administrator may act anywhere; a scoped one only within the
   * districts granted.
   */
  public boolean administersDistrict(String orgUnitCode) {
    if (isAdmin()) {
      return true;
    }
    return orgUnitCode != null && adminDistricts().contains(orgUnitCode);
  }

  // ─── Internal helpers ─────────────────────────────────────────────

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
