package ca.bc.gov.nrs.fta.tenure.tab.rotations;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reads the tenure the rotation tabs work on ({@link RotationsTenure}) and enforces a tab's
 * {@link RotationsRules} before a write.
 *
 * <p>Ports what FTA611/612/613's GET take from {@code FTA_GET_FILE_HEADER} and the
 * {@code FTA_VALID_GRAZING/HAY/RANGE_FILE_TYPE} and {@code FTA_APPLICATION_PENDING} functions,
 * inlined as scalar subqueries. The term is read with MIN/MAX so a file with more than one
 * {@code TENURE_TERM} row (legacy assumes one) cannot fail the read.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class RotationsTenureService {

  private static final String TENURE_SQL =
      """
      SELECT pfu.file_type_code,
             pfu.file_status_st,
             pfu.revision_count,
             (SELECT MIN(tt.legal_effective_dt) FROM the.tenure_term tt
               WHERE tt.forest_file_id = pfu.forest_file_id)                  AS term_start,
             (SELECT MAX(NVL(tt.current_expiry_dt, tt.initial_expiry_dt)) FROM the.tenure_term tt
               WHERE tt.forest_file_id = pfu.forest_file_id)                  AS term_end,
             (SELECT COUNT(*) FROM the.grazing_file_type_code g
               WHERE g.grazing_file_type_code = pfu.file_type_code)           AS grazing_type,
             (SELECT COUNT(*) FROM the.hay_file_type_code h
               WHERE h.hay_file_type_code = pfu.file_type_code)               AS hay_type,
             (SELECT COUNT(*) FROM the.range_file_type_code r
               WHERE r.range_file_type_code = pfu.file_type_code)             AS range_type,
             (SELECT COUNT(*) FROM the.tenure_application ta
               WHERE ta.forest_file_id = pfu.forest_file_id
                 AND ta.tenure_application_type_code IN ('FIL', 'RNG', 'RP')
                 AND ta.tenure_application_state_code = 'INB'
                 AND ta.tenure_app_purp_code = 'N')                           AS app_pending
        FROM the.prov_forest_use pfu
       WHERE pfu.forest_file_id = :forestFileId
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public RotationsTenureService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * The tenure.
   *
   * @throws ResponseStatusException 404 when the file does not exist
   */
  public RotationsTenure load(String forestFileId) {
    List<RotationsTenure> rows = jdbc.query(
        TENURE_SQL,
        Map.of("forestFileId", forestFileId),
        (rs, n) -> new RotationsTenure(
            rs.getString("file_type_code"),
            rs.getString("file_status_st"),
            rs.getObject("revision_count", Long.class),
            year(rs.getObject("term_start", LocalDate.class)),
            year(rs.getObject("term_end", LocalDate.class)),
            rs.getLong("grazing_type") > 0,
            rs.getLong("hay_type") > 0,
            rs.getLong("range_type") > 0,
            rs.getLong("app_pending") > 0));
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenure not found.");
    }
    return rows.get(0);
  }

  /**
   * The tenure, once {@code rules} allow the tab's writes on it.
   *
   * @throws ResponseStatusException 404 when the file does not exist; 409 with the rules'
   *     reason when the tab does not apply or is closed to changes
   */
  public RotationsTenure loadWritable(
      String forestFileId, Function<RotationsTenure, RotationsRules> rules) {
    RotationsTenure t = load(forestFileId);
    RotationsRules r = rules.apply(t);
    if (!r.edit()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, r.reason());
    }
    return t;
  }

  private static Integer year(LocalDate d) {
    return d == null ? null : d.getYear();
  }
}
