package ca.bc.gov.nrs.fta.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Pins who reaches the apportionment (APT) endpoints. A 403 is the filter chain refusing; a 404
 * means the request got past it, as no controller is loaded here. Legacy APT2 had two roles:
 * APT_VIEWER (every read and report), now FTA_APT_VIEWER, and APT_EDITOR (every write), now
 * FTA_APT_ADMIN. Both reach APT and nothing else in FTA.
 */
@SpringJUnitWebConfig(AptAuthorizationRulesTest.Config.class)
@DisplayName("Unit Test | APT authorization URL rules")
class AptAuthorizationRulesTest {

  private static final String APT = "FTA_APT_ADMIN";
  private static final String APT_VIEWER = "FTA_APT_VIEWER";
  private static final String VIEWER = "FTA_VIEWER";
  private static final String SENIOR = "FTA_HARVEST_AUTH_SENIOR_ADMIN";
  private static final String TIMBER_MARK = "FTA_TIMBER_MARK_HEADQUARTERS_ADMIN";
  private static final String ADMIN = "FTA_ADMIN";

  @Configuration
  @EnableWebMvc
  @EnableWebSecurity
  @Import(ApiAuthorizationCustomizer.class)
  static class Config {
    @Bean
    SecurityFilterChain chain(HttpSecurity http, ApiAuthorizationCustomizer rules)
        throws Exception {
      return http.authorizeHttpRequests(rules).build();
    }
  }

  @Autowired private WebApplicationContext context;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
  }

  @ParameterizedTest(name = "{0} {1} as {2} is {3}")
  @CsvSource({
    // Every APT write: FTA_APT_ADMIN and FTA_ADMIN.
    "POST, /api/fta/apt/management-units, " + APT + ", 404",
    "PUT, /api/fta/apt/management-units/1, " + APT + ", 404",
    "DELETE, /api/fta/apt/partitions/1, " + APT + ", 404",
    "DELETE, /api/fta/apt/commitments/1, " + APT + ", 404",
    "POST, /api/fta/apt/management-units, " + ADMIN + ", 404",
    "DELETE, /api/fta/apt/partitions/1, " + ADMIN + ", 404",
    // No other role writes APT.
    "POST, /api/fta/apt/management-units, " + VIEWER + ", 403",
    "PUT, /api/fta/apt/management-units/1, " + SENIOR + ", 403",
    "DELETE, /api/fta/apt/partitions/1, " + TIMBER_MARK + ", 403",
    "POST, /api/fta/apt/management-units, " + APT_VIEWER + ", 403",
    "PUT, /api/fta/apt/licences/A12345, " + APT_VIEWER + ", 403",
    "DELETE, /api/fta/apt/commitments/1, " + APT_VIEWER + ", 403",
    // APT reads and reports: both APT roles, and every role that reads FTA.
    "GET, /api/fta/apt/management-units, " + APT_VIEWER + ", 404",
    "GET, /api/fta/apt/reports/APTR011, " + APT_VIEWER + ", 404",
    "GET, /api/fta/apt/reports/APTR011/csv, " + APT_VIEWER + ", 404",
    "GET, /api/fta/apt/management-units, " + APT + ", 404",
    "GET, /api/fta/apt/management-units, " + VIEWER + ", 404",
    "GET, /api/fta/apt/management-units, " + SENIOR + ", 404",
    "GET, /api/fta/apt/management-units, " + ADMIN + ", 404",
    // The forest client type-ahead, for Create relationship: FTA_APT_ADMIN only of the APT roles.
    "GET, /api/fta/clients/suggest, " + APT + ", 404",
    "GET, /api/fta/clients/suggest, " + APT_VIEWER + ", 403",
    "GET, /api/fta/clients/suggest, " + TIMBER_MARK + ", 404",
    "GET, /api/fta/clients/suggest, " + VIEWER + ", 404",
    // Display names for the user ids APT shows.
    "POST, /api/fta/users/resolve, " + APT + ", 404",
    "POST, /api/fta/users/resolve, " + APT_VIEWER + ", 404",
    // The APT roles read nothing else in FTA, as APT2 was an application of its own.
    "GET, /api/fta/tenures, " + APT + ", 403",
    "GET, /api/fta/harvesting-authorities, " + APT + ", 403",
    "GET, /api/fta/code-lists/districts, " + APT + ", 403",
    "POST, /api/reports/fta402, " + APT + ", 403",
    "GET, /api/fta/tenures, " + APT_VIEWER + ", 403",
    "GET, /api/fta/marks, " + APT_VIEWER + ", 403",
    "GET, /api/fta/admin/audit, " + APT_VIEWER + ", 403",
    // Nor write it.
    "POST, /api/fta/tenures, " + APT + ", 403",
    "PUT, /api/fta/cutting-permits/1, " + APT + ", 403",
    "POST, /api/fta/marks, " + APT + ", 403",
    "GET, /api/fta/admin/audit, " + APT + ", 403",
  })
  void enforcesTheRoleMatrix(String method, String path, String role, int expected)
      throws Exception {
    mvc.perform(request(HttpMethod.valueOf(method), path)
            .with(user("u").authorities(AuthorityUtils.createAuthorityList(role)))
            .with(csrf()))
        .andExpect(status().is(expected));
  }

  @Test
  @DisplayName("roles stack on the backend: a harvest admin who is also APT admin writes both")
  void rolesStack() throws Exception {
    var both = AuthorityUtils.createAuthorityList(SENIOR, APT);
    mvc.perform(request(HttpMethod.PUT, "/api/fta/apt/management-units/1")
            .with(user("u").authorities(both)).with(csrf()))
        .andExpect(status().is(404));
    mvc.perform(request(HttpMethod.PUT, "/api/fta/cutting-permits/1")
            .with(user("u").authorities(both)).with(csrf()))
        .andExpect(status().is(404));
  }
}
