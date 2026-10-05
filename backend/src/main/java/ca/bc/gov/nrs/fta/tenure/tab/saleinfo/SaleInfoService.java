package ca.bc.gov.nrs.fta.tenure.tab.saleinfo;

import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import ca.bc.gov.nrs.fta.tenure.tab.saleinfo.SaleInfoDtos.SaleInfoResponse;
import ca.bc.gov.nrs.fta.tenure.tab.saleinfo.SaleInfoDtos.SaleInfoSaveResult;
import ca.bc.gov.nrs.fta.tenure.tab.saleinfo.SaleInfoDtos.SaleInfoUpdateRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * A tenure's sale information — legacy FTA940 ({@code FTA_940_SALE_INFO}).
 *
 * <p>One record per file, spread over {@code HARVEST_SALE} (sale method, type, volumes, BCTS
 * categories…), {@code TENURE_DEPOSIT} (security and other deposits) and, for a sale within an
 * admin area, an {@code ASSOCIATED_USE} row naming the C01 admin area file. The GET ports the
 * package's GET (with its annual rent computation and the awarded BCTS bidder's bonus); the
 * save ports SAVE → ADD (no {@code HARVEST_SALE} yet) or CHANGE, each table guarded by the
 * revision count legacy carried, behind legacy's checks ({@link SaleInfoFieldChecks}) and
 * protection states ({@link SaleInfoRules}).
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class SaleInfoService {

  static final String MODIFIED =
      "The sale information has been modified by another user. Reload the tab and try again.";

  private static final String MAIN_SQL =
      """
      SELECT pfu.forest_file_id,
             pfu.file_type_code,
             pfu.file_status_st,
             CASE WHEN hs.forest_file_id IS NULL THEN 'N' ELSE 'Y' END AS hs_exists,
             hs.sale_method_code,
             CASE WHEN smc.description IS NOT NULL
                  THEN hs.sale_method_code || ' - ' || smc.description END AS sale_method_desc,
             hs.sale_type_cd,
             CASE WHEN stc.description IS NOT NULL
                  THEN hs.sale_type_cd || ' - ' || stc.description END AS sale_type_desc,
             hs.payment_method_cd,
             CASE WHEN pmc.description IS NOT NULL
                  THEN hs.payment_method_cd || ' - ' || pmc.description END AS payment_method_desc,
             hs.salvage_ind,
             hs.minor_facility_ind,
             hs.admin_area_ind,
             hs.planned_sale_date,
             hs.tender_opening_dt,
             hs.cash_sale_est_vol,
             hs.cash_sale_tot_dol,
             hs.sale_volume,
             hs.total_bidders,
             hs.fta_bonus_bid,
             hs.fta_bonus_offer,
             hs.sb_fund_ind,
             TO_CHAR(hs.bcts_org_unit)                                 AS bcts_org_unit,
             CASE WHEN bou.org_unit_no IS NOT NULL
                  THEN bou.org_unit_code || ' - ' || bou.org_unit_name END AS bcts_org_desc,
             hs.plnd_sb_cat_code,
             CASE WHEN psc.description IS NOT NULL
                  THEN hs.plnd_sb_cat_code || ' - ' || psc.description END AS planned_cat_desc,
             hs.sold_sb_cat_code,
             CASE WHEN ssc.description IS NOT NULL
                  THEN hs.sold_sb_cat_code || ' - ' || ssc.description END AS sold_cat_desc,
             hs.revision_count                                         AS hs_revision_count,
             td.scrty_deposit_code,
             CASE WHEN sdt.description IS NOT NULL
                  THEN td.scrty_deposit_code || ' - ' || sdt.description END AS scrty_desc,
             td.scrty_deposit_amt,
             td.other_deposit_code,
             CASE WHEN odt.description IS NOT NULL
                  THEN td.other_deposit_code || ' - ' || odt.description END AS other_desc,
             td.other_deposit_amt,
             td.revision_count                                         AS td_revision_count,
             tt.legal_effective_dt
        FROM the.prov_forest_use pfu
        LEFT JOIN the.harvest_sale hs          ON hs.forest_file_id = pfu.forest_file_id
        LEFT JOIN the.tenure_deposit td        ON td.forest_file_id = pfu.forest_file_id
        LEFT JOIN the.tenure_term tt           ON tt.forest_file_id = pfu.forest_file_id
        LEFT JOIN the.sale_method_code smc     ON smc.sale_method_code = hs.sale_method_code
        LEFT JOIN the.sale_type_code stc       ON stc.sale_type_code = hs.sale_type_cd
        LEFT JOIN the.payment_method_code pmc  ON pmc.payment_method_code = hs.payment_method_cd
        LEFT JOIN the.sb_category_code psc     ON psc.sb_category_code = hs.plnd_sb_cat_code
        LEFT JOIN the.sb_category_code ssc     ON ssc.sb_category_code = hs.sold_sb_cat_code
        LEFT JOIN the.org_unit bou             ON bou.org_unit_no = hs.bcts_org_unit
        LEFT JOIN the.deposit_type_code sdt    ON sdt.deposit_type_code = td.scrty_deposit_code
        LEFT JOIN the.deposit_type_code odt    ON odt.deposit_type_code = td.other_deposit_code
       WHERE pfu.forest_file_id = :forestFileId
      """;

  // The current (most recent) AAC period's Schedule B total — CHECK_SALE_METHOD_MAND and
  // GET_ANNUAL_RENT both read it.
  private static final String SCHEDULE_B_SUBQUERY =
      """
      (SELECT SUM(aaa.allocation_amount)
         FROM the.aac_allocation_amount aaa
        WHERE aaa.allowable_area_type_code = 'B'
          AND aaa.aac_allocation_period_id = (
              SELECT aac_allocation_period_id FROM (
                SELECT aac_allocation_period_id,
                       ROW_NUMBER() OVER (ORDER BY effective_date DESC) rn
                  FROM the.aac_allocation_period
                 WHERE forest_file_id = :forestFileId)
               WHERE rn = 1))
      """;

  private static final String FACTS_SQL =
      "SELECT " + SCHEDULE_B_SUBQUERY + " AS schedule_b_aac,"
          + """
             (SELECT COUNT(*) FROM the.bcts_file_type_code
               WHERE bcts_file_type_code = :fileType
                 AND effective_date <= SYSDATE AND expiry_date > SYSDATE) AS bcts_type,
             (SELECT COUNT(*) FROM the.private_mark_type_code
               WHERE private_mark_type_code = :fileType)                AS mark_type,
             (SELECT COUNT(*) FROM the.fta_file_level_authority
               WHERE file_type_code = :fileType
                 AND file_level_type = 'FILE'
                 AND org_level_code = 'H')                              AS hq_authority
        FROM dual
      """;

  // GET's cur_use: the C01 admin area file, read only when the sale is within an admin area.
  private static final String ADMIN_AREA_SQL =
      """
      SELECT au.associated_file_id, au.revision_count
        FROM the.associated_use au
        JOIN the.prov_forest_use apfu ON apfu.forest_file_id = au.associated_file_id
       WHERE au.forest_file_id = :forestFileId
         AND au.file_source_code = 'F'
         AND apfu.file_type_code = 'C01'
      """;

  // GET's cur_bonus_bid / cur_lumpsum_bonus_offer: the awarded licensee's bid at the
  // sale's auction, most recent auction first.
  private static final String BCTS_BONUS_SQL =
      """
      SELECT btb.bonus_bid, btb.bonus_offer
        FROM the.bcts_tenure_bidder btb
        JOIN the.forest_file_client fcl
          ON fcl.forest_file_id = btb.forest_file_id
         AND fcl.client_number = btb.client_number
         AND fcl.forest_file_client_type_code = 'A'
        JOIN the.bcts_timber_sale bts
          ON bts.forest_file_id = btb.forest_file_id
         AND bts.auction_date = btb.auction_date
       WHERE btb.forest_file_id = :forestFileId
         AND btb.sale_awarded_ind = 'Y'
       ORDER BY btb.auction_date DESC
      """;

  private static final String SPECIAL_USE_FEE_SQL =
      "SELECT annual_rent_fee FROM the.spec_use_permit WHERE forest_file_id = :forestFileId";

  private static final String BILLING_RATE_SQL =
      """
      SELECT billing_rate FROM the.gnrl_bill_rates
       WHERE billing_type_st = :fileType
         AND in_effect_frm_date <= SYSDATE
         AND in_effect_to_date >= SYSDATE
      """;

  private static final String SCHEDULE_B_SQL = "SELECT " + SCHEDULE_B_SUBQUERY + " FROM dual";

  private static final String OBLIGATION_AREA_SQL =
      "SELECT obligation_area FROM the.timber_lic_area WHERE forest_file_id = :forestFileId";

  private static final String MAP_FEATURES_SQL =
      """
      SELECT SUM(geom.feature_area)
        FROM the.harvesting_authority cp
        JOIN the.harvest_authority_geom geom ON geom.hva_skey = cp.hva_skey
       WHERE cp.forest_file_id = :forestFileId
         AND cp.harvest_auth_status_code IN ('HI', 'HS', 'LC')
      """;

  private static final String B07_PURPOSE_SQL =
      "SELECT licence_to_cut_cd FROM the.licence_to_cut WHERE forest_file_id = :forestFileId";

  private static final String A18_SS_SQL =
      """
      SELECT COUNT(*) FROM the.harvesting_authority
       WHERE forest_file_id = :forestFileId AND licence_to_cut_code = 'SS'
      """;

  private static final String FILE_TYPE_SQL =
      "SELECT file_type_code FROM the.prov_forest_use WHERE forest_file_id = :file";

  private static final String LAND_CLEAR_SQL =
      "SELECT COUNT(*) FROM the.land_clear_file WHERE forest_file_id = :file";

  private static final String INSERT_HS_SQL =
      """
      INSERT INTO the.harvest_sale (
        forest_file_id, sale_method_code, sale_type_cd, payment_method_cd, planned_sale_date,
        salvage_ind, admin_area_ind, minor_facility_ind, cash_sale_est_vol, cash_sale_tot_dol,
        plnd_sb_cat_code, sold_sb_cat_code, tender_opening_dt, sale_volume, total_bidders,
        fta_bonus_bid, fta_bonus_offer,
        entry_userid, entry_timestamp, update_userid, update_timestamp, revision_count
      ) VALUES (
        :forestFileId, :saleMethod, :saleType, :payment, :plannedSaleDate,
        :salvage, :adminArea, :minorFacility, :estVol, :totDol,
        :plannedCat, :soldCat, :tenderDate, :saleVolume, :totalBidders,
        :ftaBonusBid, :ftaBonusOffer,
        :userId, SYSDATE, :userId, SYSDATE, 1
      )
      """;

  private static final String UPDATE_HS_SQL =
      """
      UPDATE the.harvest_sale
         SET sale_method_code = :saleMethod,
             sale_type_cd = :saleType,
             planned_sale_date = :plannedSaleDate,
             salvage_ind = :salvage,
             minor_facility_ind = :minorFacility,
             cash_sale_est_vol = :estVol,
             cash_sale_tot_dol = :totDol,
             plnd_sb_cat_code = :plannedCat,
             sold_sb_cat_code = :soldCat,
             admin_area_ind = :adminArea,
             tender_opening_dt = :tenderDate,
             sale_volume = :saleVolume,
             total_bidders = :totalBidders,
             fta_bonus_bid = :ftaBonusBid,
             fta_bonus_offer = :ftaBonusOffer,
             update_userid = :userId,
             update_timestamp = SYSDATE,
             revision_count = revision_count + 1
       WHERE forest_file_id = :forestFileId
         AND revision_count = :hsRev
      """;

  // The HDBS audit trail trigger on PROV_FOREST_USE records who changed the file.
  private static final String TOUCH_PFU_SQL =
      "UPDATE the.prov_forest_use SET update_userid = :userId WHERE forest_file_id = :forestFileId";

  private static final String UPDATE_TD_SQL =
      """
      UPDATE the.tenure_deposit
         SET scrty_deposit_amt = :scrtyAmt,
             scrty_deposit_code = :scrtyCode,
             other_deposit_amt = :otherAmt,
             other_deposit_code = :otherCode,
             update_userid = :userId,
             update_timestamp = SYSDATE,
             revision_count = revision_count + 1
       WHERE forest_file_id = :forestFileId
         AND revision_count = :tdRev
      """;

  private static final String INSERT_TD_SQL =
      """
      INSERT INTO the.tenure_deposit (
        forest_file_id, scrty_deposit_amt, scrty_deposit_code, other_deposit_amt,
        other_deposit_code, entry_userid, entry_timestamp, update_userid, update_timestamp,
        revision_count
      ) VALUES (
        :forestFileId, :scrtyAmt, :scrtyCode, :otherAmt,
        :otherCode, :userId, SYSDATE, :userId, SYSDATE,
        1
      )
      """;

  private static final String DELETE_AU_SQL =
      """
      DELETE FROM the.associated_use
       WHERE forest_file_id = :forestFileId
         AND file_source_code = 'F'
         AND associated_file_id = :oldAdminFile
         AND revision_count = :auRev
      """;

  private static final String INSERT_AU_SQL =
      """
      INSERT INTO the.associated_use (
        forest_file_id, file_source_code, associated_file_id,
        entry_userid, entry_timestamp, update_userid, update_timestamp, revision_count
      ) VALUES (
        :forestFileId, 'F', :adminFile,
        :userId, SYSDATE, :userId, SYSDATE, 1
      )
      """;

  /** The code tables behind the edit lists: name → {column, table}. */
  private static final Map<String, String[]> LOOKUPS = Map.of(
      "sale-info-sale-methods", new String[] {"sale_method_code", "the.sale_method_code"},
      "sale-info-sale-types", new String[] {"sale_type_code", "the.sale_type_code"},
      "sale-info-deposit-types", new String[] {"deposit_type_code", "the.deposit_type_code"},
      "sale-info-bcts-categories", new String[] {"sb_category_code", "the.sb_category_code"});

  /** Annual rent: types billed on the current Schedule B AAC (GET_ANNUAL_RENT). */
  private static final Set<String> RENT_ON_AAC =
      Set.of("A01", "A03", "A04", "A44", "A25", "A28", "A29", "A41");

  /** Annual rent: types billed on their cutting permits' mapped area. */
  private static final Set<String> RENT_ON_MAP =
      Set.of("A20", "A21", "A22", "A23", "A24", "A26", "A27");

  private final NamedParameterJdbcTemplate jdbc;

  public SaleInfoService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** The tab's data; 404 when the tenure does not exist. */
  public SaleInfoResponse get(String forestFileId) {
    return load(forestFileId);
  }

  /** SAVE: creates the record (ADD) or changes it (CHANGE). Returns legacy's warnings. */
  @Transactional
  public SaleInfoSaveResult save(String forestFileId, SaleInfoUpdateRequest q, String userId) {
    SaleInfoResponse cur = load(forestFileId);
    SaleInfoRules rules = cur.rules();
    if (!rules.editable()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, rules.reason());
    }
    boolean adding = !cur.recordExists();
    if (!adding && q.hsRevisionCount() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revision count is required.");
    }
    List<String> shape = new ArrayList<>();
    SaleInfoUpdateRequest v = effective(q, cur, rules, shape);
    if (!shape.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", shape));
    }

    String type = cur.fileTypeCode();
    SaleInfoFieldChecks.Result r = SaleInfoFieldChecks.check(
        v, rules, type, cur.fileStatusCode(), purpose(forestFileId, type),
        cur.paymentMethodCode(), adding);
    List<String> e = new ArrayList<>(r.errors());
    if (e.isEmpty()) {
      codeCheck(e, "sale-info-sale-methods", "Sale Method Code", v.saleMethodCode(),
          cur.saleMethodCode());
      codeCheck(e, "sale-info-sale-types", "Sale Type Code", v.saleTypeCode(),
          cur.saleTypeCode());
      codeCheck(e, "sale-info-deposit-types", "Security Deposit", v.scrtyDepositCode(),
          cur.scrtyDepositCode());
      codeCheck(e, "sale-info-deposit-types", "Other Deposit", v.otherDepositCode(),
          cur.otherDepositCode());
      codeCheck(e, "sale-info-bcts-categories", "Planned BCTS Cat.", v.plannedSbCatCode(),
          cur.plannedSbCatCode());
      codeCheck(e, "sale-info-bcts-categories", "Sold BCTS Cat.", v.soldSbCatCode(),
          cur.soldSbCatCode());
      if ("Y".equals(v.adminAreaInd()) && v.adminAreaFile() != null) {
        adminAreaFileCheck(e, v.adminAreaFile());
      }
    }
    if (!e.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", e));
    }

    String fileId = cur.forestFileId();
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("forestFileId", fileId)
        .addValue("saleMethod", v.saleMethodCode())
        .addValue("saleType", v.saleTypeCode())
        .addValue("payment", cur.paymentMethodCode())
        .addValue("plannedSaleDate", v.plannedSaleDate(), Types.DATE)
        .addValue("salvage", r.salvageInd() == null ? "N" : r.salvageInd())
        .addValue("adminArea", v.adminAreaInd() == null ? "N" : v.adminAreaInd())
        .addValue("minorFacility", v.minorFacilityInd() == null ? "N" : v.minorFacilityInd())
        .addValue("estVol", v.cashSaleEstVol(), Types.NUMERIC)
        .addValue("totDol", v.cashSaleTotDol(), Types.NUMERIC)
        .addValue("plannedCat", v.plannedSbCatCode())
        .addValue("soldCat", v.soldSbCatCode())
        .addValue("tenderDate", v.tenderOpeningDate(), Types.DATE)
        .addValue("saleVolume", v.saleVolume(), Types.NUMERIC)
        .addValue("totalBidders", v.totalBidders(), Types.INTEGER)
        .addValue("ftaBonusBid", v.ftaBonusBid(), Types.NUMERIC)
        .addValue("ftaBonusOffer", v.ftaBonusOffer(), Types.NUMERIC)
        .addValue("scrtyAmt", v.scrtyDepositAmt(), Types.NUMERIC)
        .addValue("scrtyCode", v.scrtyDepositCode())
        .addValue("otherAmt", v.otherDepositAmt(), Types.NUMERIC)
        .addValue("otherCode", v.otherDepositCode())
        .addValue("hsRev", q.hsRevisionCount(), Types.NUMERIC)
        .addValue("tdRev", q.tdRevisionCount(), Types.NUMERIC)
        .addValue("auRev", q.auRevisionCount(), Types.NUMERIC)
        .addValue("oldAdminFile", cur.adminAreaFile())
        .addValue("adminFile", v.adminAreaFile())
        .addValue("userId", userId);

    try {
      if (adding) {
        jdbc.update(INSERT_HS_SQL, p);
      } else if (jdbc.update(UPDATE_HS_SQL, p) == 0) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, MODIFIED);
      }
      jdbc.update(TOUCH_PFU_SQL, p);

      if (q.tdRevisionCount() != null) {
        if (jdbc.update(UPDATE_TD_SQL, p) == 0) {
          throw new ResponseStatusException(HttpStatus.CONFLICT, MODIFIED);
        }
      } else {
        jdbc.update(INSERT_TD_SQL, p);
      }

      // CHANGE replaces the admin area association (its file id is part of the key).
      if (!adding && q.auRevisionCount() != null && cur.adminAreaFile() != null
          && jdbc.update(DELETE_AU_SQL, p) == 0) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, MODIFIED);
      }
      if ("Y".equals(v.adminAreaInd()) && v.adminAreaFile() != null) {
        jdbc.update(INSERT_AU_SQL, p);
      }
    } catch (DuplicateKeyException ex) {
      // Someone else created the record (or deposit, or association) since the GET.
      throw new ResponseStatusException(HttpStatus.CONFLICT, MODIFIED);
    }
    return new SaleInfoSaveResult(r.warnings());
  }

  /** A code list for the edit form: current codes, "CODE - description". */
  public List<CodeOptionDto> lookup(String name) {
    String[] t = LOOKUPS.get(name);
    if (t == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such list.");
    }
    // Column and table are constants from LOOKUPS, never input.
    String sql = "SELECT " + t[0] + " AS code, " + t[0] + " || ' - ' || description AS description"
        + " FROM " + t[1] + " WHERE SYSDATE BETWEEN effective_date AND expiry_date"
        + " ORDER BY " + t[0];
    return jdbc.query(sql, Map.of(),
        (rs, i) -> new CodeOptionDto(rs.getString("code"), rs.getString("description")));
  }

  // ─── Reading ──────────────────────────────────────────────────────────────

  private SaleInfoResponse load(String forestFileId) {
    Map<String, Object> id = Map.of("forestFileId", forestFileId);
    List<SaleInfoResponse> found = jdbc.query(MAIN_SQL, id, (rs, i) -> partial(rs));
    if (found.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenure not found.");
    }
    SaleInfoResponse b = found.get(0);
    String fileId = b.forestFileId();
    String type = b.fileTypeCode();
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("forestFileId", fileId)
        .addValue("fileType", type);

    String adminFile = null;
    Long auRev = null;
    if ("Y".equals(b.adminAreaInd())) {
      List<Object[]> au = jdbc.query(ADMIN_AREA_SQL, p,
          (rs, i) -> new Object[] {rs.getString(1), rs.getLong(2)});
      if (!au.isEmpty()) {
        adminFile = (String) au.get(0)[0];
        auRev = (Long) au.get(0)[1];
      }
    }

    List<BigDecimal[]> bonus = jdbc.query(BCTS_BONUS_SQL, p,
        (rs, i) -> new BigDecimal[] {rs.getBigDecimal(1), rs.getBigDecimal(2)});
    BigDecimal bctsBid = bonus.isEmpty() ? null : bonus.get(0)[0];
    BigDecimal bctsOffer = bonus.isEmpty() || bctsBid != null ? null : bonus.get(0)[1];

    Map<String, Object> facts = jdbc.queryForMap(FACTS_SQL, p);
    BigDecimal scheduleB = (BigDecimal) facts.get("schedule_b_aac");
    SaleInfoRules rules = SaleInfoRules.of(new SaleInfoRules.Facts(
        fileId,
        type,
        b.fileStatusCode(),
        b.recordExists(),
        b.paymentMethodCode(),
        b.adminAreaInd(),
        adminFile,
        b.bctsFundInd(),
        scheduleB,
        count(facts.get("bcts_type")) > 0,
        count(facts.get("mark_type")) > 0,
        count(facts.get("hq_authority")) > 0));

    return new SaleInfoResponse(
        fileId, type, b.fileStatusCode(), b.recordExists(),
        b.saleMethodCode(), b.saleMethodDesc(), b.saleTypeCode(), b.saleTypeDesc(),
        b.paymentMethodCode(), b.paymentMethodDesc(), b.salvageInd(), b.minorFacilityInd(),
        b.adminAreaInd(), adminFile, b.plannedSaleDate(), b.tenderOpeningDate(),
        b.cashSaleEstVol(), b.cashSaleTotDol(), b.saleVolume(), b.totalBidders(),
        b.ftaBonusBid(), b.ftaBonusOffer(), bctsBid, bctsOffer, b.bctsFundInd(),
        b.bctsOrgUnitNo(), b.bctsOrgDesc(), b.plannedSbCatCode(), b.plannedSbCatDesc(),
        b.soldSbCatCode(), b.soldSbCatDesc(), b.scrtyDepositCode(), b.scrtyDepositDesc(),
        b.scrtyDepositAmt(), b.otherDepositCode(), b.otherDepositDesc(), b.otherDepositAmt(),
        annualRent(p, type, b.legalEffectiveDate(), scheduleB), b.legalEffectiveDate(),
        b.hsRevisionCount(), b.tdRevisionCount(), auRev, rules);
  }

  /** The main row, before the admin area, BCTS bonus, rent and rules are added. */
  private static SaleInfoResponse partial(ResultSet rs) throws SQLException {
    return new SaleInfoResponse(
        rs.getString("forest_file_id"),
        rs.getString("file_type_code"),
        rs.getString("file_status_st"),
        "Y".equals(rs.getString("hs_exists")),
        rs.getString("sale_method_code"),
        rs.getString("sale_method_desc"),
        rs.getString("sale_type_cd"),
        rs.getString("sale_type_desc"),
        rs.getString("payment_method_cd"),
        rs.getString("payment_method_desc"),
        rs.getString("salvage_ind"),
        rs.getString("minor_facility_ind"),
        rs.getString("admin_area_ind"),
        null,
        rs.getObject("planned_sale_date", LocalDate.class),
        rs.getObject("tender_opening_dt", LocalDate.class),
        rs.getBigDecimal("cash_sale_est_vol"),
        rs.getBigDecimal("cash_sale_tot_dol"),
        rs.getBigDecimal("sale_volume"),
        rs.getObject("total_bidders") == null ? null : rs.getInt("total_bidders"),
        rs.getBigDecimal("fta_bonus_bid"),
        rs.getBigDecimal("fta_bonus_offer"),
        null,
        null,
        rs.getString("sb_fund_ind"),
        rs.getString("bcts_org_unit"),
        rs.getString("bcts_org_desc"),
        rs.getString("plnd_sb_cat_code"),
        rs.getString("planned_cat_desc"),
        rs.getString("sold_sb_cat_code"),
        rs.getString("sold_cat_desc"),
        rs.getString("scrty_deposit_code"),
        rs.getString("scrty_desc"),
        rs.getBigDecimal("scrty_deposit_amt"),
        rs.getString("other_deposit_code"),
        rs.getString("other_desc"),
        rs.getBigDecimal("other_deposit_amt"),
        null,
        rs.getObject("legal_effective_dt", LocalDate.class),
        rs.getObject("hs_revision_count") == null ? null : rs.getLong("hs_revision_count"),
        rs.getObject("td_revision_count") == null ? null : rs.getLong("td_revision_count"),
        null,
        null);
  }

  /**
   * {@code GET_ANNUAL_RENT}: a special use permit's fee; else, once the tenure has an award
   * date, the file type's current billing rate times its billable units (the current Schedule
   * B AAC, a timber licence's obligation area, or the mapped area of its issued permits).
   * "N/A" for types with no rent basis; null when it cannot be computed.
   */
  private String annualRent(
      MapSqlParameterSource p, String type, LocalDate awardDate, BigDecimal scheduleB) {
    BigDecimal rent;
    if ("S01".equals(type) || "S02".equals(type)) {
      rent = first(SPECIAL_USE_FEE_SQL, p);
      if (rent == null) {
        rent = BigDecimal.ZERO;
      }
    } else if (awardDate == null) {
      rent = BigDecimal.ZERO;
    } else {
      BigDecimal rate = first(BILLING_RATE_SQL, p);
      if (rate != null && rate.signum() == 0) {
        rent = BigDecimal.ZERO;
      } else {
        BigDecimal units;
        if (RENT_ON_AAC.contains(type)) {
          units = scheduleB != null ? scheduleB : first(SCHEDULE_B_SQL, p);
        } else if ("A06".equals(type)) {
          units = first(OBLIGATION_AREA_SQL, p);
        } else if (RENT_ON_MAP.contains(type)) {
          units = first(MAP_FEATURES_SQL, p);
        } else {
          return "N/A";
        }
        rent = units == null || rate == null ? null : units.multiply(rate);
      }
    }
    return rent == null ? null : rent.setScale(2, RoundingMode.HALF_UP).toPlainString();
  }

  private BigDecimal first(String sql, MapSqlParameterSource p) {
    List<BigDecimal> rows = jdbc.query(sql, p, (rs, i) -> rs.getBigDecimal(1));
    return rows.isEmpty() ? null : rows.get(0);
  }

  /** SAVE's licence-to-cut purpose: a B07's code, or SS for an A18 with an SS permit. */
  private String purpose(String forestFileId, String type) {
    Map<String, Object> id = Map.of("forestFileId", forestFileId);
    if ("B07".equals(type)) {
      List<String> rows = jdbc.query(B07_PURPOSE_SQL, id, (rs, i) -> rs.getString(1));
      return rows.isEmpty() ? null : rows.get(0);
    }
    if ("A18".equals(type)) {
      Long n = jdbc.queryForObject(A18_SS_SQL, id, Long.class);
      return n != null && n > 0 ? "SS" : null;
    }
    return null;
  }

  // ─── Saving ───────────────────────────────────────────────────────────────

  /**
   * The values to store: the request's, normalized, except where the rules keep a field
   * closed — there the stored value stays, as legacy's disabled inputs kept it.
   */
  static SaleInfoUpdateRequest effective(
      SaleInfoUpdateRequest q, SaleInfoResponse cur, SaleInfoRules rules, List<String> e) {
    String salvage = rules.salvage() ? yn(q.salvageInd(), "Salvage Indicator", e) : "Y";
    String minor = rules.minorFacility()
        ? yn(q.minorFacilityInd(), "Minor Processing Facility", e)
        : cur.minorFacilityInd();
    String adminInd = rules.withinAdminArea()
        ? yn(q.adminAreaInd(), "Within Admin Area", e)
        : cur.adminAreaInd();
    String adminFile = rules.adminAreaFile() ? code(q.adminAreaFile()) : cur.adminAreaFile();
    if (adminFile != null && adminFile.length() > 10) {
      e.add("Admin Area File cannot exceed 10 characters.");
    }
    return new SaleInfoUpdateRequest(
        code(q.saleMethodCode()),
        code(q.saleTypeCode()),
        salvage,
        minor,
        adminInd,
        adminFile,
        q.plannedSaleDate(),
        q.tenderOpeningDate(),
        rules.estimatedVolume() ? q.cashSaleEstVol() : cur.cashSaleEstVol(),
        rules.cashSale() ? q.cashSaleTotDol() : cur.cashSaleTotDol(),
        q.saleVolume(),
        q.totalBidders(),
        rules.bcts() ? cur.ftaBonusBid() : q.ftaBonusBid(),
        rules.bcts() ? cur.ftaBonusOffer() : q.ftaBonusOffer(),
        code(q.plannedSbCatCode()),
        code(q.soldSbCatCode()),
        code(q.scrtyDepositCode()),
        q.scrtyDepositAmt(),
        code(q.otherDepositCode()),
        q.otherDepositAmt(),
        q.hsRevisionCount(),
        q.tdRevisionCount(),
        q.auRevisionCount());
  }

  /**
   * The code exists, and — when it was changed — is current (legacy's CodeExpiryValidator;
   * a code the record already carries may stay).
   */
  private void codeCheck(List<String> e, String list, String label, String code, String stored) {
    if (code == null) {
      return;
    }
    String[] t = LOOKUPS.get(list);
    Map<String, Object> row = jdbc.queryForMap(
        "SELECT COUNT(*) AS n,"
            + " SUM(CASE WHEN SYSDATE BETWEEN effective_date AND expiry_date THEN 1 ELSE 0 END)"
            + " AS cur FROM " + t[1] + " WHERE " + t[0] + " = :code",
        Map.of("code", code));
    if (count(row.get("n")) == 0) {
      e.add(label + " " + code + " is not a valid code.");
    } else if (count(row.get("cur")) == 0 && !Objects.equals(code, stored)) {
      e.add(label + " (" + code + ") is an expired code.");
    }
  }

  /** CHECK_ADMIN_AREA_FILE: a C01 file with a land clearance record. */
  private void adminAreaFileCheck(List<String> e, String file) {
    Map<String, Object> f = Map.of("file", file);
    List<String> type = jdbc.query(FILE_TYPE_SQL, f, (rs, i) -> rs.getString(1));
    if (!type.isEmpty() && type.get(0) != null && !"C01".equals(type.get(0))) {
      e.add("Admin Area File must be of C01 file-type.");
      return;
    }
    Long n = jdbc.queryForObject(LAND_CLEAR_SQL, f, Long.class);
    if (n == null || n == 0) {
      e.add("Admin Area must have an associated Land Clearance.  Data fix is required.  Call"
          + " Support for assistance.");
    }
  }

  private static long count(Object o) {
    return o instanceof Number n ? n.longValue() : 0;
  }

  private static String code(String s) {
    return s == null || s.isBlank() ? null : s.trim().toUpperCase(Locale.ROOT);
  }

  private static String yn(String s, String label, List<String> e) {
    String v = code(s);
    if (v != null && !"Y".equals(v) && !"N".equals(v)) {
      e.add(label + " must be Yes or No.");
      return null;
    }
    return v;
  }
}
