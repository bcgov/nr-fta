package ca.bc.gov.nrs.fta.tenure.tab.notes;

import ca.bc.gov.nrs.fta.tenure.tab.notes.NotesDtos.NotesListDto;
import ca.bc.gov.nrs.fta.tenure.tab.notes.NotesDtos.NotesNoteDto;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The tenure's forest notes — legacy FTA970 (Forest Notes), the same screen the private-mark
 * Notes tab ports.
 *
 * <p>Ports {@code FTA_970_FOREST_NOTE}: GET lists {@code THE.PROVFOREST_NOTE} for the file,
 * newest first; SAVE (its {@code ADD}) inserts one with the next {@code PROVFOREST_NOTE_SEQ}
 * value. Notes are append-only — legacy has no edit or delete. The form's checks are in
 * {@link NotesRules}.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class NotesService {

  private static final String STATUS_SQL =
      """
      SELECT pfu.file_status_st
        FROM the.prov_forest_use pfu
       WHERE pfu.forest_file_id = :forestFileId
      """;

  private static final String LIST_SQL =
      """
      SELECT pfn.entry_userid, pfn.entry_timestamp, pfn.note
        FROM the.provforest_note pfn
       WHERE pfn.forest_file_id = :forestFileId
       ORDER BY pfn.entry_timestamp DESC, pfn.provforest_note_skey DESC
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

  private final NamedParameterJdbcTemplate jdbc;

  public NotesService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * The file's notes and whether one may be added.
   *
   * @throws ResponseStatusException 404 if the file does not exist
   */
  public NotesListDto list(String forestFileId) {
    NotesRules rules = rules(forestFileId);
    List<NotesNoteDto> notes = jdbc.query(
        LIST_SQL,
        new MapSqlParameterSource("forestFileId", forestFileId),
        (rs, rowNum) -> new NotesNoteDto(
            rs.getString("entry_userid"),
            rs.getObject("entry_timestamp", LocalDateTime.class),
            rs.getString("note")));
    return new NotesListDto(rules.canAdd(), rules.blockedReason(), notes);
  }

  /**
   * Adds a note to the file.
   *
   * @throws ResponseStatusException 400 if the note is blank or too long, 404 if the file does
   *     not exist, 409 if its status is PE
   */
  @Transactional
  public void add(String forestFileId, String note, String userId) {
    List<String> errors = NotesRules.validate(note);
    if (!errors.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", errors));
    }
    NotesRules rules = rules(forestFileId);
    if (!rules.canAdd()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, rules.blockedReason());
    }
    jdbc.update(INSERT_SQL, new MapSqlParameterSource()
        .addValue("forestFileId", forestFileId)
        .addValue("userId", userId)
        .addValue("note", note.trim()));
  }

  private NotesRules rules(String forestFileId) {
    try {
      String status = jdbc.queryForObject(
          STATUS_SQL, new MapSqlParameterSource("forestFileId", forestFileId), String.class);
      return NotesRules.forStatus(status);
    } catch (EmptyResultDataAccessException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenure not found.");
    }
  }
}
