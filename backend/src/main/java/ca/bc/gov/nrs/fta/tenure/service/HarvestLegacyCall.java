package ca.bc.gov.nrs.fta.tenure.service;

import java.sql.CallableStatement;
import java.sql.Types;
import java.util.Collections;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Calls a legacy package's {@code mainline}, the only procedure the FTA908, FTA912 and FTA914
 * packages publish, and turns what it reports into a refusal.
 *
 * <p>These procedures report failure as message keys in {@code p_error_message}, having possibly
 * written part of the work already. Throwing rolls the caller's transaction back, as the legacy
 * action classes did by committing only when the message was empty.
 */
@Component
public class HarvestLegacyCall {

  private static final String CUSTOM_MESSAGE = "fta.web.error.user.custom.msg";
  private static final String MODIFIED = "fta.web.usr.database.record.modified";
  private static final String UNEXPECTED = "fta.web.usr.database.unexpected";

  private final JdbcTemplate jdbc;

  public HarvestLegacyCall(NamedParameterJdbcTemplate named) {
    this.jdbc = named.getJdbcTemplate();
  }

  /**
   * Runs {@code the.<procedure>} with every value bound IN OUT, the last being
   * {@code p_error_message}, which starts empty.
   *
   * @param cursorType the type of the REF CURSOR parameter after the error message, or null when
   *                   the procedure has none; legacy only opens it for its reads
   * @throws ResponseStatusException 409 when the record was modified, otherwise 400
   */
  public void run(String procedure, String cursorType, String... values) {
    int count = values.length + 1;
    String raw = jdbc.execute(
        (java.sql.Connection con) -> con.prepareCall(block(procedure, cursorType, count)),
        (CallableStatement cs) -> {
          for (int i = 0; i < values.length; i++) {
            cs.setString(i + 1, values[i]);
            cs.registerOutParameter(i + 1, Types.VARCHAR);
          }
          cs.setString(count, null);
          cs.registerOutParameter(count, Types.VARCHAR);
          cs.execute();
          return cs.getString(count);
        });
    if (raw != null && !raw.isBlank()) {
      throw new ResponseStatusException(
          raw.contains(MODIFIED) ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST, readable(raw));
    }
  }

  static String block(String procedure, String cursorType, int binds) {
    String args = String.join(", ", Collections.nCopies(binds, "?"));
    return cursorType == null
        ? "BEGIN the." + procedure + "(" + args + "); END;"
        : "DECLARE v_c " + cursorType + "; BEGIN the." + procedure + "(" + args + ", v_c); END;";
  }

  /**
   * Legacy error strings are {@code key;} or {@code key:args;} pairs, the ApplicationResources
   * text of each. FTA914's End Suspension raises an exception it does not handle, so its message
   * runs straight into an "unexpected" entry with no separator; only the message is meant.
   */
  static String readable(String error) {
    StringBuilder sb = new StringBuilder();
    for (String part : error.split(";")) {
      String p = part.trim();
      if (p.isEmpty()) {
        continue;
      }
      int colon = p.indexOf(':');
      String key = colon < 0 ? p : p.substring(0, colon);
      String args = colon < 0 ? "" : p.substring(colon + 1);
      String text = switch (key) {
        case CUSTOM_MESSAGE -> sentence(args);
        case MODIFIED ->
            "The record was changed by someone else. Reload the page and try again.";
        case "fta.web.usr.database.record.orphanTimberMark" ->
            "There are no other references to this Timber Mark. Therefore, it cannot be"
                + " unallocated from this Cut Block.";
        case "sil.error.usr.field.alreadyExists" -> args + " already exists.";
        case "fta.web.usr.database.record.invalid" -> "No Records Found (" + args + ").";
        case UNEXPECTED ->
            "An unexpected error has occurred in package/procedure " + args
                + ". Please contact System Support.";
        default -> p;
      };
      if (!sb.isEmpty()) {
        sb.append(' ');
      }
      sb.append(text);
    }
    return sb.toString();
  }

  private static String sentence(String message) {
    int runOn = message.indexOf(UNEXPECTED);
    String text = (runOn < 0 ? message : message.substring(0, runOn)).trim();
    return text.endsWith(".") ? text : text + ".";
  }
}
