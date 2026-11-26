package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.billing.service.JasperTemplates;
import ca.bc.gov.nrs.fta.shared.sql.ClientNameSql;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * FTA413 — the Fort St. John Harvest Authorization letter, as a PDF: legacy FTA902's "Letter"
 * button, which ran the Crystal report {@code FTA413-HarvestAuthorization.rpt} with
 * {@code P_HVA_SKEY} and {@code P_USERID}.
 *
 * <p>Its rows came from {@code THE.FTARPT_413} and its "Block List" subreport from
 * {@code THE.FTARPT_413_CUT_BLOCKS}; their SELECTs are ported here, so the letter needs only read
 * access. {@code Sil_Get_Client_Locn_Name} is a CASE ({@code '00'} reads the client's name, any
 * other location its location name) and {@code current_server} is the container name, as the
 * other ported reports read it.
 *
 * <p>FTARPT_413 inner-joins the authority to its spatial submission (HARVEST_AUTHORITY_GEOM →
 * TENURE_APPLICATION_MAP_FEATURE → TENURE_APPLICATION) and to its first licensee, so an authority
 * without either has no letter. Each row it returns is a letter.
 *
 * <p>The layout is {@code reports/FTA413_HarvestAuthorization.jrxml}; see its header for what the
 * encrypted .rpt did not give up.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Component
public class HarvestAuthorizationLetterReport {

  static final String TEMPLATE = "reports/FTA413_HarvestAuthorization.jrxml";
  static final String BLOCK_TEMPLATE = "reports/FTA413_HarvestAuthorization_Blocks.jrxml";

  /**
   * FTARPT_413's cursor. The client is the authority's own licensee (type L, else A) when it has
   * one, else the tenure's: the procedure orders by {@code hva_skey ASC}, and Oracle sorts the
   * tenure rows' null last.
   */
  private static final String LETTER_SQL =
      """
      SELECT hva.forest_file_id                                      AS forest_file_id,
             hva.harvesting_authority_id                             AS harvesting_authority_id,
             hva.issue_date                                          AS issue_date,
             NVL(hva.extend_date, hva.expiry_date)                   AS expiry_date,
             CASE WHEN client.client_locn_code = '00'
                  THEN %s
                  ELSE client.client_locn_name END                   AS client_name,
             client.address_1                                        AS address_1,
             client.city                                             AS city,
             client.province                                         AS province,
             client.country                                          AS country,
             client.postal_code                                      AS postal_code,
             ta.submission_date                                      AS submission_date
        FROM the.harvesting_authority hva
        JOIN the.harvest_authority_geom hvag
             ON hvag.hva_skey = hva.hva_skey
        JOIN the.tenure_application_map_feature tamf
             ON tamf.map_feature_id = hvag.map_feature_id
        JOIN the.tenure_application ta
             ON ta.tenure_app_id = tamf.tenure_app_id
       CROSS JOIN (
             SELECT *
               FROM (SELECT ffc.forest_file_id,
                            NULL hva_skey,
                            ffc.forest_file_client_type_code client_type_code,
                            cl.client_number,
                            cl.client_locn_code,
                            cl.client_locn_name,
                            cl.address_1,
                            cl.city,
                            cl.province,
                            cl.country,
                            cl.postal_code
                       FROM the.forest_file_client ffc
                       JOIN the.client_location cl
                            ON cl.client_number = ffc.client_number
                           AND cl.client_locn_code = ffc.client_locn_code
                       JOIN the.harvesting_authority h
                            ON h.forest_file_id = ffc.forest_file_id
                      WHERE ffc.forest_file_client_type_code IN ('L', 'A')
                        AND h.hva_skey = :hvaSkey
                     UNION
                     SELECT NULL forest_file_id,
                            hac.hva_skey,
                            hac.harvest_auth_client_type_code client_type_code,
                            cl.client_number,
                            cl.client_locn_code,
                            cl.client_locn_name,
                            cl.address_1,
                            cl.city,
                            cl.province,
                            cl.country,
                            cl.postal_code
                       FROM the.harvesting_authority_client hac
                       JOIN the.client_location cl
                            ON cl.client_number = hac.client_number
                           AND cl.client_locn_code = hac.client_locn_code
                      WHERE hac.harvest_auth_client_type_code IN ('L', 'A')
                        AND hac.hva_skey = :hvaSkey
                      ORDER BY hva_skey ASC, client_type_code DESC)
              WHERE ROWNUM = 1) client
        LEFT JOIN the.forest_client fc
             ON fc.client_number = client.client_number
       WHERE hva.hva_skey = :hvaSkey
      """.formatted(ClientNameSql.displayName("fc"));

  /** FTARPT_413_CUT_BLOCKS's cursor. */
  private static final String BLOCKS_SQL =
      "SELECT cb.cut_block_id FROM the.cut_block cb WHERE cb.hva_skey = :hvaSkey";

  private static final String SERVER_SQL = "SELECT SYS_CONTEXT('USERENV', 'CON_NAME') FROM dual";

  private static final List<String> STRINGS = List.of(
      "forest_file_id", "harvesting_authority_id", "client_name", "address_1", "city",
      "province", "country", "postal_code");

  private final NamedParameterJdbcTemplate jdbc;

  public HarvestAuthorizationLetterReport(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** The letter PDF, or null when FTARPT_413 returns no row for the authority. */
  public byte[] render(long hvaSkey, String userId) {
    MapSqlParameterSource p = new MapSqlParameterSource("hvaSkey", hvaSkey);
    String server = server();
    List<Map<String, ?>> rows = jdbc.query(LETTER_SQL, p, (rs, n) -> {
      Map<String, Object> row = new HashMap<>();
      for (String f : STRINGS) {
        row.put(f, rs.getString(f));
      }
      for (String f : List.of("issue_date", "expiry_date", "submission_date")) {
        LocalDate d = rs.getObject(f, LocalDate.class);
        row.put(f, d == null ? null : d.toString());
      }
      row.put("serverid", server);
      return row;
    });
    if (rows.isEmpty()) {
      return null;
    }
    List<Map<String, ?>> blocks = new ArrayList<>(jdbc.query(BLOCKS_SQL, p,
        (rs, n) -> {
          Map<String, Object> b = new HashMap<>();
          b.put("cut_block_id", rs.getString("cut_block_id"));
          return b;
        }));
    return fill(String.valueOf(hvaSkey), userId, rows, blocks);
  }

  /** Fills the letter with its rows and blocks. Package-visible for tests. */
  static byte[] fill(String hvaSkey, String userId, List<Map<String, ?>> rows,
      List<Map<String, ?>> blocks) {
    Map<String, Object> params = new HashMap<>();
    params.put("P_HVA_SKEY", hvaSkey);
    params.put("P_USERID", userId);
    params.put("BLOCKS", blocks);
    params.put("BLOCK_SUBREPORT", JasperTemplates.get(BLOCK_TEMPLATE));
    return JasperTemplates.pdf(TEMPLATE, params, rows);
  }

  private String server() {
    try {
      return jdbc.getJdbcTemplate().queryForObject(SERVER_SQL, String.class);
    } catch (DataAccessException e) {
      return null;
    }
  }
}
