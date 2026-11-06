package ca.bc.gov.nrs.fta.shared.sql;

import java.util.Locale;
import java.util.Map;

/**
 * A search screen's column sort: the {@code sort} request parameter, as
 * {@code <column>,asc} or {@code <column>,desc}, turned into an {@code ORDER BY}.
 *
 * <p>The column is the results table's column key (the DTO field name the frontend
 * shows); each search maps the keys it allows to a SQL expression. Only a mapped key is
 * ever written into the SQL, so the parameter cannot inject anything. A missing,
 * unknown or malformed sort falls back to the search's own default order rather than
 * failing, so a stale bookmark or saved search still runs.
 *
 * <p>Nulls sort last in both directions, and the search's tie-breaker always trails the
 * chosen column. With paging, the tie-breaker is what keeps a row from showing on two
 * pages, so it must make the order total (or close to it).
 */
public final class ColumnSort {

  private ColumnSort() {}

  /**
   * The {@code ORDER BY} clause, with a leading newline.
   *
   * @param sort          the request's {@code sort} parameter; may be null
   * @param columns       the sortable column keys and the SQL expression each sorts on
   * @param defaultOrder  the expression list used when there is no usable sort
   * @param tieBreaker    the expression list appended after the chosen column
   */
  public static String orderBy(
      String sort, Map<String, String> columns, String defaultOrder, String tieBreaker) {
    String chosen = expression(sort, columns);
    if (chosen == null) {
      return "\n ORDER BY " + defaultOrder;
    }
    return "\n ORDER BY " + chosen + ", " + tieBreaker;
  }

  /**
   * The chosen column's expression with direction and null placement, e.g.
   * {@code client_name DESC NULLS LAST}, or null when there is no usable sort.
   */
  public static String expression(String sort, Map<String, String> columns) {
    if (sort == null || sort.isBlank()) {
      return null;
    }
    String[] parts = sort.trim().split("\\s*,\\s*");
    if (parts.length > 2) {
      return null;
    }
    String column = columns.get(parts[0]);
    if (column == null) {
      return null;
    }
    String direction = parts.length == 2 ? parts[1].toLowerCase(Locale.ROOT) : "asc";
    return switch (direction) {
      case "asc" -> column + " ASC NULLS LAST";
      case "desc" -> column + " DESC NULLS LAST";
      default -> null;
    };
  }
}
