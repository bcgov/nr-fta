package ca.bc.gov.nrs.fta.tenure.tab.tenureapp;

import java.util.Set;

/**
 * Which columns legacy FTA950's "Spatial Submissions" list shows for a file type, and where
 * its CP / mark values link.
 *
 * <p>Combines the two places legacy decided this: {@code FTA_950X_TEN_APP.mainline}'s
 * {@code p_show_*_ind} flags (from the file type and the {@code FTA_VALID_*} code-table
 * checks) and {@code Fta950xTenAppAction.setHideParams} (status, type and feature-type
 * columns, and the CP link to FTA902).
 *
 * @param status          the Status column (hidden for C01)
 * @param applicationType the Type column (A01, A41, A03, A06, A30 only)
 * @param featureType     the Feature Type column (C01 only)
 * @param chart           Chart ID, Chart Block ID and Chart Volume (C01)
 * @param cuttingPermit   CP/HVA ID (timber files A…, not minor TSLs)
 * @param timberMark      Mark (timber files and single-mark file types)
 * @param location        Location (timber and permit file types)
 * @param pointOfCommencement PofC (not range files, not F05)
 * @param length          Length (km) (F05)
 * @param area            Area (ha) (not range files, not special-use roads with an RP)
 * @param cpLinksToPermit the CP (and, for A files, the mark) open the cutting permit; legacy
 *     linked a non-A file's mark to that mark's own file (FTA100)
 */
public record TenureAppColumns(
    boolean status,
    boolean applicationType,
    boolean featureType,
    boolean chart,
    boolean cuttingPermit,
    boolean timberMark,
    boolean location,
    boolean pointOfCommencement,
    boolean length,
    boolean area,
    boolean cpLinksToPermit) {

  /** setHideParams: the timber file types whose Type column shows. */
  private static final Set<String> TYPED_TIMBER = Set.of("A01", "A41", "A03", "A06", "A30");

  /** setHideParams: timber file types whose CP is not shown as a link. */
  private static final Set<String> UNLINKED_CP = Set.of("A20", "A21", "A23", "A24");

  /**
   * The columns for a file type.
   *
   * @param fileType     the tenure's file type code
   * @param minorTsl     FTA_VALID_MINOR_TSL_FILE_TYPE
   * @param permit       FTA_VALID_PERMIT_FILE_TYPE
   * @param singleMark   FTA_VALID_SINGLE_MARK_TYPE
   * @param range        FTA_VALID_RANGE_FILE_TYPE
   * @param specialRoad  an S01/S02 with a tenure application of type RP
   */
  public static TenureAppColumns of(
      String fileType,
      boolean minorTsl,
      boolean permit,
      boolean singleMark,
      boolean range,
      boolean specialRoad) {
    String ft = fileType == null ? "" : fileType.trim();
    boolean timber = ft.startsWith("A");
    boolean c01 = "C01".equals(ft);

    // mainline's flags
    boolean pofc = true;
    boolean area = true;
    boolean cp = false;
    boolean mark = false;
    boolean location = false;
    if (timber) {
      mark = true;
      location = true;
      if (!minorTsl) {
        cp = true;
      }
    }
    if (permit) {
      location = true;
    }
    if (singleMark) {
      mark = true;
    }
    if (range) {
      pofc = false;
      area = false;
    }
    if ("F05".equals(ft)) {
      pofc = false;
    }
    boolean roadWithRp = ("S01".equals(ft) || "S02".equals(ft)) && specialRoad;

    // setHideParams
    boolean typeShown = timber && TYPED_TIMBER.contains(ft);
    boolean cpLink = timber && !UNLINKED_CP.contains(ft);

    return new TenureAppColumns(
        !c01,
        typeShown,
        c01,
        c01,
        cp,
        mark,
        location,
        pofc,
        "F05".equals(ft),
        area && !roadWithRp,
        cpLink);
  }

  /** FTA_950X_TEN_APP.GET's note, for the file types it adds it to; else null. */
  public static String warning(String fileType, boolean ftc) {
    String ft = fileType == null ? "" : fileType.trim();
    if (ftc || Set.of("A28", "A29", "B40", "A04", "F05").contains(ft)) {
      return "NOTE: Information may not be displayed correctly for FTC submissions";
    }
    return null;
  }
}
