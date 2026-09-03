package ca.bc.gov.nrs.fta.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Pins the two claim-mapping rules that the Cognito → BC Gov SSO cutover made load-bearing.
 *
 * <p>Both of them fail <em>silently</em>: a wrong provider alias or a wrong GUID case produces
 * software that works, logs nothing, and quietly writes audit rows that no longer join to the
 * ones already in the {@code THE} schema. Neither is caught by manual QA, because the screen
 * looks right either way. This is the test that catches them.
 */
@DisplayName("Unit Test | JwtPrincipalUtil")
class JwtPrincipalUtilTest {

  private static final String USERNAME = "JSMITH";
  private static final String GUID_UPPER = "0A1B2C3D4E5F60718293A4B5C6D7E8F9";
  private static final String GUID_LOWER = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";

  /** A token as the standard realm's IDIR - MFA integration issues it. */
  private static Map<String, Object> idirClaims(String identityProvider) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("idir_username", USERNAME);
    claims.put("idir_user_guid", GUID_UPPER);
    claims.put("preferred_username", GUID_LOWER + "@azureidir");
    if (identityProvider != null) {
      claims.put("identity_provider", identityProvider);
    }
    return claims;
  }

  /** {@code getIdpUsername} takes a principal rather than a claims map, so wrap one. */
  private static JwtAuthenticationToken tokenOf(Map<String, Object> claims) {
    Jwt jwt = Jwt.withTokenValue("token")
        .header("alg", "none")
        .claims(c -> c.putAll(claims))
        .build();
    return new JwtAuthenticationToken(jwt);
  }

  @Nested
  @DisplayName("the audit user id")
  class UserId {

    /**
     * The whole reason {@code getProviderValue} exists. The realm reports
     * {@code azureidir}; the audit columns have always held {@code IDIR}.
     */
    @Test
    void azureidir_isNormalisedToIdir() {
      assertThat(JwtPrincipalUtil.getUserId(idirClaims("azureidir")))
          .isEqualTo("IDIR\\" + USERNAME);
    }

    @Test
    void legacyIdirAlias_alsoNormalisesToIdir() {
      assertThat(JwtPrincipalUtil.getUserId(idirClaims("idir")))
          .isEqualTo("IDIR\\" + USERNAME);
    }

    @Test
    void providerIsCaseInsensitive() {
      assertThat(JwtPrincipalUtil.getUserId(idirClaims("AzureIDIR")))
          .isEqualTo("IDIR\\" + USERNAME);
    }

    /**
     * {@code identity_provider} is added by the broker rather than by a CSS mapper, so it can
     * be absent. {@code preferred_username} is documented as always present, and its suffix
     * carries the same information.
     */
    @Test
    void providerFallsBackToThePreferredUsernameSuffix() {
      assertThat(JwtPrincipalUtil.getUserId(idirClaims(null)))
          .isEqualTo("IDIR\\" + USERNAME);
    }

    /** A provider FTA does not broker is not guessed at. */
    @Test
    void unknownProviderYieldsNoProviderPrefix() {
      Map<String, Object> claims = idirClaims("bceidbasic");
      assertThat(JwtPrincipalUtil.getUserId(claims)).isEqualTo("\\" + USERNAME);
    }

    /**
     * The GUID branch is reached whenever {@code idir_username} is missing — which is exactly
     * what happens when an environment's CSS integration has not mapped that claim onto the
     * <em>access</em> token. It is therefore not a hypothetical path.
     */
    @Test
    void fallsBackToTheGuidWhenTheUsernameIsAbsent() {
      Map<String, Object> claims = idirClaims("azureidir");
      claims.remove("idir_username");
      assertThat(JwtPrincipalUtil.getUserId(claims)).isEqualTo("IDIR\\" + GUID_UPPER);
    }

    @Test
    void fallsBackToTheGuidWhenTheUsernameIsBlank() {
      Map<String, Object> claims = idirClaims("azureidir");
      claims.put("idir_username", "  ");
      assertThat(JwtPrincipalUtil.getUserId(claims)).isEqualTo("IDIR\\" + GUID_UPPER);
    }

    /**
     * <b>The mixed-case trap.</b> A realm that emits {@code idir_user_guid} lower-cased must
     * still produce one audit string, not a second row for the same person. Whether a token
     * carries the GUID upper- or lower-cased is CSS mapper configuration, so this cannot be
     * left to how the realm happens to be set up today.
     */
    @Test
    void lowercaseGuid_isUpperCasedSoOnePersonIsOneAuditIdentity() {
      Map<String, Object> claims = idirClaims("azureidir");
      claims.remove("idir_username");
      claims.put("idir_user_guid", GUID_LOWER);
      assertThat(JwtPrincipalUtil.getUserId(claims)).isEqualTo("IDIR\\" + GUID_UPPER);
    }

    @Test
    void uppercaseAndLowercaseGuidsResolveToTheSameIdentity() {
      Map<String, Object> upper = idirClaims("azureidir");
      upper.remove("idir_username");
      Map<String, Object> lower = idirClaims("azureidir");
      lower.remove("idir_username");
      lower.put("idir_user_guid", GUID_LOWER);

      assertThat(JwtPrincipalUtil.getUserId(lower))
          .isEqualTo(JwtPrincipalUtil.getUserId(upper));
    }

    /** No username and no GUID is not an identity, and must not become {@code "IDIR\\"}. */
    @Test
    void yieldsEmptyWhenNeitherUsernameNorGuidIsPresent() {
      Map<String, Object> claims = new HashMap<>();
      claims.put("identity_provider", "azureidir");
      assertThat(JwtPrincipalUtil.getUserId(claims)).isEmpty();
    }
  }

  @Nested
  @DisplayName("the IDP username")
  class IdpUsername {

    /**
     * A username is a name, not a number: unlike the GUID it is passed through exactly as the
     * realm issued it.
     */
    @Test
    void isReturnedWithoutAProviderPrefix() {
      assertThat(JwtPrincipalUtil.getIdpUsername(tokenOf(idirClaims("azureidir"))))
          .isEqualTo(USERNAME);
    }

    @Test
    void fallsBackToTheUpperCasedGuidWhenTheUsernameIsAbsent() {
      Map<String, Object> claims = idirClaims("azureidir");
      claims.remove("idir_username");
      claims.put("idir_user_guid", GUID_LOWER);
      assertThat(JwtPrincipalUtil.getIdpUsername(tokenOf(claims))).isEqualTo(GUID_UPPER);
    }

    @Test
    void isEmptyWhenTheTokenCarriesNeither() {
      assertThat(JwtPrincipalUtil.getIdpUsername(tokenOf(Map.of("sub", "x")))).isEmpty();
    }
  }
}
