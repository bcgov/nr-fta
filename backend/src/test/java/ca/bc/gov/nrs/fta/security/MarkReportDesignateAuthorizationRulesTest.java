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
 * Pins who reaches the FTA404 Mark Report and FTA101 Change Mark Designate. A 403 is the filter
 * chain refusing; a 404 means the request got past it, as no controller is loaded here.
 */
@SpringJUnitWebConfig(MarkReportDesignateAuthorizationRulesTest.Config.class)
@DisplayName("Unit Test | mark report and change mark designate URL rules")
class MarkReportDesignateAuthorizationRulesTest {

  private static final String ADMIN = "FTA_ADMIN";
  private static final String VIEWER = "FTA_VIEWER";
  private static final String TM_HQ = "FTA_TIMBER_MARK_HEADQUARTERS_ADMIN";
  private static final String TM_DISTRICT = "FTA_TIMBER_MARK_DISTRICT_ADMIN";
  private static final String SENIOR = "FTA_HARVEST_AUTH_SENIOR_ADMIN";
  private static final String DISTRICT = "FTA_HARVEST_AUTH_DISTRICT_ADMIN";
  private static final String APT = "FTA_APT_ADMIN";

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
    // The Mark Report: whoever sees the Harvest history tab (legacy granted FTAR404P to every
    // FTA role, view-only included).
    "GET, /api/fta/timber-marks/12345A/mark-report, " + ADMIN + ", 404",
    "GET, /api/fta/timber-marks/12345A/mark-report, " + VIEWER + ", 404",
    "GET, /api/fta/timber-marks/12345A/mark-report, " + TM_HQ + ", 404",
    "GET, /api/fta/timber-marks/12345A/mark-report, " + TM_DISTRICT + ", 404",
    "GET, /api/fta/timber-marks/12345A/mark-report, " + SENIOR + ", 404",
    "GET, /api/fta/timber-marks/12345A/mark-report, " + DISTRICT + ", 404",
    "GET, /api/fta/timber-marks/12345A/mark-report, " + APT + ", 403",
    // Change Mark Designate, its lookup included: FTA_ADMIN only.
    "GET, /api/fta/tenures/A1/details/mark-designate, " + ADMIN + ", 404",
    "POST, /api/fta/tenures/A1/details/change-mark-designate, " + ADMIN + ", 404",
    "GET, /api/fta/tenures/A1/details/mark-designate, " + VIEWER + ", 403",
    "GET, /api/fta/tenures/A1/details/mark-designate, " + TM_HQ + ", 403",
    "GET, /api/fta/tenures/A1/details/mark-designate, " + SENIOR + ", 403",
    "POST, /api/fta/tenures/A1/details/change-mark-designate, " + VIEWER + ", 403",
    "POST, /api/fta/tenures/A1/details/change-mark-designate, " + TM_HQ + ", 403",
    "POST, /api/fta/tenures/A1/details/change-mark-designate, " + SENIOR + ", 403",
    // The rest of the Details tab still reads as before.
    "GET, /api/fta/tenures/A1/details, " + TM_HQ + ", 404",
  })
  void enforcesTheRoleMatrix(String method, String path, String role, int expected)
      throws Exception {
    mvc.perform(request(HttpMethod.valueOf(method), path)
            .with(user("u").authorities(AuthorityUtils.createAuthorityList(role)))
            .with(csrf()))
        .andExpect(status().is(expected));
  }
}
