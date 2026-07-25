package ca.bc.gov.nrs.fta.mark.service;

import ca.bc.gov.nrs.fta.mark.dto.MarkTransferRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write operations for the timber-mark transfer screen (FTA240).
 *
 * <p>Ports the {@code save} path of the legacy {@code FTA_230_MARKTRANFER}
 * package. A transfer re-points the harvesting/hauling authority and all
 * associated tenure data from the source file/CP onto the target file/CP and
 * then records the move in {@code THE.MARK_TRANSFER}. The re-pointing UPDATEs
 * and the MARK_TRANSFER INSERT below mirror the package body's {@code save}
 * procedure; the district/region authority validation the package performs
 * before writing should be reproduced here as the module is hardened.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local
 * database, so it is exercised only in a deployed environment.
 */
@Service
public class MarkTransferWriteService {

  private final NamedParameterJdbcTemplate jdbc;

  public MarkTransferWriteService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // Re-point the hauling authority for the source mark onto the target file.
  private static final String UPDATE_HAULING_AUTHORITY_SQL =
      """
      UPDATE the.hauling_authority
         SET forest_file_id = :targetForestFileId
       WHERE timber_mark = :timberMark
      """;

  // Re-point associated tenure rows for the source file/mark onto the target file.
  private static final String UPDATE_ASSOCIATED_TENURE_SQL =
      """
      UPDATE the.associated_tenure
         SET forest_file_id = :targetForestFileId
       WHERE forest_file_id = :sourceForestFileId
         AND timber_mark = :timberMark
      """;

  // Re-point waste assessment areas from the source file/CP onto the target file/CP.
  private static final String UPDATE_WASTE_ASSESSMENT_AREA_SQL =
      """
      UPDATE the.waste_assessment_area
         SET forest_file_id = :targetForestFileId,
             cutting_permit_id = :targetCuttingPermitId
       WHERE forest_file_id = :sourceForestFileId
         AND cutting_permit_id = :sourceCuttingPermitId
      """;

  // Re-point cut blocks from the source file/CP onto the target file/CP.
  private static final String UPDATE_CUT_BLOCK_SQL =
      """
      UPDATE the.cut_block
         SET forest_file_id = :targetForestFileId,
             cutting_permit_id = :targetCuttingPermitId
       WHERE forest_file_id = :sourceForestFileId
         AND cutting_permit_id = :sourceCuttingPermitId
      """;

  // Re-point cut-block open admin rows from the source file/CP onto the target file/CP.
  private static final String UPDATE_CUT_BLOCK_OPEN_ADMIN_SQL =
      """
      UPDATE the.cut_block_open_admin
         SET forest_file_id = :targetForestFileId,
             cutting_permit_id = :targetCuttingPermitId
       WHERE forest_file_id = :sourceForestFileId
         AND cutting_permit_id = :sourceCuttingPermitId
      """;

  // Re-point harvest amendments from the source file/CP onto the target file/CP.
  private static final String UPDATE_HARVEST_AMEND_SQL =
      """
      UPDATE the.harvest_amend
         SET forest_file_id = :targetForestFileId,
             cutting_permit_id = :targetCuttingPermitId
       WHERE forest_file_id = :sourceForestFileId
         AND cutting_permit_id = :sourceCuttingPermitId
      """;

  // Re-point the FDC audit log from the source file/mark onto the target file.
  private static final String UPDATE_FDC_AUDIT_LOG_SQL =
      """
      UPDATE the.fdc_audit_log
         SET forest_file_id = :targetForestFileId
       WHERE timber_mark = :timberMark
         AND forest_file_id = :sourceForestFileId
      """;

  // Record the transfer. The legacy package concatenates the YYYY-MM-DD effective
  // date with the current HH24MISS and stores it as a DATE; the primary key is
  // (timber_mark, transfer_eff_date). Revision count starts at 1.
  private static final String INSERT_MARK_TRANSFER_SQL =
      """
      INSERT INTO the.mark_transfer (
        timber_mark, transfer_eff_date, old_forest_file_id, old_cut_permit_id,
        old_timber_mark, entry_userid, entry_timestamp, revision_count,
        update_userid, update_timestamp
      ) VALUES (
        :timberMark,
        TO_DATE(:transferEffDate || TO_CHAR(SYSDATE, 'HH24MISS'), 'YYYY-MM-DDHH24MISS'),
        :sourceForestFileId, :sourceCuttingPermitId, :timberMark,
        :userId, SYSDATE, 1, :userId, SYSDATE
      )
      """;

  /**
   * Transfer a timber mark from its source file/CP to the target file/CP,
   * re-pointing the associated tenure data and recording the move.
   *
   * @param request the transfer to perform
   * @param userId  the authenticated user id (audit columns)
   * @return the timber mark that was transferred
   */
  @Transactional
  public String transfer(MarkTransferRequest request, String userId) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("sourceForestFileId", request.sourceForestFileId())
        .addValue("sourceCuttingPermitId", request.sourceCuttingPermitId())
        .addValue("timberMark", request.timberMark())
        .addValue("targetForestFileId", request.targetForestFileId())
        .addValue("targetCuttingPermitId", request.targetCuttingPermitId())
        .addValue("transferEffDate", request.transferEffDate())
        .addValue("userId", userId);

    jdbc.update(UPDATE_HAULING_AUTHORITY_SQL, params);
    jdbc.update(UPDATE_ASSOCIATED_TENURE_SQL, params);
    jdbc.update(UPDATE_WASTE_ASSESSMENT_AREA_SQL, params);
    jdbc.update(UPDATE_CUT_BLOCK_SQL, params);
    jdbc.update(UPDATE_CUT_BLOCK_OPEN_ADMIN_SQL, params);
    jdbc.update(UPDATE_HARVEST_AMEND_SQL, params);
    jdbc.update(UPDATE_FDC_AUDIT_LOG_SQL, params);
    jdbc.update(INSERT_MARK_TRANSFER_SQL, params);

    return request.timberMark();
  }
}
