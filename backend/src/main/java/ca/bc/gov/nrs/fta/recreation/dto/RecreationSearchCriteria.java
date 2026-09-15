package ca.bc.gov.nrs.fta.recreation.dto;

/**
 * The FTA007 Recreation Search criteria.
 *
 * <p>Fields mirror the legacy screen ({@code fta007RecreationSearch.jsp}) and
 * the parameters of {@code THE.FTA_007_REC_SEARCH.MAINLINE}, whose only
 * accepted action is {@code GET}.
 *
 * <p>Recreation files come in two generations, and the legacy package queries
 * them as two separate statements joined by {@code UNION}: "new" files live in
 * {@code RECREATION_PROJECT} and have ids like {@code REC%}, while "old" files
 * live in {@code REC_PROJECT} with ids like {@code 900%}. {@code oldFileInd}
 * chooses between them — {@code N} new only, {@code Y} old only, absent both.
 *
 * @param orgUnit               numeric admin org unit; the one mandatory field.
 *                              A region matches everything rolling up to it
 * @param mgmtUnitType          management unit type, one character
 * @param mgmtUnitNumber        management unit id
 * @param fileId                {@code pfu.forest_file_id}. Legacy matches this
 *                              with {@code LIKE} but adds no wildcard, so it is
 *                              an exact match unless the user types {@code %}
 * @param fileStatus            {@code pfu.file_status_st}
 * @param fileStatusFrom        inclusive lower bound on {@code file_status_date}
 * @param fileStatusTo          inclusive upper bound
 * @param projectName           case-insensitive contains-match on the project
 *                              name — a different column per generation
 * @param projectType           {@code tamf.feature_type_code}
 * @param riskRating            {@code rp.recreation_risk_rating_code}
 * @param controlledAccessType  {@code rp.recreation_control_access_code}
 * @param maintenanceStandard   {@code rp.recreation_maintain_std_code}
 * @param definedCampingSpaces  legacy compares this with {@code <}, not equals:
 *                              it finds projects with MORE than this many
 *                              campsites, despite the label
 * @param oldFileInd            {@code Y} old files, {@code N} new files, absent
 *                              for both
 * @param recreationDistrict    an EXISTS against {@code RECREATION_DISTRICT_XREF}
 * @param resourceFeatureInd    {@code rp.resource_feature_ind}
 * @param sortBy                {@code FID}, {@code AOU}, {@code FS} or {@code PN}
 */
public record RecreationSearchCriteria(
    String orgUnit,
    String mgmtUnitType,
    String mgmtUnitNumber,
    String fileId,
    String fileStatus,
    String fileStatusFrom,
    String fileStatusTo,
    String projectName,
    String projectType,
    String riskRating,
    String controlledAccessType,
    String maintenanceStandard,
    String definedCampingSpaces,
    String oldFileInd,
    String recreationDistrict,
    String resourceFeatureInd,
    String sortBy) {

  /** Sort on the file id — the legacy default. */
  public static final String SORT_FILE_ID = "FID";

  /** Sort on the administering org unit. */
  public static final String SORT_ADMIN_ORG = "AOU";

  /** Sort on file status. */
  public static final String SORT_FILE_STATUS = "FS";

  /** Sort on project name. */
  public static final String SORT_PROJECT_NAME = "PN";

  /** Restrict to the older {@code REC_PROJECT} generation. */
  public static final String OLD_FILES = "Y";

  /** Restrict to the newer {@code RECREATION_PROJECT} generation. */
  public static final String NEW_FILES = "N";

  /**
   * Whether the search reaches the newer {@code RECREATION_PROJECT} table.
   *
   * <p>Four criteria — risk rating, controlled access, maintenance standard and
   * resource feature — exist only on that table. Legacy puts them in the "new
   * files" branch alone, so choosing Old silently discards them; this predicate
   * is what lets the service surface that rather than hide it.
   */
  public boolean includesNewFiles() {
    return !OLD_FILES.equals(oldFileInd);
  }

  /** Whether any criterion applies only to the newer table was supplied. */
  public boolean hasNewFileOnlyCriteria() {
    return notBlank(riskRating)
        || notBlank(controlledAccessType)
        || notBlank(maintenanceStandard)
        || notBlank(resourceFeatureInd);
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }
}
