package ca.bc.gov.nrs.fta.mark.service;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The timber mark that issuing a private mark of a given type would be given next, without
 * taking it — so the user can see the mark and confirm before the save that issues it.
 *
 * <p>Reads the counter {@code THE.PRT_MRK_NUM_CTL} and steps it on as
 * {@code FTA_510_PRIVATE_MARK.ASSIGN_MARK} does ({@link #next}), but never updates it: only
 * {@link PrivateMarkPackage#assignMark}, at the save, takes the number. Another issue between
 * the preview and the save takes it first, and the save then gets the one after; the page
 * says so.
 */
@Component
public class TimberMarkPreview {

  private final NamedParameterJdbcTemplate jdbc;
  private final PrivateMarkPackage privateMarkPackage;

  public TimberMarkPreview(NamedParameterJdbcTemplate jdbc, PrivateMarkPackage privateMarkPackage) {
    this.jdbc = jdbc;
    this.privateMarkPackage = privateMarkPackage;
  }

  /**
   * Passes over the next mark — one the user won't issue, e.g. for spelling an unfortunate
   * word — and returns the one after it. Legacy's way: clicking Assign Mark again took the
   * shown mark and generated the next, so this takes the next number off the counter through
   * {@code ASSIGN_MARK} (which commits) and leaves it unused, a gap in the sequence as legacy
   * left. When the number it takes was already used (the package refuses it), it is passed over
   * all the same.
   */
  public Optional<String> skipFor(String fileTypeCode) {
    if (nextFor(fileTypeCode).isEmpty()) {
      return Optional.empty();
    }
    try {
      privateMarkPackage.assignMark(fileTypeCode);
    } catch (IllegalArgumentException alreadyUsed) {
      // The counter still moved on; the next preview is past it.
    }
    return nextFor(fileTypeCode);
  }

  /** The next mark for {@code fileTypeCode}, or empty for a type that isn't issued here. */
  public Optional<String> nextFor(String fileTypeCode) {
    if (!"B08".equals(fileTypeCode) && !"B09".equals(fileTypeCode)
        && !"B14".equals(fileTypeCode)) {
      return Optional.empty();
    }
    List<String> last = jdbc.queryForList(
        """
        SELECT CASE WHEN :type = 'B14' THEN TO_CHAR(last_ir_mark_used)
                    ELSE last_prt_mark_used END
          FROM the.prt_mrk_num_ctl
         WHERE dummy_access_key = '1'
        """,
        new MapSqlParameterSource("type", fileTypeCode),
        String.class);
    if (last.isEmpty() || last.get(0) == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(next(fileTypeCode, last.get(0).trim()));
  }

  /**
   * {@code ASSIGN_MARK}'s step: B14 counts up ({@code IR0042}); B08 and B09 step the last
   * mark's characters from the right, a {@code Z} rolling over to {@code A} and carrying, any
   * other character moving to the next one — then E (B08) or N (B09) in front.
   */
  static String next(String fileTypeCode, String last) {
    if ("B14".equals(fileTypeCode)) {
      return "IR" + String.format("%4s", Long.parseLong(last) + 1).replace(' ', '0');
    }
    char[] mark = last.toCharArray();
    for (int i = mark.length - 1, steps = 0; i >= 0 && steps < 6; i--, steps++) {
      if (mark[i] == 'Z') {
        mark[i] = 'A'; // and carry to the next character left
      } else {
        mark[i] = (char) (mark[i] + 1);
        break;
      }
    }
    String suffix = new String(mark);
    return switch (fileTypeCode) {
      case "B08" -> "E" + suffix;
      case "B09" -> "N" + suffix;
      default -> null;
    };
  }
}
