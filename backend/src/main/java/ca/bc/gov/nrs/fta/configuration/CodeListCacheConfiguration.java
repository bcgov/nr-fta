package ca.bc.gov.nrs.fta.configuration;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * An in-memory cache for the code lists behind the search screens' dropdowns, emptied on a
 * fixed schedule.
 *
 * <p>Each search screen opens with several code-list requests, and each one was a fresh query
 * against Oracle. Code tables change rarely — an effective or expiry date moves, a code is
 * added — so the lists are held in memory and the whole cache is cleared every
 * {@code fta.code-lists.cache-eviction} (an ISO-8601 duration, one hour by default). The next
 * request after a clear reloads that list, so a change to a code table is visible within one
 * interval without a restart.
 *
 * <p>Deliberately simple: a periodic clear rather than a per-entry time-to-live, so no cache
 * library is needed, and every list turns over together. Each pod holds its own copy and
 * clears it on its own clock; with lists this stable, pods briefly disagreeing after a code
 * change is acceptable.
 */
@Configuration
@EnableCaching
@EnableScheduling
public class CodeListCacheConfiguration {

  /** The cache every code list is held in, keyed by list (and filter, where there is one). */
  public static final String CODE_LISTS = "codeLists";

  private static final Logger LOGGER = LoggerFactory.getLogger(CodeListCacheConfiguration.class);

  private final CacheManager cacheManager = new ConcurrentMapCacheManager(CODE_LISTS);

  @Bean
  public CacheManager cacheManager() {
    return cacheManager;
  }

  /**
   * Empties the code-list cache.
   *
   * <p>Clears the cache directly rather than through {@code @CacheEvict}, so it does not depend
   * on the scheduler invoking this method through a proxy.
   */
  @Scheduled(
      initialDelayString = "${fta.code-lists.cache-eviction:PT1H}",
      fixedRateString = "${fta.code-lists.cache-eviction:PT1H}")
  public void evictCodeLists() {
    Objects.requireNonNull(cacheManager.getCache(CODE_LISTS)).clear();
    LOGGER.debug("Code-list cache cleared; lists reload from the database on next request.");
  }
}
