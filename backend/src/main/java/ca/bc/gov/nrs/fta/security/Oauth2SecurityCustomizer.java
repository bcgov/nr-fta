package ca.bc.gov.nrs.fta.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

/**
 * Configures the OAuth 2.0 Resource Server to validate BC Gov SSO (Keycloak)
 * <strong>access tokens</strong>.
 *
 * <h3>Why there is no {@code token_use} check any more</h3>
 * This customizer used to reject any token whose {@code token_use} claim was not
 * {@code "access"} — a Cognito-specific claim that distinguished ID tokens from access
 * tokens. <b>Keycloak does not emit {@code token_use} at all</b>, so that validator would
 * fail every single request, health checks included, and read like a broken deploy rather
 * than a broken login. It is replaced by the {@code azp} check below, which covers the
 * adjacent and more useful case.
 *
 * <h3>Why {@code azp} is checked</h3>
 * The BC Gov SSO standard realm is shared by many applications. Their clients all issue
 * tokens signed by the same issuer and verifiable against the same JWKS, so
 * <b>signature and issuer validation alone do not establish that a token was meant for
 * FTA</b> — only that the realm minted it.
 *
 * <p>In practice {@code client_roles} already limits the damage, because a token issued to
 * another client carries that client's roles and would not hold {@code FTA_ADMIN}. But that
 * is a property of how CSS happens to populate the claim rather than a control this service
 * enforces, and it is exactly the sort of implicit guarantee that stops holding the moment
 * somebody adds a role mapper. One comparison closes it.
 *
 * <p>The expected client id is configuration, not a constant: it differs per environment, and
 * a deployment pointed at the wrong realm should fail loudly rather than accept whatever that
 * realm signs.
 */
@Component
public class Oauth2SecurityCustomizer implements
    Customizer<OAuth2ResourceServerConfigurer<HttpSecurity>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(Oauth2SecurityCustomizer.class);

  private final String jwkSetUri;
  private final String expectedClientId;
  private final NimbusJwtDecoder jwtDecoder;

  public Oauth2SecurityCustomizer(
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
      @Value("${ca.bc.gov.nrs.keycloak.client-id}") String expectedClientId
  ) {
    this.jwkSetUri = jwkSetUri;
    this.expectedClientId = expectedClientId;
    this.jwtDecoder = buildJwtDecoder(jwkSetUri);

    // ── Validate issuer + the client the token was issued to ─────────
    this.jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(issuerUri),
        this::validateAuthorizedParty
    ));
  }

  /**
   * Refuses a token minted by our realm for somebody else's client.
   *
   * <p>The failure message names neither the expected nor the received client id: the caller
   * holds a valid token for some client and does not need to be told which one this API wants.
   * The mismatch is logged instead, where an operator can see it.
   */
  private OAuth2TokenValidatorResult validateAuthorizedParty(Jwt token) {
    String azp = token.getClaimAsString(TokenRoles.CLAIM_AZP);
    if (expectedClientId.equals(azp)) {
      return OAuth2TokenValidatorResult.success();
    }
    LOGGER.warn("Rejected a token issued to client '{}'; this API accepts only '{}'.",
        azp, expectedClientId);
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error(
            "invalid_token",
            "This token was not issued to this application.",
            null
        )
    );
  }

  @Override
  public void customize(
      OAuth2ResourceServerConfigurer<HttpSecurity> customize) {
    LOGGER.info("Configuring OAuth2 resource server with JWK set URI: {} (client: {})",
        jwkSetUri, expectedClientId);
    customize.jwt(jwt -> jwt.jwtAuthenticationConverter(converter()).decoder(jwtDecoder));
  }

  /**
   * Builds a JWT decoder backed by Nimbus's {@link JWKSourceBuilder}, which provides:
   * <ul>
   *   <li><b>Cached JWKS</b> — fetched once and reused for ~5 min, eliminating per-request
   *       round-trips to the realm.</li>
   *   <li><b>Refresh-ahead caching</b> — re-fetches the JWKS in the background BEFORE it
   *       expires, so user-facing requests never block on a refresh.</li>
   *   <li><b>Retry on transient failures</b> — automatically retries the JWKS fetch when
   *       the endpoint returns an error or times out.</li>
   *   <li><b>Explicit HTTP timeouts</b> — connect and read timeouts on the JWKS fetch.</li>
   * </ul>
   * This replaces the default Spring decoder builder, which uses a no-op Spring cache and
   * causes intermittent {@code Connect timed out} 401s whenever the IdP has a brief hiccup.
   */
  private static NimbusJwtDecoder buildJwtDecoder(String jwkSetUri) {
    URL jwkSetUrl;
    try {
      jwkSetUrl = new URI(jwkSetUri).toURL();
    } catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
      throw new IllegalStateException("Invalid jwk-set-uri: " + jwkSetUri, e);
    }

    DefaultResourceRetriever retriever = new DefaultResourceRetriever(
        (int) Duration.ofSeconds(10).toMillis(),
        (int) Duration.ofSeconds(15).toMillis(),
        50 * 1024
    );

    JWKSource<SecurityContext> jwkSource = JWKSourceBuilder
        .create(jwkSetUrl, retriever)
        .retrying(true)
        .refreshAheadCache(true)
        .build();

    ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
    processor.setJWSKeySelector(
        new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
    return new NimbusJwtDecoder(processor);
  }

  private Converter<Jwt, AbstractAuthenticationToken> converter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    // Roles, including the base role a FAM-scoped grant implies; see TokenRoles.
    converter.setJwtGrantedAuthoritiesConverter(TokenRoles::authoritiesFrom);
    return converter;
  }
}
