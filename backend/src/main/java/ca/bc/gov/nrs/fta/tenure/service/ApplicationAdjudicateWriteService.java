package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.tenure.dto.ApplicationAdjudicateRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write operations for the Inbox tenure-application adjudication screen.
 *
 * <p>Ports the write path of the legacy {@code THE.FTA_302_ADJUDCOMMENT} package
 * to an UPDATE against {@code THE.TENURE_APPLICATION} via {@link
 * NamedParameterJdbcTemplate}. Two mainline branches are reproduced:
 *
 * <ul>
 *   <li>{@code ADJUDICATION} — the {@code adjudication} procedure sets {@code
 *       adjudication_ind = 'Y'}, stamps {@code adjudicated_by} /
 *       {@code adjudication_date} and the audit columns, and bumps
 *       {@code revision_count}.
 *   <li>{@code SAVE} — the {@code change} procedure updates only
 *       {@code adjudication_comment} and the audit columns.
 * </ul>
 *
 * <p>The legacy {@code adjudication} procedure additionally cascades status
 * changes to {@code harvesting_authority} / {@code prov_forest_use} and runs the
 * {@code fta_find_conflict} pre-check; that downstream business logic should be
 * reproduced here as the module is hardened. The legacy optimistic-lock guard
 * ({@code AND revision_count = TO_NUMBER(p_revision_count)}) is applied when a
 * revision count is supplied. Runs against the shared {@code THE} Oracle schema —
 * there is no local database, so it is exercised only in a deployed environment.
 */
@Service
public class ApplicationAdjudicateWriteService {

  private static final String ACTION_SAVE = "SAVE";

  private final NamedParameterJdbcTemplate jdbc;

  public ApplicationAdjudicateWriteService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // Mirrors FTA_302_ADJUDCOMMENT.adjudication: full adjudication of the application.
  private static final String ADJUDICATION_SQL =
      """
      UPDATE the.tenure_application
         SET adjudication_ind = 'Y',
             adjudicated_by = :userId,
             adjudication_comment = :adjudicationComment,
             adjudication_date = SYSDATE,
             update_userid = :userId,
             update_timestamp = SYSDATE,
             revision_count = revision_count + 1
       WHERE tenure_app_id = :tenureAppId
      """;

  // Mirrors FTA_302_ADJUDCOMMENT.change (invoked by the SAVE branch): comment-only save.
  private static final String SAVE_SQL =
      """
      UPDATE the.tenure_application
         SET adjudication_comment = :adjudicationComment,
             update_userid = :userId,
             update_timestamp = SYSDATE,
             revision_count = revision_count + 1
       WHERE tenure_app_id = :tenureAppId
      """;

  // Legacy optimistic-lock guard from both procedures.
  private static final String REVISION_GUARD = " AND revision_count = :revisionCount";

  /**
   * Adjudicate (or save a comment against) a tenure application. Returns the
   * number of rows updated.
   *
   * @param esfId   the tenure application id (path {@code p_tenure_app_id})
   * @param request the adjudication payload
   * @param userId  the authenticated user id (audit + {@code adjudicated_by})
   */
  @Transactional
  public int adjudicate(String esfId, ApplicationAdjudicateRequest request, String userId) {
    boolean saveOnly = ACTION_SAVE.equalsIgnoreCase(request.action());
    String sql = saveOnly ? SAVE_SQL : ADJUDICATION_SQL;
    if (request.revisionCount() != null) {
      sql = sql + REVISION_GUARD;
    }

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("tenureAppId", esfId)
            .addValue("adjudicationComment", request.adjudicationComment())
            .addValue("revisionCount", request.revisionCount())
            .addValue("userId", userId);
    return jdbc.update(sql, params);
  }
}
