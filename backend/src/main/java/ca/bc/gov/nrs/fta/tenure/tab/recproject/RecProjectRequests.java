package ca.bc.gov.nrs.fta.tenure.tab.recproject;

import java.time.LocalDate;

/** Request bodies of the Rec project tab's writes (legacy FTA701). */
public final class RecProjectRequests {

  private RecProjectRequests() {}

  /**
   * The project details — legacy FTA701's main Save. Numbers arrive as the text the user typed,
   * so the legacy validators' own messages ("… must be an integer.") can be given. Y/N
   * indicators are "Y", "N" or blank.
   *
   * @param revisionCount the project's revision count when it was read; null to create it
   */
  public record Save(
      Long revisionCount,
      String projectName,
      String riskRatingCode,
      LocalDate projectEstablishedDate,
      String siteLocation,
      String utmZone,
      String utmEasting,
      String utmNorthing,
      String rightOfWay,
      String featureCode,
      String userDaysCode,
      String maintainStdCode,
      String campHostInd,
      String overflowCampsites,
      String lowMobilityAccessInd,
      String recreationViewInd,
      String resourceFeatureInd,
      String controlAccessCode,
      LocalDate lastRecInspectionDate,
      LocalDate lastHzrdTreeAssessDate,
      String archImpactAssessInd,
      LocalDate archImpactDate,
      String bordenNo,
      String aiaComment,
      String siteDescription) {}

  /**
   * A fee (FTA701's fee row). {@code revisionCount} is the fee's when updating one.
   *
   * @param amount the amount as typed
   */
  public record Fee(
      Long revisionCount,
      String feeCode,
      String amount,
      LocalDate startDate,
      LocalDate endDate,
      boolean monday,
      boolean tuesday,
      boolean wednesday,
      boolean thursday,
      boolean friday,
      boolean saturday,
      boolean sunday) {}

  /** An access type / sub type pair (FTA701's access row, {@code FTA_701_PROJECT_ACCESS}). */
  public record Access(String accessCode, String subAccessCode) {}

  /** A recreation district (FTA701's Add District). */
  public record District(String districtCode) {}

  /**
   * An establishment order (FTA701's attachment upload): the PDF's file name and its bytes,
   * base64-encoded, so the upload is an ordinary JSON write.
   */
  public record Attachment(String fileName, String contentBase64) {}
}
