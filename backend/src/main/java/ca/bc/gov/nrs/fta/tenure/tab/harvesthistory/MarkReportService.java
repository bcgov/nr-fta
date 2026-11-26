package ca.bc.gov.nrs.fta.tenure.tab.harvesthistory;

import ca.bc.gov.nrs.fta.billing.service.JasperTemplates;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * The FTA404 Mark Report — what FTA990 Harvest History's "Print Mark Report" button produced
 * for the mark on screen ({@code Fta990HarvHistAction.handlePrint}, report
 * {@code FTA404-MarkReport.rpt} with {@code p_user_id} and {@code p_timber_mark}).
 *
 * <p>Ports {@code FTAR404P}: the mark's harvest history per billing year, cutting permit,
 * billing type, species and log grade, with the billed volume and the reserve, bonus,
 * silviculture and development dollars. Its arithmetic is kept: the four amounts are added
 * without {@code NVL} (a row missing one counts for nothing in the total), and the species,
 * billing type and grade descriptions are inner joins (a row without one is left out). Its
 * {@code current_server} is the container name, as the FTA675 reports read it. The layout is
 * the legacy Jasper Server report. Legacy queued it on Jasper Server; it is produced on request
 * here.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so the
 * query is exercised only in a deployed environment.
 */
@Service
public class MarkReportService {

  /** The PDF's template. */
  static final String TEMPLATE = "reports/FTA404_MarkReport.jrxml";

  /** FTAR404P's cursor, for one mark. */
  private static final String SQL =
      """
      SELECT tm.timber_mark                   AS timber_mark,
             hh.billing_year                  AS billing_year,
             tm.cutting_permit_id             AS cutting_permit_id,
             bt.expanded_result               AS billing_type_code,
             hts.description                  AS hdbs_tree_species,
             sgc.description                  AS log_grade,
             SUM(hh.volume_billed)            AS volume_billed,
             SUM(hh.reserve_stmpg_amt + hh.bonus_stumpage_amt
                 + hh.silv_levy_amount + hh.dev_levy_amount) AS total_amount,
             tm.catastrophic_ind              AS catastrophic_ind
        FROM the.harvest_history hh
        JOIN the.v_fta_timber_mark_vj tm ON tm.timber_mark = hh.timber_mark
        JOIN the.hdbs_tree_species hts   ON hts.hdbs_tree_species = hh.hdbs_tree_species
        JOIN the.code_list_table bt      ON bt.column_name = 'BILLING_TYPE_CODE'
                                        AND bt.code_argument = hh.billing_type_code
        JOIN the.scale_grade_code sgc    ON sgc.scale_grade_code = hh.log_grade
       WHERE tm.timber_mark = :timberMark
       GROUP BY tm.timber_mark, hh.billing_year, tm.cutting_permit_id, bt.expanded_result,
                hts.description, sgc.description, tm.catastrophic_ind
       ORDER BY 1, 2, 3, 4, 5, 6
      """;

  private static final String SERVER_SQL = "SELECT SYS_CONTEXT('USERENV', 'CON_NAME') FROM dual";

  private final NamedParameterJdbcTemplate jdbc;

  public MarkReportService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** The PDF and the name to save it under. */
  public record Pdf(String filename, byte[] content) {}

  /**
   * The report for one timber mark — no rows gives the headings and an empty total, as legacy.
   *
   * @param timberMark the mark on screen
   * @param userId     who asked, printed in the heading
   */
  public Pdf render(String timberMark, String userId) {
    String mark = MarkReportRules.timberMark(timberMark);
    String server = jdbc.queryForObject(SERVER_SQL, Map.of(), String.class);
    List<Map<String, ?>> rows = new ArrayList<>();
    jdbc.query(SQL, new MapSqlParameterSource("timberMark", mark), rs -> {
      Map<String, Object> row = new HashMap<>();
      row.put("timber_mark", rs.getString("timber_mark"));
      row.put("billing_year", rs.getBigDecimal("billing_year"));
      row.put("cutting_permit_id", rs.getString("cutting_permit_id"));
      row.put("billing_type_code", rs.getString("billing_type_code"));
      row.put("hdbs_tree_species", rs.getString("hdbs_tree_species"));
      row.put("log_grade", rs.getString("log_grade"));
      row.put("volume_billed", rs.getBigDecimal("volume_billed"));
      row.put("total_amount", rs.getBigDecimal("total_amount"));
      row.put("catastrophic_ind", rs.getString("catastrophic_ind"));
      row.put("db_name", server);
      rows.add(row);
    });
    return new Pdf(MarkReportRules.filename(mark),
        JasperTemplates.pdf(TEMPLATE, MarkReportRules.parameters(mark, userId), rows));
  }
}
