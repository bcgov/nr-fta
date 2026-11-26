package ca.bc.gov.nrs.fta.tenure.tab.details;

import ca.bc.gov.nrs.fta.tenure.tab.details.ChangeMarkDesignateRules.Outcome;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * FTA101 Change Mark Designate, which FTA100's button opened: pick a Source File, Go shows its
 * designate, Save Designate copies it onto this file — "for reuse of older mark designates"
 * ({@code Fta101ChangeMarkDesigAction}). Both steps run
 * {@code THE.FTA_101_CHANGE_MARK_DESIG.mainline} (see {@link ChangeMarkDesignatePackage}).
 *
 * <p>Save re-runs GET for the source, so the designate written is the source's own rather than
 * one the browser sends (legacy's session form held the designate GET had shown).
 */
@Service
public class ChangeMarkDesignateService {

  /** What FTA101 shows: the source file and its designate, and the lock for Save. */
  public record MarkDesignate(
      String forestFileId, String sourceFileId, String markDesignate, String revisionCount) {}

  private final ChangeMarkDesignatePackage pkg;
  private final TenureDetailsService details;

  public ChangeMarkDesignateService(ChangeMarkDesignatePackage pkg,
      TenureDetailsService details) {
    this.pkg = pkg;
    this.details = details;
  }

  /**
   * GET (FTA101's Go): the source's designate, and this file's revision count.
   *
   * @param sourceFileId blank for this file itself (what FTA101 opened on)
   * @throws ResponseStatusException 400 with the package's message ("Invalid file ID")
   */
  public MarkDesignate get(String forestFileId, String sourceFileId) {
    String source = ChangeMarkDesignateRules.sourceFileId(sourceFileId, forestFileId);
    Map<String, String> out = run("GET", forestFileId, source, null, null, null);
    Outcome outcome = ChangeMarkDesignateRules.outcome(out.get("p_error_message"));
    if (outcome.failed()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          String.join(" ", outcome.errors()));
    }
    return new MarkDesignate(forestFileId, source, out.get("p_mark_designate"),
        out.get("p_revision_count"));
  }

  /**
   * SAVE (FTA101's Save Designate): copies the source's designate onto this file.
   *
   * @param revisionCount this file's TIMBER_TENURE revision count, as GET returned it
   * @throws ResponseStatusException 409 when FTA100 does not offer the button for this file or it
   *     was changed since it was read; 400 with the package's messages
   */
  @Transactional
  public void save(String forestFileId, String sourceFileId, String revisionCount,
      String userId) {
    if (!details.get(forestFileId).rules().changesMarkDesignate()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "The mark designate of this file cannot be changed.");
    }
    MarkDesignate shown = get(forestFileId, sourceFileId);
    Map<String, String> out = run("SAVE", forestFileId, shown.sourceFileId(),
        shown.markDesignate(), userId, revisionCount);
    Outcome outcome = ChangeMarkDesignateRules.outcome(out.get("p_error_message"));
    if (outcome.modified()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          ChangeMarkDesignateRules.MODIFIED_TEXT);
    }
    if (outcome.failed()) {
      // Throwing rolls back whatever the package wrote.
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          String.join(" ", outcome.errors()));
    }
  }

  private Map<String, String> run(String action, String forestFileId, String source,
      String designate, String userId, String revisionCount) {
    Map<String, String> in = new HashMap<>();
    in.put("p_action", action);
    in.put("p_forest_file_id", forestFileId);
    in.put("p_forest_file_id_src", source);
    in.put("p_mark_designate", designate);
    in.put("p_update_userid", userId);
    in.put("p_revision_count", revisionCount);
    in.put("p_error_message", "");
    return pkg.call(in);
  }
}
