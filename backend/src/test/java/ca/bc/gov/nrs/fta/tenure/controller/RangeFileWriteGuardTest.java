package ca.bc.gov.nrs.fta.tenure.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.range.service.RangeFileAccess;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("Unit Test | RangeFileWriteGuard")
class RangeFileWriteGuardTest {

  @ParameterizedTest(name = "{0} {1} is guarded")
  @CsvSource({
    "PUT, /api/fta/tenures/RAN1/details",
    "POST, /api/fta/tenures/RAN1/details/retire",
    "POST, /api/fta/tenures/RAN1/details/delete",
    "POST, /api/fta/tenures/RAN1/details/assign-mark-designate",
    "POST, /api/fta/tenures/RAN1/details/change-mark-designate",
    "POST, /api/fta/tenures/RAN1/associated-clients",
    "PUT, /api/fta/tenures/RAN1/associated-clients/7",
    "DELETE, /api/fta/tenures/RAN1/associated-clients/7",
    "POST, /api/fta/tenures/RAN1/aac",
    "PUT, /api/fta/tenures/RAN1/sale-info",
    "POST, /api/fta/tenures/RAN1/tl-blocks/T1/retire",
    "POST, /api/fta/tenures/RAN1/cutting-permits",
    "POST, /api/fta/tenures/RAN1/cut-blocks",
    "DELETE, /api/fta/tenures/RAN1/cut-blocks/3",
  })
  void timberOnlyWritesAreGuarded(String method, String path) {
    assertThat(RangeFileWriteGuard.guardedFileId(method, path)).contains("RAN1");
  }

  @ParameterizedTest(name = "{0} {1} is not guarded")
  @CsvSource({
    // The tabs legacy's range tab set shared: 910, 950x, 970.
    "POST, /api/fta/tenures/RAN1/associated-files",
    "DELETE, /api/fta/tenures/RAN1/associated-files/F2",
    "POST, /api/fta/tenures/RAN1/tenure-applications/5/issue-permit",
    "POST, /api/fta/tenures/RAN1/notes",
    // Reads, and writes that name no tenure.
    "GET, /api/fta/tenures/RAN1/details",
    "POST, /api/fta/tenures",
    "PUT, /api/fta/cutting-permits/1",
  })
  void sharedTabsReadsAndOtherPathsAreNot(String method, String path) {
    assertThat(RangeFileWriteGuard.guardedFileId(method, path)).isEmpty();
  }

  @Test
  void anEncodedIdIsDecoded() {
    assertThat(RangeFileWriteGuard.guardedFileId("PUT", "/api/fta/tenures/A%20B/details"))
        .isEqualTo(Optional.of("A B"));
  }

  @Test
  void aRangeFileIsRefusedWith400() {
    RangeFileAccess access = mock(RangeFileAccess.class);
    when(access.isRangeFile("RAN1")).thenReturn(true);
    RangeFileWriteGuard guard = new RangeFileWriteGuard(access);

    MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/fta/tenures/RAN1/details");
    assertThatThrownBy(() -> guard.preHandle(request, new MockHttpServletResponse(), new Object()))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(e.getReason()).contains("RAN1").contains("range tenure");
        });
  }

  @Test
  void aTimberFileGoesThrough() {
    RangeFileAccess access = mock(RangeFileAccess.class);
    RangeFileWriteGuard guard = new RangeFileWriteGuard(access);

    MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/fta/tenures/A1/details");
    assertThat(guard.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    verify(access).isRangeFile("A1");
  }

  @Test
  void aSharedTabIsNotLookedUp() {
    RangeFileAccess access = mock(RangeFileAccess.class);
    RangeFileWriteGuard guard = new RangeFileWriteGuard(access);

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/fta/tenures/RAN1/notes");
    assertThat(guard.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    verify(access, never()).isRangeFile(anyString());
  }
}
