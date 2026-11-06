package ca.bc.gov.nrs.fta.apt.service;

import java.sql.Types;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * The licences a commitment can be entered against from a partition apportionment
 * ({@code APT_FOREST_FILE_ID_GET}'s query, against the tables): the management unit's held
 * licences of the apportionment's FOA type, plus, for an undesignated unit (TSA, type U), every
 * held FOA-type licence outside a management unit (type Z).
 */
@Service
public class AptForestFileIdService {

  private static final String SQL =
      """
      SELECT lic.forest_file_id
        FROM the.apt_licences lic
        JOIN the.prov_forest_use pfu ON lic.forest_file_id = pfu.forest_file_id
        JOIN the.apt_management_units mu
          ON mu.apt_mgmt_unit_st = pfu.mgmt_unit_type
         AND mu.mgmt_unit_number = pfu.mgmt_unit_id
       WHERE pfu.file_status_st IN ('HI', 'HS')
         AND mu.id = :managementUnitId
         AND lic.apt_foa_st = :foaType
      UNION
      SELECT l.forest_file_id
        FROM the.apt_licences l
        JOIN the.prov_forest_use p ON p.forest_file_id = l.forest_file_id
       CROSS JOIN the.apt_management_units m
       WHERE p.mgmt_unit_type = 'Z'
         AND p.file_status_st IN ('HI', 'HS')
         AND m.id = :managementUnitId
         AND m.apt_mgmt_unit_st = 'U'
         AND l.apt_foa_st = :foaType
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public AptForestFileIdService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * @param managementUnitId the management unit's id ({@code APT_MANAGEMENT_UNITS.ID})
   * @param foaType          form of agreement type code
   */
  public List<String> forestFileIds(long managementUnitId, String foaType) {
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("managementUnitId", managementUnitId, Types.NUMERIC)
        .addValue("foaType", foaType == null || foaType.isBlank() ? null
            : foaType.trim().toUpperCase(Locale.ROOT), Types.VARCHAR);
    return jdbc.queryForList(SQL, p, String.class).stream().distinct().sorted().toList();
  }
}
