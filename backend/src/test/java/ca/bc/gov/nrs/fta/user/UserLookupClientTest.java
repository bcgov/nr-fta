package ca.bc.gov.nrs.fta.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** HTTP-contract tests for {@link UserLookupClient} against nr-user-lookup-api. */
class UserLookupClientTest {

  private MockRestServiceServer server;
  private UserLookupClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://lookup.test");
    server = MockRestServiceServer.bindTo(builder).build();
    client = new UserLookupClient(builder.build());
  }

  @Test
  void getIdirDetailFoundTrueMapsToUser() {
    server.expect(requestTo(Matchers.startsWith(
            "http://lookup.test/api/v1/user-lookup/idir-account-detail")))
        .andExpect(method(HttpMethod.GET))
        .andExpect(queryParam("userId", "JSMITH"))
        .andRespond(withSuccess(
            "{\"found\":true,\"userId\":\"JSMITH\",\"guid\":\"g1\",\"firstName\":\"Jane\","
                + "\"lastName\":\"Smith\",\"email\":\"jane@gov.bc.ca\"}",
            MediaType.APPLICATION_JSON));

    assertThat(client.getIdirDetail("JSMITH"))
        .hasValueSatisfying(u -> {
          assertThat(u.firstName()).isEqualTo("Jane");
          assertThat(u.lastName()).isEqualTo("Smith");
        });
    server.verify();
  }

  @Test
  void getIdirDetailFoundFalseIsEmpty() {
    server.expect(requestTo(Matchers.startsWith(
            "http://lookup.test/api/v1/user-lookup/idir-account-detail")))
        .andRespond(withSuccess("{\"found\":false}", MediaType.APPLICATION_JSON));

    assertThat(client.getIdirDetail("NOBODY")).isEmpty();
    server.verify();
  }

  @Test
  void getIdirDetailBlankUserIdSkipsTheCall() {
    assertThat(client.getIdirDetail("  ")).isEmpty();
    server.verify();
  }

  @Test
  void withoutABaseUrlNothingIsCalled() {
    UserLookupClient unconfigured = new UserLookupClient(
        "", "", "", "", "", Duration.ofSeconds(1), Duration.ofSeconds(1));
    assertThat(unconfigured.isConfigured()).isFalse();
    assertThat(unconfigured.getIdirDetail("JSMITH")).isEmpty();
  }
}
