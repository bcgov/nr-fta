package ca.bc.gov.nrs.fta.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ca.bc.gov.nrs.fta.integration.DocumentStore.StoredDocument;
import java.time.Duration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** HTTP contract of {@link ComsDocumentStore} against COMS {@code PUT /object}. */
class ComsDocumentStoreTest {

  private static final String BASE = "http://coms.test/api/v1";
  private static final byte[] PDF = "%PDF".getBytes();

  @Test
  void putsTheFileInTheBucketWithTheTenureAndTitle() {
    RestClient.Builder b = RestClient.builder().baseUrl(BASE);
    MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
    ComsDocumentStore store = new ComsDocumentStore(b.build(), BASE, "bkt-1", "");

    server.expect(requestTo(Matchers.startsWith(BASE + "/object")))
        .andExpect(method(HttpMethod.PUT))
        .andExpect(queryParam("bucketId", "bkt-1"))
        .andExpect(header("Content-Type", "application/pdf"))
        .andExpect(header("Content-Disposition",
            Matchers.matchesPattern("attachment; filename=\"permit\\d+\\.pdf\".*")))
        .andExpect(header("x-amz-meta-forest-file-id", "A12345"))
        .andExpect(header("x-amz-meta-document-title", "CP+12+permit+%C3%A9"))
        .andExpect(content().bytes(PDF))
        .andRespond(withSuccess("{\"id\":\"obj-1\",\"path\":\"fta/permit1.pdf\"}",
            MediaType.APPLICATION_JSON));

    StoredDocument doc = store.upload("A12345", "CP 12 permit é", "permit.pdf", PDF);
    server.verify();
    assertThat(doc.id()).isEqualTo("obj-1");
    assertThat(doc.link()).isEqualTo(BASE + "/object/obj-1");
    // No viewer configured: the e-mail links to the object itself.
    assertThat(doc.viewerUrl()).isEqualTo(BASE + "/object/obj-1");
  }

  @Test
  void aConfiguredViewerPrefixesTheId() {
    RestClient.Builder b = RestClient.builder().baseUrl(BASE);
    MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
    ComsDocumentStore store =
        new ComsDocumentStore(b.build(), BASE + "/", "bkt-1", "https://viewer.test/docs/");
    server.expect(requestTo(Matchers.startsWith(BASE + "/object")))
        .andRespond(withSuccess("{\"id\":\"obj-2\"}", MediaType.APPLICATION_JSON));

    StoredDocument doc = store.upload("A12345", "t", "permit.pdf", PDF);
    assertThat(doc.link()).isEqualTo(BASE + "/object/obj-2");
    assertThat(doc.viewerUrl()).isEqualTo("https://viewer.test/docs/obj-2");
  }

  @Test
  void aRefusalSaysSo() {
    RestClient.Builder b = RestClient.builder().baseUrl(BASE);
    MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
    ComsDocumentStore store = new ComsDocumentStore(b.build(), BASE, "bkt-1", "");
    server.expect(requestTo(Matchers.startsWith(BASE + "/object")))
        .andRespond(withStatus(HttpStatus.FORBIDDEN));

    assertThatThrownBy(() -> store.upload("A12345", "t", "permit.pdf", PDF))
        .isInstanceOf(IntegrationException.class)
        .hasMessage("Document storage refused the upload (HTTP 403).");
  }

  @Test
  void noIdIsAFailure() {
    RestClient.Builder b = RestClient.builder().baseUrl(BASE);
    MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
    ComsDocumentStore store = new ComsDocumentStore(b.build(), BASE, "bkt-1", "");
    server.expect(requestTo(Matchers.startsWith(BASE + "/object")))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> store.upload("A12345", "t", "permit.pdf", PDF))
        .isInstanceOf(IntegrationException.class);
  }

  @Test
  void unconfiguredFailsNamingTheVariables() {
    ComsDocumentStore store = new ComsDocumentStore(
        BASE, "", "", "", "", "", Duration.ofSeconds(1), Duration.ofSeconds(1));
    assertThatThrownBy(() -> store.upload("A12345", "t", "permit.pdf", PDF))
        .isInstanceOf(IntegrationNotConfiguredException.class)
        .hasMessageContaining("COMS_BUCKET_ID");
  }

  @Test
  void storedNamesAreLegacysTempFileNames() {
    assertThat(ComsDocumentStore.storedName("permit.pdf")).matches("permit\\d+\\.pdf");
    assertThat(ComsDocumentStore.storedName("cp.12.final.pdf")).matches("cp\\d+\\.12\\.final\\.pdf");
    assertThat(ComsDocumentStore.storedName("permit")).matches("permit\\d+");
    assertThat(ComsDocumentStore.storedName("permit.pdf"))
        .isNotEqualTo(ComsDocumentStore.storedName("permit.pdf"));
    assertThat(ComsDocumentStore.contentType("P.PDF")).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(ComsDocumentStore.contentType("p.docx"))
        .isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
  }
}
