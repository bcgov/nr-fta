package ca.bc.gov.nrs.fta.tenure.tab.recproject;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The Rec project tab of the tenure detail — legacy FTA701 (Recreation Project Details), the
 * port of {@code FTA_701_PROJECT_DETAILS.GET} (project, fees), its {@code GET_DISTRICT} and
 * {@code GET_ATTACHMENT}, and {@code FTA_701_PROJECT_ACCESS.GET}.
 *
 * <p>Codes come with their description, shown as "CODE - Description".
 *
 * @param applicable          whether the file is a recreation file this screen serves
 * @param notApplicableReason why not, when {@code applicable} is false
 * @param exists              whether the file has a {@code RECREATION_PROJECT} row yet
 * @param revisionCount       the project's revision count; null when it does not exist
 * @param projectTypeCode     from the file's current recreation map feature (read only)
 * @param trailProject        a trail type (RTR, IFT, TBL, RTE): Right of Way is editable and
 *                            required
 * @param definedCampsites    count of FTA707 defined campsites (read only)
 * @param associatedFiles     whether the file has associated files (read only)
 */
public record RecProjectDto(
    boolean applicable,
    String notApplicableReason,
    boolean exists,
    Long revisionCount,
    String projectTypeCode,
    String projectTypeDesc,
    BigDecimal projectLength,
    BigDecimal projectArea,
    boolean trailProject,
    String projectName,
    LocalDate projectEstablishedDate,
    String riskRatingCode,
    String riskRatingDesc,
    String siteLocation,
    BigDecimal utmZone,
    BigDecimal utmEasting,
    BigDecimal utmNorthing,
    BigDecimal rightOfWay,
    String featureCode,
    String featureDesc,
    String userDaysCode,
    String userDaysDesc,
    String maintainStdCode,
    String maintainStdDesc,
    long definedCampsites,
    String campHostInd,
    BigDecimal overflowCampsites,
    String lowMobilityAccessInd,
    String recreationViewInd,
    String resourceFeatureInd,
    boolean associatedFiles,
    String controlAccessCode,
    String controlAccessDesc,
    LocalDate lastRecInspectionDate,
    LocalDate lastHzrdTreeAssessDate,
    String archImpactAssessInd,
    LocalDate archImpactDate,
    String bordenNo,
    String aiaComment,
    String siteDescription,
    RecProjectRules rules,
    List<District> districts,
    List<Fee> fees,
    List<Access> accesses,
    List<Attachment> attachments) {

  /** A recreation district of the project ({@code RECREATION_DISTRICT_XREF}). */
  public record District(String districtCode, String districtDesc) {}

  /** A fee ({@code RECREATION_FEE}), newest end date first within a fee type. */
  public record Fee(
      long feeId,
      String feeCode,
      String feeDesc,
      BigDecimal amount,
      LocalDate startDate,
      LocalDate endDate,
      boolean monday,
      boolean tuesday,
      boolean wednesday,
      boolean thursday,
      boolean friday,
      boolean saturday,
      boolean sunday,
      long revisionCount) {}

  /** An access type / sub type ({@code RECREATION_ACCESS}). */
  public record Access(
      String accessCode,
      String accessDesc,
      String subAccessCode,
      String subAccessDesc,
      long revisionCount) {}

  /** An establishment order ({@code RECREATION_ATTACHMENT}); its PDF is downloaded apart. */
  public record Attachment(long attachmentId, String fileName, Long sizeBytes) {}

  /** The tab for a file this screen does not serve. */
  static RecProjectDto notApplicable(String reason) {
    return new RecProjectDto(false, reason, false, null, null, null, null, null, false, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, 0,
        null, null, null, null, null, false, null, null, null, null, null, null, null, null,
        null, RecProjectRules.none(reason), List.of(), List.of(), List.of(), List.of());
  }
}
