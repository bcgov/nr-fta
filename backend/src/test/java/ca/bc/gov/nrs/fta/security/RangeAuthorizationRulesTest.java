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
 * Pins who reaches which range endpoint. A 403 is the filter chain refusing; a 404 means the
 * request got past it, as no controller is loaded here. The failure this guards against is
 * silent: a range admin rule placed after the generic {@code /api/fta/admin/**} rule never fires,
 * and a shared read placed after the timber mark allow-list shuts the range roles out.
 */
@SpringJUnitWebConfig(RangeAuthorizationRulesTest.Config.class)
@DisplayName("Unit Test | range URL rules")
class RangeAuthorizationRulesTest {

  private static final String SENIOR = "FTA_RANGE_HEADQUARTERS_ADMIN";
  private static final String DISTRICT = "FTA_RANGE_DISTRICT_ADMIN";
  private static final String ADMIN = "FTA_ADMIN";
  private static final String VIEWER = "FTA_VIEWER";
  private static final String HARVEST = "FTA_HARVEST_AUTH_SENIOR_ADMIN";
  private static final String TIMBER_MARK = "FTA_TIMBER_MARK_HEADQUARTERS_ADMIN";

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
    // Range search and detail: both range roles, and the roles that read everything.
    "GET, /api/fta/range-tenures, " + DISTRICT + ", 404",
    "GET, /api/fta/range-tenures/export, " + DISTRICT + ", 404",
    // FTA001 Range Tenure Export, one file: whoever reads the range tenure page.
    "GET, /api/fta/range-tenures/RAN1/export, " + DISTRICT + ", 404",
    "GET, /api/fta/range-tenures/RAN1/export, " + SENIOR + ", 404",
    "GET, /api/fta/range-tenures/RAN1/export, " + VIEWER + ", 404",
    "GET, /api/fta/range-tenures/RAN1/export, " + ADMIN + ", 404",
    "GET, /api/fta/range-tenures/RAN1/export, " + TIMBER_MARK + ", 403",
    "GET, /api/fta/range-tenures/RAN1, " + DISTRICT + ", 404",
    "GET, /api/fta/range-units, " + DISTRICT + ", 404",
    "GET, /api/fta/range-units/1, " + DISTRICT + ", 404",
    "GET, /api/fta/range-tenures/RAN1, " + SENIOR + ", 404",
    "GET, /api/fta/range-tenures/RAN1, " + VIEWER + ", 404",
    "GET, /api/fta/range-tenures/RAN1, " + HARVEST + ", 404",
    "GET, /api/fta/range-tenures/RAN1, " + ADMIN + ", 404",
    "GET, /api/fta/range-tenures/RAN1, " + TIMBER_MARK + ", 403",
    // The range tenure page's shared tabs, code lists, default org unit, clients, user names.
    "GET, /api/fta/tenures/RAN1/associated-files, " + DISTRICT + ", 404",
    "GET, /api/fta/tenures/RAN1/associated-clients, " + DISTRICT + ", 404",
    "GET, /api/fta/tenures/RAN1/tenure-applications, " + DISTRICT + ", 404",
    "GET, /api/fta/tenures/RAN1/tenure-applications/1/professional-declarations, "
        + DISTRICT + ", 404",
    "GET, /api/fta/tenures/RAN1/notes, " + SENIOR + ", 404",
    "GET, /api/fta/code-lists/range-zones, " + DISTRICT + ", 404",
    "GET, /api/fta/org-unit-default, " + DISTRICT + ", 404",
    "GET, /api/fta/clients/suggest, " + DISTRICT + ", 404",
    "POST, /api/fta/users/resolve, " + DISTRICT + ", 404",
    // ...and those reads stay open to the roles that had them.
    "GET, /api/fta/tenures/F1/notes, " + TIMBER_MARK + ", 404",
    "GET, /api/fta/code-lists/file-types, " + TIMBER_MARK + ", 404",
    "GET, /api/fta/tenures/F1/associated-clients, " + VIEWER + ", 404",
    // Nothing else of FTA: the timber screens' reads and every write.
    "GET, /api/fta/tenures, " + DISTRICT + ", 403",
    "GET, /api/fta/tenures/F1, " + DISTRICT + ", 403",
    "GET, /api/fta/tenures/F1/cutting-permits, " + DISTRICT + ", 403",
    "GET, /api/fta/harvesting-authorities, " + SENIOR + ", 403",
    "GET, /api/fta/marks, " + SENIOR + ", 403",
    "GET, /api/fta/apt/licences, " + SENIOR + ", 403",
    // Notes: anyone who can open the file may add one (legacy FTA970).
    "POST, /api/fta/tenures/F1/notes, " + DISTRICT + ", 404",
    "PUT, /api/fta/tenures/F1/details, " + SENIOR + ", 403",
    "POST, /api/fta/tenures/F1/associated-files, " + SENIOR + ", 403",
    // Range admin and billing: FTA_ADMIN and the headquarters range role, every method.
    "GET, /api/fta/admin/range-zones, " + SENIOR + ", 404",
    "POST, /api/fta/admin/range-zones, " + SENIOR + ", 404",
    "GET, /api/fta/admin/archive-tenures, " + SENIOR + ", 404",
    "POST, /api/fta/admin/archive-tenures, " + SENIOR + ", 404",
    "GET, /api/fta/admin/billing/instructions, " + SENIOR + ", 404",
    "POST, /api/fta/admin/billing/instructions/prepare, " + SENIOR + ", 404",
    "GET, /api/fta/admin/billing/invoice-preview, " + SENIOR + ", 404",
    "GET, /api/fta/admin/billing/pre-billing-report, " + SENIOR + ", 404",
    "GET, /api/fta/admin/billing/post-billing-report, " + SENIOR + ", 404",
    "POST, /api/fta/admin/billing/approval, " + SENIOR + ", 404",
    "GET, /api/fta/admin/rates, " + SENIOR + ", 404",
    "GET, /api/fta/admin/rates/years, " + SENIOR + ", 404",
    "PUT, /api/fta/admin/rates, " + SENIOR + ", 404",
    "PUT, /api/fta/admin/rates, " + ADMIN + ", 404",
    "GET, /api/fta/admin/billing/instructions, " + ADMIN + ", 404",
    // ...not to the district range role, nor to the roles that read everything.
    "GET, /api/fta/admin/range-zones, " + DISTRICT + ", 403",
    "GET, /api/fta/admin/billing/instructions, " + DISTRICT + ", 403",
    "PUT, /api/fta/admin/rates, " + DISTRICT + ", 403",
    "GET, /api/fta/admin/rates, " + VIEWER + ", 403",
    "GET, /api/fta/admin/billing/instructions, " + HARVEST + ", 403",
    // The other admin screens stay FTA_ADMIN's.
    "GET, /api/fta/admin/audit, " + SENIOR + ", 403",
    "GET, /api/fta/admin/org-unit-default, " + SENIOR + ", 403",
    "POST, /api/fta/marks/transfer, " + SENIOR + ", 403",
    // FTA670 Annual Rents and Fees is not a range screen: FTA_ADMIN only.
    "GET, /api/fta/admin/rents-fees, " + ADMIN + ", 404",
    "POST, /api/fta/admin/rents-fees/prepare, " + ADMIN + ", 404",
    "GET, /api/fta/admin/rents-fees, " + SENIOR + ", 403",
    "POST, /api/fta/admin/rents-fees/prepare, " + SENIOR + ", 403",
    "GET, /api/fta/admin/rents-fees, " + VIEWER + ", 403",
    // ...and its Print (the FTA406 report), likewise.
    "GET, /api/fta/admin/rents-fees/report, " + ADMIN + ", 404",
    "GET, /api/fta/admin/rents-fees/report, " + SENIOR + ", 403",
    "GET, /api/fta/admin/rents-fees/report, " + DISTRICT + ", 403",
    "GET, /api/fta/admin/rents-fees/report, " + VIEWER + ", 403",
    // FTA630 range unit / pasture writes: FTA_ADMIN and both range roles (districts: the service).
    "POST, /api/fta/range-units, " + DISTRICT + ", 404",
    "PUT, /api/fta/range-units/RU1, " + DISTRICT + ", 404",
    "DELETE, /api/fta/range-units/RU1, " + SENIOR + ", 404",
    "POST, /api/fta/range-units/RU1/pastures, " + SENIOR + ", 404",
    "PUT, /api/fta/range-units/RU1/pastures/01, " + DISTRICT + ", 404",
    "DELETE, /api/fta/range-units/RU1/pastures/01, " + ADMIN + ", 404",
    "POST, /api/fta/range-units, " + VIEWER + ", 403",
    "PUT, /api/fta/range-units/RU1, " + HARVEST + ", 403",
    "DELETE, /api/fta/range-units/RU1, " + TIMBER_MARK + ", 403",
  })
  void enforcesTheRoleMatrix(String method, String path, String role, int expected)
      throws Exception {
    mvc.perform(request(HttpMethod.valueOf(method), path)
            .with(user("u").authorities(AuthorityUtils.createAuthorityList(role)))
            .with(csrf()))
        .andExpect(status().is(expected));
  }
}
