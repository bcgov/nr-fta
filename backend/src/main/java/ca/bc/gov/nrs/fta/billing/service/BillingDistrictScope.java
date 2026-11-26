package ca.bc.gov.nrs.fta.billing.service;

import ca.bc.gov.nrs.fta.security.LoggedUserHelper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Which districts a range billing screen may act on.
 *
 * <p>Legacy FTA680, 685, 686 and 690 defaulted the district to a district user's own org unit
 * and protected it, and offered 686's "*" (every district) only to users who were not district
 * users. Here the district a user may pick is the one a district-scoped grant
 * ({@code FTA_ADMIN_DISTRICT-xxx}) names; an unscoped {@code FTA_ADMIN} may pick any, and "*".
 */
@Component
public class BillingDistrictScope {

  static final String NOT_YOUR_DISTRICT = "You do not administer district %s.";
  static final String ALL_NOT_ALLOWED = "Only a provincial administrator may report on every district.";

  private final LoggedUserHelper auth;

  public BillingDistrictScope(LoggedUserHelper auth) {
    this.auth = auth;
  }

  /**
   * @param orgUnitCode the district's ORG_UNIT_CODE, or null when the number named none
   * @throws ResponseStatusException 403 unless the user administers that district
   */
  public void requireDistrict(String orgUnitCode) {
    if (!auth.administersDistrict(orgUnitCode)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
          NOT_YOUR_DISTRICT.formatted(orgUnitCode == null ? "that" : orgUnitCode));
    }
  }

  /** @throws ResponseStatusException 403 unless the user administers every district */
  public void requireAll() {
    if (!auth.isUnscopedAdmin()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, ALL_NOT_ALLOWED);
    }
  }
}
