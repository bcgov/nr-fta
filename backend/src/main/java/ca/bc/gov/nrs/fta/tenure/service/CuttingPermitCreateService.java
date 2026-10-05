package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitCreateRequest;
import java.math.BigDecimal;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Adds a cutting permit, with its timber mark, to a tenure.
 *
 * <p>Legacy's FTA901 "Add New" opened FTA902 in add mode, but FTA902's save never inserts a
 * permit; permits are created by the ESF tenure-application load,
 * {@code FTA_XML_PROCESS.process_harvest_cp}. This ports that create's harvesting-authority
 * part — {@code generate_timber_mark}, {@code create_harvest_auth},
 * {@code create_hauling_authority}, {@code create_harvesting_hauling_xref} — without ESF's
 * geometry, amendment and application records, behind FTA902's field checks:
 *
 * <ul>
 *   <li>the tenure is a timber tenure (file type A…), as FTA901 requires;
 *   <li>the CP ID passes {@code FTA_EDIT_CP_ID} (per-file-type rules, no duplicate);
 *   <li>the term is at most 48 months (60 for an A11) — FTA902's rule for permits issued
 *       after Nov 3 2003 — and the marking instrument is given unless the method is E;
 *   <li>salvage is SSS for an A31, and for an A01/A41 whose licence is for salvage (SNRFL).
 * </ul>
 *
 * <p>The permit starts PE, as ESF's do, with the quota type {@code FTA_DEFAULT_QUOTA_TYPE}
 * gives the file type and, when none is chosen, the district's default cascade split.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class CuttingPermitCreateService {

  /** ESF's starting status for a new harvesting authority (create_harvest_auth). */
  private static final String NEW_STATUS = "PE";

  private static final int MAX_LOCATION = 50;

  private static final BigDecimal MAX_AREA = new BigDecimal("99999999.9999");

  private static final Set<String> SALVAGE_LICENCE_TYPES = Set.of("A01", "A41");

  private static final String TENURE_SQL =
      """
      SELECT pfu.file_type_code,
             TO_CHAR(pfu.forest_region) AS forest_region,
             hs.salvage_ind
        FROM the.prov_forest_use pfu
        LEFT JOIN the.harvest_sale hs ON hs.forest_file_id = pfu.forest_file_id
       WHERE pfu.forest_file_id = :forestFileId
      """;

  private static final String DISTRICT_SQL =
      """
      SELECT COUNT(*) FROM the.org_unit
       WHERE org_unit_no = :code AND org_level_code = 'D'
         AND SYSDATE BETWEEN effective_date AND expiry_date
      """;

  private static final String MARK_EXISTS_SQL =
      "SELECT COUNT(*) FROM the.hauling_authority WHERE timber_mark = :timberMark";

  private static final String DEFAULT_CASCADE_SQL =
      "SELECT the.fta_get_default_cascade(:district) FROM dual";

  private static final String NEXT_HVA_SQL =
      "SELECT the.harvesting_authority_seq.NEXTVAL FROM dual";

  private static final String INSERT_HVA_SQL =
      """
      INSERT INTO the.harvesting_authority (
        hva_skey, forest_file_id, cutting_permit_id, forest_district, geographic_district,
        harvest_type_code, harvest_auth_status_code, tenure_term, status_date,
        quota_type_code, salvage_type_code, cascade_split_code, catastrophic_ind,
        crown_granted_ind, cruise_based_ind, deciduous_ind, location, harvest_area,
        entry_userid, entry_timestamp, update_userid, update_timestamp, revision_count
      ) VALUES (
        :hvaSkey, :forestFileId, :cpId, :district, :district,
        'G', :status, :term, SYSDATE,
        the.fta_default_quota_type(:fileType), :salvage, :cascade, :catastrophic,
        'N', :cruiseBased, :deciduous, :location, :area,
        :userId, SYSDATE, :userId, SYSDATE, 0
      )
      """;

  private static final String INSERT_HAULING_SQL =
      """
      INSERT INTO the.hauling_authority (
        forest_file_id, timber_mark, marking_method_code, marking_instrument_code,
        entry_userid, entry_timestamp, update_userid, update_timestamp, revision_count
      ) VALUES (
        :forestFileId, :timberMark, :method, :instrument,
        :userId, SYSDATE, :userId, SYSDATE, 0
      )
      """;

  private static final String INSERT_XREF_SQL =
      """
      INSERT INTO the.harvesting_hauling_xref (
        hva_skey, timber_mark, primary_mark_ind,
        entry_userid, entry_timestamp, update_userid, update_timestamp, revision_count
      ) VALUES (
        :hvaSkey, :timberMark, 'Y',
        :userId, SYSDATE, :userId, SYSDATE, 0
      )
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final CuttingPermitLegacy legacy;

  public CuttingPermitCreateService(NamedParameterJdbcTemplate jdbc, CuttingPermitLegacy legacy) {
    this.jdbc = jdbc;
    this.legacy = legacy;
  }

  /** What was created: the CP and its generated timber mark. */
  public record Created(String cuttingPermitId, String timberMark) {}

  /**
   * Creates the cutting permit.
   *
   * @throws ResponseStatusException 404 if the tenure does not exist; 409 if it takes no
   *     cutting permits; 400 if a field is invalid or no mark can be made for it
   */
  @Transactional
  public Created create(String forestFileId, CuttingPermitCreateRequest q, String userId) {
    Map<String, Object> tenure;
    try {
      tenure = jdbc.queryForMap(TENURE_SQL, Map.of("forestFileId", forestFileId));
    } catch (EmptyResultDataAccessException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenure not found.");
    }
    String fileType = (String) tenure.get("file_type_code");
    if (fileType == null || !fileType.startsWith("A")) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Cutting permits can be added only to a timber tenure (file type A…).");
    }

    String cpId = upper(q.cuttingPermitId());
    String district = trim(q.forestDistrict());
    String location = trim(q.location());
    String method = upper(q.markingMethodCode());
    String instrument = upper(q.markingInstrumentCode());
    String salvage = upper(q.salvageTypeCode());
    String cascade = upper(q.cascadeSplitCode());

    List<String> e = new ArrayList<>();
    if (cpId == null) {
      e.add("CP ID is mandatory.");
    } else if (cpId.length() > 3) {
      e.add("CP ID can be at most 3 characters.");
    } else {
      String problem = legacy.cpIdProblem(cpId, forestFileId, fileType);
      if (problem != null) {
        e.add(problem);
      }
    }
    if (district == null) {
      e.add("District is mandatory.");
    } else if (!exists(DISTRICT_SQL, district)) {
      e.add("District " + district + " is not a current district.");
    }
    if (location != null && location.length() > MAX_LOCATION) {
      e.add("Location can be at most " + MAX_LOCATION + " characters.");
    }
    int maxTerm = "A11".equals(fileType) ? 60 : 48;
    if (q.tenureTerm() == null) {
      e.add("Term is mandatory.");
    } else if (q.tenureTerm() < 1 || q.tenureTerm() > maxTerm) {
      e.add("Term must be 1 to " + maxTerm + " months"
          + ("A11".equals(fileType) ? "." : " — a cutting permit's term cannot be more than"
              + " 4 years."));
    }
    if (q.harvestArea() != null
        && (q.harvestArea().signum() < 0 || q.harvestArea().compareTo(MAX_AREA) > 0)) {
      e.add("Area must be 0 or more hectares.");
    }
    if (method == null) {
      e.add("Compliance Method is mandatory.");
    } else if (!codeExists("the.marking_method_code", "marking_method_code", method)) {
      e.add("Compliance Method " + method + " is not a current code.");
    }
    if (instrument == null) {
      if (!"E".equals(method)) {
        e.add("Marking Instrument is mandatory.");
      }
    } else if (!codeExists("the.marking_instrument_code", "marking_instrument_code", instrument)) {
      e.add("Marking Instrument " + instrument + " is not a current code.");
    }
    if ("A31".equals(fileType)) {
      // ESF overwrites an A31's salvage type: it is always for salvage.
      salvage = "SSS";
    } else if (SALVAGE_LICENCE_TYPES.contains(fileType)
        && "Y".equals(tenure.get("salvage_ind")) && !"SSS".equals(salvage)) {
      e.add("Salvage type for this CP must be SSS if the Forest Licence is for salvage (SNRFL).");
    }
    if (salvage != null && !codeExists("the.salvage_type_code", "salvage_type_code", salvage)) {
      e.add("Salvage Type " + salvage + " is not a current code.");
    }
    if (cascade != null
        && !codeExists("the.cascade_split_code", "cascade_split_code", cascade)) {
      e.add("Cascade Split " + cascade + " is not a current code.");
    }
    if (!e.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", e));
    }

    String timberMark;
    try {
      timberMark = legacy.generateTimberMark(forestFileId, fileType, cpId);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
    Long markCount = jdbc.queryForObject(
        MARK_EXISTS_SQL, Map.of("timberMark", timberMark), Long.class);
    if (markCount != null && markCount > 0) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Timber mark " + timberMark + " already exists.");
    }
    if (cascade == null) {
      cascade = jdbc.queryForObject(
          DEFAULT_CASCADE_SQL, Map.of("district", district), String.class);
    }

    Long hvaSkey = jdbc.queryForObject(NEXT_HVA_SQL, Map.of(), Long.class);
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("hvaSkey", hvaSkey)
        .addValue("forestFileId", forestFileId.toUpperCase(Locale.ROOT))
        .addValue("cpId", cpId)
        .addValue("district", district, Types.INTEGER)
        .addValue("status", NEW_STATUS)
        .addValue("term", q.tenureTerm(), Types.INTEGER)
        .addValue("fileType", fileType)
        .addValue("salvage", salvage)
        .addValue("cascade", cascade)
        .addValue("catastrophic", yn(q.catastrophic()))
        .addValue("cruiseBased", yn(q.cruiseBased()))
        .addValue("deciduous", yn(q.deciduous()))
        .addValue("location", location)
        .addValue("area", q.harvestArea(), Types.NUMERIC)
        .addValue("timberMark", timberMark)
        .addValue("method", method)
        .addValue("instrument", instrument)
        .addValue("userId", userId);
    try {
      jdbc.update(INSERT_HVA_SQL, p);
      jdbc.update(INSERT_HAULING_SQL, p);
      jdbc.update(INSERT_XREF_SQL, p);
    } catch (DuplicateKeyException ex) {
      // Another add of the same CP or mark got in after the checks above.
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "CP " + cpId + " or timber mark " + timberMark + " was just added. Reload the list.");
    }
    return new Created(cpId, timberMark);
  }

  private boolean exists(String sql, String code) {
    Long n = jdbc.queryForObject(sql, Map.of("code", code), Long.class);
    return n != null && n > 0;
  }

  private boolean codeExists(String table, String column, String code) {
    // Table and column are constants from this class, never input.
    return exists(
        "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = :code"
            + " AND SYSDATE BETWEEN effective_date AND expiry_date",
        code);
  }

  private static String trim(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  private static String upper(String s) {
    String t = trim(s);
    return t == null ? null : t.toUpperCase(Locale.ROOT);
  }

  private static String yn(boolean b) {
    return b ? "Y" : "N";
  }
}
