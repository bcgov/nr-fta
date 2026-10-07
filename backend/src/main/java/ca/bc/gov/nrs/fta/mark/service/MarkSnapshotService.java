package ca.bc.gov.nrs.fta.mark.service;

import ca.bc.gov.nrs.fta.mark.dto.MarkDetailDto;
import ca.bc.gov.nrs.fta.user.UserDirectoryService;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The private mark snapshot ({@link MarkSnapshotReport}): reads the mark as its detail page
 * does and stamps it with the time and the user who took it. Read-only — nothing about the
 * snapshot is recorded on the mark.
 */
@Service
public class MarkSnapshotService {

  /** Snapshots are stamped in BC time, whatever the server's zone. */
  static final ZoneId ZONE = ZoneId.of("America/Vancouver");

  private final MarkDetailService markDetailService;
  private final UserDirectoryService users;
  private final MarkSnapshotReport report;
  private final Clock clock;

  @Autowired
  public MarkSnapshotService(
      MarkDetailService markDetailService,
      UserDirectoryService users,
      MarkSnapshotReport report) {
    this(markDetailService, users, report, Clock.system(ZONE));
  }

  MarkSnapshotService(
      MarkDetailService markDetailService,
      UserDirectoryService users,
      MarkSnapshotReport report,
      Clock clock) {
    this.markDetailService = markDetailService;
    this.users = users;
    this.report = report;
    this.clock = clock;
  }

  /** A mark's snapshot PDF and the time it was stamped with. */
  public record Snapshot(byte[] pdf, ZonedDateTime generatedAt) {}

  /**
   * The snapshot of a mark, or empty when there is no such mark.
   *
   * @param id            the timber mark, or the certificate when {@code byCertificate}
   * @param byCertificate whether {@code id} is a certificate
   * @param userId        the user taking it (an audit id, {@code IDIR\JSMITH})
   */
  public Optional<Snapshot> snapshot(String id, boolean byCertificate, String userId) {
    Optional<MarkDetailDto> found = byCertificate
        ? markDetailService.findByCertificate(id)
        : markDetailService.findByMarkNumber(id);
    return found.map(mark -> {
      Map<String, String> names = users.resolveDisplayNames(List.of(userId));
      String me = names.get(userId);
      ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(ZONE);
      byte[] pdf = report.render(
          mark,
          new MarkSnapshotReport.Stamp(now, me == null ? userId : me + " (" + userId + ")"));
      return new Snapshot(pdf, now);
    });
  }
}
