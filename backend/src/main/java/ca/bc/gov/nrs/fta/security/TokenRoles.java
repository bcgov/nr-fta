package ca.bc.gov.nrs.fta.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The caller's roles, read off the access token, and the authorities they grant.
 *
 * <p>One place for both, because two consumers need them and must agree: the security filter
 * chain authorises on {@link #authoritiesFrom}, and {@link LoggedUserHelper} asks which
 * districts a user is scoped to via {@link #rolesFrom}.
 *
 * <h3>Why a scoped role grants its base authority</h3>
 * FAM puts a grant's scope in the role name and nowhere else. A district-scoped
 * administrator's token carries {@code FTA_ADMIN_DISTRICT-DCC} — never {@code FTA_ADMIN} on its
 * own — so the URL rules in {@link ApiAuthorizationCustomizer}, which match the bare
 * authority, would refuse every one of that user's requests. Each scoped role therefore also
 * grants its base role as an authority. The URL rules stay as they are, and "is this user an
 * administrator" keeps meaning what it says.
 *
 * <p>The scoped role name is kept as an authority too, but where a question depends on the
 * scope — which districts, or whether the grant is unscoped — ask {@link #rolesFrom}: once the
 * base authority is derived, the authority list alone can no longer tell an unscoped
 * administrator from a scoped one.
 */
public final class TokenRoles {

  /** Roles CSS attaches to a token for the client it was issued to. */
  static final String CLAIM_CLIENT_ROLES = "client_roles";

  /** Where stock Keycloak puts the same information. */
  static final String CLAIM_RESOURCE_ACCESS = "resource_access";

  static final String CLAIM_AZP = "azp";

  private TokenRoles() {}

  /**
   * The role names on the token, FAM bookkeeping roles excluded.
   *
   * <p>Under CSS these arrive as {@code client_roles}. Falls back to
   * {@code resource_access.<azp>.roles}, which is where stock Keycloak puts them — which one
   * appears depends on the realm's mappers, so both are read rather than assuming.
   *
   * <p>FAM records per-grant expiry as a role assigned to the person, shaped
   * {@code FAM:EXPIRES:2026-09-30:FTA_ADMIN}; those are never privileges and are dropped.
   */
  public static List<String> rolesFrom(Jwt jwt) {
    List<String> roles = jwt.getClaimAsStringList(CLAIM_CLIENT_ROLES);
    if (roles == null || roles.isEmpty()) {
      roles = rolesFromResourceAccess(jwt);
    }
    return roles.stream().filter(role -> !RoleScope.isSidecar(role)).toList();
  }

  /**
   * The authorities the token's roles grant: every role name, plus the base role of every
   * scoped one. Authorities are used with no prefix; {@code FTA_ADMIN} and {@code FTA_VIEWER}
   * are matched verbatim.
   */
  public static Collection<GrantedAuthority> authoritiesFrom(Jwt jwt) {
    Set<String> names = new LinkedHashSet<>();
    for (String role : rolesFrom(jwt)) {
      names.add(role);
      RoleScope.Parsed parsed = RoleScope.parse(role);
      if (!parsed.scopes().isEmpty()) {
        names.add(parsed.baseRole());
      }
    }
    return names.stream()
        .map(name -> (GrantedAuthority) new SimpleGrantedAuthority(name))
        .toList();
  }

  /** {@code resource_access.<azp>.roles}, or an empty list when it is absent or malformed. */
  private static List<String> rolesFromResourceAccess(Jwt jwt) {
    Object resourceAccess = jwt.getClaim(CLAIM_RESOURCE_ACCESS);
    String clientId = jwt.getClaimAsString(CLAIM_AZP);

    if (!(resourceAccess instanceof Map<?, ?> byClient) || clientId == null) {
      return List.of();
    }
    if (byClient.get(clientId) instanceof Map<?, ?> entry
        && entry.get("roles") instanceof List<?> roles) {
      return roles.stream()
          .filter(String.class::isInstance)
          .map(String.class::cast)
          .toList();
    }
    return List.of();
  }
}
