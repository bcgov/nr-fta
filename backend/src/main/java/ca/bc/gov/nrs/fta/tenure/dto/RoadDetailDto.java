package ca.bc.gov.nrs.fta.tenure.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Road-section detail for the FTA131 road detail screen.
 *
 * <p>Mirrors the {@code rec_rsection} record returned by the legacy Oracle
 * package {@code THE.FTA_131_ROADSECTION} (see
 * {@code fta-archive/fta/database/ddl/pkg/FTA_131_ROADSECTION.pks} — the
 * {@code get}/{@code mainline} procedures): the columns the road-section GET
 * returns for a single section, including the transferred-from/to links, the
 * derived mark list, current amendment id, and enablement indicators.
 */
public record RoadDetailDto(
    String forestFileId,
    String roadSectionId,
    String markList,
    String sectionCurrentAmendId,
    String roadSectName,
    String roadSectLength,
    String roadOrigLength,
    String roadSectionStatusCode,
    String withinAlrInd,
    String districtAdmnZone,
    BigDecimal sectionWidth,
    String xferedFromForestFileId,
    String xferedFromRoadSectionId,
    String xferedToForestFileId,
    String xferedToRoadSectionId,
    LocalDate retirementDate,
    Integer revisionCount,
    String updateEnabledInd,
    String segmentsEnabledInd) {}
