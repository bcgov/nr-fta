package ca.bc.gov.nrs.fta.tenure.dto;

import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import java.time.LocalDate;
import java.util.List;

/** The cutting permit detail's write requests and its condition and postponement rows. */
public final class CuttingPermitWriteDtos {

  private CuttingPermitWriteDtos() {}

  /** FTA902's Save. Dates are {@code yyyy-MM-dd}, as the legacy package takes them. */
  public record PermitSaveRequest(
      Long revisionCount,
      String statusCode,
      String statusDate,
      String issueDate,
      String expiryDate,
      String extendDate,
      String extendReasonCode,
      String extendCount,
      String termYears,
      String termMonths,
      String quotaTypeCode,
      String salvageTypeCode,
      String deciduousInd,
      String catastrophicInd,
      String cruiseBasedInd,
      String crownLandsRegionCode,
      String markingMethodCode,
      String markingInstrumentCode,
      String districtAdmnZone,
      String location,
      String harvestArea,
      String mgmtUnitTypeCode,
      String mgmtUnitId,
      String purposeCode,
      String extAppFeeWaived,
      String extAppFeePaid,
      String withinFibreRecoveryZone) {}

  /** A write's outcome: legacy's warnings, which do not stop the save. */
  public record PermitWriteResult(List<String> warnings) {}

  /** Retire, by the revision the screen read. */
  public record PermitRetireRequest(Long revisionCount) {}

  public record PermitConditionRow(
      Long id, String themeCode, String themeDescription, String description) {}

  public record PermitConditionRequest(String themeCode, String description) {}

  public record PermitPostponementRow(
      Long id,
      LocalDate startDate,
      LocalDate expiryDate,
      String rescinded,
      LocalDate previousExpiryDate,
      Integer termMonths,
      Integer termYears) {}

  /** Adds or updates a postponement; {@code rescinded} only means something on an update. */
  public record PermitPostponementRequest(
      Long revisionCount,
      String startDate,
      String expiryDate,
      String termYears,
      String termMonths,
      String rescinded) {}

  /** The edit dialog's code lists that the shared code-list API does not carry. */
  public record PermitLookups(
      List<CodeOptionDto> quotaTypes,
      List<CodeOptionDto> extendReasons,
      List<CodeOptionDto> crownLandsRegions,
      List<CodeOptionDto> conditionThemes) {}
}
