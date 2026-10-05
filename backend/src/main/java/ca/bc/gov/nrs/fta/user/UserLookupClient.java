package ca.bc.gov.nrs.fta.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Client for <b>nr-user-lookup-api</b> — the shared BC Gov service that resolves IDIR
 * identities. FTA uses it to show the people behind the {@code IDIR\USERNAME} values its
 * audit columns hold. Copied from REPT's client, keeping only the exact lookup FTA needs.
 *
 * <p>Base path {@code /api/v1/user-lookup}. Every call carries a Keycloak
 * {@code client_credentials} bearer token minted from FTA's own service account (see
 * {@link ClientCredentialsTokenSource}); the caller's token is <b>not</b> forwarded —
 * nr-user-lookup-api validates the service account's default client scopes, not an end-user
 * token.
 *
 * <h3>Configuration</h3>
 * <ul>
 *   <li>{@code fta.user-lookup.base-url} — scheme + host of the API</li>
 *   <li>{@code fta.user-lookup.token-url} — Keycloak token endpoint</li>
 *   <li>{@code fta.user-lookup.client-id} / {@code .client-secret} — FTA's confidential
 *       service-account credentials</li>
 *   <li>{@code fta.user-lookup.scope} — optional; the scopes are DEFAULT client scopes on the
 *       service account, so normally left blank</li>
 * </ul>
 */
@Component
public class UserLookupClient {

  private static final Logger LOG = LoggerFactory.getLogger(UserLookupClient.class);

  private static final String IDIR_DETAIL_PATH = "/api/v1/user-lookup/idir-account-detail";

  private final RestClient http;
  private final ClientCredentialsTokenSource clientCredentials;
  private final boolean configured;

  @Autowired
  public UserLookupClient(
      @Value("${fta.user-lookup.base-url:}") String baseUrl,
      @Value("${fta.user-lookup.token-url:}") String tokenUrl,
      @Value("${fta.user-lookup.client-id:}") String clientId,
      @Value("${fta.user-lookup.client-secret:}") String clientSecret,
      @Value("${fta.user-lookup.scope:}") String scope,
      @Value("${fta.user-lookup.connect-timeout:5s}") Duration connectTimeout,
      @Value("${fta.user-lookup.read-timeout:10s}") Duration readTimeout) {
    this.clientCredentials = ClientCredentialsTokenSource.fromProps(
        tokenUrl, clientId, clientSecret, scope, connectTimeout, readTimeout);
    this.configured = StringUtils.hasText(baseUrl);

    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Math.toIntExact(connectTimeout.toMillis()));
    factory.setReadTimeout(Math.toIntExact(readTimeout.toMillis()));
    this.http = RestClient.builder()
        .baseUrl(baseUrl == null ? "" : baseUrl)
        .requestFactory(factory)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build();

    if (!configured) {
      LOG.info("user-lookup client inactive (USER_LOOKUP_BASE_URL unset) — user names show as"
          + " their IDIR ids");
    } else if (this.clientCredentials != null) {
      LOG.info("user-lookup client active (OAuth2 client_credentials — base-url={}, token-url={},"
              + " client-id={})",
          baseUrl, this.clientCredentials.tokenUrl(), this.clientCredentials.clientId());
    } else {
      LOG.info("user-lookup client active (no client_credentials auth configured — calls will be"
          + " unauthenticated; set USER_LOOKUP_TOKEN_URL/CLIENT_ID/CLIENT_SECRET to enable)");
    }
  }

  /** Test hook: a pre-built {@link RestClient} (e.g. bound to a MockRestServiceServer), no auth. */
  UserLookupClient(RestClient http) {
    this.http = http;
    this.clientCredentials = null;
    this.configured = true;
  }

  /** Whether a base URL is set; without one every lookup is skipped. */
  public boolean isConfigured() {
    return configured;
  }

  /**
   * Exact IDIR match by bare user id (no {@code IDIR\} prefix) via
   * {@code GET /idir-account-detail}. Empty when the API reports {@code found=false}.
   */
  public Optional<IdirUser> getIdirDetail(String userId) {
    if (!configured || !StringUtils.hasText(userId)) {
      return Optional.empty();
    }
    String user = userId.trim();
    IdirUserResponse resp = http.get()
        .uri(uriBuilder -> {
          URI uri = uriBuilder.path(IDIR_DETAIL_PATH).queryParam("userId", user).build();
          LOG.debug("user-lookup idir detail: {}", uri);
          return uri;
        })
        .headers(this::applyAuth)
        .retrieve()
        .body(IdirUserResponse.class);

    if (resp == null || !resp.found()) {
      return Optional.empty();
    }
    return Optional.of(new IdirUser(
        resp.userId(), resp.guid(), resp.firstName(), resp.lastName(), resp.email()));
  }

  private void applyAuth(HttpHeaders headers) {
    if (clientCredentials == null) {
      return;
    }
    String token = clientCredentials.fetchCached();
    if (!token.isBlank()) {
      headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
  }

  /** An IDIR user as returned by account-detail. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record IdirUser(
      String userId, String guid, String firstName, String lastName, String email) {}

  /** Wire shape of {@code GET /idir-account-detail}. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record IdirUserResponse(
      boolean found, String userId, String guid, String firstName, String lastName,
      String email) {}
}
