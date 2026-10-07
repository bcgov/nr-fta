package ca.bc.gov.nrs.fta.tenure.tab.assocclients;

import java.time.LocalDate;
import java.util.List;

/** Request and response bodies of the tenure's Associated clients tab (legacy FTA920). */
public final class AssocClientsDtos {

  private AssocClientsDtos() {}

  /**
   * One of the file's clients ({@code FOREST_FILE_CLIENT}).
   *
   * @param forestFileClientSkey the row's key
   * @param fileClientType       FOREST_FILE_CLIENT_TYPE_CODE (A main licensee, B licensee, C
   *                             previous licensee, …)
   * @param fileClientTypeDesc   "CODE - Description"
   * @param revisionCount        for the optimistic lock on update and delete
   * @param canDelete            whether this row may be deleted (a C or S type, and the gate)
   */
  public record AssocClientRowDto(
      Long forestFileClientSkey,
      String clientNumber,
      String clientLocnCode,
      String clientName,
      String fileClientType,
      String fileClientTypeDesc,
      LocalDate licenseeStartDate,
      LocalDate licenseeEndDate,
      Long revisionCount,
      boolean canDelete) {}

  /**
   * The tab.
   *
   * @param rows         the file's clients, main licensee first
   * @param canEdit      whether clients may be added or updated
   * @param editReason   why not, when {@code canEdit} is false
   * @param deleteReason why C/S clients cannot be deleted, when the gate forbids it
   */
  public record AssocClientsListDto(
      List<AssocClientRowDto> rows, boolean canEdit, String editReason, String deleteReason) {}

  /**
   * {@code POST .../associated-clients} (add) and {@code PUT .../associated-clients/{skey}}
   * (update; {@code revisionCount} required).
   *
   * @param licenseeStartDate required for A, B and C
   * @param licenseeEndDate   required for C and P; blank for A and B
   */
  public record AssocClientRequest(
      String clientNumber,
      String clientLocnCode,
      String fileClientType,
      LocalDate licenseeStartDate,
      LocalDate licenseeEndDate,
      Long revisionCount) {}

  /**
   * What a write returns: the refreshed tab, and legacy's notice when a new Main Licensee made
   * the current one the previous licensee (else null).
   */
  public record AssocClientWriteResult(AssocClientsListDto list, String note) {}
}
