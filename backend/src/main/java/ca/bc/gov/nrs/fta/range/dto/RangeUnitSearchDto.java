package ca.bc.gov.nrs.fta.range.dto;

/**
 * Range unit / pasture search result row.
 *
 * <p>Mirrors the {@code rec_range_unit_results} record returned by the legacy
 * Oracle package {@code THE.FTA_006_RU_SRCH} (see
 * {@code fta-archive/fta/database/ddl/pkg/FTA_006_RU_SRCH.pks}): the columns the
 * range unit / pasture search returns.
 */
public record RangeUnitSearchDto(
    String rangeUnitId,
    String pastureId,
    String rangeUnitName,
    String pastureName,
    String rangeUnitStatusDesc) {}
