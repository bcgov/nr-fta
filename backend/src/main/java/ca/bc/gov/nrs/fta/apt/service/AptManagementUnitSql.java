package ca.bc.gov.nrs.fta.apt.service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * What the management unit and partition services share now that they run their own SQL
 * instead of the {@code THE.APT_*} routines: the code-table views those routines read, written
 * out against their tables, the routines' error convention, and null-safe readers and binders.
 *
 * <p><b>Errors.</b> The routines caught every Oracle error ({@code WHEN OTHERS}), returned
 * {@code SQLERRM} as the message, and APT2 showed it: 409 for a duplicate (ORA-00001) or a
 * delete blocked by child rows (ORA-02292), 400 for anything else, unless the routine had its
 * own wording for that case. {@link #dbError} does the same with the statement's Oracle error;
 * a lost connection or a timeout is not an Oracle error the routine could have caught, so it
 * is rethrown unchanged.
 */
final class AptManagementUnitSql {

  static final int ORA_DUPLICATE = 1;
  static final int ORA_CHILD_RECORD = 2292;

  /**
   * {@code APT_MGMT_UNIT_NAMES_V}: every TFL, TSA and woodlot in the code tables with its APT
   * type ({@code T}, {@code U}, {@code F}), plus the one {@code Z} type, which has no number.
   */
  static final String MU_NAMES =
      """
      (SELECT DECODE(clt.column_name, 'TFL_NUMBER', 'T', 'TSA_NUMBER', 'U', 'WL_NUMBER', 'F')
                                    AS apt_mgmt_unit_st,
              clt.code_argument     AS mgmt_unit_number,
              clt.expanded_result   AS mgmt_unit_name
         FROM the.code_list_table clt
        WHERE clt.column_name IN ('TFL_NUMBER', 'TSA_NUMBER', 'WL_NUMBER')
       UNION
       SELECT DECODE(clt2.column_name, 'MGMT_UNIT_TYPE', 'Z'),
              NULL,
              clt2.expanded_result
         FROM the.code_list_table clt2
        WHERE clt2.column_name = 'MGMT_UNIT_TYPE'
          AND clt2.code_argument = 'Z')""";

  /** {@code APT_MGMT_UNIT_SOURCE_V}: an APT management unit's id with its code-table name. */
  static final String MU_SOURCE =
      "(SELECT mus.id, munv.mgmt_unit_name, munv.apt_mgmt_unit_st, munv.mgmt_unit_number"
          + " FROM the.apt_management_units mus"
          + " JOIN " + MU_NAMES + " munv"
          + " ON mus.apt_mgmt_unit_st = munv.apt_mgmt_unit_st"
          + " AND mus.mgmt_unit_number = munv.mgmt_unit_number)";

  /** {@code APT_MU_TYPE_V}'s codes: the management unit types in the APT subset. */
  static final String MU_TYPE_CODES =
      """
      (SELECT cs.code_argument
         FROM the.code_subset_tbl cs
         JOIN the.code_list_table clt
           ON clt.code_argument = cs.code_argument
          AND clt.column_name = cs.column_name
        WHERE cs.code_subset_name = 'APT_MGMT_UNIT_ST')""";

  /** {@code APT_PART_TYPES_V}: the partition types. */
  static final String PART_TYPES =
      """
      (SELECT clt.code_argument   AS part_type_cd,
              clt.expanded_result AS part_type_desc
         FROM the.code_list_table clt
         JOIN the.code_subset_tbl cst
           ON clt.column_name = cst.column_name
          AND clt.code_argument = cst.code_argument
        WHERE cst.code_subset_name = 'APT_PARTITION_ST')""";

  /** {@code APT_FOA_TYPES_V}: the form of agreement types, each with its management unit type. */
  static final String FOA_TYPES =
      """
      (SELECT clt.code_argument,
              clt.expanded_result,
              acat.apt_mgmt_unit_st
         FROM the.code_list_table clt
         JOIN the.code_subset_tbl cst
           ON clt.column_name = cst.column_name
          AND clt.code_argument = cst.code_argument
         JOIN the.apt_apportionment_categories acat
           ON acat.apt_foa_st = clt.code_argument
        WHERE cst.code_subset_name = 'APT_FOA_ST')""";

  private AptManagementUnitSql() {}

  // ── errors ──

  /**
   * The routines' {@code WHEN OTHERS}: 409 for ORA-00001 or ORA-02292, 400 for any other Oracle
   * error, the message being the Oracle error ({@code SQLERRM}'s first line). Anything that is
   * not an Oracle error, or is a connection or timeout failure, comes back unchanged.
   */
  static RuntimeException dbError(DataAccessException ex) {
    int code = oraCode(ex);
    if (code == 0 || ex instanceof TransientDataAccessException
        || ex instanceof DataAccessResourceFailureException) {
      return ex;
    }
    HttpStatus status = code == ORA_DUPLICATE || code == ORA_CHILD_RECORD
        ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
    return new ResponseStatusException(status, oraMessage(ex));
  }

  /**
   * {@link #dbError}, except that the Oracle error {@code code} gets the routine's own
   * {@code message} and a 409.
   */
  static RuntimeException dbError(DataAccessException ex, int code, String message) {
    return oraCode(ex) == code ? conflict(message) : dbError(ex);
  }

  /** True for an Oracle error the routines' {@code WHEN OTHERS} would have caught. */
  static boolean isOracleError(DataAccessException ex) {
    return dbError(ex) != ex;
  }

  /** The vendor code of the Oracle error under {@code ex}, or 0 when there is none. */
  static int oraCode(Throwable ex) {
    SQLException s = sqlException(ex);
    return s == null ? 0 : s.getErrorCode();
  }

  /** The Oracle error's first line, e.g. {@code ORA-00001: unique constraint (...) violated}. */
  static String oraMessage(Throwable ex) {
    SQLException s = sqlException(ex);
    String m = s != null && s.getMessage() != null ? s.getMessage() : String.valueOf(ex.getMessage());
    int nl = m.indexOf('\n');
    return (nl < 0 ? m : m.substring(0, nl)).trim();
  }

  static ResponseStatusException conflict(String message) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message);
  }

  static ResponseStatusException badRequest(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private static SQLException sqlException(Throwable ex) {
    for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
      if (t instanceof SQLException s && s.getErrorCode() != 0) {
        return s;
      }
    }
    return null;
  }

  // ── binding: null-safe parameter values ──

  /** A blank string bound as null, as the routines were given it. */
  static String str(String s) {
    return s == null || s.isBlank() ? null : s;
  }

  static Date date(LocalDate d) {
    return d == null ? null : Date.valueOf(d);
  }

  // ── reading ──

  static Long getLong(ResultSet rs, String col) throws SQLException {
    BigDecimal v = rs.getBigDecimal(col);
    return v == null ? null : v.longValue();
  }

  static LocalDate getDate(ResultSet rs, String col) throws SQLException {
    Timestamp t = rs.getTimestamp(col);
    return t == null ? null : t.toLocalDateTime().toLocalDate();
  }

  static LocalDateTime getDateTime(ResultSet rs, String col) throws SQLException {
    Timestamp t = rs.getTimestamp(col);
    return t == null ? null : t.toLocalDateTime();
  }
}
