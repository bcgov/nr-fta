package ca.bc.gov.nrs.fta.mark.service;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Adds a note to a private mark — the Notes tab of the legacy private-mark tab
 * set, which is the shared FTA970 forest-notes screen.
 *
 * <p>Ports {@code FTA_970_FOREST_NOTE.ADD}: an INSERT into {@code
 * THE.PROVFOREST_NOTE} against the mark's forest file. Notes belong to the
 * forest file, not the mark, so an application that has not been given a
 * forest file cannot take one — legacy disables Save in that case, and this
 * rejects it with 409. The legacy form's validation is kept: the note is
 * required, trimmed, and at most 4000 characters.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local
 * database, so it is exercised only in a deployed environment.
 */
@Service
public class MarkNoteWriteService {

  /** The legacy form's limit ({@code Fta970ForestNotesForm}, StringLengthValidator 4000). */
  static final int MAX_NOTE_LENGTH = 4000;

  private final NamedParameterJdbcTemplate jdbc;

  public MarkNoteWriteService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String FOREST_FILE_SQL =
      """
      SELECT pmc.forest_file_id
        FROM the.private_mark_certificate pmc
       WHERE (:byCertificate = 'N' AND pmc.timber_mark = :id)
          OR (:byCertificate = 'Y' AND pmc.certificate = :id)
       FETCH FIRST 1 ROW ONLY
      """;

  private static final String INSERT_SQL =
      """
      INSERT INTO the.provforest_note (
        provforest_note_skey, forest_file_id, entry_userid, entry_timestamp,
        note, revision_count, update_userid, update_timestamp
      ) VALUES (
        the.provforest_note_seq.NEXTVAL, :forestFileId, :userId, SYSDATE,
        :note, 1, :userId, SYSDATE
      )
      """;

  /**
   * Adds a note to the mark's forest file.
   *
   * @param id            the timber mark, or the certificate when {@code byCertificate}
   * @param byCertificate whether {@code id} is a certificate
   * @param note          the note text
   * @param userId        the authenticated user id (audit columns)
   * @return the forest file the note was added to
   * @throws ResponseStatusException 400 if the note is blank or too long, 404 if the mark
   *     does not exist, 409 if it has no forest file yet
   */
  @Transactional
  public String add(String id, boolean byCertificate, String note, String userId) {
    String text = note == null ? "" : note.trim();
    if (text.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A note is required.");
    }
    if (text.length() > MAX_NOTE_LENGTH) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "A note can be at most " + MAX_NOTE_LENGTH + " characters.");
    }

    String forestFileId;
    try {
      forestFileId = jdbc.queryForObject(
          FOREST_FILE_SQL,
          new MapSqlParameterSource()
              .addValue("id", id)
              .addValue("byCertificate", byCertificate ? "Y" : "N"),
          String.class);
    } catch (EmptyResultDataAccessException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Private mark not found.");
    }
    if (forestFileId == null) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "This application has no forest file yet, so it cannot take notes.");
    }

    jdbc.update(INSERT_SQL, new MapSqlParameterSource()
        .addValue("forestFileId", forestFileId)
        .addValue("userId", userId)
        .addValue("note", text));
    return forestFileId;
  }
}
