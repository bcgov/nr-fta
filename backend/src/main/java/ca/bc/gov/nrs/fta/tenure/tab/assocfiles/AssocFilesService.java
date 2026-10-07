package ca.bc.gov.nrs.fta.tenure.tab.assocfiles;

import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import ca.bc.gov.nrs.fta.tenure.tab.assocfiles.AssocFilesDtos.AssocFileAddRequest;
import ca.bc.gov.nrs.fta.tenure.tab.assocfiles.AssocFilesDtos.AssocFileRowDto;
import ca.bc.gov.nrs.fta.tenure.tab.assocfiles.AssocFilesDtos.AssocFilesListDto;
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
 * The tenure's associated files — legacy FTA910 (Associated Files).
 *
 * <p>Ports {@code FTA_910_ASSOC_FILE}: GET lists {@code THE.ASSOCIATED_USE} for the file, and
 * cross-populates the FTAS (source F) associations other files made to it, dropping one that
 * duplicates an association in the other direction. SAVE inserts (legacy never updates an
 * association: its three key fields are the key, so a change is a new row); REMOVE deletes, and
 * for source F deletes both directions. The form's checks are in {@link AssocFilesRules}; the
 * package's own checks (the associated FTA file exists, no FTAS association between the two
 * files already, an AAC end date inside the associated file's term too) are here, as is its
 * AAC file-type warning.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class AssocFilesService {

  private static final String TENURE_SQL =
      """
      SELECT pfu.file_status_st,
             tt.legal_effective_dt,
             NVL(tt.current_expiry_dt, tt.initial_expiry_dt) AS expiry_dt
        FROM the.prov_forest_use pfu
        LEFT JOIN the.tenure_term tt ON tt.forest_file_id = pfu.forest_file_id
       WHERE pfu.forest_file_id = :forestFileId
      """;

  /**
   * {@code FTA_910_ASSOC_FILE.GET}: the file's associations, then the F-source associations made
   * to it from a file it is not itself associated with (seen from this side: the other file is
   * the associated one).
   */
  private static final String LIST_SQL =
      """
      SELECT a.*
        FROM (
      SELECT au.associated_file_id                           AS associated_file_id,
             au.file_source_code                             AS file_source_code,
             au.file_source_code || ' - ' || fsc.description AS source_desc,
             au.file_association_type_code                   AS type_code,
             CASE WHEN au.file_association_type_code IS NOT NULL
                  THEN au.file_association_type_code || ' - ' || atc.description
             END                                             AS type_desc,
             au.association_end_date                         AS association_end_date,
             au.revision_count                               AS revision_count
        FROM the.associated_use au
        JOIN the.file_source_code fsc ON fsc.file_source_code = au.file_source_code
        LEFT JOIN the.file_association_type_code atc
               ON atc.file_association_type_code = au.file_association_type_code
       WHERE au.forest_file_id = :forestFileId
      UNION
      SELECT au.forest_file_id                               AS associated_file_id,
             au.file_source_code                             AS file_source_code,
             au.file_source_code || ' - ' || fsc.description AS source_desc,
             au.file_association_type_code                   AS type_code,
             CASE WHEN au.file_association_type_code IS NOT NULL
                  THEN au.file_association_type_code || ' - ' || atc.description
             END                                             AS type_desc,
             au.association_end_date                         AS association_end_date,
             au.revision_count                               AS revision_count
        FROM the.associated_use au
        JOIN the.file_source_code fsc ON fsc.file_source_code = au.file_source_code
        LEFT JOIN the.file_association_type_code atc
               ON atc.file_association_type_code = au.file_association_type_code
       WHERE au.associated_file_id = :forestFileId
         AND au.file_source_code = 'F'
         AND NOT EXISTS (SELECT 1 FROM the.associated_use own
                          WHERE own.forest_file_id = :forestFileId
                            AND own.associated_file_id = au.forest_file_id)
             ) a
       ORDER BY a.associated_file_id, a.file_source_code
      """;

  private static final String SOURCE_CODE_SQL =
      """
      SELECT CASE WHEN SYSDATE BETWEEN effective_date AND expiry_date THEN 'Y' ELSE 'N' END
        FROM the.file_source_code WHERE file_source_code = :code
      """;

  private static final String TYPE_CODE_SQL =
      """
      SELECT CASE WHEN SYSDATE BETWEEN effective_date AND expiry_date THEN 'Y' ELSE 'N' END
        FROM the.file_association_type_code WHERE file_association_type_code = :code
      """;

  private static final String TYPES_SQL =
      """
      SELECT file_association_type_code AS code,
             file_association_type_code || ' - ' || description AS description
        FROM the.file_association_type_code
       WHERE SYSDATE BETWEEN effective_date AND expiry_date
       ORDER BY file_association_type_code
      """;

  private static final String FILE_EXISTS_SQL =
      "SELECT COUNT(*) FROM the.prov_forest_use WHERE forest_file_id = :associatedFileId";

  /** {@code ADD}'s existing_assoc_f_combo: an FTAS association either way. */
  private static final String F_COMBO_SQL =
      """
      SELECT COUNT(*) FROM the.associated_use au
       WHERE ((au.forest_file_id = :forestFileId AND au.associated_file_id = :associatedFileId)
           OR (au.forest_file_id = :associatedFileId AND au.associated_file_id = :forestFileId))
         AND au.file_source_code = 'F'
      """;

  /** {@code ADD}: the end date falls in the associated file's term. */
  private static final String END_IN_ASSOCIATED_TERM_SQL =
      """
      SELECT COUNT(*) FROM the.tenure_term
       WHERE forest_file_id = :associatedFileId
         AND :endDate BETWEEN legal_effective_dt AND NVL(current_expiry_dt, initial_expiry_dt)
      """;

  private static final String INSERT_SQL =
      """
      INSERT INTO the.associated_use (
        forest_file_id, file_source_code, file_association_type_code, association_end_date,
        associated_file_id, revision_count, entry_userid, entry_timestamp, update_userid,
        update_timestamp
      ) VALUES (
        :forestFileId, :source, :type, :endDate,
        :associatedFileId, 1, :userId, SYSDATE, :userId,
        SYSDATE
      )
      """;

  /** {@code ADD}'s warning: an AAC association where either file is A11, B05, B06 or BCTS. */
  private static final String AAC_FILE_TYPE_SQL =
      """
      SELECT COUNT(*) FROM the.prov_forest_use
       WHERE forest_file_id IN (:forestFileId, :associatedFileId)
         AND (file_type_code IN ('A11', 'B05', 'B06')
              OR file_type_code IN (SELECT bcts_file_type_code FROM the.bcts_file_type_code))
      """;

  /** {@code REMOVE} for source F: both directions, whatever the revision. */
  private static final String DELETE_F_SQL =
      """
      DELETE FROM the.associated_use
       WHERE ((forest_file_id = :forestFileId AND associated_file_id = :associatedFileId)
           OR (forest_file_id = :associatedFileId AND associated_file_id = :forestFileId))
         AND file_source_code = 'F'
      """;

  /** {@code REMOVE} otherwise: the one row, at its revision. */
  private static final String DELETE_SQL =
      """
      DELETE FROM the.associated_use
       WHERE forest_file_id = :forestFileId
         AND associated_file_id = :associatedFileId
         AND file_source_code = :source
         AND revision_count = :revisionCount
      """;

  /** {@code fta.web.warning.usr.fta910.associatedFileType}. */
  static final String AAC_WARNING =
      "The file type selected for AAC association is not an AAC association type.";

  static final String MODIFIED =
      "The association has been changed or removed by another user. Refresh the tab and try"
          + " again.";

  private final NamedParameterJdbcTemplate jdbc;

  public AssocFilesService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private record Tenure(String status, LocalDate awardDate, LocalDate expiryDate) {}

  private Tenure tenure(String forestFileId) {
    try {
      return jdbc.queryForObject(
          TENURE_SQL,
          new MapSqlParameterSource("forestFileId", forestFileId),
          (rs, n) -> new Tenure(
              rs.getString("file_status_st"),
              rs.getObject("legal_effective_dt", LocalDate.class),
              rs.getObject("expiry_dt", LocalDate.class)));
    } catch (EmptyResultDataAccessException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenure not found.");
    }
  }

  /** The tab. 404 when the tenure does not exist. */
  public AssocFilesListDto list(String forestFileId) {
    AssocFilesRules rules = AssocFilesRules.forStatus(tenure(forestFileId).status());
    List<AssocFileRowDto> rows = jdbc.query(
        LIST_SQL,
        new MapSqlParameterSource("forestFileId", forestFileId),
        (rs, n) -> new AssocFileRowDto(
            rs.getString("associated_file_id"),
            rs.getString("file_source_code"),
            rs.getString("source_desc"),
            rs.getString("type_code"),
            rs.getString("type_desc"),
            rs.getObject("association_end_date", LocalDate.class),
            rs.getObject("revision_count", Long.class),
            AssocFilesRules.SOURCE_FTAS.equals(rs.getString("file_source_code"))));
    return new AssocFilesListDto(rows, rules.canAdd(), rules.addReason());
  }

  /** The current association types, for the add dialog. */
  public List<CodeOptionDto> associationTypes() {
    return jdbc.query(
        TYPES_SQL,
        (rs, n) -> new CodeOptionDto(rs.getString("code"), rs.getString("description")));
  }

  /**
   * Adds an association. Returns legacy's AAC file-type warning when it applies, else null.
   *
   * @throws ResponseStatusException 404 if the tenure does not exist; 409 if it cannot be
   *     changed at its status, or the association exists; 400 if a field is invalid
   */
  @Transactional
  public String add(String forestFileId, AssocFileAddRequest q, String userId) {
    Tenure t = tenure(forestFileId);
    AssocFilesRules rules = AssocFilesRules.forStatus(t.status());
    if (!rules.canAdd()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, rules.addReason());
    }
    String associated = upper(q == null ? null : q.associatedFileId());
    String source = upper(q == null ? null : q.fileSourceCode());
    String type = upper(q == null ? null : q.fileAssociationTypeCode());
    LocalDate end = q == null ? null : q.associationEndDate();

    List<String> e = AssocFilesRules.validate(
        forestFileId, associated, source, type, end, t.awardDate(), t.expiryDate());
    // CodeExpiryValidator on Source and Association Type.
    codeProblem(SOURCE_CODE_SQL, "Source", source, e);
    codeProblem(TYPE_CODE_SQL, "Association Type", type, e);
    if (!e.isEmpty()) {
      throw bad(e);
    }

    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("forestFileId", forestFileId)
        .addValue("associatedFileId", associated)
        .addValue("source", source)
        .addValue("type", type, Types.VARCHAR)
        .addValue("endDate", end, Types.DATE)
        .addValue("userId", userId);

    // FTA_910_ASSOC_FILE.ADD's own checks.
    if (AssocFilesRules.SOURCE_FTAS.equals(source)) {
      if (count(FILE_EXISTS_SQL, p) == 0) {
        throw bad(List.of("The Associated File selected is not a valid FTA Forest File ID."));
      }
      if (count(F_COMBO_SQL, p) != 0) {
        throw bad(List.of("Files " + forestFileId + " and " + associated
            + " have already been associated with each other via FTAS source."));
      }
    }
    if (end != null && count(END_IN_ASSOCIATED_TERM_SQL, p) == 0) {
      throw bad(List.of(AssocFilesRules.END_DATE_RANGE));
    }

    try {
      jdbc.update(INSERT_SQL, p);
    } catch (DuplicateKeyException ex) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "File " + associated + " is already associated with this tenure from that source.");
    }

    if (AssocFilesRules.TYPE_AAC.equals(type) && count(AAC_FILE_TYPE_SQL, p) > 0) {
      return AAC_WARNING;
    }
    return null;
  }

  /**
   * Deletes an association — for source F, in both directions (legacy's REMOVE).
   *
   * @throws ResponseStatusException 404 if the tenure does not exist; 400 without a source;
   *     409 if nothing was deleted (changed or removed by someone else)
   */
  @Transactional
  public void delete(
      String forestFileId, String associatedFileId, String fileSourceCode, Long revisionCount) {
    tenure(forestFileId);
    String associated = upper(associatedFileId);
    String source = upper(fileSourceCode);
    if (associated == null || source == null) {
      throw bad(List.of("Associated File and Source are mandatory."));
    }
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("forestFileId", forestFileId)
        .addValue("associatedFileId", associated)
        .addValue("source", source)
        .addValue("revisionCount", revisionCount, Types.NUMERIC);
    boolean ftas = AssocFilesRules.SOURCE_FTAS.equals(source);
    if (!ftas && revisionCount == null) {
      throw bad(List.of("The association's revision count is required."));
    }
    int n = jdbc.update(ftas ? DELETE_F_SQL : DELETE_SQL, p);
    if (n == 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, MODIFIED);
    }
  }

  /** Adds "Invalid X." / "X (code) is an expired code." for a code that is not current. */
  private void codeProblem(String sql, String label, String code, List<String> e) {
    if (code == null) {
      return;
    }
    List<String> current = jdbc.queryForList(
        sql, new MapSqlParameterSource("code", code), String.class);
    if (current.isEmpty()) {
      e.add("Invalid " + label + ".");
    } else if (!"Y".equals(current.get(0))) {
      e.add(label + " (" + code + ") is an expired code.");
    }
  }

  private long count(String sql, MapSqlParameterSource p) {
    Long n = jdbc.queryForObject(sql, p, Long.class);
    return n == null ? 0 : n;
  }

  private static ResponseStatusException bad(List<String> errors) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", errors));
  }

  private static String upper(String v) {
    if (v == null || v.isBlank()) {
      return null;
    }
    return v.trim().toUpperCase(Locale.ROOT);
  }
}
