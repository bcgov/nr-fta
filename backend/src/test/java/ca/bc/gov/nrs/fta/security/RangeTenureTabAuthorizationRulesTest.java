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
 * Pins who reaches the FTA100R range Tenure tab and range Add New endpoints. A 403 is the filter
 * chain refusing; a 404 means the request got past it, as no controller is loaded here. A rule
 * placed after the generic write rules would silently shut both range roles out.
 */
@SpringJUnitWebConfig(RangeTenureTabAuthorizationRulesTest.Config.class)
@DisplayName("Unit Test | range Tenure tab URL rules (FTA100R)")
class RangeTenureTabAuthorizationRulesTest {

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
    "POST, /api/fta/range-tenures, FTA_RANGE_DISTRICT_ADMIN, 404",
    "POST, /api/fta/range-tenures, FTA_RANGE_HEADQUARTERS_ADMIN, 404",
    "POST, /api/fta/range-tenures, FTA_ADMIN, 404",
    "POST, /api/fta/range-tenures, FTA_VIEWER, 403",
    "POST, /api/fta/range-tenures, FTA_HARVEST_AUTH_SENIOR_ADMIN, 403",
    "PUT, /api/fta/range-tenures/RAN1/tenure-tab, FTA_RANGE_DISTRICT_ADMIN, 404",
    "PUT, /api/fta/range-tenures/RAN1/tenure-tab, FTA_RANGE_HEADQUARTERS_ADMIN, 404",
    "PUT, /api/fta/range-tenures/RAN1/tenure-tab, FTA_ADMIN, 404",
    "PUT, /api/fta/range-tenures/RAN1/tenure-tab, FTA_VIEWER, 403",
    "PUT, /api/fta/range-tenures/RAN1/tenure-tab, FTA_HARVEST_AUTH_SENIOR_ADMIN, 403",
    "POST, /api/fta/range-tenures/RAN1/tenure-tab/retire, FTA_RANGE_DISTRICT_ADMIN, 404",
    "POST, /api/fta/range-tenures/RAN1/tenure-tab/retire, FTA_RANGE_HEADQUARTERS_ADMIN, 404",
    "POST, /api/fta/range-tenures/RAN1/tenure-tab/retire, FTA_ADMIN, 404",
    "POST, /api/fta/range-tenures/RAN1/tenure-tab/retire, FTA_VIEWER, 403",
    "POST, /api/fta/range-tenures/RAN1/tenure-tab/retire, FTA_HARVEST_AUTH_SENIOR_ADMIN, 403",
    "POST, /api/fta/range-tenures/RAN1/tenure-tab/cancel-replacement, FTA_RANGE_DISTRICT_ADMIN, 404",
    "POST, /api/fta/range-tenures/RAN1/tenure-tab/cancel-replacement, FTA_RANGE_HEADQUARTERS_ADMIN, 404",
    "POST, /api/fta/range-tenures/RAN1/tenure-tab/cancel-replacement, FTA_ADMIN, 404",
    "POST, /api/fta/range-tenures/RAN1/tenure-tab/cancel-replacement, FTA_VIEWER, 403",
    "POST, /api/fta/range-tenures/RAN1/tenure-tab/cancel-replacement, FTA_HARVEST_AUTH_SENIOR_ADMIN, 403",
    "POST, /api/fta/range-tenures/RAN1/special-conditions, FTA_RANGE_DISTRICT_ADMIN, 404",
    "POST, /api/fta/range-tenures/RAN1/special-conditions, FTA_RANGE_HEADQUARTERS_ADMIN, 404",
    "POST, /api/fta/range-tenures/RAN1/special-conditions, FTA_ADMIN, 404",
    "POST, /api/fta/range-tenures/RAN1/special-conditions, FTA_VIEWER, 403",
    "POST, /api/fta/range-tenures/RAN1/special-conditions, FTA_HARVEST_AUTH_SENIOR_ADMIN, 403",
    "PUT, /api/fta/range-tenures/RAN1/special-conditions/7, FTA_RANGE_DISTRICT_ADMIN, 404",
    "PUT, /api/fta/range-tenures/RAN1/special-conditions/7, FTA_RANGE_HEADQUARTERS_ADMIN, 404",
    "PUT, /api/fta/range-tenures/RAN1/special-conditions/7, FTA_ADMIN, 404",
    "PUT, /api/fta/range-tenures/RAN1/special-conditions/7, FTA_VIEWER, 403",
    "PUT, /api/fta/range-tenures/RAN1/special-conditions/7, FTA_HARVEST_AUTH_SENIOR_ADMIN, 403",
    "DELETE, /api/fta/range-tenures/RAN1/special-conditions/7, FTA_RANGE_DISTRICT_ADMIN, 404",
    "DELETE, /api/fta/range-tenures/RAN1/special-conditions/7, FTA_RANGE_HEADQUARTERS_ADMIN, 404",
    "DELETE, /api/fta/range-tenures/RAN1/special-conditions/7, FTA_ADMIN, 404",
    "DELETE, /api/fta/range-tenures/RAN1/special-conditions/7, FTA_VIEWER, 403",
    "DELETE, /api/fta/range-tenures/RAN1/special-conditions/7, FTA_HARVEST_AUTH_SENIOR_ADMIN, 403",
    "GET, /api/fta/range-tenures/RAN1/tenure-tab, FTA_RANGE_DISTRICT_ADMIN, 404",
    "GET, /api/fta/range-tenures/RAN1/tenure-tab, FTA_VIEWER, 404",
    "GET, /api/fta/range-tenures/RAN1/tenure-tab, FTA_TIMBER_MARK_HEADQUARTERS_ADMIN, 403",
    "GET, /api/fta/range-tenures/RAN1/invoice-preview, FTA_RANGE_DISTRICT_ADMIN, 404",
    "GET, /api/fta/range-tenures/RAN1/invoice-preview, FTA_VIEWER, 404",
    "GET, /api/fta/range-tenures/RAN1/invoice-preview, FTA_TIMBER_MARK_HEADQUARTERS_ADMIN, 403",
    "GET, /api/fta/range-tenures/lookups/zones, FTA_RANGE_DISTRICT_ADMIN, 404",
    "GET, /api/fta/range-tenures/lookups/zones, FTA_VIEWER, 404",
    "GET, /api/fta/range-tenures/lookups/zones, FTA_TIMBER_MARK_HEADQUARTERS_ADMIN, 403",
    "GET, /api/fta/range-tenures/lookups/create-types, FTA_RANGE_DISTRICT_ADMIN, 404",
    "GET, /api/fta/range-tenures/lookups/create-types, FTA_VIEWER, 404",
    "GET, /api/fta/range-tenures/lookups/create-types, FTA_TIMBER_MARK_HEADQUARTERS_ADMIN, 403",
  })
  void enforcesTheRoleMatrix(String method, String path, String role, int expected)
      throws Exception {
    mvc.perform(request(HttpMethod.valueOf(method), path)
            .with(user("u").authorities(AuthorityUtils.createAuthorityList(role)))
            .with(csrf()))
        .andExpect(status().is(expected));
  }
}
