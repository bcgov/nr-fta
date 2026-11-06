package ca.bc.gov.nrs.fta.apt.service;

import java.util.Locale;
import java.util.Optional;

/**
 * APT's two CSV data extracts, APT2's {@code APTR013_CSV_EXTRACT} and
 * {@code APTR041_CSV_EXTRACT} functions ported to SQL ({@link AptReportExtractSql}). The
 * download is named for the report code and the day, as APT2 now names it. (APT2 dropped
 * APTR065's TSA exception extract.)
 */
public enum AptReportExtract {
  APTR013("APTR013"),
  APTR041("APTR041");

  private final String fileName;

  AptReportExtract(String fileName) {
    this.fileName = fileName;
  }

  /** The download's name stem; the date and {@code .csv} are added. */
  public String fileName() {
    return fileName;
  }

  /** The extract for a code such as {@code APTR041} or {@code aptr041}. */
  public static Optional<AptReportExtract> of(String code) {
    if (code == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(valueOf(code.trim().toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
