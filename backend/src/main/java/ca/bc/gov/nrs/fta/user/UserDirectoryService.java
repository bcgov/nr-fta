package ca.bc.gov.nrs.fta.user;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Turns the user ids FTA's records hold ({@code IDIR\JSMITH}, or a bare {@code JSMITH}) into
 * display names through nr-user-lookup-api — FSP's batch resolver, IDIR only.
 *
 * <p>Best-effort: an id that can't be resolved (not found, lookup unconfigured, upstream error)
 * is left out of the result and the screen keeps showing the id. Names are cached in memory —
 * a found name for {@code fta.user-lookup.cache-ttl} (default 12h), a miss for a minute — since
 * the same few clerks appear on every screen and each lookup is a network call.
 */
@Service
public class UserDirectoryService {

  private static final Logger LOG = LoggerFactory.getLogger(UserDirectoryService.class);

  /** The most ids one request resolves; the rest are left for the screen to show as ids. */
  static final int MAX_IDS = 100;

  private static final Duration MISS_TTL = Duration.ofMinutes(1);

  private final UserLookupClient client;
  private final Duration ttl;
  private final Clock clock;
  private final Map<String, Entry> cache = new ConcurrentHashMap<>();

  @Autowired
  public UserDirectoryService(
      UserLookupClient client, @Value("${fta.user-lookup.cache-ttl:PT12H}") Duration ttl) {
    this(client, ttl, Clock.systemUTC());
  }

  UserDirectoryService(UserLookupClient client, Duration ttl, Clock clock) {
    this.client = client;
    this.ttl = ttl;
    this.clock = clock;
  }

  /**
   * Display names for the given ids, keyed by each id exactly as sent. Ids that can't be
   * resolved are absent.
   */
  public Map<String, String> resolveDisplayNames(Collection<String> userIds) {
    Map<String, String> out = new LinkedHashMap<>();
    if (userIds == null || !client.isConfigured()) {
      return out;
    }
    int looked = 0;
    for (String raw : userIds) {
      if (raw == null || out.containsKey(raw) || looked++ >= MAX_IDS) {
        continue;
      }
      resolve(raw).ifPresent(name -> out.put(raw, name));
    }
    return out;
  }

  private Optional<String> resolve(String raw) {
    String bare = bareIdir(raw);
    if (bare == null) {
      return Optional.empty();
    }
    Instant now = clock.instant();
    Entry hit = cache.get(bare);
    if (hit != null && now.isBefore(hit.expires())) {
      return Optional.ofNullable(hit.name());
    }
    try {
      Optional<String> name = client.getIdirDetail(bare)
          .map(u -> displayName(u.firstName(), u.lastName()))
          .filter(StringUtils::hasText);
      cache.put(bare, new Entry(name.orElse(null), now.plus(name.isPresent() ? ttl : MISS_TTL)));
      return name;
    } catch (RuntimeException ex) {
      // Not cached, so the next screen retries.
      LOG.debug("user-lookup miss for {} ({})", raw, ex.getMessage());
      return Optional.empty();
    }
  }

  /**
   * The bare, upper-cased IDIR name: {@code IDIR\jsmith} and {@code jsmith} give
   * {@code JSMITH}. Null for an id in another directory, or a blank one.
   */
  static String bareIdir(String raw) {
    if (!StringUtils.hasText(raw)) {
      return null;
    }
    String id = raw.trim();
    int slash = id.indexOf('\\');
    if (slash >= 0) {
      if (!"IDIR".equalsIgnoreCase(id.substring(0, slash).trim())) {
        return null;
      }
      id = id.substring(slash + 1).trim();
    }
    return id.isEmpty() ? null : id.toUpperCase(Locale.ROOT);
  }

  static String displayName(String firstName, String lastName) {
    String first = firstName == null ? "" : firstName.trim();
    String last = lastName == null ? "" : lastName.trim();
    return (first + " " + last).trim();
  }

  private record Entry(String name, Instant expires) {}
}
