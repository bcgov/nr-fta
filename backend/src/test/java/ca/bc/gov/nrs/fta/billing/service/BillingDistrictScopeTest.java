package ca.bc.gov.nrs.fta.billing.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.security.LoggedUserHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("Unit Test | BillingDistrictScope")
class BillingDistrictScopeTest {

  private static BillingDistrictScope scope(boolean unscoped, String... districts) {
    LoggedUserHelper auth = mock(LoggedUserHelper.class);
    when(auth.isUnscopedRangeAdmin()).thenReturn(unscoped);
    when(auth.administersRangeDistrict(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
      String code = inv.getArgument(0);
      if (unscoped) {
        return true;
      }
      return code != null && java.util.List.of(districts).contains(code);
    });
    return new BillingDistrictScope(auth);
  }

  private static void forbidden(Runnable r) {
    assertThatThrownBy(r::run).isInstanceOfSatisfying(ResponseStatusException.class,
        e -> org.assertj.core.api.Assertions.assertThat(e.getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN));
  }

  @Test
  void anUnscopedAdministratorMayActAnywhereAndOnAll() {
    BillingDistrictScope s = scope(true);
    assertThatCode(() -> s.requireDistrict("DCC")).doesNotThrowAnyException();
    assertThatCode(s::requireAll).doesNotThrowAnyException();
  }

  @Test
  void aScopedAdministratorMayActOnlyInTheirDistricts() {
    BillingDistrictScope s = scope(false, "DCC");
    assertThatCode(() -> s.requireDistrict("DCC")).doesNotThrowAnyException();
    forbidden(() -> s.requireDistrict("DKA"));
    forbidden(() -> s.requireDistrict(null));
    forbidden(s::requireAll);
  }

  @Test
  void theAdminOnlyScopeIgnoresTheRangeHeadquartersRole() {
    LoggedUserHelper auth = mock(LoggedUserHelper.class);
    // A scoped FTA_ADMIN who is also a range headquarters: every district on billing, not on audit.
    when(auth.isUnscopedRangeAdmin()).thenReturn(true);
    when(auth.administersRangeDistrict(org.mockito.ArgumentMatchers.any())).thenReturn(true);
    when(auth.isUnscopedAdmin()).thenReturn(false);
    when(auth.administersDistrict("DCC")).thenReturn(true);

    assertThatCode(new BillingDistrictScope(auth)::requireAll).doesNotThrowAnyException();
    BillingDistrictScope admin = BillingDistrictScope.adminOnly(auth);
    assertThatCode(() -> admin.requireDistrict("DCC")).doesNotThrowAnyException();
    forbidden(() -> admin.requireDistrict("DKA"));
    forbidden(admin::requireAll);
  }
}
