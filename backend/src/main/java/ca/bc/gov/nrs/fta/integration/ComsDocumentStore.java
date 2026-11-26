package ca.bc.gov.nrs.fta.integration;

import ca.bc.gov.nrs.fta.user.ClientCredentialsTokenSource;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * {@link DocumentStore} over BC Gov's <b>Common Object Management Service</b> (COMS), standing in
 * for the NRS Document Management Service (WebCenter Content behind {@code dms-api}) legacy FTA
 * filed permits in — that service and its WebADE OAuth client are not reachable from this app.
 *
 * <p>{@code PUT {base-url}/object?bucketId=…} with the file as the body and a {@code
 * client_credentials} bearer token. What legacy kept, mapped:
 * <ul>
 *   <li>its per-tenure folder ({@code NRS/Resource Road Authorizations/<forest file id>}, an
 *       engagement folder keyed on the file id) → the configured bucket, with the forest file id
 *       as the object's {@code forest-file-id} metadata;
 *   <li>the document title the user typed → {@code document-title} metadata (URL-encoded, since
 *       metadata travels as headers);
 *   <li>the stored file name — legacy uploaded a temp copy, {@code File.createTempFile(name
 *       before the first ".", the rest)}, so the name carried a random number → the same here,
 *       so re-issuing a permit with the same file name never collides;
 *   <li>its self link (recorded on the permit) → {@code {base-url}/object/{id}}; its viewer URL
 *       (e-mailed: {@code dms-viewer.url} + item id) → {@code fta.documents.coms.viewer-url} +
 *       id, or the self link when no viewer is configured.
 * </ul>
 * Legacy's DMS security metadata (externally visible; client read, staff read/update ACLs;
 * transitory records class; Protected B) has no COMS equivalent here — access is the bucket's.
 *
 * <h3>Configuration</h3>
 * <ul>
 *   <li>{@code fta.documents.coms.base-url} — COMS API root including its version, e.g.
 *       {@code https://coms.api.gov.bc.ca/api/v1}
 *   <li>{@code fta.documents.coms.bucket-id} — the bucket permits are filed in
 *   <li>{@code fta.documents.coms.token-url} / {@code .client-id} / {@code .client-secret} — the
 *       service client COMS accepts
 *   <li>{@code fta.documents.coms.viewer-url} — optional prefix a document's id is appended to
 * </ul>
 * Unset, every upload fails with a message naming the variables.
 */
@Component
public class ComsDocumentStore implements DocumentStore {

  private static final Logger LOG = LoggerFactory.getLogger(ComsDocumentStore.class);

  static final String NOT_CONFIGURED =
      "Document storage is not configured for this environment (set COMS_BASE_URL,"
          + " COMS_BUCKET_ID, COMS_TOKEN_URL, COMS_CLIENT_ID and COMS_CLIENT_SECRET).";

  private static final SecureRandom RANDOM = new SecureRandom();

  private final RestClient http;
  private final ClientCredentialsTokenSource token;
  private final String baseUrl;
  private final String bucketId;
  private final String viewerUrl;
  private final boolean configured;

  @Autowired
  public ComsDocumentStore(
      @Value("${fta.documents.coms.base-url:}") String baseUrl,
      @Value("${fta.documents.coms.bucket-id:}") String bucketId,
      @Value("${fta.documents.coms.token-url:}") String tokenUrl,
      @Value("${fta.documents.coms.client-id:}") String clientId,
      @Value("${fta.documents.coms.client-secret:}") String clientSecret,
      @Value("${fta.documents.coms.viewer-url:}") String viewerUrl,
      @Value("${fta.documents.coms.connect-timeout:5s}") Duration connectTimeout,
      @Value("${fta.documents.coms.read-timeout:60s}") Duration readTimeout) {
    this.token = ClientCredentialsTokenSource.fromProps(
        tokenUrl, clientId, clientSecret, null, connectTimeout, readTimeout, "COMS", "COMS");
    this.baseUrl = trimSlash(baseUrl);
    this.bucketId = bucketId == null ? "" : bucketId.trim();
    this.viewerUrl = viewerUrl == null ? "" : viewerUrl.trim();
    this.configured =
        !this.baseUrl.isEmpty() && !this.bucketId.isEmpty() && token != null;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Math.toIntExact(connectTimeout.toMillis()));
    factory.setReadTimeout(Math.toIntExact(readTimeout.toMillis()));
    this.http = RestClient.builder()
        .baseUrl(this.baseUrl)
        .requestFactory(factory)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build();
    LOG.info(configured
        ? "COMS document store active (base-url={})"
        : "COMS document store inactive (COMS_BASE_URL / COMS_BUCKET_ID / client credentials"
            + " unset) — Issue Permit uploads will fail", this.baseUrl);
  }

  /** Test hook: a pre-built {@link RestClient} bound to {@code baseUrl}, no auth. */
  ComsDocumentStore(RestClient http, String baseUrl, String bucketId, String viewerUrl) {
    this.http = http;
    this.token = null;
    this.baseUrl = trimSlash(baseUrl);
    this.bucketId = bucketId;
    this.viewerUrl = viewerUrl == null ? "" : viewerUrl;
    this.configured = true;
  }

  @Override
  public StoredDocument upload(
      String forestFileId, String documentTitle, String fileName, byte[] content) {
    if (!configured) {
      throw new IntegrationNotConfiguredException(NOT_CONFIGURED);
    }
    String storedName = storedName(fileName);
    ComsObject created;
    try {
      created = http.put()
          .uri(b -> b.path("/object").queryParam("bucketId", bucketId).build())
          .headers(h -> {
            applyAuth(h);
            // A plain name when it is ASCII; RFC 5987 encoding only when it must.
            h.setContentDisposition(
                StandardCharsets.US_ASCII.newEncoder().canEncode(storedName)
                    ? ContentDisposition.attachment().filename(storedName).build()
                    : ContentDisposition.attachment()
                        .filename(storedName, StandardCharsets.UTF_8).build());
            h.set("x-amz-meta-forest-file-id", forestFileId);
            h.set("x-amz-meta-document-title",
                URLEncoder.encode(documentTitle, StandardCharsets.UTF_8));
          })
          .contentType(contentType(fileName))
          .body(content)
          .retrieve()
          .body(ComsObject.class);
    } catch (RestClientResponseException e) {
      throw new IntegrationException(
          "Document storage refused the upload (HTTP " + e.getStatusCode().value() + ").", e);
    } catch (RestClientException e) {
      throw new IntegrationException("Document storage could not be reached.", e);
    }
    if (created == null || !StringUtils.hasText(created.id())) {
      throw new IntegrationException("Document storage did not return the document's id.");
    }
    String id = created.id();
    String link = baseUrl + "/object/" + id;
    String viewer = viewerUrl.isEmpty() ? link : viewerUrl + id;
    LOG.info("Filed {} for {} in COMS as {}", storedName, forestFileId, id);
    return new StoredDocument(id, link, viewer);
  }

  /**
   * Legacy's {@code File.createTempFile(prefix, suffix)} name: the part before the first ".",
   * a random number, then the rest ("permit.pdf" → "permit8127361923.pdf").
   */
  static String storedName(String fileName) {
    String name = fileName == null ? "" : fileName;
    int dot = name.indexOf('.');
    String prefix = dot < 0 ? name : name.substring(0, dot);
    String suffix = dot < 0 ? "" : name.substring(dot);
    return prefix + Long.toUnsignedString(RANDOM.nextLong()) + suffix;
  }

  static MediaType contentType(String fileName) {
    return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")
        ? MediaType.APPLICATION_PDF
        : MediaType.APPLICATION_OCTET_STREAM;
  }

  private void applyAuth(HttpHeaders headers) {
    if (token == null) {
      return;
    }
    String t = token.fetchCached();
    if (t.isBlank()) {
      throw new IntegrationException("Could not get an access token for document storage.");
    }
    headers.setBearerAuth(t);
  }

  private static String trimSlash(String url) {
    String u = url == null ? "" : url.trim();
    return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ComsObject(String id) {}
}
