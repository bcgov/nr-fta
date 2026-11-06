package ca.bc.gov.nrs.fta.apt.service;

import ca.bc.gov.nrs.fta.apt.dto.AptLicenceDto;
import ca.bc.gov.nrs.fta.apt.dto.AptLicenceUpdateRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** APT2's licence form checks, held to {@code APT_LICENCES}' column sizes. */
public final class AptLicenceRules {

  /** {@code APT_LICENCES.APT_FOA_ST} is VARCHAR2(6). */
  static final int MAX_FOA = 6;

  /** {@code APT_LICENCES.REPLACEMENT_TERM} is NUMBER(3). */
  static final int MAX_TERM = 999;

  /** {@code APT_LICENCES.COMMENTS} is VARCHAR2(2000). */
  static final int MAX_COMMENTS = 2000;

  private AptLicenceRules() {}

  /** What is wrong with a licence update, or null when nothing is. */
  public static String problem(AptLicenceUpdateRequest r) {
    if (r == null || r.foaType() == null || r.foaType().isBlank()) {
      return "FOA Type is required.";
    }
    if (r.foaType().trim().length() > MAX_FOA) {
      return "FOA Type is not a valid code.";
    }
    if (r.replacementTerm() != null
        && (r.replacementTerm() < 0 || r.replacementTerm() > MAX_TERM)) {
      return "Replacement Term must be a number between 0 and 999.";
    }
    if (r.comments() != null && r.comments().length() > MAX_COMMENTS) {
      return "Comments cannot exceed 2000 characters.";
    }
    return null;
  }

  /**
   * One row per licence. {@code APT_LICENCES_GET} joins the file's A and B clients, so a licence
   * with both comes back twice; APT2's search dropped the repeats, keeping the first. Done here,
   * before paging, so the page counts are right.
   */
  public static List<AptLicenceDto> distinctByForestFileId(List<AptLicenceDto> rows) {
    Map<String, AptLicenceDto> byId = new LinkedHashMap<>();
    for (AptLicenceDto l : rows) {
      byId.putIfAbsent(l.forestFileId(), l);
    }
    return new ArrayList<>(byId.values());
  }
}
