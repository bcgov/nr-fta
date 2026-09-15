package ca.bc.gov.nrs.fta.tenure.dto;

/**
 * The FTA005 Harvesting Authority Search criteria.
 *
 * <p>Fields mirror the legacy screen
 * ({@code fta005HarvestingAuthoritySearch.jsp}) and the parameters of the
 * standalone procedure {@code THE.FTA_005_HVA_SEARCH}. Note that the search is
 * a <em>procedure</em>, not a package — {@code THE.FTA_HVA_SEARCH} is only a
 * REF CURSOR record declaration and holds no logic.
 *
 * <p>The last twelve belong to the screen's boxed "Oil and Gas Criteria" panel,
 * though two of them are not oil-and-gas filters at all: see {@code purposeCode}.
 *
 * @param forestDistrict          numeric district; {@code hva.forest_district}
 * @param mgmtUnitType            matched against <em>both</em> {@code hva} and
 *                                {@code pfu} management unit type
 * @param mgmtUnitId              matched against both {@code hva} and {@code pfu}
 * @param forestFileId            file id, prefix match
 * @param cuttingPermitId         exact cutting permit
 * @param timberMark              prefix match on the cross-reference's mark —
 *                                but a <em>valid</em> mark is a key search
 * @param hvaId                   {@code hva.harvesting_authority_id}
 * @param fileTypeCode            exact {@code pfu.file_type_code}
 * @param harvestAuthStatusCode   the screen's "CP Status"
 * @param clientNumber            exact client number
 * @param clientLocationCode      client location; unlabelled on the screen
 * @param clientName              client name, prefix match, against
 *                                {@code V_CLIENT_PUBLIC}
 * @param clientTypeCode          harvest authority client type; defaults to
 *                                {@code L} inside the client sub-select
 * @param issueDateFrom           inclusive lower bound on {@code hva.issue_date}
 * @param issueDateTo             inclusive upper bound
 * @param expiryDateFrom          inclusive lower bound on {@code hva.expiry_date}
 * @param expiryDateTo            inclusive upper bound
 * @param salvageTypeCode         exact {@code hva.salvage_type_code}
 * @param zone                    {@code hva.district_admn_zone}
 * @param invoiceNumber           matched across three tables — the oil and gas
 *                                authority, pipeline segment and seismic line
 * @param searchOnlyOg            {@code Y} restricts to file type {@code A11}
 * @param ogcNumber               labelled "App Determination Number" on screen,
 *                                despite the field and result header saying OGC
 * @param geographicIdentifier    exact {@code og.geographic_identifier}
 * @param purposeCode             labelled "Purpose" and drawn inside the oil and
 *                                gas box, but it filters
 *                                {@code hva.licence_to_cut_code} — an ordinary
 *                                harvesting authority column. Filter only: it is
 *                                not selected or displayed
 * @param ntsQuarter              {@code og.nts_quarter}
 * @param ntsMapUnit              {@code og.nts_mapunit}
 * @param ntsMapBlock             {@code og.nts_mapblock}
 * @param ntsMapsheetGrid         {@code og.mapsheet_grid}
 * @param ntsMapsheetLetter       {@code og.mapsheet_letter}
 * @param ntsMapsheetSquare       {@code og.mapsheet_square}
 * @param sortBy                  {@code 1} district, {@code 2} client name,
 *                                {@code 3} file type
 */
public record HarvestingSearchCriteria(
    String forestDistrict,
    String mgmtUnitType,
    String mgmtUnitId,
    String forestFileId,
    String cuttingPermitId,
    String timberMark,
    String hvaId,
    String fileTypeCode,
    String harvestAuthStatusCode,
    String clientNumber,
    String clientLocationCode,
    String clientName,
    String clientTypeCode,
    String issueDateFrom,
    String issueDateTo,
    String expiryDateFrom,
    String expiryDateTo,
    String salvageTypeCode,
    String zone,
    String invoiceNumber,
    String searchOnlyOg,
    String ogcNumber,
    String geographicIdentifier,
    String purposeCode,
    String ntsQuarter,
    String ntsMapUnit,
    String ntsMapBlock,
    String ntsMapsheetGrid,
    String ntsMapsheetLetter,
    String ntsMapsheetSquare,
    String sortBy) {

  /** Sort on the district org unit code — the legacy default. */
  public static final String SORT_DISTRICT = "1";

  /** Sort on client name. */
  public static final String SORT_CLIENT = "2";

  /**
   * Sort on file type. In the legacy {@code DECODE} this is the default branch
   * rather than an explicit match, so any unrecognised value sorts this way too.
   */
  public static final String SORT_FILE_TYPE = "3";

  /** The file type the "Only Oil and Gas" checkbox restricts the search to. */
  public static final String OIL_AND_GAS_FILE_TYPE = "A11";

  /**
   * Whether the oil and gas result columns should be shown.
   *
   * <p>Mirrors {@code HarvestingAuthoritySearchVOImpl.getShowOilAndGasColumns}:
   * the checkbox being ticked <em>or</em> the file type being {@code A11}. The
   * two conditions are independent — only the checkbox filters rows.
   */
  public boolean showOilAndGasColumns() {
    return "Y".equals(searchOnlyOg) || OIL_AND_GAS_FILE_TYPE.equals(fileTypeCode);
  }
}
