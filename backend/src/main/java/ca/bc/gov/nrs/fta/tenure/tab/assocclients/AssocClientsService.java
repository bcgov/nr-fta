package ca.bc.gov.nrs.fta.tenure.tab.assocclients;

import ca.bc.gov.nrs.fta.tenure.tab.assocclients.AssocClientsDtos.AssocClientRequest;
import ca.bc.gov.nrs.fta.tenure.tab.assocclients.AssocClientsDtos.AssocClientRowDto;
import ca.bc.gov.nrs.fta.tenure.tab.assocclients.AssocClientsDtos.AssocClientsListDto;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The tenure's clients — legacy FTA920 (Associated Clients) at the file level, i.e.
 * {@code THE.FOREST_FILE_CLIENT}. (FTA920's cutting-permit and cut-block levels belong to those
 * screens; the range variant FTA920R to range tenures.)
 *
 * <p>Ports {@code FTA_920_ASSO_CLIENTS}: GET's file-client query and {@code p_save_ok} gate;
 * SAVE's client-location check, then {@code save_forest_client} (add) or {@code update_rec}
 * (update), each with its own checks; {@code delete_rec}. A new Main Licensee (A) makes the
 * current one the previous licensee (C). Gates and the database-free checks are in
 * {@link AssocClientsRules}.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class AssocClientsService {

  /** The file and the facts the gates need, from legacy's helper functions. */
  private static final String TENURE_SQL =
      """
      SELECT pfu.file_type_code,
             pfu.file_status_st,
             pfu.bcts_org_unit,
             NVL(pfu.bcts_org_unit, pfu.forest_region) AS manager_org_unit,
             (SELECT COUNT(*) FROM the.private_mark_type_code pm
               WHERE pm.private_mark_type_code = pfu.file_type_code)        AS private_mark,
             (SELECT COUNT(*) FROM the.file_type_code ftc
               WHERE ftc.file_type_code = pfu.file_type_code
                 AND ftc.expiry_date <= SYSDATE)                             AS expired_type,
             (SELECT COUNT(*) FROM the.recreation_file_type_code rft
               WHERE rft.recreation_file_type_code = pfu.file_type_code)    AS recreation,
             (SELECT COUNT(*) FROM the.fta_file_level_authority fla
               WHERE fla.file_type_code = pfu.file_type_code
                 AND fla.file_level_type = 'FILE'
                 AND fla.org_level_code = 'H')                               AS file_authority,
             (SELECT COUNT(*) FROM the.single_mark_file_type_code smf
               WHERE smf.single_mark_file_type_code = pfu.file_type_code)   AS single_mark
        FROM the.prov_forest_use pfu
       WHERE pfu.forest_file_id = :forestFileId
      """;

  private static final String LIST_SQL =
      """
      SELECT ffc.forest_file_client_skey,
             ffc.client_number,
             ffc.client_locn_code,
             cli.client_name,
             ffc.forest_file_client_type_code                           AS type_code,
             CASE WHEN fct.description IS NOT NULL
                  THEN ffc.forest_file_client_type_code || ' - ' || fct.description
             END                                                        AS type_desc,
             ffc.licensee_start_date,
             ffc.licensee_end_date,
             ffc.revision_count
        FROM the.forest_file_client ffc
        LEFT JOIN the.file_client_type_code fct
               ON fct.file_client_type_code = ffc.forest_file_client_type_code
        LEFT JOIN the.forest_client cli ON cli.client_number = ffc.client_number
       WHERE ffc.forest_file_id = :forestFileId
       ORDER BY ffc.forest_file_client_type_code,
                ffc.licensee_start_date DESC NULLS LAST,
                ffc.client_number, ffc.client_locn_code
      """;

  private static final String ROW_SQL =
      """
      SELECT forest_file_client_type_code, client_number, client_locn_code
        FROM the.forest_file_client
       WHERE forest_file_id = :forestFileId AND forest_file_client_skey = :skey
      """;

  /** SAVE: the location must exist (NO_DATA_FOUND otherwise) and not be expired. */
  private static final String LOCATION_SQL =
      """
      SELECT NVL(locn_expired_ind, 'N') FROM the.client_location
       WHERE client_number = :number AND client_locn_code = :locn
      """;

  private static final String TYPE_CODE_SQL =
      """
      SELECT CASE WHEN SYSDATE BETWEEN effective_date AND expiry_date THEN 'Y' ELSE 'N' END
        FROM the.file_client_type_code WHERE file_client_type_code = :code
      """;

  /** curMainClient: the Main Licensee other than this row. */
  private static final String MAIN_SQL =
      """
      SELECT forest_file_client_skey, revision_count, licensee_start_date,
             client_number, client_locn_code
        FROM the.forest_file_client
       WHERE forest_file_id = :forestFileId
         AND forest_file_client_skey <> NVL(:skey, 0)
         AND forest_file_client_type_code = 'A'
      """;

  /** curEndDate: the latest previous licensee's end. */
  private static final String PREVIOUS_END_SQL =
      """
      SELECT MAX(licensee_end_date) FROM the.forest_file_client
       WHERE forest_file_id = :forestFileId AND forest_file_client_type_code = 'C'
      """;

  /** save_forest_client's duplicate check: the client is already the A or B licensee. */
  private static final String DUP_LICENSEE_SQL =
      """
      SELECT COUNT(*) FROM the.forest_file_client
       WHERE forest_file_id = :forestFileId
         AND client_number = :number AND client_locn_code = :locn
         AND forest_file_client_type_code IN ('A', 'B')
         AND forest_file_client_skey <> NVL(:skey, 0)
      """;

  /** update_rec's duplicate check: same client as a licensee twice, or with the same type. */
  private static final String DUP_UPDATE_SQL =
      """
      SELECT COUNT(*) FROM the.forest_file_client
       WHERE forest_file_id = :forestFileId
         AND client_number = :number AND client_locn_code = :locn
         AND ((:type IN ('A', 'B') AND forest_file_client_type_code IN ('A', 'B'))
              OR forest_file_client_type_code = :type)
         AND forest_file_client_skey <> NVL(:skey, 0)
      """;

  private static final String OTHER_S_SQL =
      """
      SELECT COUNT(*) FROM the.forest_file_client
       WHERE forest_file_id = :forestFileId
         AND forest_file_client_skey <> NVL(:skey, 0)
         AND forest_file_client_type_code = 'S'
      """;

  /** cur_is_dm_lookup: the client is the district's (or TSO's) manager. */
  private static final String IS_MANAGER_SQL =
      """
      SELECT COUNT(*) FROM the.dist_tenr_deflt
       WHERE client_number = :number AND admin_forest_dist = :orgUnit
      """;

  /** CUR_XMASTREE: an A on a B02 when the client already holds another active B02. */
  private static final String XMAS_SQL =
      """
      SELECT COUNT(*) FROM the.forest_file_client a
        JOIN the.prov_forest_use b ON b.forest_file_id = a.forest_file_id
       WHERE a.client_number = :number
         AND b.file_type_code = 'B02'
         AND b.forest_file_id <> :forestFileId
         AND b.file_status_st IN ('HI', 'HA')
      """;

  private static final String DEMOTE_SQL =
      """
      UPDATE the.forest_file_client
         SET forest_file_client_type_code = 'C',
             revision_count = revision_count + 1,
             licensee_end_date = :endDate,
             update_userid = :userId,
             update_timestamp = SYSDATE
       WHERE forest_file_client_skey = :skey
         AND revision_count = :revisionCount
      """;

  private static final String INSERT_SQL =
      """
      INSERT INTO the.forest_file_client (
        forest_file_id, forest_file_client_skey, client_locn_code, client_number,
        forest_file_client_type_code, licensee_end_date, licensee_start_date,
        entry_timestamp, entry_userid, update_timestamp, update_userid, revision_count
      ) VALUES (
        :forestFileId, the.for_client_link_seq.NEXTVAL, :locn, :number,
        :type, :end, :start,
        SYSDATE, :userId, SYSDATE, :userId, 0
      )
      """;

  private static final String UPDATE_SQL =
      """
      UPDATE the.forest_file_client
         SET forest_file_client_type_code = :type,
             client_number = :number,
             client_locn_code = :locn,
             licensee_start_date = :start,
             licensee_end_date = :end,
             update_userid = :userId,
             update_timestamp = SYSDATE,
             revision_count = revision_count + 1
       WHERE forest_file_id = :forestFileId
         AND forest_file_client_skey = :skey
         AND revision_count = :revisionCount
      """;

  private static final String DELETE_SQL =
      """
      DELETE FROM the.forest_file_client
       WHERE forest_file_id = :forestFileId
         AND forest_file_client_skey = :skey
         AND revision_count = :revisionCount
      """;

  static final String MODIFIED =
      "The client has been changed or removed by another user. Refresh the tab and try again.";

  private final NamedParameterJdbcTemplate jdbc;

  public AssocClientsService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private record Tenure(
      String fileType,
      String status,
      boolean bctsFunded,
      Long managerOrgUnit,
      AssocClientsRules rules,
      boolean singleMark) {}

  private record Main(long skey, long revisionCount, LocalDate start, String number,
                      String locn) {}

  private record Row(String type, String number, String locn) {}

  private Tenure tenure(String forestFileId) {
    try {
      return jdbc.queryForObject(
          TENURE_SQL,
          new MapSqlParameterSource("forestFileId", forestFileId),
          (rs, n) -> {
            String status = rs.getString("file_status_st");
            boolean unsupported = rs.getLong("expired_type") == 1 || rs.getLong("recreation") > 0;
            return new Tenure(
                rs.getString("file_type_code"),
                status,
                rs.getObject("bcts_org_unit") != null,
                rs.getObject("manager_org_unit", Long.class),
                AssocClientsRules.of(
                    status,
                    rs.getLong("private_mark") > 0,
                    unsupported,
                    rs.getLong("file_authority") > 0),
                rs.getLong("single_mark") > 0);
          });
    } catch (EmptyResultDataAccessException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenure not found.");
    }
  }

  /** The tab. 404 when the tenure does not exist. */
  public AssocClientsListDto list(String forestFileId) {
    AssocClientsRules rules = tenure(forestFileId).rules();
    List<AssocClientRowDto> rows = jdbc.query(
        LIST_SQL,
        new MapSqlParameterSource("forestFileId", forestFileId),
        (rs, n) -> {
          String type = rs.getString("type_code");
          return new AssocClientRowDto(
              rs.getObject("forest_file_client_skey", Long.class),
              rs.getString("client_number"),
              rs.getString("client_locn_code"),
              rs.getString("client_name"),
              type,
              rs.getString("type_desc"),
              rs.getObject("licensee_start_date", LocalDate.class),
              rs.getObject("licensee_end_date", LocalDate.class),
              rs.getObject("revision_count", Long.class),
              rules.canDelete(type));
        });
    return new AssocClientsListDto(
        rows, rules.canEdit(), rules.editReason(), rules.canDelete() ? null : rules.deleteReason());
  }

  /**
   * Adds a client (save_forest_client). Returns the "set to previous licensee" note when a new
   * Main Licensee demoted the current one, else null.
   *
   * @throws ResponseStatusException 404 if the tenure does not exist; 409 if its clients cannot
   *     be changed; 400 if a field is invalid
   */
  @Transactional
  public String add(String forestFileId, AssocClientRequest q, String userId) {
    Tenure t = tenure(forestFileId);
    gate(t.rules());
    Fields f = Fields.of(q);

    List<String> e = AssocClientsRules.validateFields(f.number, f.locn, f.type, f.start, f.end);
    typeCodeProblem(null, f.type, e);
    if (e.isEmpty()) {
      locationProblem(f, e);
    }
    if (!e.isEmpty()) {
      throw bad(e);
    }
    MapSqlParameterSource p = params(forestFileId, f, null, userId);

    String manager = AssocClientsRules.managerProblem(
        t.fileType(), t.bctsFunded(),
        t.managerOrgUnit() != null && count(IS_MANAGER_SQL,
            new MapSqlParameterSource("number", f.number).addValue("orgUnit", t.managerOrgUnit()))
            > 0,
        f.type);
    add(e, manager);
    if ("S".equals(f.type)) {
      add(e, AssocClientsRules.sTypeProblem(
          f.type, t.singleMark(), t.singleMark() ? count(OTHER_S_SQL, p) : 0));
    }
    Main main = main(p);
    if (main == null && !"A".equals(f.type)) {
      e.add(AssocClientsRules.MAIN_REQUIRED);
    }
    if (count(DUP_LICENSEE_SQL, p) > 0) {
      e.add("Client already exists as the main or secondary licensee.");
    }
    add(e, AssocClientsRules.licenseeStartProblem(
        f.type, f.start, previousEnd(p), main == null ? null : main.start()));
    if ("A".equals(f.type) && "B02".equals(t.fileType()) && count(XMAS_SQL, p) > 0) {
      e.add("Only one active B02 Xmas Permit allowed for licensee.");
    }
    if (!e.isEmpty()) {
      throw bad(e);
    }

    // Only one Main Licensee: the current one becomes the previous licensee, ending the day
    // the new one starts.
    String note = "A".equals(f.type) && main != null ? demote(main, f.start, userId) : null;
    try {
      jdbc.update(INSERT_SQL, p);
    } catch (DuplicateKeyException ex) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Duplicate client number/type not allowed.");
    }
    return note;
  }

  /**
   * Updates a client (update_rec), at its revision.
   *
   * @throws ResponseStatusException 404 if the tenure or client does not exist; 409 if the
   *     clients cannot be changed, or the row changed since read; 400 if a field is invalid
   */
  @Transactional
  public String update(String forestFileId, long skey, AssocClientRequest q, String userId) {
    Tenure t = tenure(forestFileId);
    gate(t.rules());
    Row prev = row(forestFileId, skey);
    Fields f = Fields.of(q);
    if (q == null || q.revisionCount() == null) {
      throw bad(List.of("The client's revision count is required."));
    }

    List<String> e = AssocClientsRules.validateFields(f.number, f.locn, f.type, f.start, f.end);
    typeCodeProblem(prev.type(), f.type, e);
    if (e.isEmpty()) {
      locationProblem(f, e);
    }
    if (!e.isEmpty()) {
      throw bad(e);
    }
    MapSqlParameterSource p = params(forestFileId, f, skey, userId)
        .addValue("revisionCount", q.revisionCount());

    if (count(DUP_UPDATE_SQL, p) > 0) {
      e.add("Client already exists.");
    }
    e.addAll(AssocClientsRules.updateIdentityRules(prev.type(), prev.number(), f.number, f.type));
    Main main = main(p);
    add(e, AssocClientsRules.licenseeStartProblem(
        f.type, f.start, previousEnd(p), main == null ? null : main.start()));
    if (!e.isEmpty()) {
      throw bad(e);
    }

    // A licensee (B) made the Main Licensee: the current one becomes previous, ended today.
    String note = "A".equals(f.type) && main != null ? demote(main, LocalDate.now(), userId)
        : null;
    int n;
    try {
      n = jdbc.update(UPDATE_SQL, p);
    } catch (DuplicateKeyException ex) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Duplicate client number/type not allowed.");
    }
    if (n == 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, MODIFIED);
    }
    return note;
  }

  /**
   * Deletes a client (delete_rec) — a previous licensee (C) or S type only, as legacy offers.
   *
   * @throws ResponseStatusException 404 if the tenure or client does not exist; 409 if it may
   *     not be deleted, or changed since read
   */
  @Transactional
  public void delete(String forestFileId, long skey, Long revisionCount) {
    Tenure t = tenure(forestFileId);
    if (!t.rules().canDelete()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, t.rules().deleteReason());
    }
    Row row = row(forestFileId, skey);
    if (!t.rules().canDelete(row.type())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Only a Previous Licensee (C) or an S type client can be deleted.");
    }
    if (revisionCount == null) {
      throw bad(List.of("The client's revision count is required."));
    }
    int n = jdbc.update(DELETE_SQL, new MapSqlParameterSource()
        .addValue("forestFileId", forestFileId)
        .addValue("skey", skey)
        .addValue("revisionCount", revisionCount));
    if (n == 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, MODIFIED);
    }
  }

  // ---------------------------------------------------------------------------------------------

  /** The request's fields, trimmed (codes upper-cased), blank as null. */
  private record Fields(String number, String locn, String type, LocalDate start, LocalDate end) {
    static Fields of(AssocClientRequest q) {
      if (q == null) {
        return new Fields(null, null, null, null, null);
      }
      return new Fields(
          trim(q.clientNumber()),
          trim(q.clientLocnCode()),
          q.fileClientType() == null ? null : upper(q.fileClientType()),
          q.licenseeStartDate(),
          q.licenseeEndDate());
    }
  }

  private static void gate(AssocClientsRules rules) {
    if (!rules.canEdit()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, rules.editReason());
    }
  }

  private Row row(String forestFileId, long skey) {
    List<Row> rows = jdbc.query(
        ROW_SQL,
        new MapSqlParameterSource("forestFileId", forestFileId).addValue("skey", skey),
        (rs, n) -> new Row(
            rs.getString("forest_file_client_type_code"),
            rs.getString("client_number"),
            rs.getString("client_locn_code")));
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found on this tenure.");
    }
    return rows.get(0);
  }

  private MapSqlParameterSource params(String forestFileId, Fields f, Long skey, String userId) {
    return new MapSqlParameterSource()
        .addValue("forestFileId", forestFileId)
        .addValue("skey", skey, Types.NUMERIC)
        .addValue("number", f.number)
        .addValue("locn", f.locn)
        .addValue("type", f.type)
        .addValue("start", f.start, Types.DATE)
        .addValue("end", f.end, Types.DATE)
        .addValue("userId", userId);
  }

  private Main main(MapSqlParameterSource p) {
    List<Main> rows = jdbc.query(MAIN_SQL, p, (rs, n) -> new Main(
        rs.getLong("forest_file_client_skey"),
        rs.getLong("revision_count"),
        rs.getObject("licensee_start_date", LocalDate.class),
        rs.getString("client_number"),
        rs.getString("client_locn_code")));
    return rows.isEmpty() ? null : rows.get(0);
  }

  private LocalDate previousEnd(MapSqlParameterSource p) {
    return jdbc.queryForObject(PREVIOUS_END_SQL, p, LocalDate.class);
  }

  /** Makes the current Main Licensee the previous one; returns legacy's fta520.client.reset. */
  private String demote(Main main, LocalDate endDate, String userId) {
    int n = jdbc.update(DEMOTE_SQL, new MapSqlParameterSource()
        .addValue("endDate", endDate, Types.DATE)
        .addValue("userId", userId)
        .addValue("skey", main.skey())
        .addValue("revisionCount", main.revisionCount()));
    if (n == 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, MODIFIED);
    }
    return "Client " + main.number() + " " + main.locn()
        + " has been set to previous licensee. Please review licensee end date if required.";
  }

  /** CodeExpiryValidator on Client Type; a row keeping its (expired) type is fine. */
  private void typeCodeProblem(String stored, String type, List<String> e) {
    if (type == null || type.equals(stored)) {
      return;
    }
    List<String> current = jdbc.queryForList(
        TYPE_CODE_SQL, new MapSqlParameterSource("code", type), String.class);
    if (current.isEmpty()) {
      e.add("Invalid Client Type.");
    } else if (!"Y".equals(current.get(0))) {
      e.add("Client Type (" + type + ") is an expired code.");
    }
  }

  /** SAVE's CLIENT_LOCATION check (and the form's client field's). */
  private void locationProblem(Fields f, List<String> e) {
    List<String> expired = jdbc.queryForList(
        LOCATION_SQL,
        new MapSqlParameterSource().addValue("number", f.number).addValue("locn", f.locn),
        String.class);
    if (expired.isEmpty()) {
      e.add("Client " + f.number + " has no location " + f.locn + ".");
    } else if ("Y".equals(expired.get(0))) {
      e.add("That client location is expired. Please check your client and location.");
    }
  }

  private long count(String sql, MapSqlParameterSource p) {
    Long n = jdbc.queryForObject(sql, p, Long.class);
    return n == null ? 0 : n;
  }

  private static void add(List<String> e, String problem) {
    if (problem != null) {
      e.add(problem);
    }
  }

  private static ResponseStatusException bad(List<String> errors) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", errors));
  }

  private static String trim(String v) {
    return v == null || v.isBlank() ? null : v.trim();
  }

  private static String upper(String v) {
    String t = trim(v);
    return t == null ? null : t.toUpperCase(Locale.ROOT);
  }
}
