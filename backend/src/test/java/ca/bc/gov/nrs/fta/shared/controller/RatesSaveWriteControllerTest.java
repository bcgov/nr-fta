package ca.bc.gov.nrs.fta.shared.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.security.LoggedUserHelper;
import ca.bc.gov.nrs.fta.shared.dto.RatesSaveRequest;
import ca.bc.gov.nrs.fta.shared.service.RatesSaveWriteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("Unit Test | RatesSaveWriteController (FTA699 Save scope)")
class RatesSaveWriteControllerTest {

  private final RatesSaveWriteService service = mock(RatesSaveWriteService.class);
  private final LoggedUserHelper auth = mock(LoggedUserHelper.class);
  private final RatesSaveWriteController controller = new RatesSaveWriteController(service, auth);

  @Test
  void aDistrictScopedAdministratorMayNotSaveRates() {
    when(auth.mayEditRangeRates()).thenReturn(false);
    assertThatThrownBy(() -> controller.save(mock(RatesSaveRequest.class), null))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    verify(service, never()).save(any(), any());
  }
}
