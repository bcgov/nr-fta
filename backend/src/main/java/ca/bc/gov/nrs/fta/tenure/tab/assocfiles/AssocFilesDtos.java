package ca.bc.gov.nrs.fta.tenure.tab.assocfiles;

import java.time.LocalDate;
import java.util.List;

/** Request and response bodies of the tenure's Associated files tab (legacy FTA910). */
public final class AssocFilesDtos {

  private AssocFilesDtos() {}

  /**
   * One association, as {@code FTA_910_ASSOC_FILE.GET} lists it: the file's own associations,
   * plus the FTAS (source F) associations other files made to it, seen from this side.
   *
   * @param associatedFileId        the other file (or another system's id, for a non-F source)
   * @param fileSourceCode          FILE_SOURCE_CODE; F (Forest Tenure System) is an FTA file
   * @param fileSourceDesc          "CODE - Description"
   * @param fileAssociationTypeCode FILE_ASSOCIATION_TYPE_CODE, or null
   * @param fileAssociationTypeDesc "CODE - Description", or null
   * @param associationEndDate      only for an AAC association
   * @param revisionCount           for the optimistic lock on delete
   * @param tenure                  whether {@code associatedFileId} is an FTA file (source F) —
   *                                legacy's Details button, a link here
   * @param markCertificate         the private mark certificate, when that FTA file is a
   *                                private mark — the link opens the mark instead
   */
  public record AssocFileRowDto(
      String associatedFileId,
      String fileSourceCode,
      String fileSourceDesc,
      String fileAssociationTypeCode,
      String fileAssociationTypeDesc,
      LocalDate associationEndDate,
      Long revisionCount,
      boolean tenure,
      String markCertificate) {}

  /**
   * The tab: its rows, and whether an association may be added (and why not).
   *
   * @param rows      the associations, by associated file
   * @param canAdd    legacy's Save gate (not while the file is PE)
   * @param addReason why not, when {@code canAdd} is false
   */
  public record AssocFilesListDto(List<AssocFileRowDto> rows, boolean canAdd, String addReason) {}

  /**
   * {@code POST /api/fta/tenures/{forestFileId}/associated-files}.
   *
   * @param associatedFileId        required; upper-cased
   * @param fileSourceCode          required
   * @param fileAssociationTypeCode optional; only with source F
   * @param associationEndDate      optional; only with type AAC, inside both tenures' terms
   */
  public record AssocFileAddRequest(
      String associatedFileId,
      String fileSourceCode,
      String fileAssociationTypeCode,
      LocalDate associationEndDate) {}

  /**
   * What an add returns: the refreshed tab, and legacy's AAC file-type warning when it applies
   * (the association is saved regardless).
   */
  public record AssocFileAddResult(AssocFilesListDto list, String warning) {}
}
