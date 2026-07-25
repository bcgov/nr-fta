package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.tenure.dto.RoadDetailDto;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Road-section detail business logic.
 *
 * <p>Ports the legacy Oracle package {@code THE.FTA_131_ROADSECTION} (its
 * {@code get} procedure driven by {@code mainline}) to a native query against
 * the shared {@code THE} schema. Column selection matches the package's
 * {@code rec_rsection} record; the derived columns ({@code mark_list},
 * {@code section_current_amend_id}, {@code segments_enabled_ind}) inline the
 * package helper functions ({@code get_section_marks},
 * {@code get_latest_rs_amend}, {@code get_segments_enabled}) as scalar
 * subqueries, and the transferred-from/to links reproduce the two outer joins
 * against {@code road_section_key_event} (transfer events only). The optional
 * {@code forestFileId} filter is applied only when supplied (NVL-style),
 * matching the legacy behaviour.
 *
 * <p>The SQL runs against the BC Gov shared Oracle ({@code THE}) via the
 * configured {@code DataSource}; there is no local database, so it is exercised
 * only in a deployed environment.
 */
@Service
public class RoadDetailService {

  private final NamedParameterJdbcTemplate jdbc;

  public RoadDetailService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String DETAIL_SQL =
      """
      SELECT rs.forest_file_id           AS forest_file_id,
             rs.road_section_id          AS road_section_id,
             (SELECT LISTAGG(DISTINCT rsas.timber_mark, ',') WITHIN GROUP (ORDER BY rsas.timber_mark)
                FROM the.road_permit rp2
                JOIN the.road_section rs2  ON rp2.forest_file_id = rs2.forest_file_id
                JOIN the.road_prmt_amend rpa2
                       ON rs2.forest_file_id = rpa2.forest_file_id
                      AND rs2.road_section_id = rpa2.road_section_id
                JOIN the.road_section_amend_segment rsas
                       ON rpa2.road_prmt_amend_skey = rsas.road_prmt_amend_skey
               WHERE rs2.forest_file_id = rs.forest_file_id
                 AND rs2.road_section_id = rs.road_section_id
                 AND rsas.timber_mark IS NOT NULL) AS mark_list,
             (SELECT MAX(rpa3.amendment_id)
                FROM the.road_prmt_amend rpa3
               WHERE rpa3.forest_file_id = rs.forest_file_id
                 AND rpa3.road_section_id = rs.road_section_id) AS section_current_amend_id,
             rs.road_sect_name           AS road_sect_name,
             TO_CHAR(rs.road_sect_length, 'FM9999990.0000') AS road_sect_length,
             TO_CHAR(rs.road_orig_length, 'FM9999990.0000') AS road_orig_length,
             rs.road_section_status_code AS road_section_status_code,
             rs.within_alr_ind           AS within_alr_ind,
             rs.district_admn_zone       AS district_admn_zone,
             rs.section_width            AS section_width,
             xfered_from_rs.forest_file_id_from  AS xfered_from_forest_file_id,
             xfered_from_rs.road_section_id_from AS xfered_from_road_section_id,
             xfered_to_rs.forest_file_id_to      AS xfered_to_forest_file_id,
             xfered_to_rs.road_section_id_to     AS xfered_to_road_section_id,
             rs.retirement_date          AS retirement_date,
             rs.revision_count           AS revision_count,
             CASE
               WHEN (rs.road_section_status_code = 'PE' OR rs.retirement_date IS NOT NULL) THEN 'N'
               ELSE 'Y'
             END                         AS update_enabled_ind,
             (SELECT DECODE(COUNT(rsas4.road_section_amend_seg_id), 0, 'N', 'Y')
                FROM the.road_prmt_amend rpa4
                JOIN the.road_section_amend_segment rsas4
                       ON rpa4.road_prmt_amend_skey = rsas4.road_prmt_amend_skey
               WHERE rpa4.forest_file_id = rs.forest_file_id
                 AND rpa4.road_section_id = rs.road_section_id) AS segments_enabled_ind
        FROM the.road_section rs
        LEFT JOIN the.road_section_key_event xfered_from_rs
               ON xfered_from_rs.road_section_key_event_code = 'T'
              AND rs.forest_file_id = xfered_from_rs.forest_file_id_to
              AND rs.road_section_id = xfered_from_rs.road_section_id_to
        LEFT JOIN the.road_section_key_event xfered_to_rs
               ON xfered_to_rs.road_section_key_event_code = 'T'
              AND rs.forest_file_id = xfered_to_rs.forest_file_id_from
              AND rs.road_section_id = xfered_to_rs.road_section_id_from
       WHERE rs.road_section_id = :roadId
         AND (:forestFileId IS NULL OR rs.forest_file_id = :forestFileId)
       ORDER BY rs.forest_file_id, rs.road_section_id
      """;

  private static final RowMapper<RoadDetailDto> ROW_MAPPER = (rs, rowNum) -> new RoadDetailDto(
      rs.getString("forest_file_id"),
      rs.getString("road_section_id"),
      rs.getString("mark_list"),
      rs.getString("section_current_amend_id"),
      rs.getString("road_sect_name"),
      rs.getString("road_sect_length"),
      rs.getString("road_orig_length"),
      rs.getString("road_section_status_code"),
      rs.getString("within_alr_ind"),
      rs.getString("district_admn_zone"),
      rs.getBigDecimal("section_width"),
      rs.getString("xfered_from_forest_file_id"),
      rs.getString("xfered_from_road_section_id"),
      rs.getString("xfered_to_forest_file_id"),
      rs.getString("xfered_to_road_section_id"),
      rs.getObject("retirement_date", java.time.LocalDate.class),
      rs.getObject("revision_count", Integer.class),
      rs.getString("update_enabled_ind"),
      rs.getString("segments_enabled_ind"));

  /**
   * Road-section detail — mirrors {@code FTA_131_ROADSECTION.get}.
   *
   * @param roadId       road-section id (the {@code road_section_id} key)
   * @param forestFileId owning forest-file id filter, or null to match any file
   * @return the matching road-section detail, or empty if none
   */
  public Optional<RoadDetailDto> getRoad(String roadId, String forestFileId) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("roadId", roadId)
        .addValue("forestFileId", blankToNull(forestFileId));

    return jdbc.query(DETAIL_SQL, params, ROW_MAPPER).stream().findFirst();
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
