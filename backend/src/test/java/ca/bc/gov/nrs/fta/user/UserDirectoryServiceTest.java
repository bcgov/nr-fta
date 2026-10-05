package ca.bc.gov.nrs.fta.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.user.UserLookupClient.IdirUser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserDirectoryServiceTest {

  private UserLookupClient client;
  private MutableClock clock;
  private UserDirectoryService service;

  @BeforeEach
  void setUp() {
    client = mock(UserLookupClient.class);
    when(client.isConfigured()).thenReturn(true);
    clock = new MutableClock();
    service = new UserDirectoryService(client, Duration.ofHours(12), clock);
  }

  private void found(String bare, String first, String last) {
    when(client.getIdirDetail(bare))
        .thenReturn(Optional.of(new IdirUser(bare, "g", first, last, null)));
  }

  @Test
  void resolvesIdirIdsKeyedAsSent() {
    found("JSMITH", "Jane", "Smith");
    assertThat(service.resolveDisplayNames(List.of("IDIR\\jsmith")))
        .containsEntry("IDIR\\jsmith", "Jane Smith");
  }

  @Test
  void aBareIdIsTreatedAsIdir() {
    found("JSMITH", "Jane", "Smith");
    assertThat(service.resolveDisplayNames(List.of("JSMITH"))).containsEntry("JSMITH", "Jane Smith");
  }

  @Test
  void unresolvedAndOtherDirectoriesAreLeftOut() {
    when(client.getIdirDetail("GHOST")).thenReturn(Optional.empty());
    assertThat(service.resolveDisplayNames(List.of("IDIR\\GHOST", "BCEID\\ACME", " "))).isEmpty();
    verify(client, never()).getIdirDetail("ACME");
  }

  @Test
  void anUpstreamFailureIsAMissAndIsRetriedNextTime() {
    when(client.getIdirDetail("JSMITH")).thenThrow(new IllegalStateException("down"));
    assertThat(service.resolveDisplayNames(List.of("IDIR\\JSMITH"))).isEmpty();
    service.resolveDisplayNames(List.of("IDIR\\JSMITH"));
    verify(client, times(2)).getIdirDetail("JSMITH");
  }

  @Test
  void aFoundNameIsCachedUntilItsTtl() {
    found("JSMITH", "Jane", "Smith");
    service.resolveDisplayNames(List.of("IDIR\\JSMITH"));
    service.resolveDisplayNames(List.of("JSMITH"));
    verify(client, times(1)).getIdirDetail("JSMITH");

    clock.advance(Duration.ofHours(13));
    service.resolveDisplayNames(List.of("IDIR\\JSMITH"));
    verify(client, times(2)).getIdirDetail("JSMITH");
  }

  @Test
  void unconfiguredResolvesNothingWithoutCalling() {
    when(client.isConfigured()).thenReturn(false);
    assertThat(service.resolveDisplayNames(List.of("IDIR\\JSMITH"))).isEmpty();
    verify(client, never()).getIdirDetail(anyString());
  }

  @Test
  void aRequestIsCappedAtMaxIds() {
    when(client.getIdirDetail(anyString())).thenReturn(Optional.empty());
    List<String> ids = new ArrayList<>();
    IntStream.range(0, UserDirectoryService.MAX_IDS + 20).forEach(i -> ids.add("IDIR\\U" + i));
    service.resolveDisplayNames(ids);
    verify(client, times(UserDirectoryService.MAX_IDS)).getIdirDetail(anyString());
  }

  /** A clock the test moves forward. */
  private static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-10-05T12:00:00Z");

    void advance(Duration d) {
      now = now.plus(d);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
