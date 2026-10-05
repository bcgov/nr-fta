package ca.bc.gov.nrs.fta.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/fta/users/resolve} — display names for the user ids a screen shows, in one
 * batch (the frontend's {@code <UserName>} collects every id on screen into one request). A
 * POST only so a table's worth of ids fits in the body; it changes nothing, and every role
 * that reads FTA may call it.
 */
@RestController
@RequestMapping("/api/fta/users")
public class UserController {

  private final UserDirectoryService directory;

  public UserController(UserDirectoryService directory) {
    this.directory = directory;
  }

  @PostMapping("/resolve")
  public Map<String, String> resolve(@RequestBody UserResolveRequest request) {
    return directory.resolveDisplayNames(request == null ? null : request.userIds());
  }

  /** The ids to resolve, as the records hold them ({@code IDIR\JSMITH}). */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record UserResolveRequest(List<String> userIds) {}
}
