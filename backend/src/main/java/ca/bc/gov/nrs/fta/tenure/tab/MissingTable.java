package ca.bc.gov.nrs.fta.tenure.tab;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

/**
 * Lets a tenure tab read its other lists when one of them needs a table this database does not
 * have, or has not granted to the app ({@code ORA-00942}). Not every FTA database carries every
 * legacy table, so instead of failing the whole tab, that list comes back empty and the tab gets
 * a notice saying so. Any other error is rethrown.
 */
public final class MissingTable {

  private static final Logger LOGGER = LoggerFactory.getLogger(MissingTable.class);

  /** Oracle's "table or view does not exist". */
  private static final int ORA_TABLE_OR_VIEW_DOES_NOT_EXIST = 942;

  private static final Pattern ORACLE_POSITION =
      Pattern.compile("Error : 942, Position : (\\d+), Sql = (.*)", Pattern.DOTALL);

  /** A possibly schema-qualified table name. */
  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][\\w$#]*(\\.[A-Za-z][\\w$#]*)?");

  private MissingTable() {}

  /**
   * Runs {@code query}; when it needs a missing table, logs it, adds a notice naming
   * {@code what} to {@code notices} and returns an empty list.
   *
   * @param what    the list being read, as the notice names it (e.g. "Smart form applications")
   * @param notices collects the tab's notices
   */
  public static <T> List<T> orEmpty(Supplier<List<T>> query, String what, List<String> notices) {
    try {
      return query.get();
    } catch (DataAccessException e) {
      if (!isMissingTable(e)) {
        throw e;
      }
      String table = missingTableName(e);
      LOGGER.warn("{} not read: table {} does not exist or is not granted",
          what, table == null ? "(unknown)" : table);
      notices.add(what + " could not be read: "
          + (table == null ? "a table they need" : "table " + table)
          + " is missing from this database or not granted to the application.");
      return List.of();
    }
  }

  /**
   * The missing table's name, from the position Oracle reports for the error ("Error : 942,
   * Position : 509, Sql = ..." in its driver's innermost cause); null when it can't be told.
   */
  static String missingTableName(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      String table = nameAt(oraclePosition(t), oracleSql(t));
      if (table == null) {
        // The driver's exception prints its position in toString(), not in getMessage().
        Matcher m = ORACLE_POSITION.matcher(String.valueOf(t));
        if (m.find()) {
          table = nameAt(Integer.parseInt(m.group(1)), m.group(2));
        }
      }
      if (table != null) {
        return table;
      }
    }
    return null;
  }

  /** {@code OracleDatabaseException.getErrorPosition()}, read reflectively; -1 when absent. */
  private static int oraclePosition(Throwable t) {
    try {
      return t.getClass().getMethod("getErrorPosition").invoke(t) instanceof Integer i ? i : -1;
    } catch (ReflectiveOperationException | RuntimeException ex) {
      return -1;
    }
  }

  /** {@code OracleDatabaseException.getSql()}, read reflectively; null when absent. */
  private static String oracleSql(Throwable t) {
    try {
      return t.getClass().getMethod("getSql").invoke(t) instanceof String sql ? sql : null;
    } catch (ReflectiveOperationException | RuntimeException ex) {
      return null;
    }
  }

  /** The table name starting at {@code position} in {@code sql}, upper-cased. */
  private static String nameAt(int position, String sql) {
    if (sql == null || position < 0 || position >= sql.length()) {
      return null;
    }
    Matcher name = IDENTIFIER.matcher(sql.substring(position));
    return name.lookingAt() ? name.group().toUpperCase(Locale.ROOT) : null;
  }

  static boolean isMissingTable(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof SQLException s && s.getErrorCode() == ORA_TABLE_OR_VIEW_DOES_NOT_EXIST) {
        return true;
      }
    }
    return false;
  }
}
