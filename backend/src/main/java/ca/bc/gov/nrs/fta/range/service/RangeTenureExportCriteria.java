package ca.bc.gov.nrs.fta.range.service;

import ca.bc.gov.nrs.fta.shared.sql.SearchIds;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * The 26 inputs of {@code THE.FTA_001R_TENR_SRCH_CSV1..10}, in the procedures' order, as the
 * FTA001 Range Tenure Export passes them.
 *
 * <p>The procedures build their SQL by concatenation, so every value is checked or escaped here
 * before it reaches them:
 * <ul>
 *   <li>numbers they splice in bare (org unit, provision year, the usage ranges) must be
 *       numbers;</li>
 *   <li>dates they wrap in {@code TO_DATE('…','YYYY-MM-DD')} must be ISO dates;</li>
 *   <li>text they wrap in quotes without escaping has its quotes doubled, which is the literal the
 *       user meant;</li>
 *   <li>the file id they escape themselves, so it is only trimmed and upper-cased, as the search
 *       does.</li>
 * </ul>
 */
public record RangeTenureExportCriteria(
    String forestFileId,
    String fileTypeCode,
    String adminOrgUnitNo,
    String zone,
    String clientName,
    String clientNumber,
    String clientLocnCode,
    String fileClientType,
    String fileStatus,
    String mgmtUnitType,
    String mgmtUnitId,
    String issueDateFrom,
    String issueDateTo,
    String expiryDateFrom,
    String expiryDateTo,
    String provisionYear,
    String authorizedUseFrom,
    String authorizedUseTo,
    String temporaryIncreaseFrom,
    String temporaryIncreaseTo,
    String billableNonUseFrom,
    String billableNonUseTo,
    String nonBillableNonUseFrom,
    String nonBillableNonUseTo,
    String totalAnnualUseFrom,
    String totalAnnualUseTo) {

  private static final Pattern WHOLE = Pattern.compile("\\d{1,10}");
  private static final Pattern DECIMAL = Pattern.compile("\\d{1,10}(\\.\\d{1,4})?");

  /**
   * One file's export, as the 100R, 613R, 615R and 920R Export buttons asked for it: the file id
   * and the file's administration org unit, every other input blank.
   */
  public static RangeTenureExportCriteria forFile(String forestFileId, String adminOrgUnitNo) {
    return new RangeTenureExportCriteria(forestFileId, null, adminOrgUnitNo, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null);
  }

  /**
   * The values bound to the procedures, in order: blanks as null, checked and escaped as the
   * class describes.
   *
   * @throws ResponseStatusException 400 when a number or date is malformed
   */
  public List<String> procedureArguments() {
    return Arrays.asList(
        SearchIds.upper(forestFileId),
        quoted(fileTypeCode),
        number("Administration organization unit", adminOrgUnitNo, WHOLE),
        quoted(zone),
        quoted(clientName),
        quoted(clientNumber),
        quoted(clientLocnCode),
        quoted(fileClientType),
        quoted(fileStatus),
        quoted(mgmtUnitType),
        quoted(mgmtUnitId),
        date("Issue date from", issueDateFrom),
        date("Issue date to", issueDateTo),
        date("Expiry date from", expiryDateFrom),
        date("Expiry date to", expiryDateTo),
        number("Provision year", provisionYear, WHOLE),
        number("Authorized use from", authorizedUseFrom, DECIMAL),
        number("Authorized use to", authorizedUseTo, DECIMAL),
        number("Temporary increase from", temporaryIncreaseFrom, DECIMAL),
        number("Temporary increase to", temporaryIncreaseTo, DECIMAL),
        number("Billable non-use from", billableNonUseFrom, DECIMAL),
        number("Billable non-use to", billableNonUseTo, DECIMAL),
        number("Non-billable non-use from", nonBillableNonUseFrom, DECIMAL),
        number("Non-billable non-use to", nonBillableNonUseTo, DECIMAL),
        number("Total annual use from", totalAnnualUseFrom, DECIMAL),
        number("Total annual use to", totalAnnualUseTo, DECIMAL));
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  private static String quoted(String s) {
    String v = blankToNull(s);
    return v == null ? null : v.replace("'", "''");
  }

  private static String number(String label, String s, Pattern pattern) {
    String v = blankToNull(s);
    if (v != null && !pattern.matcher(v).matches()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " must be a number.");
    }
    return v;
  }

  private static String date(String label, String s) {
    String v = blankToNull(s);
    if (v == null) {
      return null;
    }
    try {
      return LocalDate.parse(v).toString();
    } catch (DateTimeParseException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, label + " must be a date (YYYY-MM-DD).");
    }
  }
}
