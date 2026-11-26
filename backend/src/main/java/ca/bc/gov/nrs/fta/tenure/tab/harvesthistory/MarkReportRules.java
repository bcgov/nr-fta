package ca.bc.gov.nrs.fta.tenure.tab.harvesthistory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * The FTA404 Mark Report's inputs: the timber mark FTA990 had on screen and the user who
 * pressed "Print Mark Report" — the report's only two parameters ({@code p_timber_mark},
 * {@code p_user_id}).
 */
final class MarkReportRules {

  private MarkReportRules() {}

  /**
   * The mark as FTA990's six-character field held it, upper-cased.
   *
   * <p>Legacy's FTAR404P read a blank mark as "every mark"; here the report is always for the
   * mark on screen, so a blank one is refused rather than run over the whole province.
   *
   * @throws ResponseStatusException 400 for a blank mark or one longer than six characters
   */
  static String timberMark(String raw) {
    String mark = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    if (mark.isEmpty() || mark.length() > 6) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Timber Mark.");
    }
    return mark;
  }

  /** The report's parameters; the rest keep the template's defaults. */
  static Map<String, Object> parameters(String timberMark, String userId) {
    Map<String, Object> params = new HashMap<>();
    params.put("p_user_id", userId);
    params.put("p_timber_mark", timberMark);
    return params;
  }

  static String filename(String timberMark) {
    return "FTA404-MarkReport-" + timberMark + ".pdf";
  }
}
