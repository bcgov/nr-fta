package ca.bc.gov.nrs.fta.recreation.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The four child lists on the FTA701 Recreation Project screen.
 *
 * <p>Grouped in one file because each is a handful of fields and they are only
 * ever used together, as parts of {@link RecreationProjectDetailDto}.
 */
public final class RecreationChildDtos {

  private RecreationChildDtos() {}

  /**
   * A district the project is cross-referenced to.
   *
   * @param districtCode {@code RECREATION_DISTRICT_XREF.recreation_district_code}
   * @param description  the code's description, which is all the grid shows
   */
  public record District(String districtCode, String description) {}

  /**
   * One fee, with the days of the week it applies on.
   *
   * <p>The seven day flags are {@code Y}/{@code N} columns rather than a set,
   * so they are carried individually and the UI renders them as checkboxes.
   *
   * @param feeId         primary key, from {@code RECREATION_FEE_SEQ}
   * @param feeAmount     formatted by legacy as {@code FM990.00}
   * @param feeStartDate  ISO date
   * @param feeEndDate    ISO date
   * @param feeCode       {@code RECREATION_FEE_CODE}
   * @param feeDescription the code's description
   * @param mondayInd     applies on Monday
   * @param tuesdayInd    applies on Tuesday
   * @param wednesdayInd  applies on Wednesday
   * @param thursdayInd   applies on Thursday
   * @param fridayInd     applies on Friday
   * @param saturdayInd   applies on Saturday
   * @param sundayInd     applies on Sunday
   * @param revisionCount for the delete guard
   */
  public record Fee(
      Long feeId,
      BigDecimal feeAmount,
      String feeStartDate,
      String feeEndDate,
      String feeCode,
      String feeDescription,
      String mondayInd,
      String tuesdayInd,
      String wednesdayInd,
      String thursdayInd,
      String fridayInd,
      String saturdayInd,
      String sundayInd,
      Long revisionCount) {}

  /**
   * One access route to the project.
   *
   * <p>The type and sub-type are not independent: only the pairs listed in
   * {@code RECREATION_ACCESS_XREF} are legal, which is why the UI has to filter
   * sub-types by the chosen type rather than offer both lists freely.
   *
   * @param accessCode        {@code RECREATION_ACCESS_CODE}
   * @param accessDescription that code's description
   * @param subAccessCode     {@code RECREATION_SUB_ACCESS_CODE}
   * @param subAccessDescription that code's description
   * @param revisionCount     for the delete guard
   */
  public record Access(
      String accessCode,
      String accessDescription,
      String subAccessCode,
      String subAccessDescription,
      Long revisionCount) {}

  /**
   * An establishment order document.
   *
   * <p>The content itself is a BLOB in a separate table and is streamed on
   * demand, so it is deliberately not carried here.
   *
   * @param attachmentId   {@code RECREATION_ATTACHMENT_SEQ}
   * @param fileName       the uploaded file's name
   * @param revisionCount  for the delete guard
   */
  public record Attachment(Long attachmentId, String fileName, Long revisionCount) {}

  /**
   * What the current user may do with this project.
   *
   * <p>Legacy computes these with {@code THE.FTA_RECREATION_SECURITY}: Recreation
   * headquarters may always save; recreation staff only when their district (or
   * its rollup region) matches the file's administering office. Two further
   * rules force saving off regardless — the file must be in {@code HI} status,
   * and the project must have a spatial description. Child records additionally
   * require the parent {@code RECREATION_PROJECT} row to exist.
   *
   * @param parentSaveEnabled whether the project fields may be saved
   * @param childSaveEnabled  whether districts, fees, access and attachments may
   *                          be changed
   * @param establishedFieldsLocked whether the Project Established date and the
   *                          establishment order are locked — legacy restricts
   *                          both to Recreation headquarters
   */
  public record Permissions(
      boolean parentSaveEnabled, boolean childSaveEnabled, boolean establishedFieldsLocked) {}

  /**
   * The tombstone strip above the form, shared with the other file screens.
   *
   * @param fileStatusCode  {@code PROV_FOREST_USE.file_status_st}
   * @param fileStatusDesc  its description
   * @param fileStatusDate  when the status was set
   * @param fileTypeCode    {@code PROV_FOREST_USE.file_type_code}
   * @param fileTypeDesc    its description
   * @param adminOrgCode    administering org unit code
   * @param adminOrgName    administering org unit name
   */
  public record Tombstone(
      String fileStatusCode,
      String fileStatusDesc,
      String fileStatusDate,
      String fileTypeCode,
      String fileTypeDesc,
      String adminOrgCode,
      String adminOrgName) {}

  /** Convenience holder so the service can return every child list at once. */
  public record Children(
      List<District> districts,
      List<Fee> fees,
      List<Access> access,
      List<Attachment> attachments) {}
}
