package ca.bc.gov.nrs.fta.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.integration.DocumentStore.StoredDocument;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/** What {@link S3DocumentStore} asks of the (mocked) S3 client and presigner. */
class S3DocumentStoreTest {

  private static final String ENDPOINT = "https://s3.example.com";
  private static final byte[] PDF = "%PDF".getBytes(StandardCharsets.US_ASCII);
  private static final String PRESIGNED = ENDPOINT + "/fta-docs/A12345/x?X-Amz-Signature=abc";

  private S3Client s3;
  private S3Presigner presigner;
  private S3DocumentStore store;

  @BeforeEach
  void setUp() throws Exception {
    s3 = mock(S3Client.class);
    presigner = mock(S3Presigner.class);
    when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());
    PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
    when(presigned.url()).thenReturn(URI.create(PRESIGNED).toURL());
    when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);
    store = new S3DocumentStore(s3, presigner, ENDPOINT + "/", "fta-docs", Duration.ofDays(3));
  }

  @Test
  void filesThePdfUnderItsTenureAndEmailsAPresignedLink() throws Exception {
    StoredDocument doc = store.upload("A12345", "CP 12 permit é", "permit.pdf", PDF);

    ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
    ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
    verify(s3).putObject(put.capture(), body.capture());
    PutObjectRequest req = put.getValue();
    assertThat(req.bucket()).isEqualTo("fta-docs");
    assertThat(req.key()).matches("A12345/permit\\d+\\.pdf");
    assertThat(req.contentType()).isEqualTo("application/pdf");
    assertThat(req.contentDisposition()).isEqualTo("inline; filename=\"permit.pdf\"");
    assertThat(req.metadata())
        .containsEntry("forest-file-id", "A12345")
        .containsEntry("document-title", "CP+12+permit+%C3%A9");
    try (var in = body.getValue().contentStreamProvider().newStream()) {
      assertThat(in.readAllBytes()).isEqualTo(PDF);
    }

    ArgumentCaptor<GetObjectPresignRequest> presign =
        ArgumentCaptor.forClass(GetObjectPresignRequest.class);
    verify(presigner).presignGetObject(presign.capture());
    assertThat(presign.getValue().signatureDuration()).isEqualTo(Duration.ofDays(3));
    assertThat(presign.getValue().getObjectRequest().bucket()).isEqualTo("fta-docs");
    assertThat(presign.getValue().getObjectRequest().key()).isEqualTo(req.key());

    assertThat(doc.id()).isEqualTo(req.key());
    assertThat(doc.link()).isEqualTo(ENDPOINT + "/fta-docs/" + req.key());
    assertThat(doc.viewerUrl()).isEqualTo(PRESIGNED);
  }

  @Test
  void theStoredLinkEncodesTheKeyButKeepsItsFolders() {
    assertThat(store.link("A12345/cp 12#é.pdf"))
        .isEqualTo(ENDPOINT + "/fta-docs/A12345/cp%2012%23%C3%A9.pdf");
  }

  @Test
  void aRefusedUploadIsAnIntegrationException() {
    when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenThrow(S3Exception.builder().statusCode(403).message("denied").build());

    assertThatThrownBy(() -> store.upload("A12345", "t", "permit.pdf", PDF))
        .isExactlyInstanceOf(IntegrationException.class)
        .hasMessageContaining("HTTP 403");
  }

  @Test
  void anUnreachableStoreIsAnIntegrationException() {
    when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenThrow(SdkClientException.create("connect timed out"));

    assertThatThrownBy(() -> store.upload("A12345", "t", "permit.pdf", PDF))
        .isExactlyInstanceOf(IntegrationException.class)
        .hasMessageContaining("could not be reached");
  }

  @Test
  void unsetIsNotConfigured() {
    S3DocumentStore unset = new S3DocumentStore(
        "", "", "", "", "ca-central-1", Duration.ofDays(7), Duration.ofSeconds(1),
        Duration.ofSeconds(1));

    assertThatThrownBy(() -> unset.upload("A12345", "t", "permit.pdf", PDF))
        .isInstanceOf(IntegrationNotConfiguredException.class)
        .hasMessageContaining("S3_ENDPOINT")
        .hasMessageContaining("S3_BUCKET");
  }

  @Test
  void partlySetIsNotConfiguredEither() {
    S3DocumentStore partial = new S3DocumentStore(
        ENDPOINT, "fta-docs", "", "", "ca-central-1", Duration.ofDays(7), Duration.ofSeconds(1),
        Duration.ofSeconds(1));

    assertThatThrownBy(() -> partial.upload("A12345", "t", "permit.pdf", PDF))
        .isInstanceOf(IntegrationNotConfiguredException.class);
  }

  @Test
  void configuredBuildsRealClientsWithoutCallingOut() {
    S3DocumentStore real = new S3DocumentStore(
        ENDPOINT, "fta-docs", "key-id", "not-a-secret", " ", Duration.ofDays(7),
        Duration.ofSeconds(1), Duration.ofSeconds(1));
    try {
      assertThat(real.link("A1/p.pdf")).isEqualTo(ENDPOINT + "/fta-docs/A1/p.pdf");
    } finally {
      real.close();
    }
  }

  @Test
  void theLinkExpiryIsCappedAtSigV4sSevenDays() {
    assertThatThrownBy(() -> new S3DocumentStore(s3, presigner, ENDPOINT, "b", Duration.ofDays(8)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("S3_LINK_EXPIRY");
    assertThatThrownBy(() -> new S3DocumentStore(s3, presigner, ENDPOINT, "b", Duration.ZERO))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void storedNamesFollowLegacysTempFileNaming() {
    assertThat(S3DocumentStore.storedName("permit.pdf")).matches("permit\\d+\\.pdf");
    assertThat(S3DocumentStore.storedName("cp.12.final.pdf")).matches("cp\\d+\\.12\\.final\\.pdf");
    assertThat(S3DocumentStore.storedName("permit")).matches("permit\\d+");
    assertThat(S3DocumentStore.storedName("../x/permit.pdf")).doesNotContain("/");
    assertThat(S3DocumentStore.storedName("permit.pdf"))
        .isNotEqualTo(S3DocumentStore.storedName("permit.pdf"));
    assertThat(S3DocumentStore.contentType("P.PDF")).isEqualTo("application/pdf");
    assertThat(S3DocumentStore.contentType("p.docx")).isEqualTo("application/octet-stream");
  }
}
