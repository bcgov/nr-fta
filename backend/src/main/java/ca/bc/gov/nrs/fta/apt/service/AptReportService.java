package ca.bc.gov.nrs.fta.apt.service;

import ca.bc.gov.nrs.fta.apt.dto.AptReportParams;
import ca.bc.gov.nrs.fta.apt.service.AptReportExtractSql.Aptr013Row;
import ca.bc.gov.nrs.fta.apt.service.AptReportExtractSql.Aptr041Row;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * APT's reports (APT2's APTM8000): nine PDFs rendered in the backend from JRXML templates
 * ({@link AptReportRenderer}), and two CSV data extracts read from the APT tables
 * ({@link AptReportExtractSql}).
 */
@Service
public class AptReportService {

  private final NamedParameterJdbcTemplate jdbc;
  private final AptReportRenderer renderer;

  public AptReportService(NamedParameterJdbcTemplate jdbc, AptReportRenderer renderer) {
    this.jdbc = jdbc;
    this.renderer = renderer;
  }

  /**
   * One PDF report.
   *
   * @param report the report
   * @param in     the request's inputs; the ones the report needs must be present (400)
   * @param user   the caller as {@code IDIR\USERNAME}, passed as the report's {@code user}
   * @throws org.springframework.web.server.ResponseStatusException 501 until the report's
   *     template has been added
   */
  public byte[] pdf(AptReportDefinition report, AptReportParams in, String user) {
    return renderer.render(report.template(), reportParams(report, in, user));
  }

  /** Every parameter of a report: APT2's common {@code user}, then the report's own. */
  static Map<String, String> reportParams(AptReportDefinition report, AptReportParams in,
      String user) {
    Map<String, String> own = report.reportParams(in);
    Map<String, String> params = new LinkedHashMap<>();
    params.put("user", user == null ? "" : user);
    params.putAll(own);
    return params;
  }

  /**
   * One CSV extract's rows, header first, each split into its fields. The lines are built as
   * APT2's extract function built them (names quoted, values comma-joined, the header padded
   * with a space so it sorts first, duplicates dropped, sorted); the fields are then split back
   * out so the download is written as clean CSV.
   */
  public List<List<String>> extract(AptReportExtract extract) {
    List<String> lines;
    try {
      lines = switch (extract) {
        case APTR013 -> AptReportExtractSql.fileLines(AptReportExtractSql.APTR013_HEADER,
            jdbc.query(AptReportExtractSql.APTR013_SQL, (rs, i) -> aptr013(rs).line()));
        case APTR041 -> AptReportExtractSql.fileLines(AptReportExtractSql.APTR041_HEADER,
            jdbc.query(AptReportExtractSql.APTR041_SQL, (rs, i) -> aptr041(rs).line()));
      };
    } catch (DataAccessException e) {
      // The functions reported any database error as their own (a 400).
      throw AptRelatedClientService.databaseError(e);
    }
    return toRows(lines);
  }

  /** The lines split into fields; a blank line is skipped. */
  static List<List<String>> toRows(List<String> lines) {
    List<List<String>> rows = new ArrayList<>(lines.size());
    for (String line : lines) {
      if (line != null && !line.isBlank()) {
        rows.add(splitLine(line));
      }
    }
    return rows;
  }

  private static Aptr013Row aptr013(ResultSet rs) throws SQLException {
    return new Aptr013Row(
        rs.getString("ORG_UNIT_CODE"),
        rs.getString("MGMT_UNIT_NAME"),
        rs.getBigDecimal("MU_AAC"),
        rs.getBigDecimal("MU_AAC_HA"),
        date(rs, "AAC_EFFECTIVE_DATE"),
        date(rs, "APPORT_EFFECTIVE_DATE"),
        rs.getString("PART_TYPE_DESC"),
        rs.getBigDecimal("PART_AAC"),
        rs.getBigDecimal("PART_AAC_HA"),
        rs.getString("FOA_DESC"),
        rs.getBigDecimal("FOA_AAC"),
        rs.getBigDecimal("FOA_AAC_HA"));
  }

  private static Aptr041Row aptr041(ResultSet rs) throws SQLException {
    return new Aptr041Row(
        rs.getString("HOLDER"),
        rs.getString("PATH"),
        rs.getString("SIZE_CLASS"),
        rs.getString("PARENT_NAME"),
        rs.getString("FOREST_FILE_ID"),
        rs.getString("CLIENT_NAME"),
        rs.getString("APT_FOA_ST"),
        date(rs, "EXPIRY_DT"),
        rs.getString("ORG_UNIT_CODE"),
        rs.getString("MGMT_UNIT_NAME"),
        rs.getBigDecimal("AAC"),
        "Y".equals(rs.getString("UAV")),
        rs.getString("JOINT_NAMES"));
  }

  private static LocalDate date(ResultSet rs, String col) throws SQLException {
    Timestamp t = rs.getTimestamp(col);
    return t == null ? null : t.toLocalDateTime().toLocalDate();
  }

  /**
   * Splits one extract line into its fields: commas separate, a
   * double-quoted field may hold commas, and {@code ""} inside one is a quote. Each field is
   * trimmed (APTR041 ends its rows with {@code ", "}, and every header starts with a space).
   */
  static List<String> splitLine(String line) {
    List<String> fields = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (quoted) {
        if (c == '"') {
          if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
            field.append('"');
            i++;
          } else {
            quoted = false;
          }
        } else {
          field.append(c);
        }
      } else if (c == '"') {
        quoted = true;
      } else if (c == ',') {
        fields.add(field.toString().trim());
        field.setLength(0);
      } else {
        field.append(c);
      }
    }
    fields.add(field.toString().trim());
    return fields;
  }
}
