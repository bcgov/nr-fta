package ca.bc.gov.nrs.fta.tenure.tab.recproject;

/**
 * What may be changed on a recreation project — the port of legacy {@code FTA_RECREATION_SECURITY}
 * as {@code FTA_701_PROJECT_DETAILS.GET} calls it twice: once for the project itself (the
 * "parent" Save) and once for its child records (fees, access types, districts, establishment
 * orders).
 *
 * <p>Legacy decides by recreation role and organization (Headquarters, Region, District). This
 * app has no organization levels, so every user who may write gets Headquarters' rules — which
 * also opens the HQ-only Project Established date and Establishment Orders. What is left are
 * the file's own conditions:
 *
 * <ul>
 *   <li>the file must be a recreation file in the RECnnnn format (FTA701's GET);
 *   <li>its status must be HI;
 *   <li>it must have an approved spatial submission (an APP or RET tenure application with map
 *       features — {@code fta_recreation_utils.get_rec_proj_spatial_info});
 *   <li>children only: the project record must exist.
 * </ul>
 *
 * <p>Computed once and returned on the GET (to enable the buttons and say why not) and enforced
 * on every write, so the two cannot disagree.
 *
 * @param project       saving the project details (create or update)
 * @param projectReason why not, when {@code project} is false
 * @param child         adding/removing fees, access types, districts and establishment orders
 * @param childReason   why not, when {@code child} is false
 */
public record RecProjectRules(
    boolean project, String projectReason, boolean child, String childReason) {

  static final String NOT_HI = "The project can be changed only while the file's status is HI.";
  static final String NO_SPATIAL =
      "This file does not contain an approved spatial submission. You may not save changes to "
          + "this file until a spatial submission has been approved.";
  static final String NO_PROJECT =
      "You must save project details before adding fees and access types.";

  /** Nothing can be changed — not a recreation file (the reason is shown elsewhere). */
  public static RecProjectRules none(String reason) {
    return new RecProjectRules(false, reason, false, reason);
  }

  /**
   * @param fileStatus       {@code PROV_FOREST_USE.FILE_STATUS_ST}
   * @param spatialApproved  whether the file has an approved spatial submission
   * @param projectExists    whether a {@code RECREATION_PROJECT} row exists for the file
   */
  public static RecProjectRules of(
      String fileStatus, boolean spatialApproved, boolean projectExists) {
    String reason = null;
    if (!"HI".equals(fileStatus)) {
      reason = NOT_HI;
    } else if (!spatialApproved) {
      reason = NO_SPATIAL;
    }
    boolean project = reason == null;
    String childReason = reason;
    if (project && !projectExists) {
      childReason = NO_PROJECT;
    }
    return new RecProjectRules(project, reason, childReason == null, childReason);
  }
}
