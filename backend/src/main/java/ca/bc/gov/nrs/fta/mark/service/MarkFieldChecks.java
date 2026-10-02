package ca.bc.gov.nrs.fta.mark.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The FTA510 checks shared by creating a mark application and saving a mark — the legacy
 * form's "Save" validator chain ({@code Fta510PrivateMarkForm}) for the application fields,
 * so both paths reject exactly the same input.
 */
@Component
public class MarkFieldChecks {

  /** The application fields: District and the legacy screen's application section. */
  public record Application(
      String forestDistrict,
      String permitBlockLocn,
      String legal,
      String ltoPid,
      BigDecimal area,
      String mgmtUnitType,
      String mgmtUnitId,
      String cascade,
      String reg,
      String comp) {}

  static final String DISTRICT_SQL =
      """
      SELECT COUNT(*) FROM the.org_unit
       WHERE TO_CHAR(org_unit_no) = :code AND org_level_code = 'D'
         AND SYSDATE BETWEEN effective_date AND expiry_date
      """;
  static final String CASCADE_SQL =
      """
      SELECT COUNT(*) FROM the.cascade_split_code
       WHERE cascade_split_code = :code AND SYSDATE BETWEEN effective_date AND expiry_date
      """;
  static final String METHOD_SQL =
      """
      SELECT COUNT(*) FROM the.marking_method_code
       WHERE marking_method_code = :code AND SYSDATE BETWEEN effective_date AND expiry_date
      """;
  static final String INSTRUMENT_SQL =
      """
      SELECT COUNT(*) FROM the.marking_instrument_code
       WHERE marking_instrument_code = :code AND SYSDATE BETWEEN effective_date AND expiry_date
      """;

  /** Private mark types (B08, B09, B14…) — the FTA510 Mark Type list; B15/B16 are view-only. */
  static final String MARK_TYPE_SQL =
      """
      SELECT COUNT(*) FROM the.private_mark_type_code
       WHERE private_mark_type_code = :code
         AND private_mark_type_code NOT IN ('B15', 'B16')
         AND SYSDATE BETWEEN effective_date AND expiry_date
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public MarkFieldChecks(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Adds a message to {@code e} for each invalid application field.
   *
   * @param stored the values on record, or null for a new application — a code already on
   *               record may stay even if it has since expired; a new or changed one must be
   *               current
   * @param v      the values to save
   */
  public void validateApplication(List<String> e, Application stored, Application v) {
    if (blank(v.forestDistrict())) {
      e.add("District is required.");
    } else if (!unchangedOrActive(
        stored == null ? null : stored.forestDistrict(), v.forestDistrict(), DISTRICT_SQL)) {
      e.add("District is not a current district.");
    }
    required(e, v.permitBlockLocn(), "Geographic Location", 50);
    required(e, v.legal(), "Legal", 4000);
    required(e, v.ltoPid(), "LTO PID", 23);
    if (v.area() == null) {
      e.add("Area is required.");
    } else if (v.area().signum() < 0 || v.area().compareTo(new BigDecimal("9999.9")) > 0) {
      e.add("Area must be between 0 and 9999.9 ha.");
    } else if (v.area().stripTrailingZeros().scale() > 1) {
      e.add("Area can have at most one decimal place.");
    }
    if (blank(v.mgmtUnitType())) {
      e.add("Management Unit type is required.");
    } else if (v.mgmtUnitType().length() != 1) {
      e.add("Management Unit type is a single character.");
    } else if (v.mgmtUnitId() != null && !v.mgmtUnitId().matches("\\d{1,4}")) {
      e.add("Management Unit ID must be up to four digits.");
    } else if (!mgmtUnitExists(v.mgmtUnitType(), v.mgmtUnitId())) {
      e.add("Management Unit " + v.mgmtUnitType()
          + (v.mgmtUnitId() == null ? "" : " " + v.mgmtUnitId()) + " does not exist.");
    }
    if (blank(v.cascade())) {
      e.add("Cascade is required.");
    } else if (!unchangedOrActive(
        stored == null ? null : stored.cascade(), v.cascade(), CASCADE_SQL)) {
      e.add("Cascade is not a current code.");
    }
    if (v.reg() != null && !v.reg().matches("\\d{1,2}")) {
      e.add("Reg must be a number from 0 to 99.");
    }
    if (v.comp() != null && !v.comp().matches("\\d{1,3}")) {
      e.add("Comp must be a number from 0 to 999.");
    }
  }

  /**
   * True when the code is unchanged, or is a current one. Legacy rejects an expired code
   * ({@code CodeExpiryValidator}); a record already holding one keeps it.
   */
  public boolean unchangedOrActive(String stored, String value, String sql) {
    if (stored != null && Objects.equals(stored, value)) {
      return true;
    }
    Long n = jdbc.queryForObject(sql, new MapSqlParameterSource("code", value), Long.class);
    return n != null && n > 0;
  }

  /** {@code SIL_EDIT_MGMT_UNIT}: the type/id pair is a FOREST_MGMT_UNIT. */
  public boolean mgmtUnitExists(String type, String unitId) {
    Long n = jdbc.queryForObject(
        """
        SELECT COUNT(*) FROM the.forest_mgmt_unit
         WHERE mgmt_unit_type_code = :type
           AND NVL(mgmt_unit_id, ' ') = NVL(:unitId, ' ')
        """,
        new MapSqlParameterSource().addValue("type", type).addValue("unitId", unitId),
        Long.class);
    return n != null && n > 0;
  }

  /** LPAD(reg,2,'0') || LPAD(comp,3,'0'), as legacy builds MAP_REFERENCE_ID; null when both blank. */
  public static String mapReferenceId(String reg, String comp) {
    String id = (reg == null ? "" : leftPad(reg, 2)) + (comp == null ? "" : leftPad(comp, 3));
    return id.isEmpty() ? null : id;
  }

  /** Quota type follows the management unit: TSA/TFL quota (A), else non-quota (J). */
  public static String quotaType(String mgmtUnitType) {
    return "T".equals(mgmtUnitType) || "F".equals(mgmtUnitType) ? "A" : "J";
  }

  static void required(List<String> e, String value, String label, int max) {
    if (blank(value)) {
      e.add(label + " is required.");
    } else if (value.length() > max) {
      e.add(label + " can be at most " + max + " characters.");
    }
  }

  static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  static String trim(String s) {
    return blank(s) ? null : s.trim();
  }

  private static String leftPad(String s, int width) {
    return "0".repeat(Math.max(0, width - s.length())) + s;
  }
}
