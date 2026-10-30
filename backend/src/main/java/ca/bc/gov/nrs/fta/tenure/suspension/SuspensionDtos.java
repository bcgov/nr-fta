package ca.bc.gov.nrs.fta.tenure.suspension;

import java.time.LocalDate;
import java.util.List;

/** What the suspension screens, legacy FTA912 and FTA914, read and send. */
public final class SuspensionDtos {

  private SuspensionDtos() {}

  /** A code and its description, for a dropdown. */
  public record SuspensionCode(String code, String description) {}

  /** A block on FTA912's list; {@code selectable} is false for one already suspended. */
  public record PermitBlock(
      long cbSkey,
      String cutBlockId,
      String statusCode,
      String statusDescription,
      LocalDate plannedHarvestDate,
      Double plannedGrossArea,
      Double plannedNetArea,
      boolean selectable) {}

  /** FTA912: a permit's term, the blocks it can suspend, and the partition codes on offer. */
  public record PermitSuspension(
      String forestFileId,
      String cuttingPermitId,
      LocalDate effectiveDate,
      LocalDate expiryDate,
      LocalDate extendDate,
      List<PermitBlock> blocks,
      List<SuspensionCode> partitionCodes) {}

  /** A block's suspension record. */
  public record Suspension(
      long suspensionId,
      String partitionCode,
      String orderNumber,
      LocalDate startDate,
      LocalDate endDate,
      String preStatusCode,
      String preStatusDescription,
      Long monthsRemaining,
      long revisionCount) {}

  /** FTA914: a block, its suspension if it has one, and what may be done with it. */
  public record BlockSuspension(
      long cbSkey,
      String forestFileId,
      String cuttingPermitId,
      String cutBlockId,
      String statusCode,
      String statusDescription,
      LocalDate permitIssueDate,
      LocalDate permitExpiryDate,
      LocalDate permitExtendDate,
      Suspension suspension,
      boolean canSuspend,
      boolean canEdit,
      List<SuspensionCode> partitionCodes,
      List<SuspensionCode> postStatusCodes) {}

  /** FTA914's Suspend Block. */
  public record SuspensionCreateRequest(
      String partitionCode, String orderNumber, LocalDate startDate, LocalDate endDate) {}

  /** The action FTA914's Save Suspension and End Suspension share a PUT for. */
  public enum SuspensionUpdateAction { SAVE, END }

  /** FTA914's Save Suspension or End Suspension. */
  public record SuspensionUpdateRequest(
      SuspensionUpdateAction action,
      String partitionCode,
      String orderNumber,
      LocalDate startDate,
      LocalDate endDate,
      String postStatusCode,
      boolean updateAllBlocks,
      Long revisionCount) {}

  /** FTA914's Delete Suspension. */
  public record SuspensionDeleteRequest(boolean updateAllBlocks, Long revisionCount) {}
}
