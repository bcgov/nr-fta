package ca.bc.gov.nrs.fta.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import ca.bc.gov.nrs.fta.integration.EmailSender.Attachment;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** HTTP contract of {@link ChesEmailSender} against CHES {@code POST /email}. */
class ChesEmailSenderTest {

  @Test
  void postsOneHtmlMessageToAllRecipientsWithBase64Attachments() {
    RestClient.Builder b = RestClient.builder().baseUrl("http://ches.test/api/v1");
    MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
    ChesEmailSender sender = new ChesEmailSender(b.build(), "fta@gov.bc.ca");

    server.expect(requestTo("http://ches.test/api/v1/email"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.from").value("fta@gov.bc.ca"))
        .andExpect(jsonPath("$.to[0]").value("a@gov.bc.ca"))
        .andExpect(jsonPath("$.to[1]").value("b@gov.bc.ca"))
        .andExpect(jsonPath("$.subject").value("Forest Permit B07 A12345"))
        .andExpect(jsonPath("$.bodyType").value("html"))
        .andExpect(jsonPath("$.body").value("hello <br><br> link"))
        .andExpect(jsonPath("$.attachments[0].filename").value("permit.pdf"))
        .andExpect(jsonPath("$.attachments[0].contentType").value("application/pdf"))
        .andExpect(jsonPath("$.attachments[0].encoding").value("base64"))
        .andExpect(jsonPath("$.attachments[0].content").value("JVBERg=="))
        .andRespond(withStatus(HttpStatus.CREATED)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"txId\":\"t1\",\"messages\":[]}"));

    sender.send("Forest Permit B07 A12345", "hello <br><br> link",
        List.of("a@gov.bc.ca", "b@gov.bc.ca"),
        List.of(new Attachment("permit.pdf", "application/pdf", "%PDF".getBytes())));
    server.verify();
  }

  @Test
  void aRefusalSaysSo() {
    RestClient.Builder b = RestClient.builder().baseUrl("http://ches.test/api/v1");
    MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
    ChesEmailSender sender = new ChesEmailSender(b.build(), "fta@gov.bc.ca");
    server.expect(requestTo("http://ches.test/api/v1/email")).andRespond(withBadRequest());

    assertThatThrownBy(() -> sender.send("s", "b", List.of("a@gov.bc.ca"), List.of()))
        .isInstanceOf(IntegrationException.class)
        .hasMessage("The e-mail service refused the message (HTTP 400).");
  }

  @Test
  void unconfiguredFailsNamingTheVariables() {
    ChesEmailSender sender = new ChesEmailSender(
        "", "", "", "", "fta@gov.bc.ca", Duration.ofSeconds(1), Duration.ofSeconds(1));
    assertThatThrownBy(() -> sender.send("s", "b", List.of("a@gov.bc.ca"), List.of()))
        .isInstanceOf(IntegrationNotConfiguredException.class)
        .hasMessageContaining("CHES_BASE_URL");
  }

  @Test
  void aBaseUrlWithoutCredentialsIsNotConfigured() {
    ChesEmailSender sender = new ChesEmailSender(
        "http://ches.test/api/v1", "", "", "", "fta@gov.bc.ca", Duration.ofSeconds(1),
        Duration.ofSeconds(1));
    assertThatThrownBy(() -> sender.send("s", "b", List.of("a@gov.bc.ca"), List.of()))
        .isInstanceOf(IntegrationNotConfiguredException.class);
  }

  @Test
  void partialCredentialsFailAtStartup() {
    assertThatThrownBy(() -> new ChesEmailSender(
            "http://ches.test/api/v1", "http://kc/token", "", "", "fta@gov.bc.ca",
            Duration.ofSeconds(1), Duration.ofSeconds(1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CHES_TOKEN_URL, CHES_CLIENT_ID, CHES_CLIENT_SECRET");
    assertThat(ChesEmailSender.NOT_CONFIGURED).endsWith(".");
  }
}
