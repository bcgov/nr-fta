package ca.bc.gov.nrs.fta.tenure.dto;

import java.time.LocalDate;

/**
 * Request body for creating a new forest file / tenure ({@code POST /api/fta/tenures}): what
 * legacy FTA100's Add New asked the user for and has no default. Everything else takes
 * legacy's defaults and is edited afterwards on the Details tab.
 *
 * <p>No client: a Headquarters user's new file gets none in legacy (the district manager is
 * linked only for district and BCTS users). Clients are added on the Associated Clients tab.
 *
 * @param forestFileId      entered for A02, A06, A30 and B40, optional for B10 (assigned when
 *                          blank); assigned for every other type, so ignored
 * @param fileTypeCode      FILE_TYPE_CODE
 * @param adminOrgUnitNo    ORG_UNIT_NO of the Admin Org; for file types starting A, U or P
 * @param districtNo        ORG_UNIT_NO of the District; for every type except A… and P01
 * @param mgmtUnitType      management unit type (mandatory)
 * @param mgmtUnitId        management unit id, blank for a type-only unit (e.g. Z)
 * @param issueDate         Effective Date (optional)
 * @param purposeCode       licence to cut purpose, B05, B06 and B07 (mandatory)
 * @param districtAdminZone Zone, B05, B06, B07 and B10 (mandatory)
 * @param location          Geographic Location, B05 (mandatory), B06 and B07 (optional)
 * @param quotaTypeCode     Quota Type, B05 and B06 (mandatory), B07 (optional)
 * @param initialArea       Initial Area (ha), A30 (mandatory)
 * @param fupTypeCode       FUP Type, B10 (mandatory)
 * @param fupFnUsageCode    First Nations Purpose, when the FUP type is FN (mandatory)
 * @param fupCedarSpeciesInd Majority Volume Cedar Species, Y/N, FUP type FN
 * @param fupTreatyPurposeInd First Nations Treaty Purpose, Y/N, FUP type FN (optional)
 */
public record CreateTenureRequest(
    String forestFileId,
    String fileTypeCode,
    String adminOrgUnitNo,
    String districtNo,
    String mgmtUnitType,
    String mgmtUnitId,
    LocalDate issueDate,
    String purposeCode,
    String districtAdminZone,
    String location,
    String quotaTypeCode,
    String initialArea,
    String fupTypeCode,
    String fupFnUsageCode,
    String fupCedarSpeciesInd,
    String fupTreatyPurposeInd) {}
