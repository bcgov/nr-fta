package ca.bc.gov.nrs.fta.shared.sql;

import java.util.Locale;

/**
 * A typed identifier criterion (forest file ID, cutting permit, timber mark, cut block,
 * certificate) as the search queries bind it.
 *
 * <p>FTA stores these identifiers upper-cased, so a search matches whatever case the user typed
 * by upper-casing the criterion, not the column: the column stays bare, so its index still
 * serves the equality and prefix ({@code LIKE :x || '%'}) predicates.
 */
public final class SearchIds {

  private SearchIds() {}

  /** The criterion trimmed and upper-cased, or null when blank. */
  public static String upper(String s) {
    return s == null || s.isBlank() ? null : s.trim().toUpperCase(Locale.ROOT);
  }
}
