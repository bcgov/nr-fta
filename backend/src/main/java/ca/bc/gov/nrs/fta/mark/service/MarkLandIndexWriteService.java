package ca.bc.gov.nrs.fta.mark.service;

import static ca.bc.gov.nrs.fta.mark.service.MarkFieldChecks.trim;

import ca.bc.gov.nrs.fta.mark.dto.MarkDetailDto;
import ca.bc.gov.nrs.fta.mark.dto.MarkEditRules;
import ca.bc.gov.nrs.fta.mark.dto.MarkLandIndexRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Adds a land index to a private mark — legacy FTA511's add row.
 *
 * <p>Ports {@code FTA_511_MARK_LAND_INDEX}: the gate from its {@code GET} (Headquarters only;
 * not at HX, DV or DD; not for B15/B16 — {@link MarkEditRules#landIndex()}), the form's checks
 * ({@code Fta511MarkLandIndexForm}: Land District/Island required, both codes current,
 * description up to 40), and {@code SAVE}'s insert. The row carries the certificate and the
 * timber mark, so an application not yet issued keeps its land index when the mark is.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class MarkLandIndexWriteService {

  /** The legacy add row's description maxlength. */
  private static final int MAX_DESC = 40;

  private final NamedParameterJdbcTemplate jdbc;
  private final MarkDetailService markDetailService;
  private final MarkFieldChecks checks;

  public MarkLandIndexWriteService(
      NamedParameterJdbcTemplate jdbc,
      MarkDetailService markDetailService,
      MarkFieldChecks checks) {
    this.jdbc = jdbc;
    this.markDetailService = markDetailService;
    this.checks = checks;
  }

  private static final String PRIMARY_SQL =
      """
      SELECT COUNT(*) FROM the.primary_land_index_code
       WHERE primary_land_index_code = :code AND SYSDATE BETWEEN effective_date AND expiry_date
      """;

  private static final String SECONDARY_SQL =
      """
      SELECT COUNT(*) FROM the.secondary_land_index_code
       WHERE secondary_land_index_code = :code
         AND SYSDATE BETWEEN effective_date AND expiry_date
      """;

  /** The same index already on the mark — legacy relied on the unique key for this. */
  private static final String DUPLICATE_SQL =
      """
      SELECT COUNT(*) FROM the.mark_land_index
       WHERE (timber_mark = :timberMark OR certificate = :certificate)
         AND primary_land_index_code = :primary
         AND NVL(secondary_land_index_code, ' ') = NVL(:secondary, ' ')
         AND NVL(mark_land_index_desc, ' ') = NVL(:description, ' ')
      """;

  private static final String INSERT_SQL =
      """
      INSERT INTO the.mark_land_index (
        mark_land_index_skey, certificate, timber_mark, primary_land_index_code,
        secondary_land_index_code, mark_land_index_desc, index_deactivate_date,
        entry_userid, entry_timestamp, update_userid, update_timestamp, revision_count
      ) VALUES (
        the.mark_land_index_seq.NEXTVAL, :certificate, :timberMark, :primary,
        :secondary, :description, NULL,
        :userId, SYSDATE, :userId, SYSDATE, 0
      )
      """;

  /**
   * Adds the land index.
   *
   * @throws ResponseStatusException 404 if the mark does not exist; 409 if land index cannot
   *     be changed at its status or type, or the index is already on it; 400 if a field is
   *     invalid
   */
  @Transactional
  public void add(
      String id, boolean byCertificate, MarkLandIndexRequest request, String userId) {
    MarkDetailDto mark = (byCertificate
            ? markDetailService.findByCertificate(id)
            : markDetailService.findByMarkNumber(id))
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Private mark not found."));
    if (!MarkEditRules.of(mark, true).landIndex()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Land index may not be updated at this mark's status or type.");
    }

    String primary = trim(request.primaryLandIndexCode());
    String secondary = trim(request.secondaryLandIndexCode());
    String description = trim(request.markLandIndexDesc());

    List<String> e = new ArrayList<>();
    if (primary == null) {
      e.add("Land District/Island is required.");
    } else if (!checks.unchangedOrActive(null, primary, PRIMARY_SQL)) {
      e.add("Land District/Island " + primary + " is not a current code.");
    }
    if (secondary != null && !checks.unchangedOrActive(null, secondary, SECONDARY_SQL)) {
      e.add("Primary ID " + secondary + " is not a current code.");
    }
    if (description != null && description.length() > MAX_DESC) {
      e.add("Description can be at most " + MAX_DESC + " characters.");
    }
    if (!e.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", e));
    }

    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("certificate", mark.certificate())
        .addValue("timberMark", mark.timberMark())
        .addValue("primary", primary)
        .addValue("secondary", secondary)
        .addValue("description", description)
        .addValue("userId", userId);

    Long dup = jdbc.queryForObject(DUPLICATE_SQL, p, Long.class);
    if (dup != null && dup > 0) {
      throw alreadyOnMark();
    }
    try {
      jdbc.update(INSERT_SQL, p);
    } catch (DuplicateKeyException ex) {
      // The unique key, should two adds race past the check above.
      throw alreadyOnMark();
    }
  }

  private static ResponseStatusException alreadyOnMark() {
    return new ResponseStatusException(
        HttpStatus.CONFLICT, "This land index is already on the mark.");
  }
}
