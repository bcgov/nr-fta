package ca.bc.gov.nrs.fta.integration;

import ca.bc.gov.nrs.fta.user.ClientCredentialsTokenSource;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * {@link EmailSender} over BC Gov's <b>Common Hosted Email Service</b> (CHES), the successor of
 * the NRS Common Messaging Service (CMSG) legacy FTA sent through.
 *
 * <p>{@code POST {base-url}/email} with a {@code client_credentials} bearer token from the CHES
 * Keycloak realm (see {@link ClientCredentialsTokenSource}). One message to all recipients,
 * HTML body, attachments base64-encoded — as legacy's CMSG {@code EmailMessageResource}.
 *
 * <h3>Configuration</h3>
 * <ul>
 *   <li>{@code fta.email.ches.base-url} — CHES API root including its version, e.g.
 *       {@code https://ches.api.gov.bc.ca/api/v1}
 *   <li>{@code fta.email.ches.token-url} / {@code .client-id} / {@code .client-secret} — FTA's
 *       CHES service client
 *   <li>{@code fta.email.from} — the sender (legacy: {@code fta@gov.bc.ca})
 * </ul>
 * Unset, every send fails with a message naming the variables.
 */
@Component
public class ChesEmailSender implements EmailSender {

  private static final Logger LOG = LoggerFactory.getLogger(ChesEmailSender.class);

  static final String NOT_CONFIGURED =
      "E-mail is not configured for this environment (set CHES_BASE_URL, CHES_TOKEN_URL,"
          + " CHES_CLIENT_ID and CHES_CLIENT_SECRET).";

  private final RestClient http;
  private final ClientCredentialsTokenSource token;
  private final String from;
  private final boolean configured;

  @Autowired
  public ChesEmailSender(
      @Value("${fta.email.ches.base-url:}") String baseUrl,
      @Value("${fta.email.ches.token-url:}") String tokenUrl,
      @Value("${fta.email.ches.client-id:}") String clientId,
      @Value("${fta.email.ches.client-secret:}") String clientSecret,
      @Value("${fta.email.from:fta@gov.bc.ca}") String from,
      @Value("${fta.email.ches.connect-timeout:5s}") Duration connectTimeout,
      @Value("${fta.email.ches.read-timeout:30s}") Duration readTimeout) {
    this.token = ClientCredentialsTokenSource.fromProps(
        tokenUrl, clientId, clientSecret, null, connectTimeout, readTimeout, "CHES", "CHES");
    this.configured = StringUtils.hasText(baseUrl) && token != null;
    this.from = from;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Math.toIntExact(connectTimeout.toMillis()));
    factory.setReadTimeout(Math.toIntExact(readTimeout.toMillis()));
    this.http = RestClient.builder()
        .baseUrl(baseUrl == null ? "" : baseUrl.trim())
        .requestFactory(factory)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build();
    LOG.info(configured
        ? "CHES e-mail active (base-url={})"
        : "CHES e-mail inactive (CHES_BASE_URL / client credentials unset) — Issue Permit"
            + " e-mails will fail", baseUrl);
  }

  /** Test hook: a pre-built {@link RestClient} (e.g. bound to MockRestServiceServer), no auth. */
  ChesEmailSender(RestClient http, String from) {
    this.http = http;
    this.token = null;
    this.from = from;
    this.configured = true;
  }

  @Override
  public void send(
      String subject, String htmlBody, List<String> recipients, List<Attachment> attachments) {
    if (!configured) {
      throw new IntegrationNotConfiguredException(NOT_CONFIGURED);
    }
    List<Map<String, String>> files = attachments == null
        ? List.of()
        : attachments.stream()
            .map(a -> Map.of(
                "content", Base64.getEncoder().encodeToString(a.content()),
                "contentType", a.contentType(),
                "encoding", "base64",
                "filename", a.fileName()))
            .toList();
    Map<String, Object> body = Map.of(
        "from", from,
        "to", recipients,
        "subject", subject,
        "bodyType", "html",
        "body", htmlBody,
        "attachments", files);
    try {
      ChesResponse resp = http.post()
          .uri("/email")
          .headers(this::applyAuth)
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(ChesResponse.class);
      LOG.info("CHES accepted e-mail \"{}\" to {} recipient(s) (txId={})",
          subject, recipients.size(), resp == null ? null : resp.txId());
    } catch (RestClientResponseException e) {
      throw new IntegrationException(
          "The e-mail service refused the message (HTTP " + e.getStatusCode().value() + ").", e);
    } catch (RestClientException e) {
      throw new IntegrationException("The e-mail service could not be reached.", e);
    }
  }

  private void applyAuth(HttpHeaders headers) {
    if (token == null) {
      return;
    }
    String t = token.fetchCached();
    if (t.isBlank()) {
      throw new IntegrationException("Could not get an access token for the e-mail service.");
    }
    headers.setBearerAuth(t);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ChesResponse(String txId) {}
}
