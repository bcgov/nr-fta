package ca.bc.gov.nrs.fta.tenure.service;

import java.sql.CallableStatement;
import java.sql.Types;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The legacy PL/SQL a new cutting permit is checked and marked with, called rather than
 * ported so its file-type rules stay exactly legacy's:
 *
 * <ul>
 *   <li>{@code FTA_EDIT_CP_ID} — FTA902's CP ID rules (letters allowed per file type, no I or
 *       O, one/two/three-character limits with a mark designate, no duplicate in the file);
 *   <li>{@code FTA_XML_PROCESS.generate_timber_mark} — the mark for the CP by file type (mark
 *       designate + CP, file id prefix + CP, …). Reads only; writes nothing.
 * </ul>
 *
 * <p>Both report failure as a message key in {@code p_error_message}; {@link #readable} turns
 * the keys into legacy's ApplicationResources text.
 */
@Component
public class CuttingPermitLegacy {

  /** ApplicationResources.properties text for the keys these procedures return. */
  private static final Map<String, String> MESSAGES = Map.ofEntries(
      Map.entry("fta.cp.exists", "CP exists already."),
      Map.entry("fta.cp.mandatory", "CP ID is mandatory."),
      Map.entry("fta.cp.one.letter.only", "CP ID can only be 1 letter."),
      Map.entry("fta.cp.two.letters.only", "CP ID cannot be more than 2 letters."),
      // No text for these two in legacy's resources; worded after the procedure's rules.
      Map.entry("fta.cp.two.letters.max", "CP ID can be at most 2 letters."),
      Map.entry("fta.cp.three.letters.only",
          "CP ID must be 3 characters when the tenure has a mark designate."),
      Map.entry("fta.cp.letterIO.invalid", "CP ID cannot contain I or O."),
      Map.entry("fta.cp.specialchars.invalid",
          "CP ID cannot contain spaces or special characters or the letters I and O."),
      Map.entry("fta.cp.letter.format.invalid", "CP ID must only contain letters except I and O."),
      Map.entry("fta.web.xml.database.mark_designate_missing",
          "Mark Designate missing — the tenure needs a mark designate before a CP can be added."),
      Map.entry("fta.web.xml.database.max_cp_reached",
          "Could not assign CP id as maximum has been reached."),
      Map.entry("fta.web.xml.database.timber_mark_generation",
          "Error occurred generating timber mark."));

  private final JdbcTemplate jdbc;

  public CuttingPermitLegacy(NamedParameterJdbcTemplate named) {
    this.jdbc = named.getJdbcTemplate();
  }

  /** FTA_EDIT_CP_ID: null when the CP ID is valid for the file, else the problem. */
  public String cpIdProblem(String cpId, String forestFileId, String fileTypeCode) {
    String error = jdbc.execute(
        (java.sql.Connection con) -> con.prepareCall(
            "DECLARE v_err VARCHAR2(4000); BEGIN"
                + " the.fta_edit_cp_id(?, ?, ?, v_err); ? := v_err; END;"),
        (CallableStatement cs) -> {
          cs.setString(1, cpId);
          cs.setString(2, forestFileId);
          cs.setString(3, fileTypeCode);
          cs.registerOutParameter(4, Types.VARCHAR);
          cs.execute();
          return cs.getString(4);
        });
    return error == null || error.isBlank() ? null : readable(error);
  }

  /**
   * FTA_XML_PROCESS.generate_timber_mark: the mark for {@code cpId} on the file.
   *
   * @throws IllegalArgumentException with the readable reason when it cannot make one
   */
  public String generateTimberMark(String forestFileId, String fileTypeCode, String cpId) {
    String[] out = jdbc.execute(
        (java.sql.Connection con) -> con.prepareCall(
            "DECLARE v_mark VARCHAR2(30); v_err VARCHAR2(4000); BEGIN"
                + " the.fta_xml_process.generate_timber_mark(?, ?, 'N', ?, v_mark, v_err);"
                + " ? := v_mark; ? := v_err; END;"),
        (CallableStatement cs) -> {
          cs.setString(1, forestFileId);
          cs.setString(2, fileTypeCode);
          cs.setString(3, cpId);
          cs.registerOutParameter(4, Types.VARCHAR);
          cs.registerOutParameter(5, Types.VARCHAR);
          cs.execute();
          return new String[] {cs.getString(4), cs.getString(5)};
        });
    String error = out == null ? null : out[1];
    if (error != null && !error.isBlank()) {
      throw new IllegalArgumentException(readable(error));
    }
    if (out == null || out[0] == null || out[0].isBlank()) {
      throw new IllegalArgumentException("Error occurred generating timber mark.");
    }
    return out[0].trim().toUpperCase();
  }

  /**
   * Legacy error strings are {@code key;} or {@code key:args;} pairs, sometimes followed by
   * {@code fta.web.error.user.custom.msg:text;} — the readable text of each.
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
      String text = "fta.web.error.user.custom.msg".equals(key)
          ? p.substring(colon + 1).replaceFirst("^~W,", "")
          : MESSAGES.getOrDefault(key, p);
      if (!sb.isEmpty()) {
        sb.append(' ');
      }
      sb.append(text);
    }
    return sb.toString();
  }
}
