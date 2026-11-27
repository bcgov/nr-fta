package ca.bc.gov.nrs.fta.integration;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * {@link DocumentStore} over an S3-compatible bucket (e.g. BC Gov object storage) through the AWS
 * SDK, standing in for the NRS Document Management Service legacy FTA filed permits in — that
 * service and its WebADE OAuth client are not reachable from this app.
 *
 * <p>What legacy kept, mapped:
 * <ul>
 *   <li>its per-tenure folder ({@code NRS/Resource Road Authorizations/<forest file id>}) → the
 *       key prefix: objects are filed as {@code <forest file id>/<stored name>}, with the forest
 *       file id also as the object's {@code forest-file-id} user metadata;
 *   <li>the document title the user typed → {@code document-title} user metadata (URL-encoded,
 *       since metadata travels as US-ASCII headers);
 *   <li>the stored file name — legacy uploaded a temp copy, {@code File.createTempFile(name
 *       before the first ".", the rest)}, so the name carried a random number → the same here,
 *       so re-issuing a permit with the same file name never overwrites the earlier one;
 *   <li>its self link (recorded on the permit, {@code CUTTING_PERMIT_DOCUMENT_URI}) → the
 *       object's stable address {@code {endpoint}/{bucket}/{key}} (path-style, key URL-encoded);
 *   <li>its viewer URL (e-mailed) → a presigned GET URL, since the bucket's objects are private.
 *       It expires after {@code fta.documents.s3.link-expiry} (SigV4 allows at most 7 days).
 * </ul>
 * Legacy's DMS security metadata (externally visible; client read, staff read/update ACLs;
 * transitory records class; Protected B) has no equivalent here — access is the bucket's.
 *
 * <h3>Configuration</h3>
 * <ul>
 *   <li>{@code fta.documents.s3.endpoint} ({@code S3_ENDPOINT}) — the S3-compatible endpoint
 *   <li>{@code fta.documents.s3.bucket} ({@code S3_BUCKET})
 *   <li>{@code fta.documents.s3.access-key-id} / {@code .secret-access-key}
 *       ({@code S3_ACCESS_KEY_ID} / {@code S3_SECRET_ACCESS_KEY})
 *   <li>{@code fta.documents.s3.region} ({@code S3_REGION}, default {@code ca-central-1})
 *   <li>{@code fta.documents.s3.link-expiry} ({@code S3_LINK_EXPIRY}, ISO-8601, default
 *       {@code P7D})
 * </ul>
 * Unset, every upload fails with a message naming the variables.
 */
@Component
public class S3DocumentStore implements DocumentStore {

  private static final Logger LOG = LoggerFactory.getLogger(S3DocumentStore.class);

  static final String NOT_CONFIGURED =
      "Document storage is not configured for this environment (set S3_ENDPOINT, S3_BUCKET,"
          + " S3_ACCESS_KEY_ID and S3_SECRET_ACCESS_KEY).";

  /** SigV4's longest presigned URL lifetime. */
  static final Duration MAX_LINK_EXPIRY = Duration.ofDays(7);

  private static final SecureRandom RANDOM = new SecureRandom();

  private final S3Client s3;
  private final S3Presigner presigner;
  private final String endpoint;
  private final String bucket;
  private final Duration linkExpiry;

  @Autowired
  public S3DocumentStore(
      @Value("${fta.documents.s3.endpoint:}") String endpoint,
      @Value("${fta.documents.s3.bucket:}") String bucket,
      @Value("${fta.documents.s3.access-key-id:}") String accessKeyId,
      @Value("${fta.documents.s3.secret-access-key:}") String secretAccessKey,
      @Value("${fta.documents.s3.region:ca-central-1}") String region,
      @Value("${fta.documents.s3.link-expiry:P7D}") Duration linkExpiry,
      @Value("${fta.documents.s3.connect-timeout:5s}") Duration connectTimeout,
      @Value("${fta.documents.s3.read-timeout:60s}") Duration readTimeout) {
    this.endpoint = trimSlash(endpoint);
    this.bucket = trim(bucket);
    this.linkExpiry = checkedExpiry(linkExpiry);
    List<String> missing = missing(Map.of(
        "S3_ENDPOINT", this.endpoint,
        "S3_BUCKET", this.bucket,
        "S3_ACCESS_KEY_ID", trim(accessKeyId),
        "S3_SECRET_ACCESS_KEY", trim(secretAccessKey)));
    if (!missing.isEmpty()) {
      this.s3 = null;
      this.presigner = null;
      if (missing.size() < 4) {
        LOG.warn("S3 document store partly configured; missing {}", missing);
      }
      LOG.info("S3 document store inactive (S3_ENDPOINT / S3_BUCKET / S3 credentials unset) —"
          + " Issue Permit uploads will fail");
      return;
    }
    URI uri = URI.create(this.endpoint);
    Region r = Region.of(trim(region).isEmpty() ? "ca-central-1" : trim(region));
    StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
        AwsBasicCredentials.create(trim(accessKeyId), trim(secretAccessKey)));
    this.s3 = S3Client.builder()
        .endpointOverride(uri)
        .region(r)
        .credentialsProvider(credentials)
        .forcePathStyle(true)
        // S3-compatible stores do not all accept the SDK's default flexible checksums.
        .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
        .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
        .httpClientBuilder(ApacheHttpClient.builder()
            .connectionTimeout(connectTimeout)
            .socketTimeout(readTimeout))
        .build();
    this.presigner = S3Presigner.builder()
        .endpointOverride(uri)
        .region(r)
        .credentialsProvider(credentials)
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build();
    LOG.info("S3 document store active (endpoint={}, bucket={}, link expiry {})",
        this.endpoint, this.bucket, this.linkExpiry);
  }

  /** Test hook: pre-built (mock) clients. */
  S3DocumentStore(
      S3Client s3, S3Presigner presigner, String endpoint, String bucket, Duration linkExpiry) {
    this.s3 = s3;
    this.presigner = presigner;
    this.endpoint = trimSlash(endpoint);
    this.bucket = bucket;
    this.linkExpiry = checkedExpiry(linkExpiry);
  }

  @Override
  public StoredDocument upload(
      String forestFileId, String documentTitle, String fileName, byte[] content) {
    if (s3 == null || presigner == null) {
      throw new IntegrationNotConfiguredException(NOT_CONFIGURED);
    }
    String key = key(forestFileId, storedName(fileName));
    String viewerUrl;
    try {
      s3.putObject(PutObjectRequest.builder()
              .bucket(bucket)
              .key(key)
              .contentType(contentType(fileName))
              .contentDisposition(contentDisposition(fileName))
              .metadata(Map.of(
                  "forest-file-id", forestFileId,
                  "document-title", URLEncoder.encode(
                      documentTitle == null ? "" : documentTitle, StandardCharsets.UTF_8)))
              .build(),
          RequestBody.fromBytes(content));
      viewerUrl = presigner.presignGetObject(GetObjectPresignRequest.builder()
              .signatureDuration(linkExpiry)
              .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
              .build())
          .url()
          .toString();
    } catch (S3Exception e) {
      throw new IntegrationException(
          "Document storage refused the upload (HTTP " + e.statusCode() + ").", e);
    } catch (SdkException e) {
      throw new IntegrationException("Document storage could not be reached.", e);
    }
    LOG.info("Filed {} in bucket {}", key, bucket);
    return new StoredDocument(key, link(key), viewerUrl);
  }

  @PreDestroy
  void close() {
    if (s3 != null) {
      s3.close();
    }
    if (presigner != null) {
      presigner.close();
    }
  }

  /** {@code <forest file id>/<stored name>} — legacy's folder per tenure. */
  static String key(String forestFileId, String storedName) {
    return forestFileId.trim() + "/" + storedName;
  }

  /** The object's stable path-style address, each key segment URL-encoded. */
  String link(String key) {
    String path = Arrays.stream(key.split("/", -1))
        .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"))
        .collect(Collectors.joining("/"));
    return endpoint + "/" + URLEncoder.encode(bucket, StandardCharsets.UTF_8) + "/" + path;
  }

  /**
   * Legacy's {@code File.createTempFile(prefix, suffix)} name: the part before the first ".",
   * a random number, then the rest ("permit.pdf" → "permit8127361923.pdf"). A path separator in
   * the name is replaced so the object stays in its tenure's folder.
   */
  static String storedName(String fileName) {
    String name = fileName == null ? "" : fileName.replace('/', '_').replace('\\', '_');
    int dot = name.indexOf('.');
    String prefix = dot < 0 ? name : name.substring(0, dot);
    String suffix = dot < 0 ? "" : name.substring(dot);
    return prefix + Long.toUnsignedString(RANDOM.nextLong()) + suffix;
  }

  static String contentType(String fileName) {
    return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")
        ? "application/pdf"
        : "application/octet-stream";
  }

  /** Opens in the browser under the uploaded file's own name. */
  static String contentDisposition(String fileName) {
    String name = fileName == null ? "" : fileName;
    return (StandardCharsets.US_ASCII.newEncoder().canEncode(name)
        ? ContentDisposition.inline().filename(name).build()
        : ContentDisposition.inline().filename(name, StandardCharsets.UTF_8).build())
        .toString();
  }

  private static Duration checkedExpiry(Duration expiry) {
    if (expiry == null || expiry.isZero() || expiry.isNegative()
        || expiry.compareTo(MAX_LINK_EXPIRY) > 0) {
      throw new IllegalStateException(
          "S3_LINK_EXPIRY must be a positive ISO-8601 duration of at most P7D (was " + expiry
              + ").");
    }
    return expiry;
  }

  private static List<String> missing(Map<String, String> values) {
    List<String> out = new ArrayList<>();
    for (String name : List.of(
        "S3_ENDPOINT", "S3_BUCKET", "S3_ACCESS_KEY_ID", "S3_SECRET_ACCESS_KEY")) {
      if (values.get(name).isEmpty()) {
        out.add(name);
      }
    }
    return out;
  }

  private static String trim(String s) {
    return s == null ? "" : s.trim();
  }

  private static String trimSlash(String url) {
    String u = trim(url);
    return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
  }
}
