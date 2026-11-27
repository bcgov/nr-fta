package ca.bc.gov.nrs.fta.range.service;

import java.util.Locale;

/**
 * FTA001R Range Tenure Search's criteria rules that need no database
 * ({@code Fta001RangeTenureSearchForm}).
 */
final class RangeTenureSearchRules {

  /** The form's mgmtValidator ({@code sil.error.usr.invalid.value}). */
  static final String MGMT_UNIT_INVALID = "Invalid Management Unit type/id combination.";

  private RangeTenureSearchRules() {}

  /**
   * The Client Name criterion as BUILD_WHERE_CLAUSE compared it: forceFormats upper-cased what
   * was typed on Search, and the package matched {@code LIKE} it with no wildcard of its own —
   * the screen's tip was "Use LastName%First Name%". Null when blank.
   */
  static String clientName(String typed) {
    return typed == null || typed.isBlank() ? null : typed.toUpperCase(Locale.ROOT);
  }
}
