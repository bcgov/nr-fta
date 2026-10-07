package ca.bc.gov.nrs.fta.tenure.tab.notes;

import java.time.LocalDateTime;
import java.util.List;

/** Request and response shapes of the tenure Notes tab (legacy FTA970). */
public final class NotesDtos {

  private NotesDtos() {}

  /**
   * One forest note ({@code THE.PROVFOREST_NOTE}).
   *
   * @param entryUserid     who entered it
   * @param entryTimestamp  when (legacy shows {@code YYYY-MM-DD HH12:MI AM})
   * @param note            the text
   */
  public record NotesNoteDto(String entryUserid, LocalDateTime entryTimestamp, String note) {}

  /**
   * {@code GET /api/fta/tenures/{forestFileId}/notes} — the file's notes, newest first, and
   * whether one may be added.
   */
  public record NotesListDto(boolean canAdd, String addBlockedReason, List<NotesNoteDto> notes) {}

  /** {@code POST /api/fta/tenures/{forestFileId}/notes} body. */
  public record NotesAddRequest(String note) {}
}
