package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.exception.DuplicateDataException;
import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import ca.bc.gov.nrs.fta.tenure.dto.CreateTenureRequest;
import ca.bc.gov.nrs.fta.tenure.tab.details.TenureDetailsLookups;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Creates a tenure as legacy FTA100's Add New did ({@code FTA_100_TENURE.ADD_NEW_REC}), by
 * writing the tables directly.
 *
 * <p><b>One transaction.</b> {@link #create} is the transaction: every check runs first, then
 * every row is written on the same connection, and any failure (a refused check, a SQL error, a
 * generator's error) rolls all of it back, so no part of a file is left behind. The control
 * tables the ids come from are updated in the same transaction and roll back with it, with one
 * exception: legacy's {@code FTA_MARK_DESIGNATE} is an autonomous transaction that commits its
 * counter itself, so a create that fails after it leaves that designate unused (a gap in the
 * sequence, not data).
 *
 * <p><b>Generators kept.</b> The ids still come from legacy's generators, which lock the shared
 * control tables, so the new app and anything else creating files never hand out the same
 * number: {@code FTA_GENERATE_FILE_ID} (file ids), {@code FTC_VALIDATION.handleFFFreeUsePermit}
 * (B10 ids) and {@code FTA_MARK_DESIGNATE} (designates for the mark-designate file types). The
 * woodlot and A06 designates come from {@code ADD_NEW_REC}'s private {@code create_mark_designate},
 * which can't be called, so its counter update is done here, locked {@code FOR UPDATE} as it did.
 *
 * <p><b>The user is Headquarters.</b> Only FTA Admin creates tenures, and the app treats that
 * role as a Headquarters user (as the Details tab does). Legacy gives a Headquarters user's new
 * file no district defaults and no client.
 *
 * <p>What ADD_NEW_REC writes, in its order: PROV_FOREST_USE; for B05, B06 and B07 the harvesting
 * authority, hauling authority and cross-reference, and licence to cut; TIMBER_TENURE for timber
 * file types; TENURE_TERM for A and U files and B08; a designate for the mark-designate file
 * types; TIMBER_LIC_AREA for A06 and A30; HARVEST_SALE for every non-recreation file;
 * FREE_USE_PERMIT for B10; and a designate for new woodlots and timber licences.
 */
@Service
public class TenureWriteService {

  private final NamedParameterJdbcTemplate jdbc;
  private final TenureDetailsLookups lookups;

  public TenureWriteService(NamedParameterJdbcTemplate jdbc, TenureDetailsLookups lookups) {
    this.jdbc = jdbc;
    this.lookups = lookups;
  }

  /** The created file's id. */
  public record Created(String forestFileId) {}

  private static final int DESIGNATE_ATTEMPTS = 50;

  // ---------------------------------------------------------------------------------------
  // SQL
  // ---------------------------------------------------------------------------------------

  private static final String FILE_TYPE_SQL = """
      SELECT COUNT(*) FROM the.file_type_code
       WHERE file_type_code = :t AND SYSDATE BETWEEN effective_date AND expiry_date
      """;

  private static final String IS_SQL = "SELECT %s(:t) FROM dual";

  /** An org unit's level and whether it is current. */
  private static final String ORG_SQL = """
      SELECT org_level_code,
             CASE WHEN SYSDATE BETWEEN effective_date AND expiry_date THEN 'Y' ELSE 'N' END
               AS current_ind
        FROM the.org_unit
       WHERE org_unit_no = :no
      """;

  /** SIL_GET_REGION_NO for a district code, and whether that district is current. */
  private static final String DISTRICT_BY_CODE_SQL = """
      SELECT TO_CHAR(the.sil_get_region_no(org_unit_code)) AS region_no,
             CASE WHEN SYSDATE BETWEEN effective_date AND expiry_date THEN 'Y' ELSE 'N' END
               AS current_ind
        FROM the.org_unit
       WHERE org_unit_code = :code
      """;

  private static final String FILE_EXISTS_SQL =
      "SELECT COUNT(*) FROM the.prov_forest_use WHERE forest_file_id = :id";

  private static final String MARK_PREFIX_SQL =
      "SELECT COUNT(*) FROM the.hauling_authority WHERE timber_mark LIKE :prefix || '%'";

  private static final String MARK_EXISTS_SQL =
      "SELECT COUNT(*) FROM the.hauling_authority WHERE timber_mark = :mark";

  private static final String INSERT_PFU = """
      INSERT INTO the.prov_forest_use (
        forest_file_id, file_status_st, file_status_date, file_type_code, forest_region,
        mgmt_unit_type, mgmt_unit_id, bcts_org_unit, sb_funded_ind, district_admin_zone,
        entry_userid, entry_timestamp, update_userid, update_timestamp, revision_count)
      VALUES (
        :id, :status, TRUNC(SYSDATE), :type, TO_NUMBER(:adminOrg),
        :muType, :muId, NULL, 'N', :zone,
        :user, SYSDATE, :user, SYSDATE, 0)
      """;

  private static final String NEXT_HVA_SQL =
      "SELECT the.harvesting_authority_seq.NEXTVAL FROM dual";

  /** create_harvest_authority, with FTA100's new-file defaults. */
  private static final String INSERT_HVA = """
      INSERT INTO the.harvesting_authority (
        hva_skey, forest_file_id, forest_district, district_admn_zone, geographic_district,
        mgmt_unit_id, mgmt_unit_type_code, harvest_type_code, harvest_auth_status_code,
        tenure_term, status_date, issue_date, expiry_date, quota_type_code,
        crown_lands_region_code, salvage_type_code, cascade_split_code, deciduous_ind,
        catastrophic_ind, cruise_based_ind, crown_granted_ind, location, harvest_area,
        is_waste_assessment_required, is_within_fibre_recovery_zone,
        entry_userid, entry_timestamp, update_userid, update_timestamp, revision_count)
      VALUES (
        :skey, :id, TO_NUMBER(:district), :zone, TO_NUMBER(:district),
        :muId, :muType, 'G', :status,
        0, TRUNC(SYSDATE), :issueDate, NULL, :quota,
        NULL, :salvage, NULL, 'N',
        'N', 'N', 'N', :location, NULL,
        'Y', 'U',
        :user, SYSDATE, :user, SYSDATE, 0)
      """;

  /** create_hauling_authority: FTA100's default compliance S (standard) and instrument H. */
  private static final String INSERT_HAULING = """
      INSERT INTO the.hauling_authority (
        timber_mark, forest_file_id, marking_method_code, marking_instrument_code,
        entry_userid, entry_timestamp, update_userid, update_timestamp, revision_count)
      VALUES (:mark, :id, 'S', 'H', :user, SYSDATE, :user, SYSDATE, 0)
      """;

  private static final String INSERT_XREF = """
      INSERT INTO the.harvesting_hauling_xref (
        hva_skey, timber_mark, revision_count, primary_mark_ind,
        entry_userid, entry_timestamp, update_userid, update_timestamp)
      VALUES (:skey, :mark, 0, 'Y', :user, SYSDATE, :user, SYSDATE)
      """;

  /** create_licence_to_cut: ADD_NEW_REC passes Y for the valid-mark indicator. */
  private static final String INSERT_LTC = """
      INSERT INTO the.licence_to_cut (
        forest_file_id, licence_to_cut_cd, valid_mark_ind,
        entry_userid, entry_timestamp, update_userid, update_timestamp, revision_count)
      VALUES (:id, NVL(:purpose, 'OT'), 'Y', :user, SYSDATE, :user, SYSDATE, 0)
      """;

  /** fta_create_timber_tenure: not replaceable (GET_DEFAULTS), not SFL. */
  private static final String INSERT_TIMBER_TENURE = """
      INSERT INTO the.timber_tenure (
        forest_file_id, mark_designate, licence_replaceable_ind, supplemental_fl_ind,
        revision_count, entry_userid, entry_timestamp, update_userid, update_timestamp)
      VALUES (:id, :designate, 'N', 'N', 0, :user, SYSDATE, :user, SYSDATE)
      """;

  /** Fta_Create_Tenure_Term: no term or expiry yet. */
  private static final String INSERT_TENURE_TERM = """
      INSERT INTO the.tenure_term (
        forest_file_id, tenure_term, legal_effective_dt, initial_expiry_dt,
        entry_userid, entry_timestamp, update_userid, update_timestamp, revision_count)
      VALUES (:id, NULL, :issueDate, NULL, :user, SYSDATE, :user, SYSDATE, 0)
      """;

  /** save_mark_designate, for a file that has its TIMBER_TENURE row. */
  private static final String UPDATE_DESIGNATE = """
      UPDATE the.timber_tenure
         SET mark_designate = :designate, revision_count = revision_count + 1,
             update_timestamp = SYSDATE, update_userid = :user
       WHERE forest_file_id = :id
      """;

  /** save_mark_designate, for a file without one. */
  private static final String INSERT_DESIGNATE = """
      INSERT INTO the.timber_tenure (
        forest_file_id, mark_designate, revision_count,
        update_userid, update_timestamp, entry_userid, entry_timestamp)
      VALUES (:id, :designate, 0, :user, SYSDATE, :user, SYSDATE)
      """;

  private static final String CURRENT_DESIGNATE_SQL =
      "SELECT mark_designate FROM the.timber_tenure WHERE forest_file_id = :id";

  private static final String INSERT_TL_AREA = """
      INSERT INTO the.timber_lic_area (
        forest_file_id, init_licence_area, obligation_area, eliminated_area, non_harvest_area,
        entry_userid, entry_timestamp, update_userid, update_timestamp, revision_count)
      VALUES (:id, TO_NUMBER(:initial), NULL, NULL, NULL, :user, SYSDATE, :user, SYSDATE, 0)
      """;

  /** create_harvest_sale: payment method A (not a cash sale), not BCTS-funded. */
  private static final String INSERT_HARVEST_SALE = """
      INSERT INTO the.harvest_sale (
        forest_file_id, sale_method_code, sale_type_cd, payment_method_cd, salvage_ind,
        sb_fund_ind, bcts_org_unit, plnd_sb_cat_code,
        revision_count, update_userid, update_timestamp, entry_userid, entry_timestamp)
      VALUES (:id, :method, :saleType, 'A', :salvage,
        'N', NULL, :category,
        0, :user, SYSDATE, :user, SYSDATE)
      """;

  /** create_free_use_permit. */
  private static final String INSERT_FUP = """
      INSERT INTO the.free_use_permit (
        forest_file_id, amendment_id, amend_status_code, amend_status_date, current_ind,
        fup_comment, retirement_date, revision_count,
        entry_userid, entry_timestamp, update_userid, update_timestamp,
        majority_vol_cedar_species_ind, fn_treaty_purpose, free_use_permit_fn_usage_code,
        free_use_permit_type_code)
      VALUES (:id, LPAD('0', 5, ' '), 'PND', SYSDATE, 'Y',
        'Free use permit', NULL, 1,
        :user, SYSDATE, :user, SYSDATE,
        :cedar, :treaty, :fnUsage, :fupType)
      """;

  /** create_mark_designate's counter: key 6 for woodlots (W…), 5 for A06 (T…). */
  private static final String LOCK_DESIGNATE_CTL = """
      SELECT mark_desgn_num FROM the.mrk_dsg_num_ctl
       WHERE dummy_access_key = :key
         FOR UPDATE
      """;

  private static final String UPDATE_DESIGNATE_CTL =
      "UPDATE the.mrk_dsg_num_ctl SET mark_desgn_num = :d WHERE dummy_access_key = :key";

  // FTA_GENERATE_FILE_ID(type, trace, id IN OUT, error IN OUT, payment method, BCTS fund, SFL),
  // with ADD_NEW_REC's defaults for a new file: payment method A, not BCTS-funded, not SFL.
  private static final String GENERATE_ID_CALL =
      "{call the.fta_generate_file_id(?, 'N', ?, ?, 'A', 'N', 'N')}";

  // FTA_MARK_DESIGNATE(trace, file type, designate IN OUT, error IN OUT).
  private static final String MARK_DESIGNATE_CALL = "{call the.fta_mark_designate('N', ?, ?, ?)}";

  // handleFFFreeUsePermit takes a BOOLEAN, which JDBC can't bind, so it is wrapped.
  private static final String FUP_ID_CALL = """
      DECLARE
        v_err VARCHAR2(4000);
      BEGIN
        ? := the.ftc_validation.handleFFFreeUsePermit(?, TRUE, 'FTA_100_ADD_FUP', v_err);
        ? := v_err;
      END;
      """;

  // ---------------------------------------------------------------------------------------
  // Create
  // ---------------------------------------------------------------------------------------

  /**
   * Creates the tenure: checks the request as FTA100 did, then writes every row ADD_NEW_REC
   * writes, all in this one transaction.
   *
   * @throws ResponseStatusException 400 with legacy's messages when the request is refused,
   *     422 when an id or designate can't be generated
   * @throws DuplicateDataException 409 when an entered file id is already in use
   */
  @Transactional
  public Created create(CreateTenureRequest r, String userId) {
    String type = upper(r.fileTypeCode());
    boolean recreation = !type.isEmpty() && is("the.fta_valid_recreation_file_type", type);
    String status = type.isEmpty() ? "" : TenureCreateRules.initialStatus(type, recreation);

    // 1. Every check first: a refusal writes nothing.
    Checked c = check(r, type);

    // 2. The id. The generators lock their control table rows until this transaction ends.
    String forestFileId = TenureCreateRules.entersId(type) ? c.typedId
        : TenureCreateRules.optionalId(type) ? freeUsePermitId(c.typedId)
        : generateFileId(type);
    if (count(FILE_EXISTS_SQL, Map.of("id", forestFileId)) > 0) {
      if (TenureCreateRules.entersId(type)) {
        throw new DuplicateDataException(
            "A record already exists where the File is " + forestFileId + ".");
      }
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The file id "
          + forestFileId + " generated already exists. There is a problem with the control"
          + " table; please contact Application Services.");
    }

    String hqOrg = lookups.headquarters()[0];
    String adminOrg = TenureCreateRules.storedAdminOrg(type, c.adminOrg, c.district, hqOrg);
    String user = userId;

    // 3. The rows, in ADD_NEW_REC's order.
    try {
      jdbc.update(INSERT_PFU, params(forestFileId, user)
          .addValue("status", status)
          .addValue("type", type)
          .addValue("adminOrg", adminOrg)
          .addValue("muType", c.muType)
          .addValue("muId", emptyToNull(c.muId))
          .addValue("zone", emptyToNull(c.zone)));
    } catch (DuplicateKeyException e) {
      // Another request took the entered id between the check and here.
      throw new DuplicateDataException(
          "A record already exists where the File is " + forestFileId + ".", e);
    }

    if (TenureCreateRules.licenceToCut(type)) {
      createLicenceToCut(type, forestFileId, status, c, r.issueDate(), user);
    }

    boolean timberTenure = is("the.fta_valid_timber_tenure_type", type);
    if (timberTenure) {
      jdbc.update(INSERT_TIMBER_TENURE, params(forestFileId, user).addValue("designate",
          "A02".equals(type) ? TenureCreateRules.a02Designate(forestFileId) : null));
    }

    if (TenureCreateRules.createsTenureTerm(type)) {
      jdbc.update(INSERT_TENURE_TERM, params(forestFileId, user)
          .addValue("issueDate", r.issueDate(), Types.DATE));
    }

    // A Headquarters user's new file gets no client (ADD_NEW_REC links the district manager
    // only for district and BCTS users, and for a region user's A28).

    if (is("the.fta_valid_designate_file_type", type)) {
      // SFL A02s are excluded; new files here are never SFL.
      saveDesignate(forestFileId, generateNumberedDesignate(type), user);
    }

    if ("A30".equals(type) || "A06".equals(type)) {
      jdbc.update(INSERT_TL_AREA, params(forestFileId, user)
          .addValue("initial", emptyToNull(c.initialArea)));
    }

    if (!recreation) {
      TenureCreateRules.HarvestSale sale = TenureCreateRules.harvestSale(type, c.purpose);
      jdbc.update(INSERT_HARVEST_SALE, params(forestFileId, user)
          .addValue("method", sale.saleMethod())
          .addValue("saleType", sale.saleType())
          .addValue("salvage", sale.salvageInd())
          .addValue("category", sale.plannedCategory()));
    }

    if ("B10".equals(type) && !c.fupType.isEmpty()) {
      boolean fn = "FN".equals(c.fupType);
      String cedar = upper(r.fupCedarSpeciesInd());
      jdbc.update(INSERT_FUP, params(forestFileId, user)
          .addValue("fupType", c.fupType)
          .addValue("fnUsage", fn ? emptyToNull(upper(r.fupFnUsageCode())) : null)
          .addValue("treaty", fn ? emptyToNull(upper(r.fupTreatyPurposeInd())) : null)
          .addValue("cedar", !fn && cedar.isEmpty() ? "N" : emptyToNull(cedar)));
    }

    if (TenureCreateRules.assignsWoodlotOrTlDesignate(type, status)) {
      assignWoodlotOrTlDesignate(type, forestFileId, user);
    }

    return new Created(forestFileId);
  }

  // ---------------------------------------------------------------------------------------
  // Checks (FTA100's Save validators for a new file)
  // ---------------------------------------------------------------------------------------

  /** The request's values once checked. */
  private record Checked(String typedId, String adminOrg, String district, String muType,
      String muId, String purpose, String zone, String location, String quota,
      String initialArea, String fupType) {}

  private Checked check(CreateTenureRequest r, String type) {
    List<String> e = new ArrayList<>();

    if (type.isEmpty()) {
      e.add("Type is mandatory.");
    } else if (count(FILE_TYPE_SQL, Map.of("t", type)) == 0) {
      e.add("File type " + type + " is not a current file type.");
    }
    if (!e.isEmpty()) {
      throw badRequest(e);
    }

    // File id
    String typedId = upper(r.forestFileId());
    if (TenureCreateRules.entersId(type)) {
      if (typedId.isEmpty()) {
        e.add("File is mandatory.");
      } else {
        String format = TenureCreateRules.fileIdFormatError(type, typedId);
        if (format != null) {
          e.add(format);
        }
      }
    } else if (!TenureCreateRules.optionalId(type)) {
      typedId = "";
    }

    // Admin Org
    String adminOrg = trim(r.adminOrgUnitNo());
    if (TenureCreateRules.showsAdminOrg(type)) {
      if (adminOrg.isEmpty()) {
        e.add("Admin Org is mandatory.");
      } else {
        String[] org = org(adminOrg);
        if (org == null || !"Y".equals(org[1])) {
          e.add("Admin Org is not a current organization unit.");
        } else if (!TenureCreateRules.requiredAdminOrgLevel(type).equals(org[0])) {
          e.add("D".equals(TenureCreateRules.requiredAdminOrgLevel(type))
              ? "For " + type + " files, Admin Org must be a District."
              : "For " + type + " files, Admin Org must be a Region.");
        }
      }
    } else {
      adminOrg = "";
    }

    // A30's id names a current district within the Admin Org (a region).
    if ("A30".equals(type) && !typedId.isEmpty()) {
      String format = TenureCreateRules.a30FormatError(typedId);
      if (format != null) {
        e.add(format);
      } else {
        String code = TenureCreateRules.a30District(typedId);
        List<String[]> rows = jdbc.query(DISTRICT_BY_CODE_SQL, Map.of("code", code),
            (rs, n) -> new String[] {rs.getString("region_no"), rs.getString("current_ind")});
        if (rows.isEmpty() || !adminOrg.equals(rows.get(0)[0])) {
          e.add("File must be in the format TAANN where AA is the last 2 characters of a"
              + " district within the Admin Org and NN are 2 numbers. " + code
              + " does not belong to the Admin Org.");
        } else if (!"Y".equals(rows.get(0)[1])) {
          e.add(code + " is an expired district and therefore cannot be used in the File ID.");
        }
      }
    }

    // District
    String district = trim(r.districtNo());
    if (TenureCreateRules.showsDistrict(type)) {
      if (district.isEmpty()) {
        e.add("District is mandatory.");
      } else {
        String[] org = org(district);
        if (org == null || !"D".equals(org[0]) || !"Y".equals(org[1])) {
          e.add("District is not a current district.");
        }
      }
    } else {
      district = "";
    }

    // Management unit
    String muType = upper(r.mgmtUnitType());
    String muId = upper(r.mgmtUnitId());
    if (muType.isEmpty()) {
      e.add("Mgmt Unit Type is mandatory.");
      if (!muId.isEmpty()) {
        e.add("If Mgmt Unit Type is blank, Mgmt Unit ID must be blank.");
      }
    } else if (!lookups.mgmtUnitExists(muType, muId)) {
      e.add("Invalid Management Unit type/id combination.");
    } else if ("A02".equals(type) && !typedId.isEmpty()
        && !TenureCreateRules.a02MgmtUnitMatches(typedId, muType, muId)) {
      e.add("If file type is A02 and management type is 'T' then management unit must be the"
          + " same number as the last 2 digits in the file ID.");
    }

    // Licence to cut fields
    String purpose = upper(r.purposeCode());
    String zone = upper(r.districtAdminZone());
    String location = trim(r.location());
    String quota = upper(r.quotaTypeCode());
    Map<String, List<CodeOptionDto>> codes = lookups.options();
    if (TenureCreateRules.licenceToCut(type)) {
      if (purpose.isEmpty()) {
        e.add("Purpose is mandatory.");
      } else if (!has(codes, TenureCreateRules.purposeList(type), purpose)) {
        e.add("Purpose " + purpose + " is not a current code.");
      }
      if (TenureCreateRules.needsLocation(type) && location.isEmpty()) {
        e.add("Geographic Location is mandatory.");
      }
      if (location.length() > TenureCreateRules.MAX_LOCATION) {
        e.add("Geographic Location must not exceed " + TenureCreateRules.MAX_LOCATION
            + " characters.");
      }
      if (TenureCreateRules.needsQuotaType(type) && quota.isEmpty()) {
        e.add("Quota type is mandatory if file type is " + type + ".");
      }
      if (!quota.isEmpty()) {
        if (!has(codes, "quotaTypes", quota) || !lookups.quotaTypeValid(type, quota)) {
          e.add("Quota Type " + quota + " is not valid for file type " + type + ".");
        } else if ("D".equals(quota)) {
          e.add("Quota type should be C-Forest Service Reserve if this sale is not BCTS"
              + " funded");
        }
      }
    } else {
      purpose = "";
      location = "";
      quota = "";
    }
    if (TenureCreateRules.needsZone(type)) {
      if (zone.isEmpty()) {
        e.add("Zone is mandatory.");
      } else if (zone.length() > TenureCreateRules.MAX_ZONE) {
        e.add("Zone must not exceed " + TenureCreateRules.MAX_ZONE + " characters.");
      }
    } else {
      zone = "";
    }

    // A30's initial area
    String initialArea = trim(r.initialArea());
    if ("A30".equals(type)) {
      if (initialArea.isEmpty()) {
        e.add("Initial Area(ha) is mandatory.");
      } else {
        String err = TenureCreateRules.initialAreaError(initialArea);
        if (err != null) {
          e.add(err);
        }
      }
    } else {
      initialArea = "";
    }

    // B10's free use permit (a new B10 starts PA, where FUP Type is mandatory)
    String fupType = upper(r.fupTypeCode());
    if ("B10".equals(type)) {
      if (fupType.isEmpty()) {
        e.add("FUP Type is mandatory when file type code is B10 and status is PA.");
      } else if (!has(codes, "fupTypes", fupType)) {
        e.add("FUP Type " + fupType + " is not a current code.");
      } else if ("FN".equals(fupType)) {
        String usage = upper(r.fupFnUsageCode());
        if (usage.isEmpty()) {
          e.add("First Nations Purpose is mandatory when FUP type is FN.");
        } else if (!has(codes, "fupFnUsages", usage)) {
          e.add("First Nations Purpose " + usage + " is not a current code.");
        }
        if (!yesNoOrBlank(r.fupCedarSpeciesInd()) || !yesNoOrBlank(r.fupTreatyPurposeInd())) {
          e.add("Majority Volume Cedar Species and First Nations Treaty Purpose are Y or N.");
        }
      }
    } else {
      fupType = "";
    }

    if (!e.isEmpty()) {
      throw badRequest(e);
    }
    return new Checked(typedId, adminOrg, district, muType, muId, purpose, zone, location,
        quota, initialArea, fupType);
  }

  // ---------------------------------------------------------------------------------------
  // Writes
  // ---------------------------------------------------------------------------------------

  /** A B05, B06 or B07's harvesting authority, hauling authority and licence to cut. */
  private void createLicenceToCut(String type, String forestFileId, String status, Checked c,
      LocalDate issueDate, String user) {
    String mark = TenureCreateRules.timberMark(type, forestFileId);
    Long skey = jdbc.queryForObject(NEXT_HVA_SQL, Map.of(), Long.class);
    jdbc.update(INSERT_HVA, params(forestFileId, user)
        .addValue("skey", skey)
        .addValue("district", c.district)
        .addValue("zone", emptyToNull(c.zone))
        .addValue("muId", emptyToNull(c.muId))
        .addValue("muType", c.muType)
        .addValue("status", status)
        .addValue("issueDate", issueDate, Types.DATE)
        .addValue("quota", emptyToNull(c.quota))
        .addValue("salvage", TenureCreateRules.salvageType(c.purpose))
        .addValue("location", emptyToNull(c.location)));

    if (TenureCreateRules.createsHaulingAuthority(type, c.purpose)) {
      if (count(MARK_EXISTS_SQL, Map.of("mark", mark)) > 0) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Timber mark " + mark + " is already in use.");
      }
      jdbc.update(INSERT_HAULING, params(forestFileId, user).addValue("mark", mark));
      jdbc.update(INSERT_XREF, new MapSqlParameterSource()
          .addValue("skey", skey).addValue("mark", mark).addValue("user", user));
    }

    jdbc.update(INSERT_LTC, params(forestFileId, user)
        .addValue("purpose", emptyToNull(c.purpose)));
  }

  /**
   * A designate from FTA_MARK_DESIGNATE that no timber mark already starts with. Legacy refused
   * the save and asked the user to save again for the next one; this takes the next one itself.
   * Each call commits its own counter (autonomous), so a skipped designate stays used, as a
   * retried save left it.
   */
  private String generateNumberedDesignate(String type) {
    for (int i = 0; i < DESIGNATE_ATTEMPTS; i++) {
      String[] out = jdbc.getJdbcTemplate().execute(
          (Connection con) -> con.prepareCall(MARK_DESIGNATE_CALL),
          (CallableStatement cs) -> {
            cs.setString(1, type);
            cs.setNull(2, Types.VARCHAR);
            cs.registerOutParameter(2, Types.VARCHAR);
            cs.setNull(3, Types.VARCHAR);
            cs.registerOutParameter(3, Types.VARCHAR);
            cs.execute();
            return new String[] {cs.getString(2), cs.getString(3)};
          });
      String designate = out == null ? null : out[0];
      String error = out == null ? null : out[1];
      if (error != null && !error.isBlank()) {
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
            "A mark designate could not be generated. Contact APPHELP.");
      }
      if (designate == null || designate.isBlank()) {
        break;
      }
      if (count(MARK_PREFIX_SQL, Map.of("prefix", designate)) == 0) {
        return designate;
      }
    }
    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
        "No mark designate free of existing timber marks could be generated. Contact APPHELP.");
  }

  /** assign_mark_designate for a new woodlot (A04, A44), A06, A28 or A29. */
  private void assignWoodlotOrTlDesignate(String type, String forestFileId, String user) {
    List<String> current = jdbc.query(CURRENT_DESIGNATE_SQL, Map.of("id", forestFileId),
        (rs, n) -> rs.getString(1));
    String existing = current.isEmpty() ? null : current.get(0);
    if (existing != null && !existing.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "Mark designate " + existing + " has already been assigned to this file.");
    }
    if (is("the.fta_valid_minor_tsl_file_type", type)) {
      // A new file has no cutting permits, so legacy always refused here.
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "You have not used up all possible CP id(s) for the file.");
    }
    String designate;
    if ("A28".equals(type) || "A29".equals(type)) {
      designate = forestFileId; // the designate is the timber mark
    } else {
      designate = nextLetterDesignate("A06".equals(type) ? "T" : "W",
          "A06".equals(type) ? "5" : "6");
    }
    saveDesignate(forestFileId, designate, user);
  }

  /**
   * create_mark_designate's counter, locked until this transaction ends, so it rolls back with
   * a failed create. Designates a timber mark already starts with are passed over (legacy
   * refused them and had the user ask again).
   */
  private String nextLetterDesignate(String prefix, String key) {
    List<String> rows = jdbc.query(LOCK_DESIGNATE_CTL, Map.of("key", key),
        (rs, n) -> rs.getString(1));
    if (rows.isEmpty() || rows.get(0) == null) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "Mark designate in control table is corrupt. Contact APPHELP.");
    }
    String d = rows.get(0).trim();
    for (int i = 0; i < DESIGNATE_ATTEMPTS; i++) {
      try {
        d = TenureCreateRules.nextLetterDesignate(prefix, d);
      } catch (IllegalStateException e) {
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
            "Mark designate in control table is corrupt. Contact APPHELP.");
      }
      if (d == null) {
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
            "Mark designate could not be generated. System has reached maximum.");
      }
      if (count(MARK_PREFIX_SQL, Map.of("prefix", d)) == 0) {
        jdbc.update(UPDATE_DESIGNATE_CTL, Map.of("d", d, "key", key));
        return d;
      }
    }
    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
        "No mark designate free of existing timber marks could be generated. Contact APPHELP.");
  }

  /** save_mark_designate. */
  private void saveDesignate(String forestFileId, String designate, String user) {
    MapSqlParameterSource p = params(forestFileId, user).addValue("designate", designate);
    if (jdbc.update(UPDATE_DESIGNATE, p) == 0) {
      jdbc.update(INSERT_DESIGNATE, p);
    }
  }

  // ---------------------------------------------------------------------------------------
  // Generators
  // ---------------------------------------------------------------------------------------

  /** The next id for {@code type} from FTA_GENERATE_FILE_ID. */
  private String generateFileId(String type) {
    String[] out = jdbc.getJdbcTemplate().execute(
        (Connection con) -> con.prepareCall(GENERATE_ID_CALL),
        (CallableStatement cs) -> {
          cs.setString(1, type);
          cs.setNull(2, Types.VARCHAR);
          cs.registerOutParameter(2, Types.VARCHAR);
          cs.setNull(3, Types.VARCHAR);
          cs.registerOutParameter(3, Types.VARCHAR);
          cs.execute();
          return new String[] {cs.getString(2), cs.getString(3)};
        });
    String id = out == null ? null : out[0];
    String error = out == null ? null : out[1];
    if ((error != null && !error.isBlank()) || id == null || id.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "A file id could not be assigned for file type " + type + ".");
    }
    return id.trim();
  }

  /** A B10's id: the one typed (an unused F id), or the next F id when blank. */
  private String freeUsePermitId(String typed) {
    String[] out = jdbc.getJdbcTemplate().execute(
        (Connection con) -> con.prepareCall(FUP_ID_CALL),
        (CallableStatement cs) -> {
          cs.registerOutParameter(1, Types.VARCHAR);
          if (typed.isEmpty()) {
            cs.setNull(2, Types.VARCHAR);
          } else {
            cs.setString(2, typed);
          }
          cs.registerOutParameter(3, Types.VARCHAR);
          cs.execute();
          return new String[] {cs.getString(1), cs.getString(3)};
        });
    String id = out == null ? null : out[0];
    String error = out == null ? null : out[1];
    if (error != null && !error.isBlank()) {
      String message = error.contains("invalid_ffid_format") ? "Invalid Forest File format."
          : error.contains("feature_already_exist") ? "Forest File already exists."
          : "That Forest File ID can't be used for a free use permit.";
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    if (id == null || id.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "That Forest File ID can't be used for a free use permit.");
    }
    return id.trim();
  }

  // ---------------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------------

  /** An org unit's level and whether it is current ([level, Y/N]), or null when unknown. */
  private String[] org(String orgUnitNo) {
    if (!orgUnitNo.chars().allMatch(Character::isDigit)) {
      return null;
    }
    List<String[]> rows = jdbc.query(ORG_SQL, Map.of("no", Long.valueOf(orgUnitNo)),
        (rs, n) -> new String[] {rs.getString(1), rs.getString(2)});
    return rows.isEmpty() ? null : rows.get(0);
  }

  /** One of legacy's FTA_VALID_* functions: whether the file type is in its code table. */
  private boolean is(String function, String type) {
    String v = jdbc.queryForObject(IS_SQL.formatted(function),
        new MapSqlParameterSource("t", type), String.class);
    return "Y".equals(v);
  }

  private long count(String sql, Map<String, ?> params) {
    Long n = jdbc.queryForObject(sql, params, Long.class);
    return n == null ? 0 : n;
  }

  private static boolean has(Map<String, List<CodeOptionDto>> codes, String list, String code) {
    return codes.getOrDefault(list, List.of()).stream().anyMatch(o -> code.equals(o.code()));
  }

  private static MapSqlParameterSource params(String forestFileId, String user) {
    return new MapSqlParameterSource().addValue("id", forestFileId).addValue("user", user);
  }

  private static ResponseStatusException badRequest(List<String> errors) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", errors));
  }

  private static boolean yesNoOrBlank(String v) {
    String s = upper(v);
    return s.isEmpty() || "Y".equals(s) || "N".equals(s);
  }

  private static String trim(String s) {
    return s == null ? "" : s.trim();
  }

  private static String upper(String s) {
    return trim(s).toUpperCase(Locale.ROOT);
  }

  private static String emptyToNull(String s) {
    return s == null || s.isEmpty() ? null : s;
  }
}
