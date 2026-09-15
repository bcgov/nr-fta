package ca.bc.gov.nrs.fta.recreation.dto;

/**
 * A single FTA007 Recreation Search result.
 *
 * <p>The legacy cursor returns eight columns, of which the screen renders five.
 * Of the three it drops, one ({@code fmfc.description}) is selected twice under
 * two different aliases, so only five distinct values were ever displayed. This
 * record carries those five.
 *
 * @param forestFileId   {@code pfu.forest_file_id}
 * @param fileStatusCode {@code pfu.file_status_st}
 * @param orgUnitCode    {@code ou.org_unit_code} — what the grid shows
 * @param orgUnitName    {@code ou.org_unit_name} — what the legacy "Admin Org"
 *                       sort actually orders by, which is why it is carried
 *                       here even though the grid shows the code
 * @param projectName    {@code rp.project_name} for new files,
 *                       {@code orp.rec_project_name} for old ones
 * @param projectType    {@code fmfc.description}
 */
public record RecreationSearchDto(
    String forestFileId,
    String fileStatusCode,
    String orgUnitCode,
    String orgUnitName,
    String projectName,
    String projectType) {}
