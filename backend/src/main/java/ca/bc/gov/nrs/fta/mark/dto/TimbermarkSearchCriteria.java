package ca.bc.gov.nrs.fta.mark.dto;

/**
 * The FTA002 Timber Mark Search criteria.
 *
 * <p>One record rather than a twenty-argument method. The fields mirror the
 * legacy screen (see {@code fta002MarkSrch.jsp}) and the {@code IN OUT}
 * parameters of {@code THE.FTA_002_MARK_SRCH.MAINLINE}.
 *
 * <p>The last four belong to the screen's boxed "Private Mark Criteria" panel.
 *
 * @param adminOrgUnitNo      numeric district; filters {@code tm.forest_district}
 * @param districtAdminZone   4-character zone
 * @param forestFileId        file id, prefix match
 * @param cuttingPermitId     exact cutting permit
 * @param timberMark          prefix match — but see the key-search note below
 * @param fileTypeCode        exact file type
 * @param markStatusSt        harvest authority status code
 * @param clientNumber        exact client number
 * @param clientLocnCode      client location
 * @param clientName          client name, prefix match
 * @param fileClientType      A (main) or B (secondary); absent means both
 * @param mgmtUnitType        management unit type
 * @param mgmtUnitId          management unit id
 * @param issueDateFrom       inclusive lower bound on the mark's issue date
 * @param issueDateTo         inclusive upper bound
 * @param expiryDateFrom      inclusive lower bound on extend-or-expiry date
 * @param expiryDateTo        inclusive upper bound
 * @param salvageTypeCode     salvage type; the literal {@code ALL} means "any
 *                            salvage type at all" rather than an equality
 * @param certificate         private mark certificate
 * @param landDistrict        {@code PRIMARY_LAND_INDEX_CODE} on the land index
 * @param primaryId           {@code SECONDARY_LAND_INDEX_CODE} on the land index
 * @param primaryDetail       the land index description
 * @param privateMarkOnlyInd  {@code Y} restricts to private mark file types
 * @param sortBy              {@code district}, {@code client} or {@code fileType}
 */
public record TimbermarkSearchCriteria(
    String adminOrgUnitNo,
    String districtAdminZone,
    String forestFileId,
    String cuttingPermitId,
    String timberMark,
    String fileTypeCode,
    String markStatusSt,
    String clientNumber,
    String clientLocnCode,
    String clientName,
    String fileClientType,
    String mgmtUnitType,
    String mgmtUnitId,
    String issueDateFrom,
    String issueDateTo,
    String expiryDateFrom,
    String expiryDateTo,
    String salvageTypeCode,
    String certificate,
    String landDistrict,
    String primaryId,
    String primaryDetail,
    String privateMarkOnlyInd,
    String sortBy) {

  /** Sort on the administrative district — the legacy default. */
  public static final String SORT_DISTRICT = "district";

  /** Sort on client name. */
  public static final String SORT_CLIENT = "client";

  /** Sort on file type. */
  public static final String SORT_FILE_TYPE = "fileType";

  /** The salvage value meaning "has any salvage type", not an equality match. */
  public static final String SALVAGE_ALL = "ALL";
}
