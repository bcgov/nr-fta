package ca.bc.gov.nrs.fta.tenure.dto;

/**
 * A single harvesting-authority search result.
 *
 * <p>Mirrors the {@code rec_results} record returned by the legacy Oracle
 * package {@code THE.FTA_HVA_SEARCH} (see
 * {@code fta-archive/fta/database/ddl/pkg/FTA_HVA_SEARCH.PKS}): the cutting
 * permit / timber mark / oil-and-gas identifying columns the harvesting
 * authority search returns. Column order and names follow the record type.
 */
public record HarvestingSearchDto(
    Long hvaSkey,
    String orgUnitCode,
    String clientName,
    String clientNumber,
    String fileTypeCode,
    String forestFileId,
    String cuttingPermitId,
    String timberMark,
    String ogcNumber,
    String ntsMapblock,
    String ntsMapunit,
    String ntsMapquarter,
    String ntsMapsheetGrid,
    String ntsMapsheetLetter,
    String ntsMapsheetSquare,
    String programNumber,
    String geographicIdentifier) {}
