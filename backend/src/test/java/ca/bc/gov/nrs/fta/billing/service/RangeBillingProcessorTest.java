package ca.bc.gov.nrs.fta.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.batch.LogCapture;
import ca.bc.gov.nrs.fta.billing.service.RangeBillingPackage.ProcessResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("Unit Test | RangeBillingProcessor")
class RangeBillingProcessorTest {

  private static final Instant NOW = Instant.parse("2026-11-27T20:00:00Z");

  private final RangeBillingPackage pkg = mock(RangeBillingPackage.class);
  private final LockProvider locks = mock(LockProvider.class);
  private final SimpleLock lock = mock(SimpleLock.class);
  private final RangeBillingProcessor processor =
      new RangeBillingProcessor(pkg, locks, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void billsTheRequestUnderItsDistrictAndYearsLock() {
    when(locks.lock(any())).thenReturn(Optional.of(lock));
    when(pkg.isUnprocessed(101L)).thenReturn(true);
    when(pkg.process(101L)).thenReturn(new ProcessResult("fta411-ApprovalSummary.rpt", ""));

    processor.bill(101L, 1840L, "2026", "IDIR\\JSAMPLE");

    verify(pkg).process(101L);
    verify(lock).unlock();
    ArgumentCaptor<LockConfiguration> config = ArgumentCaptor.forClass(LockConfiguration.class);
    verify(locks).lock(config.capture());
    assertThat(config.getValue().getName()).isEqualTo("range-billing-1840-2026");
    assertThat(config.getValue().getLockAtMostUntil())
        .isEqualTo(NOW.plus(RangeBillingProcessor.LOCK_AT_MOST));
  }

  @Test
  void aRunningDistrictAndYearIsRefusedStraightAway() {
    when(locks.lock(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> processor.bill(101L, 1840L, "2026", "IDIR\\JSAMPLE"))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(e.getReason()).isEqualTo(RangeBillingProcessor.RUNNING);
        });
    verify(pkg, never()).process(anyLong());
  }

  @Test
  void anAlreadyProcessedRequestIsLeftAlone() {
    when(locks.lock(any())).thenReturn(Optional.of(lock));
    when(pkg.isUnprocessed(101L)).thenReturn(false);

    processor.bill(101L, 1840L, "2026", "IDIR\\JSAMPLE");

    verify(pkg, never()).process(anyLong());
    verify(lock).unlock();
  }

  @Test
  void aPackageErrorIsLoggedAtError() {
    when(locks.lock(any())).thenReturn(Optional.of(lock));
    when(pkg.isUnprocessed(101L)).thenReturn(true);
    when(pkg.process(101L)).thenReturn(new ProcessResult("", "*** No instructions found ***"));

    try (LogCapture log = new LogCapture(RangeBillingProcessor.class)) {
      processor.bill(101L, 1840L, "2026", "IDIR\\JSAMPLE");
      assertThat(log.errors()).containsExactly("Range billing error: request 101 — an error"
          + " occurred in the database package FTA_690_TENURE_APPROVAL: *** No instructions found"
          + " ***");
    }
    verify(lock).unlock();
  }

  @Test
  void anExceptionReleasesTheLock() {
    when(locks.lock(any())).thenReturn(Optional.of(lock));
    when(pkg.isUnprocessed(101L)).thenReturn(true);
    when(pkg.process(101L)).thenThrow(new IllegalStateException("ORA-04068"));

    assertThatThrownBy(() -> processor.bill(101L, 1840L, "2026", "IDIR\\JSAMPLE"))
        .isInstanceOf(IllegalStateException.class);
    verify(lock).unlock();
  }
}
