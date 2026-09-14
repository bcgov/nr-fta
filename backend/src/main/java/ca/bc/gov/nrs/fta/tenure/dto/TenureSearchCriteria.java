package ca.bc.gov.nrs.fta.tenure.dto;

/**
 * The FTA001 Tenure Search criteria.
 *
 * <p>One record rather than a twenty-argument method. The fields mirror the
 * legacy screen (see {@code fta001TenureSearch.jsp}) and, one for one, the
 * {@code IN OUT} parameters of {@code THE.FTA_001_TENR_SRCH.MAINLINE} — so the
 * package source can pass them straight through and the table source can
 * reproduce the same predicates.
 *
 * <p>Dates are {@code String} in {@code yyyy-MM-dd} form because that is what
 * the package takes and what the screen submits; they are converted at the
 * point of use rather than parsed and re-formatted on the way in.
 *
 * @param adminOrgUnitNo      numeric {@code ORG_UNIT_NO}; filters by region or
 *                            district depending on that unit's level
 * @param forestFileId        file id, prefix match
 * @param fileTypeCode        one code, or several comma-separated
 * @param tenureType          {@code T}imber, {@code R}ange or recreation
 *                            ({@code F}) — selects which file-type code table
 *                            the file type must appear in
 * @param fileStatus          {@code TENURE_FILE_STATUS_CODE}
 * @param clientNumber        exact client number
 * @param clientLocnCode      client location, the small box beside the number
 * @param clientName          client name, prefix match
 * @param fileClientType      {@code A} (main) or {@code B} (secondary)
 * @param mgmtUnitType        management unit type, the first small box
 * @param mgmtUnitId          management unit id, the second
 * @param fileSource          pairs with {@code assocFileId}
 * @param assocFileId         associated file id, prefix match
 * @param fileName            recreation project name, prefix match
 * @param issueDateFrom       inclusive lower bound on the term's effective date
 * @param issueDateTo         inclusive upper bound
 * @param expiryDateFrom      inclusive lower bound on the term's expiry
 * @param expiryDateTo        inclusive upper bound
 * @param salvageInd          {@code Y}/{@code N} against the harvest sale
 * @param cashSaleInd         {@code Y}/{@code N}; maps to the sale's payment method
 * @param mapNotationTypeCode only meaningful when the file type is {@code M01}
 * @param sortBy              {@code org}, {@code client} or {@code fileType}
 */
public record TenureSearchCriteria(
    String adminOrgUnitNo,
    String forestFileId,
    String fileTypeCode,
    String tenureType,
    String fileStatus,
    String clientNumber,
    String clientLocnCode,
    String clientName,
    String fileClientType,
    String mgmtUnitType,
    String mgmtUnitId,
    String fileSource,
    String assocFileId,
    String fileName,
    String issueDateFrom,
    String issueDateTo,
    String expiryDateFrom,
    String expiryDateTo,
    String salvageInd,
    String cashSaleInd,
    String mapNotationTypeCode,
    String sortBy) {

  /** Sort on the administrative org unit — the legacy default. */
  public static final String SORT_ORG = "org";

  /** Sort on client name. */
  public static final String SORT_CLIENT = "client";

  /** Sort on file type. */
  public static final String SORT_FILE_TYPE = "fileType";
}
