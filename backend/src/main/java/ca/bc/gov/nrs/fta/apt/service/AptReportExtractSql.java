package ca.bc.gov.nrs.fta.apt.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * The two CSV extracts APT2 read from {@code THE.APTR013_CSV_EXTRACT} and
 * {@code THE.APTR041_CSV_EXTRACT}, ported to SQL over the tables plus the Java that builds each
 * line of text exactly as those functions concatenated it.
 *
 * <p>Both functions returned one {@code TEXT} column: a header line {@code UNION} the data
 * lines. The {@code UNION} dropped duplicate lines and sorted them (the header starts with a
 * space so it sorts first); {@link #fileLines} does the same explicitly. Each SQL here returns
 * the raw values; {@code line(...)} reproduces the functions' concatenation, including their
 * quirks (unescaped quotes, the stray leading comma on APTR041's Z-type "INDEPENDENT MEDIUM"
 * rows, the trailing {@code ", "} / {@code ",#"} unapportioned-volume indicator).
 *
 * <p>Oracle's string concatenation treats NULL as empty, so a null value is written as nothing.
 * Numbers are written as {@code TO_CHAR(n)} writes them ({@link #toChar}).
 */
final class AptReportExtractSql {

  private AptReportExtractSql() {}

  // ─────────────────────────────── APTR013 ───────────────────────────────

  /** APTR013's header line, leading space included. */
  static final String APTR013_HEADER = " Region,Management Unit Name,MU AAC(m3),MU AAC(ha),"
      + "AAC Effect Date,Apport Effect Date,Partition,Partition AAC(m3),Partition AAC(ha),"
      + "FOA,FOA AAC(m3),FOA AAC(ha)";

  /**
   * APTR013 (TSA apportionment data): each current TSA's latest history row in effect, its
   * partitions and their FOA apportionments. The function's views are written out:
   * {@code APT_MGMT_UNIT_NAMES_V} for type {@code U} is {@code CODE_LIST_TABLE} column
   * {@code TSA_NUMBER}; {@code APT_PART_TYPES_V} and {@code APT_FOA_TYPES_V} are
   * code-subset joins (the latter with {@code APT_APPORTIONMENT_CATEGORIES}).
   */
  static final String APTR013_SQL = """
      SELECT ou.org_unit_code,
             munv.expanded_result              AS mgmt_unit_name,
             muh.allowable_annual_cut          AS mu_aac,
             muh.allowable_annual_cut_ha       AS mu_aac_ha,
             muh.effective_date                AS aac_effective_date,
             muh.apportionment_effective_date  AS apport_effective_date,
             ptv.expanded_result               AS part_type_desc,
             part.allowable_annual_cut         AS part_aac,
             part.allowable_annual_cut_ha      AS part_aac_ha,
             aftv.expanded_result              AS foa_desc,
             papt.allowable_annual_cut         AS foa_aac,
             papt.allowable_annual_cut_ha      AS foa_aac_ha
        FROM the.code_list_table munv,
             the.apt_management_units mu,
             the.apt_mgmt_unit_history muh,
             the.org_unit ou,
             the.apt_partitions part,
             the.code_subset_tbl ptv_cst,
             the.code_list_table ptv,
             the.apt_part_apportionments papt,
             the.code_subset_tbl aftv_cst,
             the.code_list_table aftv,
             the.apt_apportionment_categories aftv_acat
       WHERE munv.column_name = 'TSA_NUMBER'
         AND mu.apt_mgmt_unit_st = 'U'
         AND mu.mgmt_unit_number = munv.code_argument
         AND ou.org_unit_no = mu.org_unit_no
         AND mu.id = muh.mu_id
         AND TO_CHAR(muh.effective_date, 'YYYYMMDD')
             || TO_CHAR(NVL(muh.apportionment_effective_date, TO_DATE('29991231', 'YYYYMMDD')),
                        'YYYYMMDD') =
             (SELECT MAX(TO_CHAR(h.effective_date, 'YYYYMMDD')
                         || TO_CHAR(NVL(h.apportionment_effective_date,
                                        TO_DATE('29991231', 'YYYYMMDD')), 'YYYYMMDD'))
                FROM the.apt_mgmt_unit_history h
               WHERE h.mu_id = mu.id
                 AND h.effective_date <= SYSDATE
                 AND NVL(h.apportionment_effective_date, TO_DATE('1900', 'YYYY')) <= SYSDATE)
         AND muh.id = part.muh_id
         AND ptv_cst.code_subset_name = 'APT_PARTITION_ST'
         AND ptv.column_name = ptv_cst.column_name
         AND ptv.code_argument = ptv_cst.code_argument
         AND part.apt_partition_st = ptv.code_argument
         AND papt.part_id = part.id
         AND aftv_cst.code_subset_name = 'APT_FOA_ST'
         AND aftv.column_name = aftv_cst.column_name
         AND aftv.code_argument = aftv_cst.code_argument
         AND aftv_acat.apt_foa_st = aftv.code_argument
         AND papt.apt_foa_st = aftv.code_argument
         AND aftv_acat.apt_mgmt_unit_st = 'U'
      """;

  private static final DateTimeFormatter APTR013_DATE = DateTimeFormatter.ofPattern("uuuu/MM/dd");

  /** One APTR013 row's values, in column order. */
  record Aptr013Row(
      String region,
      String managementUnitName,
      BigDecimal muAac,
      BigDecimal muAacHa,
      LocalDate aacEffectiveDate,
      LocalDate apportionmentEffectiveDate,
      String partition,
      BigDecimal partitionAac,
      BigDecimal partitionAacHa,
      String foa,
      BigDecimal foaAac,
      BigDecimal foaAacHa) {

    /** The function's line: names in quotes (not escaped), numbers and dates bare. */
    String line() {
      return quoted(region) + ","
          + quoted(managementUnitName) + ","
          + toChar(muAac) + ","
          + toChar(muAacHa) + ","
          + date(aacEffectiveDate, APTR013_DATE) + ","
          + date(apportionmentEffectiveDate, APTR013_DATE) + ","
          + quoted(partition) + ","
          + toChar(partitionAac) + ","
          + toChar(partitionAacHa) + ","
          + quoted(foa) + ","
          + toChar(foaAac) + ","
          + toChar(foaAacHa);
    }
  }

  // ─────────────────────────────── APTR041 ───────────────────────────────

  /** APTR041's header line, leading and trailing space included. */
  static final String APTR041_HEADER =
      " Corporation,Forest File ID,Licensee,FOA,Expiry Date,Region,Management Unit,AAC(m3),"
          + "Non-AAC ";

  /**
   * APTR041 (linkages and licences): current AAC commitments per licence, summed per licence,
   * expiry, region and management unit, grouped by who holds the licence.
   *
   * <p>The function had 16 {@code UNION}ed branches. They differ along four lines, each of which
   * is a column here, so the branches collapse into one {@code GROUP BY} per holder kind without
   * merging anything the function kept apart:
   *
   * <ul>
   *   <li><b>path</b> — {@code D}: the licence's own management unit
   *       ({@code PROV_FOREST_USE.MGMT_UNIT_TYPE/ID}, types T and U); {@code Z}: a type-Z use,
   *       whose management unit is found through the commitment's apportionment
   *       ({@code COMM.PAPT_ID → PAPT → PART → MUH → MU}). The Z path has no T/U filter for
   *       corporate holders, as in the function; it does for the others.
   *   <li><b>uav</b> — {@code N}: commitments included in the licence AAC; {@code Y}: excluded
   *       unapportioned volume ({@code INCLUDE_IN_LIC_AAC_IND = 'N'} and partition
   *       {@code UAV}). Each was its own branch, so it is grouped on.
   *   <li><b>holder</b> — {@code CORP}: an {@code A}/{@code B} client that has a parent in
   *       {@code APT_RELATED_CLIENTS}; {@code IND}: one that has none; {@code JOINT}: a licence
   *       held jointly, with no client join.
   *   <li><b>size</b> (independents) — {@code MED} when the licence's total commitments
   *       (all of them, no date filter) are at least 25,000 (the function's
   *       {@code license_size}), {@code SMALL} below; a licence
   *       whose total is null matched neither branch and is left out.
   * </ul>
   *
   * <p>"Jointly held" is the function's own test: more than one row in the product of the
   * licence's A/B client links and its T/U/Z forest uses. The views are written out:
   * {@code V_CLIENT_PUBLIC} is {@code FOREST_CLIENT}; {@code APT_RELATED_CLIENTS_NAMES_V} is
   * {@code APT_RELATED_CLIENTS} with both clients' names; {@code APT_MGMT_UNIT_NAMES_V} is
   * {@code CODE_LIST_TABLE}'s TFL/TSA/WL numbers (its type-Z row has no number, so never joins).
   * The joint holders' names ({@code APT_JOINTLY_HELD_NAMES} over
   * {@code APT_LICENCE_CLIENTS_V}) come back as {@code JOINT_NAMES}.
   */
  static final String APTR041_SQL = """
      WITH munv AS (
        SELECT DECODE(clt.column_name, 'TFL_NUMBER', 'T', 'TSA_NUMBER', 'U', 'WL_NUMBER', 'F')
                                    AS apt_mgmt_unit_st,
               clt.code_argument    AS mgmt_unit_number,
               clt.expanded_result  AS mgmt_unit_name
          FROM the.code_list_table clt
         WHERE clt.column_name IN ('TFL_NUMBER', 'TSA_NUMBER', 'WL_NUMBER')
      ),
      jointly_held AS (
        SELECT lic1.forest_file_id
          FROM the.apt_licences lic1
         WHERE 1 < (SELECT COUNT(*)
                      FROM the.for_client_link fcl1,
                           the.prov_forest_use pfu1
                     WHERE fcl1.forest_file_id = lic1.forest_file_id
                       AND fcl1.file_client_type IN ('A', 'B')
                       AND pfu1.forest_file_id = lic1.forest_file_id
                       AND pfu1.mgmt_unit_type IN ('T', 'U', 'Z'))
      ),
      located AS (
        SELECT 'D'                     AS path,
               lic.forest_file_id,
               lic.apt_foa_st,
               DECODE(NVL(tt.current_expiry_dt, TO_DATE('9999-12-31', 'YYYY-MM-DD')),
                      TO_DATE('9999-12-31', 'YYYY-MM-DD'), tt.initial_expiry_dt,
                      tt.current_expiry_dt) AS expiry_dt,
               ou.org_unit_code,
               munv.mgmt_unit_name,
               mu.apt_mgmt_unit_st,
               DECODE(comm.include_in_lic_aac_ind, 'Y', 'N', 'Y') AS uav,
               comm.allowable_annual_cut
          FROM the.apt_licences lic,
               the.apt_commitments comm,
               the.prov_forest_use pfu,
               munv,
               the.org_unit ou,
               the.apt_management_units mu,
               the.tenure_term tt
         WHERE ou.org_unit_no = mu.org_unit_no
           AND munv.mgmt_unit_number = mu.mgmt_unit_number
           AND munv.apt_mgmt_unit_st = mu.apt_mgmt_unit_st
           AND mu.mgmt_unit_status_st = 'ACT'
           AND mu.apt_mgmt_unit_st IN ('T', 'U')
           AND mu.apt_mgmt_unit_st = pfu.mgmt_unit_type
           AND mu.mgmt_unit_number = pfu.mgmt_unit_id
           AND tt.forest_file_id = pfu.forest_file_id
           AND lic.forest_file_id = comm.forest_file_id
           AND lic.forest_file_id = pfu.forest_file_id
           AND pfu.file_status_st IN ('HI', 'HS')
           AND DECODE(NVL(tt.current_expiry_dt, TO_DATE('9999-12-31', 'YYYY-MM-DD')),
                      TO_DATE('9999-12-31', 'YYYY-MM-DD'), tt.initial_expiry_dt,
                      tt.current_expiry_dt) > SYSDATE
           AND SYSDATE BETWEEN comm.effective_date
                           AND NVL(comm.expiry_date, TO_DATE('299912312359', 'YYYYMMDDHH24MI'))
           AND (comm.include_in_lic_aac_ind = 'Y'
                OR (comm.include_in_lic_aac_ind = 'N' AND comm.apt_partition_st = 'UAV'))
        UNION ALL
        SELECT 'Z'                     AS path,
               lic.forest_file_id,
               lic.apt_foa_st,
               DECODE(NVL(tt.current_expiry_dt, TO_DATE('9999-12-31', 'YYYY-MM-DD')),
                      TO_DATE('9999-12-31', 'YYYY-MM-DD'), tt.initial_expiry_dt,
                      tt.current_expiry_dt) AS expiry_dt,
               ou.org_unit_code,
               munv.mgmt_unit_name,
               mu.apt_mgmt_unit_st,
               DECODE(comm.include_in_lic_aac_ind, 'Y', 'N', 'Y') AS uav,
               comm.allowable_annual_cut
          FROM the.apt_licences lic,
               the.apt_commitments comm,
               the.prov_forest_use pfu,
               munv,
               the.org_unit ou,
               the.apt_management_units mu,
               the.tenure_term tt,
               the.apt_part_apportionments papt,
               the.apt_partitions part,
               the.apt_mgmt_unit_history muh
         WHERE pfu.mgmt_unit_type = 'Z'
           AND pfu.forest_file_id = lic.forest_file_id
           AND pfu.file_status_st IN ('HI', 'HS')
           AND lic.forest_file_id = comm.forest_file_id
           AND comm.papt_id = papt.id
           AND papt.part_id = part.id
           AND part.muh_id = muh.id
           AND muh.mu_id = mu.id
           AND mu.org_unit_no = ou.org_unit_no
           AND mu.mgmt_unit_status_st = 'ACT'
           AND mu.mgmt_unit_number = munv.mgmt_unit_number
           AND mu.apt_mgmt_unit_st = munv.apt_mgmt_unit_st
           AND tt.forest_file_id = pfu.forest_file_id
           AND DECODE(NVL(tt.current_expiry_dt, TO_DATE('9999-12-31', 'YYYY-MM-DD')),
                      TO_DATE('9999-12-31', 'YYYY-MM-DD'), tt.initial_expiry_dt,
                      tt.current_expiry_dt) > SYSDATE
           AND SYSDATE BETWEEN comm.effective_date
                           AND NVL(comm.expiry_date, TO_DATE('299912312359', 'YYYYMMDDHH24MI'))
           AND (comm.include_in_lic_aac_ind = 'Y'
                OR (comm.include_in_lic_aac_ind = 'N' AND comm.apt_partition_st = 'UAV'))
      ),
      licence_total AS (
        SELECT comm.forest_file_id,
               SUM(comm.allowable_annual_cut) AS total_aac
          FROM the.apt_commitments comm
         GROUP BY comm.forest_file_id
      ),
      licence_clients AS (
        SELECT DISTINCT lic.forest_file_id,
               DECODE(vcp.client_type_code, 'I',
                      vcp.client_name || CHR(10) || '     ,' || vcp.legal_first_name,
                      vcp.client_name) AS client_name,
               fcl.file_client_type,
               vcp.client_number
          FROM the.apt_licences lic,
               the.for_client_link fcl,
               the.forest_client vcp
         WHERE lic.forest_file_id = fcl.forest_file_id
           AND vcp.client_number = fcl.client_number
           AND fcl.file_client_type IN ('A', 'B')
      )
      SELECT 'CORP'                 AS holder,
             l.path,
             NULL                   AS size_class,
             parent.client_name     AS parent_name,
             l.forest_file_id,
             vcp.client_name,
             l.apt_foa_st,
             l.expiry_dt,
             l.org_unit_code,
             l.mgmt_unit_name,
             SUM(l.allowable_annual_cut) AS aac,
             l.uav,
             NULL                   AS joint_names
        FROM located l,
             the.for_client_link fcl,
             the.forest_client vcp,
             the.apt_related_clients rel,
             the.forest_client parent
       WHERE l.forest_file_id = fcl.forest_file_id
         AND fcl.file_client_type IN ('A', 'B')
         AND fcl.client_number = rel.client_number
         AND parent.client_number = rel.client_number_parented_by
         AND vcp.client_number = fcl.client_number
         AND NOT EXISTS (SELECT NULL FROM jointly_held j WHERE j.forest_file_id = l.forest_file_id)
       GROUP BY l.path, l.uav, parent.client_name, l.forest_file_id, vcp.client_name,
                l.expiry_dt, l.org_unit_code, l.mgmt_unit_name, l.apt_foa_st
      UNION ALL
      SELECT 'IND'                  AS holder,
             l.path,
             CASE WHEN 25000 <= t.total_aac THEN 'MED' ELSE 'SMALL' END AS size_class,
             NULL                   AS parent_name,
             l.forest_file_id,
             DECODE(vcp.client_type_code, 'I',
                    vcp.client_name || '; ' || vcp.legal_first_name,
                    vcp.client_name) AS client_name,
             l.apt_foa_st,
             l.expiry_dt,
             l.org_unit_code,
             l.mgmt_unit_name,
             SUM(l.allowable_annual_cut) AS aac,
             l.uav,
             NULL                   AS joint_names
        FROM located l,
             licence_total t,
             the.for_client_link fcl,
             the.forest_client vcp
       WHERE l.apt_mgmt_unit_st IN ('T', 'U')
         AND t.forest_file_id = l.forest_file_id
         AND t.total_aac IS NOT NULL
         AND l.forest_file_id = fcl.forest_file_id
         AND fcl.file_client_type IN ('A', 'B')
         AND fcl.client_number NOT IN (SELECT DISTINCT client_number
                                         FROM the.apt_related_clients)
         AND vcp.client_number = fcl.client_number
         AND NOT EXISTS (SELECT NULL FROM jointly_held j WHERE j.forest_file_id = l.forest_file_id)
       GROUP BY l.path, l.uav, t.total_aac,
                l.forest_file_id,
                DECODE(vcp.client_type_code, 'I',
                       vcp.client_name || '; ' || vcp.legal_first_name,
                       vcp.client_name),
                l.apt_foa_st, l.expiry_dt, l.org_unit_code, l.mgmt_unit_name
      UNION ALL
      SELECT 'JOINT'                AS holder,
             l.path,
             NULL                   AS size_class,
             NULL                   AS parent_name,
             l.forest_file_id,
             NULL                   AS client_name,
             l.apt_foa_st,
             l.expiry_dt,
             l.org_unit_code,
             l.mgmt_unit_name,
             SUM(l.allowable_annual_cut) AS aac,
             l.uav,
             (SELECT LISTAGG(c.client_name, '; ' ON OVERFLOW TRUNCATE '' WITHOUT COUNT)
                       WITHIN GROUP (ORDER BY c.file_client_type, c.client_number)
                FROM licence_clients c
               WHERE c.forest_file_id = l.forest_file_id) AS joint_names
        FROM located l
       WHERE l.apt_mgmt_unit_st IN ('T', 'U')
         AND EXISTS (SELECT NULL FROM jointly_held j WHERE j.forest_file_id = l.forest_file_id)
       GROUP BY l.path, l.uav, l.forest_file_id, l.apt_foa_st, l.expiry_dt, l.org_unit_code,
                l.mgmt_unit_name
      """;

  private static final DateTimeFormatter APTR041_DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd");

  /** {@code APT_JOINTLY_HELD_NAMES} kept the first 98 characters of the joined names. */
  static final int JOINT_NAMES_MAX = 98;

  /**
   * One APTR041 group.
   *
   * @param holder     {@code CORP}, {@code IND} or {@code JOINT}
   * @param path       {@code D} (the licence's own MU) or {@code Z} (through the apportionment)
   * @param sizeClass  {@code MED} or {@code SMALL} for {@code IND}, else null
   * @param parentName the controlling corporation's name ({@code CORP})
   * @param clientName the licensee's name ({@code CORP}, {@code IND}; an individual's
   *                   {@code IND} name is {@code "NAME; FIRST"})
   * @param aac        the summed commitments
   * @param uav        true for the unapportioned-volume row ({@code ,#}), false for {@code , }
   * @param jointNames the joint holders' names joined with {@code "; "} ({@code JOINT})
   */
  record Aptr041Row(
      String holder,
      String path,
      String sizeClass,
      String parentName,
      String forestFileId,
      String clientName,
      String foa,
      LocalDate expiryDate,
      String regionCode,
      String managementUnitName,
      BigDecimal aac,
      boolean uav,
      String jointNames) {

    /** The function's line for this group. */
    String line() {
      boolean z = "Z".equals(path);
      String group = switch (holder) {
        case "CORP" -> "\"" + nz(parentName) + "\",";
        case "IND" -> "MED".equals(sizeClass)
            ? (z ? ",INDEPENDENT MEDIUM," : "INDEPENDENT MEDIUM,")
            : "INDEPENDENT SMALL,";
        case "JOINT" -> z ? "Jointly Held," : "JOINTLY HELD,";
        default -> throw new IllegalStateException("Unknown APTR041 holder " + holder);
      };
      String licensee = "JOINT".equals(holder)
          ? "," + jointlyHeldNames(jointNames) + ","
          : ",\"" + nz(clientName) + "\",";
      return group
          + nz(forestFileId)
          + licensee
          + nz(foa) + ","
          + date(expiryDate, APTR041_DATE) + ","
          + nz(regionCode) + ","
          + nz(managementUnitName) + ","
          + toChar(aac)
          + (uav ? ",#" : ", ");
    }
  }

  /**
   * {@code APT_JOINTLY_HELD_NAMES}: the names, cut to 98 characters, in double quotes;
   * {@code ""} when there are none.
   */
  static String jointlyHeldNames(String joined) {
    String names = joined == null ? "" : joined;
    if (names.codePointCount(0, names.length()) > JOINT_NAMES_MAX) {
      names = names.substring(0, names.offsetByCodePoints(0, JOINT_NAMES_MAX));
    }
    return "\"" + names + "\"";
  }

  // ─────────────────────────────── shared ───────────────────────────────

  /**
   * The file's lines as the function's {@code UNION} returned them: header and data lines,
   * duplicates dropped, sorted by binary comparison (Unicode code point order, which is the
   * byte order of the database's UTF-8).
   */
  static List<String> fileLines(String header, Collection<String> dataLines) {
    TreeSet<String> lines = new TreeSet<>(AptReportExtractSql::compareBinary);
    lines.add(header);
    lines.addAll(dataLines);
    return List.copyOf(lines);
  }

  static int compareBinary(String a, String b) {
    int i = 0;
    int j = 0;
    while (i < a.length() && j < b.length()) {
      int ca = a.codePointAt(i);
      int cb = b.codePointAt(j);
      if (ca != cb) {
        return Integer.compare(ca, cb);
      }
      i += Character.charCount(ca);
      j += Character.charCount(cb);
    }
    return Integer.compare(a.length() - i, b.length() - j);
  }

  /**
   * {@code TO_CHAR(n)} with no format: the shortest plain form, no trailing zeros, no leading
   * zero before the point ({@code .5}, {@code -.5}); null is empty.
   */
  static String toChar(BigDecimal n) {
    if (n == null) {
      return "";
    }
    if (n.signum() == 0) {
      return "0";
    }
    String s = n.stripTrailingZeros().toPlainString();
    if (s.startsWith("0.")) {
      return s.substring(1);
    }
    if (s.startsWith("-0.")) {
      return "-" + s.substring(2);
    }
    return s;
  }

  private static String quoted(String v) {
    return "\"" + nz(v) + "\"";
  }

  private static String date(LocalDate d, DateTimeFormatter f) {
    return d == null ? "" : d.format(f);
  }

  private static String nz(String v) {
    return v == null ? "" : v;
  }
}
