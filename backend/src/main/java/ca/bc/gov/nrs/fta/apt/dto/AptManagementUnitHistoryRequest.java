package ca.bc.gov.nrs.fta.apt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An AAC determination to create or update ({@code APT_MGMT_UNIT_HISTORY_INS_UPD}). The
 * management unit and history ids come from the path, the audit user from the token.
 *
 * @param prevId                       on create only: a determination of the same unit whose
 *                                     partitions, apportionments and BCTS distributions are
 *                                     copied onto the new one (as
 *                                     {@code apt_pkg.insert_muh_child_records} did); null
 *                                     copies nothing
 * @param determinationDate            required
 * @param effectiveDate                required
 * @param allowableAnnualCut           AAC in cubic metres; required, whole, 0 to 999,999,999
 * @param allowableAnnualCutHa         AAC in hectares; whole, 0 to 999,999,999
 * @param inventoryRevDate             optional
 * @param apportionmentDeterminationDt optional
 * @param apportionmentEffectiveDate   optional
 * @param totalArea                    hectares, 0 to 9,999,999.99
 * @param productiveArea               hectares, 0 to 9,999,999.99
 * @param longTermHarvestLevel         cubic metres; whole, 0 to 999,999,999
 * @param currentNetOperatingArea      hectares, 0 to 9,999,999.99
 * @param futureHarvestLandbaseArea    hectares, 0 to 9,999,999.99
 * @param comments                     up to 2000 characters
 */
public record AptManagementUnitHistoryRequest(
    Long prevId,
    LocalDate determinationDate,
    LocalDate effectiveDate,
    BigDecimal allowableAnnualCut,
    BigDecimal allowableAnnualCutHa,
    LocalDate inventoryRevDate,
    LocalDate apportionmentDeterminationDt,
    LocalDate apportionmentEffectiveDate,
    BigDecimal totalArea,
    BigDecimal productiveArea,
    BigDecimal longTermHarvestLevel,
    BigDecimal currentNetOperatingArea,
    BigDecimal futureHarvestLandbaseArea,
    String comments) {}
