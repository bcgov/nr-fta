package ca.bc.gov.nrs.fta.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Pins how token roles become authorities once FTA's roles are district-scoped in FAM.
 *
 * <p>The failure this guards against is silent in the worst direction: a scoped administrator
 * signs in, the UI shows them everything, and every API call comes back 403 because the URL
 * rules match {@code FTA_ADMIN} and the token only ever carries
 * {@code FTA_ADMIN_DISTRICT-DCC}.
 */
@DisplayName("Unit Test | TokenRoles")
class TokenRolesTest {

  private static Jwt tokenWithClientRoles(String... roles) {
    return Jwt.withTokenValue("token")
        .header("alg", "none")
        .claim("azp", "fta-client")
        .claim("client_roles", List.of(roles))
        .build();
  }

  private static List<String> authorities(Jwt jwt) {
    return TokenRoles.authoritiesFrom(jwt).stream().map(GrantedAuthority::getAuthority).toList();
  }

  @Nested
  @DisplayName("authorities")
  class Authorities {

    @Test
    void districtScopedAdmin_holdsTheBaseAdminAuthority() {
      assertThat(authorities(tokenWithClientRoles("FTA_ADMIN_DISTRICT-DCC")))
          .contains("FTA_ADMIN", "FTA_ADMIN_DISTRICT-DCC");
    }

    @Test
    void districtScopedViewer_holdsTheBaseViewerAuthority() {
      assertThat(authorities(tokenWithClientRoles("FTA_VIEWER_DISTRICT-DPG")))
          .contains("FTA_VIEWER")
          .doesNotContain("FTA_ADMIN");
    }

    @Test
    void unscopedRole_isUnchanged() {
      assertThat(authorities(tokenWithClientRoles("FTA_ADMIN"))).containsExactly("FTA_ADMIN");
    }

    @Test
    void severalDistricts_grantTheBaseAuthorityOnce() {
      assertThat(authorities(
          tokenWithClientRoles("FTA_ADMIN_DISTRICT-DCC", "FTA_ADMIN_DISTRICT-DPG")))
          .containsOnlyOnce("FTA_ADMIN");
    }

    @Test
    void famExpirySidecar_isNeverAnAuthority() {
      assertThat(authorities(tokenWithClientRoles(
          "FTA_ADMIN_DISTRICT-DCC", "FAM:EXPIRES:2026-09-30:FTA_ADMIN_DISTRICT-DCC")))
          .noneMatch(a -> a.startsWith("FAM:"));
    }

    /**
     * A hyphen that is not a scope separator must not invent a base role — otherwise a
     * hand-configured role like this could quietly grant some unrelated authority.
     */
    @Test
    void hyphenatedRoleWithoutScope_grantsNothingExtra() {
      assertThat(authorities(tokenWithClientRoles("SOME-ROLE"))).containsExactly("SOME-ROLE");
    }
  }

  @Nested
  @DisplayName("role names")
  class RoleNames {

    @Test
    void readsClientRoles() {
      assertThat(TokenRoles.rolesFrom(tokenWithClientRoles("FTA_ADMIN_DISTRICT-DCC")))
          .containsExactly("FTA_ADMIN_DISTRICT-DCC");
    }

    @Test
    void fallsBackToResourceAccessForTheIssuingClient() {
      Jwt jwt = Jwt.withTokenValue("token")
          .header("alg", "none")
          .claim("azp", "fta-client")
          .claim("resource_access", Map.of(
              "fta-client", Map.of("roles", List.of("FTA_VIEWER_DISTRICT-DCC")),
              "other-client", Map.of("roles", List.of("FTA_ADMIN"))))
          .build();

      assertThat(TokenRoles.rolesFrom(jwt)).containsExactly("FTA_VIEWER_DISTRICT-DCC");
      assertThat(authorities(jwt)).contains("FTA_VIEWER").doesNotContain("FTA_ADMIN");
    }

    @Test
    void scopedAndUnscopedGrants_areDistinguishableFromTheRoleNames() {
      List<String> scoped = TokenRoles.rolesFrom(tokenWithClientRoles("FTA_ADMIN_DISTRICT-DCC"));
      List<String> unscoped = TokenRoles.rolesFrom(tokenWithClientRoles("FTA_ADMIN"));

      assertThat(RoleScope.hasUnscoped(scoped, "FTA_ADMIN")).isFalse();
      assertThat(RoleScope.districtsFor(scoped, "FTA_ADMIN")).containsExactly("DCC");
      assertThat(RoleScope.hasUnscoped(unscoped, "FTA_ADMIN")).isTrue();
      assertThat(RoleScope.districtsFor(unscoped, "FTA_ADMIN")).isEmpty();
    }
  }
}
