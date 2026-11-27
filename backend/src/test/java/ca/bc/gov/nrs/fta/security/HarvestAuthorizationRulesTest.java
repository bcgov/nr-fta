package ca.bc.gov.nrs.fta.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * Pins who reaches which harvest authorization endpoint. A 403 is the filter chain refusing; a
 * 404 means the request got past it, as no controller is loaded here. The failure this guards
 * against is silent: a rule placed after the generic write rules never fires.
 */
@SpringJUnitWebConfig(HarvestAuthorizationRulesTest.Config.class)
@DisplayName("Unit Test | harvest authorization URL rules")
class HarvestAuthorizationRulesTest {

  private static final String SENIOR = "FTA_HARVEST_AUTH_SENIOR_ADMIN";
  private static final String DISTRICT = "FTA_HARVEST_AUTH_DISTRICT_ADMIN";
  private static final String VIEWER = "FTA_VIEWER";
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
    // Adding and editing: both harvest roles and FTA_ADMIN.
    "POST, /api/fta/tenures/F1/cutting-permits, " + DISTRICT + ", 404",
    "POST, /api/fta/tenures/F1/cut-blocks, " + DISTRICT + ", 404",
    "PUT, /api/fta/cutting-permits/1, " + DISTRICT + ", 404",
    "PUT, /api/fta/cut-blocks/1, " + DISTRICT + ", 404",
    "POST, /api/fta/cutting-permits/1/assign-marks, " + DISTRICT + ", 404",
    "POST, /api/fta/cutting-permits/1/retire, " + DISTRICT + ", 404",
    "POST, /api/fta/tenures/F1/notes, " + DISTRICT + ", 404",
    "PUT, /api/fta/cutting-permits/1, " + SENIOR + ", 404",
    "PUT, /api/fta/cutting-permits/1, " + ADMIN + ", 404",
    // Senior-only: refused for district staff.
    "POST, /api/fta/cutting-permits/1/suspend-blocks, " + DISTRICT + ", 403",
    "POST, /api/fta/cutting-permits/1/conditions, " + DISTRICT + ", 403",
    "POST, /api/fta/cutting-permits/1/postponements, " + DISTRICT + ", 403",
    "POST, /api/fta/cut-blocks/1/relabel, " + DISTRICT + ", 403",
    "POST, /api/fta/cut-blocks/1/suspensions, " + DISTRICT + ", 403",
    "PUT, /api/fta/cut-blocks/1/suspensions/2, " + DISTRICT + ", 403",
    "DELETE, /api/fta/cutting-permits/1, " + DISTRICT + ", 403",
    "DELETE, /api/fta/tenures/F1/cut-blocks/1, " + DISTRICT + ", 403",
    "POST, /api/fta/cutting-permits/1/suspend-blocks, " + SENIOR + ", 404",
    "POST, /api/fta/cut-blocks/1/relabel, " + SENIOR + ", 404",
    "DELETE, /api/fta/cutting-permits/1, " + SENIOR + ", 404",
    "DELETE, /api/fta/tenures/F1/cut-blocks/1, " + SENIOR + ", 404",
    "DELETE, /api/fta/tenures/F1/cut-blocks/1, " + ADMIN + ", 404",
    // Everything else stays FTA_ADMIN's.
    "POST, /api/fta/tenures, " + SENIOR + ", 403",
    "PUT, /api/fta/tenures/F1, " + SENIOR + ", 403",
    "GET, /api/fta/admin/audit, " + SENIOR + ", 403",
    // Reads are a viewer's.
    "GET, /api/fta/harvesting-authorities, " + DISTRICT + ", 404",
    "GET, /api/fta/cut-blocks/1, " + DISTRICT + ", 404",
    // Other roles gain no harvest writes.
    "PUT, /api/fta/cutting-permits/1, " + VIEWER + ", 403",
    "PUT, /api/fta/cut-blocks/1, " + TIMBER_MARK + ", 403",
    "POST, /api/fta/tenures/F1/notes, " + TIMBER_MARK + ", 404",
  })
  void enforcesTheRoleMatrix(String method, String path, String role, int expected)
      throws Exception {
    mvc.perform(request(HttpMethod.valueOf(method), path).with(user("u").authorities(AuthorityUtils.createAuthorityList(role)))
            .with(csrf()))
        .andExpect(status().is(expected));
  }
}
