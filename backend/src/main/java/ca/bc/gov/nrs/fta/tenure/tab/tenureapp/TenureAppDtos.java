package ca.bc.gov.nrs.fta.tenure.tab.tenureapp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Request and response shapes of the tenure "Tenure application" tab (legacy FTA950). */
public final class TenureAppDtos {

  private TenureAppDtos() {}

  /**
   * {@code GET /api/fta/tenures/{forestFileId}/tenure-applications} — the three lists legacy
   * FTA950 shows, plus which of the spatial list's columns legacy shows for this file type.
   *
   * @param fileTypeCode  the tenure's file type
   * @param columns       the spatial-submission columns shown for that file type
   * @param warning       legacy's note for FTC-style files, else null
   * @param cpRequests    "Smart Form Application List" (FTA_950X_TEN_APP_CUT_PERM_REQ)
   * @param cpRejections  "CP Submission Rejection List" (FTA_950X_TEN_APP_CP_REJ)
   * @param applications  "Spatial Submissions" (FTA_950X_TEN_APP.GET)
   * @param unavailable   notices for lists this database could not read (a missing table)
   */
  public record TenureAppTabDto(
      String fileTypeCode,
      TenureAppColumns columns,
      String warning,
      List<TenureAppCpRequestDto> cpRequests,
      List<TenureAppCpRejectionDto> cpRejections,
      List<TenureAppRowDto> applications,
      List<String> unavailable) {}

  /**
   * One spatial-submission row: a tenure application, or one of its map features for the file
   * types legacy lists per feature.
   *
   * @param issuePermitAllowed whether legacy enables Issue Permit (application APP or ISS)
   * @param issuePermitReason  why not, when not
   */
  public record TenureAppRowDto(
      Long submissionId,
      LocalDate submissionDate,
      String orgUnitCode,
      String orgUnitName,
      Long tenureAppId,
      String statusCode,
      String statusDesc,
      String applicationTypeCode,
      String applicationTypeDesc,
      String purposeDesc,
      String description,
      String featureTypeDesc,
      String cuttingPermitId,
      Long hvaSkey,
      String timberMark,
      String location,
      String pointOfCommencement,
      String chartAreaId,
      String chartBlockId,
      BigDecimal chartVolume,
      BigDecimal objectLength,
      BigDecimal objectArea,
      Long mapFeatureId,
      boolean exhibitAImage,
      boolean regenInProgress,
      boolean professionalDeclaration,
      boolean statusNotificationClearance,
      /** The application's SNC status request (CLEARANCE_STATUS_REQUEST_URI); legacy 950x's
       *  "SNC" link opens it in SNC. Null when none. */
      String clearanceStatusRequestUri,
      LocalDate decisionDate,
      LocalDate issuanceDate,
      boolean issuePermitAllowed,
      String issuePermitReason) {}

  /** One ESF smart-form cutting-permit request ({@code THE.CUTTING_PERMIT_REQUEST}). */
  public record TenureAppCpRequestDto(
      String requestGuid,
      Long hvaSkey,
      String cuttingPermitId,
      LocalDate requestDate,
      String requestCode,
      String requestDesc,
      String statusCode,
      String statusDesc,
      String acceptedUserId,
      LocalDate acceptedDate,
      String rationaleDetail,
      boolean rationaleDocument) {}

  /** One rejected cutting-permit submission ({@code TENURE_APPLICATION} in state REJ). */
  public record TenureAppCpRejectionDto(
      String cuttingPermitId,
      Long submissionId,
      LocalDate submissionDate,
      LocalDate rejectionDate,
      String rejectionMessage) {}

  /**
   * {@code GET .../tenure-applications/{tenureAppId}/professional-declarations} — legacy's
   * Prof Dec popup (FTA_950X_TEN_APP_PROF_DEC).
   */
  public record TenureAppProfDecDto(
      Long tenureAppId,
      String forestFileId,
      Long submissionId,
      String declarationTypeCode,
      String declarationTypeDesc,
      LocalDate declarationDate,
      String clientNumber,
      String clientLocationCode,
      String clientName,
      String declarantName,
      String certificationReferenceId,
      String professionalIdentifierCode,
      String phoneNumber,
      String emailAddress,
      String webSiteAddress,
      String declarantComments,
      String cutBlockIds) {}

  /**
   * {@code POST .../tenure-applications/{tenureAppId}/issue-permit}'s form fields (the permit
   * file travels beside them as {@link TenureAppPermitFile}) — legacy's Issue Permit popup.
   *
   * @param hvaSkey        the row's harvesting authority, when it has one (legacy passed the
   *     row's)
   * @param documentName   the document's title in document management
   * @param recipients     the e-mail addresses added with Add Email, in order
   * @param attachDocument Attach Document To Email (legacy default: checked)
   */
  public record TenureAppIssuePermitRequest(
      Long hvaSkey, String documentName, List<String> recipients, boolean attachDocument) {}

  /** The uploaded permit file. */
  public record TenureAppPermitFile(String fileName, byte[] content) {
    public long size() {
      return content == null ? 0 : content.length;
    }
  }

  /**
   * What the issue did.
   *
   * @param message      legacy's confirmation
   * @param emailWarning set when the permit was issued but the e-mail could not be sent
   */
  public record TenureAppIssuePermitResult(Long tenureAppId, String message, String emailWarning) {}
}
