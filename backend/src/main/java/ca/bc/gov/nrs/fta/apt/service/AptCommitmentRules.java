package ca.bc.gov.nrs.fta.apt.service;

import ca.bc.gov.nrs.fta.apt.dto.AptCommitmentDto;
import ca.bc.gov.nrs.fta.apt.dto.AptCommitmentRequest;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * The checks a commitment write must pass before it reaches {@code APT_COMMITMENTS}: APT2's
 * form validation, held to the columns' real sizes. Also how a database refusal of an APT write
 * becomes an HTTP error, as the {@code APT_*} procedures reported it.
 */
public final class AptCommitmentRules {

  /** {@code APT_COMMITMENTS.ALLOWABLE_ANNUAL_CUT} is NUMBER(9). */
  static final BigDecimal MAX_AAC = new BigDecimal("999999999");

  /**
   * The reason lands in {@code APT_COMMITMENTS_JN.JN_NOTES}, VARCHAR2(240). APT2 allowed 2000,
   * so a longer reason failed in the database.
   */
  public static final int MAX_REASON = 240;

  /** An Oracle extended ROWID: 18 base-64 characters. */
  private static final Pattern ROW_ID = Pattern.compile("^[A-Za-z0-9+/]{18}$");

  /** ORA-00001: unique constraint violated. */
  static final int ORA_UNIQUE = 1;

  /** ORA-02292: child record found. */
  static final int ORA_CHILD_RECORD = 2292;

  private AptCommitmentRules() {}

  /**
   * A database refusal as the {@code APT_*} procedures reported it: their {@code WHEN OTHERS}
   * returned {@code SQLERRM}, which was shown as is, with a 409 for a duplicate or a child
   * record block and a 400 for anything else.
   */
  public static ResponseStatusException refusal(Throwable e) {
    int code = oracleCode(e);
    HttpStatus status = code == ORA_UNIQUE || code == ORA_CHILD_RECORD
        ? HttpStatus.CONFLICT
        : HttpStatus.BAD_REQUEST;
    return new ResponseStatusException(status, oracleMessage(e), e);
  }

  /** The Oracle error number of the innermost {@link SQLException}, or 0 when there is none. */
  static int oracleCode(Throwable e) {
    SQLException s = innermostSql(e);
    return s == null ? 0 : s.getErrorCode();
  }

  /**
   * What {@code SQLERRM} gave: the innermost {@link SQLException}'s first line (newer drivers
   * add a help link on the following lines).
   */
  static String oracleMessage(Throwable e) {
    SQLException s = innermostSql(e);
    String m = s != null && s.getMessage() != null ? s.getMessage()
        : e == null || e.getMessage() == null ? "Database error." : e.getMessage();
    int nl = m.indexOf('\n');
    return (nl < 0 ? m : m.substring(0, nl)).trim();
  }

  private static SQLException innermostSql(Throwable e) {
    SQLException found = null;
    for (Throwable t = e; t != null && t.getCause() != t; t = t.getCause()) {
      if (t instanceof SQLException s) {
        found = s;
      }
    }
    return found;
  }

  /**
   * What is wrong with a commitment write, or null when nothing is.
   *
   * @param r      the request
   * @param update true to change an existing commitment, which needs a reason
   */
  public static String problem(AptCommitmentRequest r, boolean update) {
    if (r == null) {
      return "A commitment is required.";
    }
    if (blank(r.forestFileId())) {
      return "Forest File ID is required.";
    }
    if (blank(r.aptPartitionSt())) {
      return "Partition is required.";
    }
    if (blank(r.commitTypeSt())) {
      return "Type is required.";
    }
    if (r.aac() == null) {
      return "AAC is required.";
    }
    if (r.aac().signum() < 0 || r.aac().compareTo(MAX_AAC) > 0) {
      return "AAC must be between 0 and 999,999,999.";
    }
    if (r.aac().stripTrailingZeros().scale() > 0) {
      return "AAC must be a whole number.";
    }
    if (!"Y".equals(r.inclInLicAacInd()) && !"N".equals(r.inclInLicAacInd())) {
      return "Incl in AAC must be Y or N.";
    }
    if (r.determinationDate() == null) {
      return "Determination Date is required.";
    }
    if (r.effectiveDate() == null) {
      return "Effective Date is required.";
    }
    if (update && blank(r.reason())) {
      return "Reason is required.";
    }
    return reasonProblem(r.reason());
  }

  /** What is wrong with a reason's length, or null. */
  public static String reasonProblem(String reason) {
    return reason != null && reason.trim().length() > MAX_REASON
        ? "Reason cannot exceed " + MAX_REASON + " characters."
        : null;
  }

  /**
   * Whether a journal row id looks like a real ROWID. {@code APT_COMMITMENTS_JN_GET}
   * concatenated it into dynamic SQL; it is now a bind, but anything else is still refused.
   */
  public static boolean isRowId(String rowId) {
    return rowId != null && ROW_ID.matcher(rowId).matches();
  }

  /**
   * One row per commitment. The cursors join the licence's clients, so a licence with more than
   * one A or B client returns each commitment once per client; APT2's screens dropped the
   * repeats, keeping the first.
   */
  public static List<AptCommitmentDto> distinctById(List<AptCommitmentDto> rows) {
    Map<Long, AptCommitmentDto> byId = new LinkedHashMap<>();
    List<AptCommitmentDto> noId = new ArrayList<>();
    for (AptCommitmentDto c : rows) {
      if (c.id() == null) {
        noId.add(c);
      } else {
        byId.putIfAbsent(c.id(), c);
      }
    }
    List<AptCommitmentDto> out = new ArrayList<>(byId.values());
    out.addAll(noId);
    return out;
  }

  static boolean blank(String s) {
    return s == null || s.isBlank();
  }
}
