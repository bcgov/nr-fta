package ca.bc.gov.nrs.fta.mark.service;

import ca.bc.gov.nrs.fta.mark.dto.MarkTransferRequest;
import java.sql.Types;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Write operations for the timber-mark transfer screen (FTA240).
 *
 * <p>Ports the {@code save} path of the legacy {@code FTA_230_MARKTRANFER} package. The mark
 * moved is the source File/CP's primary mark ({@code CUR_TT}: HARVESTING_AUTHORITY through
 * HARVESTING_HAULING_XREF), not one the user names. After the package's validations, a
 * transfer re-points the harvesting and hauling authorities and the associated tenure data
 * from the source file/CP onto the target file/CP and records the move in
 * {@code THE.MARK_TRANSFER} — all in one transaction.
 *
 * <p>Not ported: the package's user org-unit checks ("Only regional staff may transfer
 * marks", the region and BCTS office of the source) — this app has no organization levels,
 * and the screen is {@code FTA_ADMIN}'s.
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

  // Re-point the master licence-to-cut rows from the source file/CP (update_mltc).
  private static final String UPDATE_MSTR_LIC_TO_CUT_SQL =
      """
      UPDATE the.mstr_lic_to_cut
         SET forest_file_id = :targetForestFileId,
             cutting_permit_id = :targetCuttingPermitId
       WHERE forest_file_id = :sourceForestFileId
         AND cutting_permit_id = :sourceCuttingPermitId
      """;

  // Move the source's harvesting authority onto the target file/CP (update_hva).
  private static final String UPDATE_HARVESTING_AUTHORITY_SQL =
      """
      UPDATE the.harvesting_authority
         SET forest_file_id = :targetForestFileId,
             cutting_permit_id = :targetCuttingPermitId
       WHERE hva_skey = :hvaSkey
      """;

  // Record the transfer; the primary key is (timber_mark, transfer_eff_date). The legacy
  // package meant to append the current time to the date but read its time variable before
  // setting it, so the effective date was stored at midnight — as here.
  private static final String INSERT_MARK_TRANSFER_SQL =
      """
      INSERT INTO the.mark_transfer (
        timber_mark, transfer_eff_date, old_forest_file_id, old_cut_permit_id,
        old_timber_mark, entry_userid, entry_timestamp, revision_count,
        update_userid, update_timestamp
      ) VALUES (
        :timberMark, :transferEffDate,
        :sourceForestFileId, :sourceCuttingPermitId, :timberMark,
        :userId, SYSDATE, 1, :userId, SYSDATE
      )
      """;

  // CUR_TT: the file/CP's primary mark, its district and its harvesting authority.
  private static final String PRIMARY_MARK_SQL =
      """
      SELECT xref.timber_mark, hva.forest_district, hva.hva_skey
        FROM the.harvesting_authority hva
        JOIN the.harvesting_hauling_xref xref ON xref.hva_skey = hva.hva_skey
       WHERE hva.forest_file_id = :forestFileId
         AND hva.cutting_permit_id = :cuttingPermitId
         AND xref.primary_mark_ind = 'Y'
      """;

  // CUR_PFU: the forest file's type and the org unit that administers it.
  private static final String FOREST_FILE_SQL =
      """
      SELECT file_type_code, forest_region
        FROM the.prov_forest_use
       WHERE forest_file_id = :forestFileId
      """;

  // CUR_TAMF: spatial (tenure application map) features in MSRM for the source file/CP.
  private static final String MAP_FEATURE_SQL =
      """
      SELECT COUNT(*)
        FROM the.tenure_application_map_feature a
        JOIN the.tenure_application b
          ON b.forest_file_id = a.forest_file_id AND b.tenure_app_id = a.tenure_app_id
       WHERE a.forest_file_id = :forestFileId
         AND a.cutting_permit_id = :cuttingPermitId
         AND b.tenure_application_state_code IN ('APP', 'INB')
         AND a.entry_userid <> 'DATAFIX'
         AND b.submission_id NOT IN (-902, -100)
      """;

  // Sil_Get_Org_Level and Sil_Get_Region_No.
  private static final String ORG_UNIT_SQL =
      """
      SELECT org_level_code, rollup_region_no
        FROM the.org_unit
       WHERE org_unit_no = :orgUnitNo
      """;

  /** The source File/CP's primary mark (CUR_TT). */
  record PrimaryMark(String timberMark, Long forestDistrict, Long hvaSkey) {}

  /** A forest file (CUR_PFU): its type and the org unit administering it. */
  record ForestFile(String fileTypeCode, Long forestRegion) {}

  /** An org unit's level (D, R, H…) and region. */
  record OrgUnit(String orgLevelCode, Long rollupRegionNo) {}

  /**
   * Transfers the source File/CP's primary mark, and the tenure data with it, to the target
   * File/CP.
   *
   * @param request the transfer to perform
   * @param userId  the authenticated user id (audit columns)
   * @return the timber mark that was transferred
   * @throws ResponseStatusException 400 if a field is missing or the package's validation
   *     fails (every message, as legacy listed them); 409 if the source's authorities changed
   *     while the transfer ran
   */
  @Transactional
  public String transfer(MarkTransferRequest request, String userId) {
    String srcFile = id(request.sourceForestFileId());
    String srcCp = id(request.sourceCuttingPermitId());
    String trgFile = id(request.targetForestFileId());
    String trgCp = id(request.targetCuttingPermitId());

    // Fta230MarktranferForm's save validators: all five fields, and a valid date.
    List<String> e = new ArrayList<>();
    required(e, srcFile, "Source forest file ID");
    required(e, srcCp, "Source cutting permit");
    required(e, trgFile, "Target forest file ID");
    required(e, trgCp, "Target cutting permit");
    LocalDate effectiveDate = effectiveDate(e, request.transferEffDate());
    failIfAny(e);

    // FTA_230_MARKTRANFER.save's validation.
    PrimaryMark source = null;
    ForestFile srcPfu = forestFile(srcFile);
    if (srcPfu == null) {
      e.add("Invalid Source File.");
    } else {
      source = primaryMark(srcFile, srcCp);
      if (source == null) {
        e.add("Invalid Source File/CP.");
      }
      if (!aType(srcPfu)) {
        e.add("Source File must be an 'A' type tenure.");
      }
    }
    if (mapFeatureExists(srcFile, srcCp)) {
      // Legacy raised here: nothing after this check was reported.
      e.add("Cannot transfer mark. Spatial data exists in MSRM.");
      failIfAny(e);
    }
    ForestFile trgPfu = forestFile(trgFile);
    if (trgPfu == null) {
      e.add("Invalid Target File.");
    } else if (!aType(trgPfu)) {
      e.add("Target File must be an 'A' type tenure.");
    }
    if (primaryMark(trgFile, trgCp) != null) {
      e.add("Target File/CP already exists.");
    }
    if (srcFile.equals(trgFile) && srcCp.equals(trgCp)) {
      e.add("Source and Target File/CP cannot be the same.");
    }
    String sameOrg = sameOrgUnit(
        source == null ? null : source.forestDistrict(),
        trgPfu == null ? null : trgPfu.forestRegion());
    if (sameOrg != null) {
      e.add(sameOrg);
    }
    failIfAny(e);

    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("sourceForestFileId", srcFile)
        .addValue("sourceCuttingPermitId", srcCp)
        .addValue("timberMark", source.timberMark())
        .addValue("hvaSkey", source.hvaSkey())
        .addValue("targetForestFileId", trgFile)
        .addValue("targetCuttingPermitId", trgCp)
        .addValue("transferEffDate", effectiveDate, Types.DATE)
        .addValue("userId", userId);

    if (jdbc.update(UPDATE_HARVESTING_AUTHORITY_SQL, params) == 0
        || jdbc.update(UPDATE_HAULING_AUTHORITY_SQL, params) == 0) {
      // update_hva / update_haa: "record modified", and nothing is kept.
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "The source File/CP was changed by someone else. Check it and try again.");
    }
    jdbc.update(UPDATE_ASSOCIATED_TENURE_SQL, params);
    jdbc.update(UPDATE_WASTE_ASSESSMENT_AREA_SQL, params);
    jdbc.update(UPDATE_CUT_BLOCK_SQL, params);
    jdbc.update(UPDATE_CUT_BLOCK_OPEN_ADMIN_SQL, params);
    jdbc.update(UPDATE_HARVEST_AMEND_SQL, params);
    jdbc.update(UPDATE_FDC_AUDIT_LOG_SQL, params);
    jdbc.update(UPDATE_MSTR_LIC_TO_CUT_SQL, params);
    jdbc.update(INSERT_MARK_TRANSFER_SQL, params);

    return source.timberMark();
  }

  /**
   * The org-unit rule: a target file administered by a district takes marks from that district
   * only; one administered by a region, from a district in that region. Null when it holds — or
   * cannot be judged, as legacy's comparisons with a missing value never failed.
   */
  String sameOrgUnit(Long sourceDistrict, Long targetOrg) {
    if (sourceDistrict == null || targetOrg == null) {
      return null;
    }
    OrgUnit target = orgUnit(targetOrg);
    if (target == null) {
      return null;
    }
    if ("D".equals(target.orgLevelCode())) {
      return sourceDistrict.equals(targetOrg)
          ? null : "Source and Target timber marks must be in the same district.";
    }
    if ("R".equals(target.orgLevelCode())) {
      OrgUnit source = orgUnit(sourceDistrict);
      Long sourceRegion = source == null ? null : source.rollupRegionNo();
      return sourceRegion == null || target.rollupRegionNo() == null
          || Objects.equals(sourceRegion, target.rollupRegionNo())
          ? null : "Source and Target timber marks must be in the same region.";
    }
    return null;
  }

  private PrimaryMark primaryMark(String forestFileId, String cuttingPermitId) {
    List<Map<String, Object>> rows = jdbc.queryForList(PRIMARY_MARK_SQL,
        new MapSqlParameterSource()
            .addValue("forestFileId", forestFileId)
            .addValue("cuttingPermitId", cuttingPermitId));
    if (rows.isEmpty()) {
      return null;
    }
    Map<String, Object> r = rows.get(0);
    return new PrimaryMark((String) r.get("timber_mark"), number(r.get("forest_district")),
        number(r.get("hva_skey")));
  }

  private ForestFile forestFile(String forestFileId) {
    List<Map<String, Object>> rows = jdbc.queryForList(FOREST_FILE_SQL,
        new MapSqlParameterSource("forestFileId", forestFileId));
    if (rows.isEmpty()) {
      return null;
    }
    Map<String, Object> r = rows.get(0);
    return new ForestFile((String) r.get("file_type_code"), number(r.get("forest_region")));
  }

  private boolean mapFeatureExists(String forestFileId, String cuttingPermitId) {
    Long n = jdbc.queryForObject(MAP_FEATURE_SQL, new MapSqlParameterSource()
        .addValue("forestFileId", forestFileId)
        .addValue("cuttingPermitId", cuttingPermitId), Long.class);
    return n != null && n > 0;
  }

  private OrgUnit orgUnit(Long orgUnitNo) {
    List<Map<String, Object>> rows = jdbc.queryForList(ORG_UNIT_SQL,
        new MapSqlParameterSource("orgUnitNo", orgUnitNo));
    if (rows.isEmpty()) {
      return null;
    }
    Map<String, Object> r = rows.get(0);
    return new OrgUnit((String) r.get("org_level_code"), number(r.get("rollup_region_no")));
  }

  /** {@code SUBSTR(file_type_code, 1, 1) = 'A'}; a file with no type passed, as in legacy. */
  private static boolean aType(ForestFile f) {
    return f.fileTypeCode() == null || f.fileTypeCode().startsWith("A");
  }

  private static Long number(Object o) {
    return o == null ? null : ((Number) o).longValue();
  }

  /** The form upper-cased the ids. */
  private static String id(String s) {
    return s == null || s.isBlank() ? null : s.trim().toUpperCase(Locale.ROOT);
  }

  private static void required(List<String> e, String value, String label) {
    if (value == null) {
      e.add(label + " is required.");
    }
  }

  private static void failIfAny(List<String> e) {
    if (!e.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", e));
    }
  }

  /** The Legal Transfer Date: required, a {@code YYYY-MM-DD} date. */
  private static LocalDate effectiveDate(List<String> e, String value) {
    if (value == null || value.isBlank()) {
      e.add("Legal transfer date is required.");
      return null;
    }
    try {
      return LocalDate.parse(value.trim());
    } catch (DateTimeParseException ex) {
      e.add("Legal transfer date must be a valid date in the format YYYY-MM-DD.");
      return null;
    }
  }
}
