package ca.bc.gov.nrs.fta.range.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Detail view of a range unit and its associated pastures.
 *
 * <p>Mirrors the {@code GET} result of the legacy Oracle package
 * {@code THE.FTA_630_MN_RG_UN_PST} (see
 * {@code fta-archive/fta/database/ddl/pkg/FTA_630_MN_RG_UN_PST.PKS}): the
 * range-unit tombstone fields returned into the mainline OUT parameters, plus
 * the {@code rec_m_r_u_p_result} pasture cursor rows.
 *
 * <p>{@code districtCode} is the district's ORG_UNIT_CODE, and {@code canEdit} is legacy's
 * {@code p_ind}: whether the user may edit range files of that district
 * ({@code LoggedUserHelper.mayEditRangeDistrict}), which enables the screen's write buttons.
 */
public record RangeUnitDetailDto(
    String rangeUnitId,
    String rangeUnitName,
    String statusCode,
    String statusDescription,
    LocalDate statusDate,
    String region,
    String regionDescription,
    String district,
    String districtDescription,
    String districtCode,
    String districtAdminZone,
    Integer revisionCount,
    boolean canEdit,
    List<Pasture> pastures) {

  /** One pasture belonging to the range unit — {@code rec_m_r_u_p_result}. */
  public record Pasture(
      String pastureId,
      String pastureName,
      Integer pastureRevisionCount) {}
}
