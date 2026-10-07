package ca.bc.gov.nrs.fta.shared.sql;

/**
 * SQL fragments for a client's display name and acronym.
 *
 * <p>These stand in for {@code THE.SIL_GET_CLIENT_NAME} and
 * {@code THE.SIL_GET_CLIENT_ACRONYM}. The application no longer calls PL/SQL,
 * and the functions are thin enough to express directly against
 * {@code FOREST_CLIENT} (both read it: the name through {@code V_CLIENT_PUBLIC},
 * the acronym through {@code CLIENT_ACRONYM}, and each view is a plain projection
 * of that table).
 */
public final class ClientNameSql {

  private ClientNameSql() {}

  /**
   * The client's display name, from a joined {@code FOREST_CLIENT} row.
   *
   * <p>{@code SIL_GET_CLIENT_NAME}: the client name, followed by
   * {@code ", first middle"} when either legal name part is present. An
   * individual reads "SMITH, JANE A"; a company reads as its name alone. Null
   * when the client does not exist, since the joined row is then null.
   *
   * @param alias the alias of a joined {@code FOREST_CLIENT} (or
   *              {@code V_CLIENT_PUBLIC}) row
   */
  public static String displayName(String alias) {
    return "CASE WHEN TRIM(" + alias + ".legal_first_name) IS NOT NULL"
        + " OR TRIM(" + alias + ".legal_middle_name) IS NOT NULL"
        + " THEN " + alias + ".client_name || ', ' || " + alias + ".legal_first_name"
        + " || ' ' || " + alias + ".legal_middle_name"
        + " ELSE " + alias + ".client_name END";
  }

  /**
   * The client's acronym, or its number when it has none.
   *
   * <p>{@code SIL_GET_CLIENT_ACRONYM} returns the number it was given whenever
   * its lookup fails, whether the client is unknown or has no acronym.
   *
   * @param alias        the alias of a left-joined {@code FOREST_CLIENT} row
   * @param clientNumber the SQL expression for the client number itself
   */
  public static String acronymOrNumber(String alias, String clientNumber) {
    return "NVL(" + alias + ".client_acronym, " + clientNumber + ")";
  }
}
