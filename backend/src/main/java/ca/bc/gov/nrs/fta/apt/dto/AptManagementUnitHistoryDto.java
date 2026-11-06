package ca.bc.gov.nrs.fta.apt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One AAC determination of a management unit, a row of {@code APT_MGMT_UNIT_HISTORIES_GET}.
 *
 * @param id                           {@code APT_MGMT_UNIT_HISTORY.ID}
 * @param muId                         the management unit
 * @param determinationDate            the AAC determination date
 * @param effectiveDate                when the determination took effect
 * @param allowableAnnualCut           AAC in cubic metres
 * @param inventoryRevDate             the inventory revision date
 * @param apportionmentDeterminationDt the apportionment determination date
 * @param apportionmentEffectiveDate   the apportionment effective date
 * @param totalArea                    hectares
 * @param productiveArea               hectares
 * @param longTermHarvestLevel         cubic metres
 * @param currentNetOperatingArea      hectares
 * @param futureHarvestLandbaseArea    hectares
 * @param comments                     free text
 * @param addTimestamp                 when the row was added
 * @param addUserid                    who added it
 * @param updateTimestamp              always null: the APT function does not return it
 * @param updateUserid                 always null: the APT function does not return it
 * @param allowableAnnualCutHa         AAC in hectares
 */
public record AptManagementUnitHistoryDto(
    Long id,
    Long muId,
    LocalDate determinationDate,
    LocalDate effectiveDate,
    BigDecimal allowableAnnualCut,
    LocalDate inventoryRevDate,
    LocalDate apportionmentDeterminationDt,
    LocalDate apportionmentEffectiveDate,
    BigDecimal totalArea,
    BigDecimal productiveArea,
    BigDecimal longTermHarvestLevel,
    BigDecimal currentNetOperatingArea,
    BigDecimal futureHarvestLandbaseArea,
    String comments,
    LocalDateTime addTimestamp,
    String addUserid,
    LocalDateTime updateTimestamp,
    String updateUserid,
    BigDecimal allowableAnnualCutHa) {}
