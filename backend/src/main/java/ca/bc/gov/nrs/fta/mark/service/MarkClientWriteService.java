package ca.bc.gov.nrs.fta.mark.service;

import static ca.bc.gov.nrs.fta.mark.service.MarkFieldChecks.trim;

import ca.bc.gov.nrs.fta.mark.dto.MarkClientRequest;
import ca.bc.gov.nrs.fta.mark.dto.MarkClientUpdateRequest;
import ca.bc.gov.nrs.fta.mark.dto.MarkDetailDto;
import ca.bc.gov.nrs.fta.mark.dto.MarkEditRules;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Adds an associated client to a private mark, or updates one on it — legacy FTA513's add
 * row and its save of an existing one ({@code UPDATE_FFC_REC} / {@code UPDATE_PMC_REC}).
 *
 * <p>Ports {@code FTA_513_PM_CLIENT}: the gate from its {@code GET} (Headquarters; not
 * B15/B16; only at HI, PI or PA — {@link MarkEditRules#clients()}), the form's and
 * {@code save_pmc}/{@code save_ffc}'s checks, and their inserts. An issued mark's clients are
 * its forest file's ({@code FOREST_FILE_CLIENT}); an application's are on the certificate
 * ({@code PRIVATE_MARK_CLIENT}) until issuing moves them across. An update has its own gate
 * ({@link MarkEditRules#clientsUpdate()}): Headquarters may correct a row at any status.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class MarkClientWriteService {

  /** Licensee types — at most one row per client among them, and A/B take no end date. */
  private static final Set<String> LICENSEES = Set.of("A", "B");

  private final NamedParameterJdbcTemplate jdbc;
  private final MarkDetailService markDetailService;
  private final MarkFieldChecks checks;

  public MarkClientWriteService(
      NamedParameterJdbcTemplate jdbc,
      MarkDetailService markDetailService,
      MarkFieldChecks checks) {
    this.jdbc = jdbc;
    this.markDetailService = markDetailService;
    this.checks = checks;
  }

  private static final String CLIENT_TYPE_SQL =
      """
      SELECT COUNT(*) FROM the.file_client_type_code
       WHERE file_client_type_code = :code AND SYSDATE BETWEEN effective_date AND expiry_date
      """;

  /**
   * The table a mark's clients live in, and the key to it. Table and column names are fixed
   * strings chosen here, never from input.
   */
  private record Target(String table, String keyColumn, String typeColumn, String skeyColumn,
                        String key) {}

  private static Target targetFor(MarkDetailDto mark) {
    // FTA_513.mainline SAVE: a mark with a file type (issued) → save_ffc, else save_pmc.
    return mark.fileTypeCode() != null && mark.timberMark() != null
        ? new Target("the.forest_file_client", "forest_file_id",
            "forest_file_client_type_code", "forest_file_client_skey", mark.timberMark())
        : new Target("the.private_mark_client", "certificate",
            "private_mark_client_type_code", "private_mark_client_skey", mark.certificate());
  }

  /**
   * Adds the client. Returns a note to show the user when adding a main licensee demoted the
   * current one (legacy's "client reset" warning), else null.
   *
   * @throws ResponseStatusException 404 if the mark does not exist; 409 if its clients cannot
   *     be changed at its status or type; 400 if a field is invalid
   */
  @Transactional
  public String add(String id, boolean byCertificate, MarkClientRequest request, String userId) {
    MarkDetailDto mark = (byCertificate
            ? markDetailService.findByCertificate(id)
            : markDetailService.findByMarkNumber(id))
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Private mark not found."));
    MarkEditRules rules = MarkEditRules.of(mark, true);
    if (!rules.clients()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, rules.clientsReason());
    }

    String number = trim(request.clientNumber());
    String locn = trim(request.clientLocnCode());
    String type = trim(request.fileClientType());
    LocalDate start = request.licenseeStartDate();
    LocalDate end = request.licenseeEndDate();
    Target t = targetFor(mark);

    List<String> e = new ArrayList<>();
    if (number == null) {
      e.add("Client is required.");
    } else if (locn == null) {
      e.add("Client location is required.");
    } else if (!clientLocationExists(number, locn)) {
      e.add("Client " + number + " has no location " + locn + ".");
    }
    if (type == null) {
      e.add("Client type is required.");
    } else if (!checks.unchangedOrActive(null, type, CLIENT_TYPE_SQL)) {
      e.add("Client type " + type + " is not a current code.");
    }
    if (type != null) {
      validateDates(e, t, type, start, end, null);
    }
    Map<String, Object> main = mainLicensee(t, null);
    if (main == null && type != null && !"A".equals(type)) {
      e.add("At least one Main Licensee (A type) is required — add it first.");
    }
    if (number != null && locn != null && type != null && alreadyAdded(t, number, locn, type, null)) {
      e.add("This client is already associated with the mark.");
    }
    if (!e.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", e));
    }

    String note = "A".equals(type) ? demote(t, main, userId) : null;

    try {
      jdbc.update(
          "INSERT INTO " + t.table() + " (" + t.keyColumn() + ", revision_count, "
              + t.typeColumn() + ", client_number, client_locn_code, " + t.skeyColumn()
              + ", licensee_start_date, licensee_end_date, entry_userid, entry_timestamp,"
              + " update_userid, update_timestamp) VALUES (:key, 1, :type, :number, :locn,"
              + " the.for_client_link_seq.NEXTVAL, :start, :end, :userId, SYSDATE, :userId,"
              + " SYSDATE)",
          new MapSqlParameterSource()
              .addValue("key", t.key())
              .addValue("type", type)
              .addValue("number", number)
              .addValue("locn", locn)
              .addValue("start", start, Types.DATE)
              .addValue("end", end, Types.DATE)
              .addValue("userId", userId));
    } catch (DuplicateKeyException ex) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "This client is already associated with the mark.");
    }
    return note;
  }

  /**
   * Updates the associated client {@code skey} on the mark. Returns the same note as
   * {@link #add} when making it the main licensee demoted the current one, else null.
   *
   * @throws ResponseStatusException 404 if the mark or the client row on it does not exist;
   *     409 if the user may not update it or someone else saved it first; 400 if a field is
   *     invalid
   */
  @Transactional
  public String update(
      String id,
      boolean byCertificate,
      long skey,
      MarkClientUpdateRequest request,
      boolean districtUser,
      String userId) {
    MarkDetailDto mark = (byCertificate
            ? markDetailService.findByCertificate(id)
            : markDetailService.findByMarkNumber(id))
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Private mark not found."));
    if (!MarkEditRules.of(mark, true, districtUser).clientsUpdate()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Clients may not be updated at this mark's status or type.");
    }
    if (request.revisionCount() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revision count is required.");
    }
    Target t = targetFor(mark);
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT client_number, " + t.typeColumn() + " AS type FROM " + t.table()
            + " WHERE " + t.skeyColumn() + " = :skey AND " + t.keyColumn() + " = :key",
        new MapSqlParameterSource().addValue("skey", skey).addValue("key", t.key()));
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found on this mark.");
    }
    String storedNumber = (String) rows.get(0).get("client_number");
    String storedType = (String) rows.get(0).get("type");

    String number = trim(request.clientNumber());
    String locn = trim(request.clientLocnCode());
    String type = trim(request.fileClientType());
    LocalDate start = request.licenseeStartDate();
    LocalDate end = request.licenseeEndDate();

    List<String> e = new ArrayList<>();
    if (number == null) {
      e.add("Client is required.");
    } else if (locn == null) {
      e.add("Client location is required.");
    } else if (!clientLocationExists(number, locn)) {
      e.add("Client " + number + " has no location " + locn + ".");
    }
    if (type == null) {
      e.add("Client type is required.");
    } else if (!checks.unchangedOrActive(storedType, type, CLIENT_TYPE_SQL)) {
      e.add("Client type " + type + " is not a current code.");
    }
    // save_ffc / save_pmc: a Main or Previous Licensee keeps its client and its type.
    if ("A".equals(storedType) || "C".equals(storedType)) {
      if (number != null && !number.equals(storedNumber)) {
        e.add("Cannot change the client of a Main or Previous Licensee (A or C).");
      } else if (type != null && !type.equals(storedType)) {
        e.add("Cannot change the client type of a Main or Previous Licensee (A or C).");
      }
    }
    if (type != null) {
      validateDates(e, t, type, start, end, skey);
    }
    if (number != null && locn != null && type != null
        && alreadyAdded(t, number, locn, type, skey)) {
      e.add("This client is already associated with the mark.");
    }
    if (!e.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", e));
    }

    // A licensee (B) made the main one takes over from the current main licensee.
    String note = "A".equals(type) ? demote(t, mainLicensee(t, skey), userId) : null;

    int n = jdbc.update(
        "UPDATE " + t.table() + " SET " + t.typeColumn() + " = :type,"
            + " client_number = :number, client_locn_code = :locn,"
            + " licensee_start_date = :start, licensee_end_date = :end,"
            + " update_userid = :userId, update_timestamp = SYSDATE,"
            + " revision_count = revision_count + 1"
            + " WHERE " + t.skeyColumn() + " = :skey AND revision_count = :revisionCount",
        new MapSqlParameterSource()
            .addValue("type", type)
            .addValue("number", number)
            .addValue("locn", locn)
            .addValue("start", start, Types.DATE)
            .addValue("end", end, Types.DATE)
            .addValue("userId", userId)
            .addValue("skey", skey)
            .addValue("revisionCount", request.revisionCount()));
    if (n == 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "This client was changed by someone else. Reload the mark and try again.");
    }
    return note;
  }

  /**
   * Only one main licensee: a new A makes the current one ({@code main}, if any) the previous
   * licensee (C). Returns the note to show, or null when there was none.
   */
  private String demote(Target t, Map<String, Object> main, String userId) {
    if (main == null) {
      return null;
    }
    jdbc.update(
        "UPDATE " + t.table() + " SET " + t.typeColumn() + " = 'C', licensee_end_date = SYSDATE,"
            + " revision_count = revision_count + 1, update_userid = :userId,"
            + " update_timestamp = SYSDATE WHERE " + t.skeyColumn() + " = :skey",
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("skey", main.get("skey")));
    return "Client " + main.get("client_number") + " is now the previous licensee (C).";
  }

  /**
   * save_pmc / save_ffc's date rules, by client type. {@code self} is the row being updated,
   * left out of the current main licensee; null when adding.
   */
  private void validateDates(
      List<String> e, Target t, String type, LocalDate start, LocalDate end, Long self) {
    if (Set.of("A", "B", "C").contains(type) && start == null) {
      e.add("Licensee Start Date is required for type " + type + ".");
    }
    if (Set.of("C", "P").contains(type) && end == null) {
      e.add("Licensee End Date is required for type " + type + ".");
    } else if ("M".equals(type) && start != null && end == null) {
      e.add("Licensee End Date is required when a start date is given.");
    }
    if (LICENSEES.contains(type) && end != null) {
      e.add("Licensee End Date must be blank for a licensee (A or B).");
    } else if (end != null && start == null) {
      e.add("Licensee Start Date is required when an end date is given.");
    }
    if (start != null && end != null && start.isAfter(end)) {
      e.add("Licensee Start Date must be on or before the End Date.");
    }
    if (LICENSEES.contains(type) && start != null) {
      LocalDate previousEnd = jdbc.queryForObject(
          "SELECT MAX(licensee_end_date) FROM " + t.table() + " WHERE " + t.keyColumn()
              + " = :key AND " + t.typeColumn() + " = 'C'",
          new MapSqlParameterSource("key", t.key()), LocalDate.class);
      Map<String, Object> main = mainLicensee(t, self);
      LocalDate mainStart = main == null ? null : (LocalDate) main.get("start");
      if (previousEnd != null && start.isBefore(previousEnd)) {
        e.add("Licensee Start Date must be on or after the previous licensee's end date ("
            + previousEnd + ").");
      } else if (mainStart != null && start.isBefore(mainStart)) {
        e.add("Licensee Start Date must be on or after the current main licensee's start ("
            + mainStart + ").");
      }
    }
  }

  /** The current main licensee (A) other than the row {@code self}, or null. */
  private Map<String, Object> mainLicensee(Target t, Long self) {
    List<Map<String, Object>> rows = jdbc.query(
        "SELECT " + t.skeyColumn() + " AS skey, client_number, licensee_start_date FROM "
            + t.table() + " WHERE " + t.keyColumn() + " = :key AND " + t.typeColumn() + " = 'A'"
            + " AND " + t.skeyColumn() + " <> NVL(:self, -1)",
        new MapSqlParameterSource("key", t.key()).addValue("self", self, Types.NUMERIC),
        (rs, n) -> {
          Map<String, Object> m = new java.util.HashMap<>();
          m.put("skey", rs.getLong("skey"));
          m.put("client_number", rs.getString("client_number"));
          m.put("start", rs.getObject("licensee_start_date", LocalDate.class));
          return m;
        });
    return rows.isEmpty() ? null : rows.get(0);
  }

  /** save_pmc / save_ffc's duplicate check: the same client as a licensee twice, or same type. */
  private boolean alreadyAdded(Target t, String number, String locn, String type, Long self) {
    Long n = jdbc.queryForObject(
        "SELECT COUNT(*) FROM " + t.table() + " WHERE " + t.keyColumn() + " = :key"
            + " AND " + t.skeyColumn() + " <> NVL(:self, -1)"
            + " AND client_number = :number AND client_locn_code = :locn"
            + " AND ((:type IN ('A', 'B') AND " + t.typeColumn() + " IN ('A', 'B'))"
            + " OR " + t.typeColumn() + " = :type)",
        new MapSqlParameterSource()
            .addValue("key", t.key())
            .addValue("number", number)
            .addValue("locn", locn)
            .addValue("type", type)
            .addValue("self", self, Types.NUMERIC),
        Long.class);
    return n != null && n > 0;
  }

  private boolean clientLocationExists(String number, String locn) {
    Long n = jdbc.queryForObject(
        """
        SELECT COUNT(*) FROM the.client_location
         WHERE client_number = :number AND client_locn_code = :locn
        """,
        new MapSqlParameterSource().addValue("number", number).addValue("locn", locn),
        Long.class);
    return n != null && n > 0;
  }
}
