package ca.bc.gov.nrs.fta.tenure.controller;

import ca.bc.gov.nrs.fta.range.service.RangeFileAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UriUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Closes the timber tenure screens' writes to range files.
 *
 * <p>Legacy FTA100 never showed a range file: it redirected every one to FTA100R, so the timber
 * tabs' saves (FTA100 details, retire, delete and mark designate; 920 associated clients; AAC,
 * sale info, TL blocks, cutting permits and cut blocks) and their PL/SQL never ran against one,
 * and those packages skip the range rules. Here a request still names a file id, so any write
 * under {@code /api/fta/tenures/{forestFileId}/...} on a range file is refused with 400.
 *
 * <p>The tabs legacy's range tab set shared with timber stay open: 910 associated files, 950x
 * tenure application and 970 notes ({@link #SHARED_WITH_RANGE}).
 */
@Component
public class RangeFileWriteGuard implements HandlerInterceptor {

  static final String RANGE_FILE_REFUSED =
      "%s is a range tenure: change it from its range tenure page.";

  private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

  private static final PathPatternParser PARSER = PathPatternParser.defaultInstance;

  /** Every write naming a tenure: the bare file and anything under it. */
  private static final PathPattern TENURE_WRITE = PARSER.parse("/api/fta/tenures/{forestFileId}/**");

  /** The tabs legacy's FtaRangeTabs reused from the timber set: 910, 950x and 970. */
  static final List<PathPattern> SHARED_WITH_RANGE = List.of(
      PARSER.parse("/api/fta/tenures/*/associated-files/**"),
      PARSER.parse("/api/fta/tenures/*/tenure-applications/**"),
      PARSER.parse("/api/fta/tenures/*/notes/**"));

  private final RangeFileAccess rangeFiles;

  public RangeFileWriteGuard(RangeFileAccess rangeFiles) {
    this.rangeFiles = rangeFiles;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    Optional<String> fileId = guardedFileId(request.getMethod(), path);
    if (fileId.isPresent() && rangeFiles.isRangeFile(fileId.get())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, RANGE_FILE_REFUSED.formatted(fileId.get()));
    }
    return true;
  }

  /**
   * The file id a write would change, when it is a timber-only tenure write; empty for a read,
   * a path outside {@code /api/fta/tenures/{id}}, or a tab shared with range.
   */
  static Optional<String> guardedFileId(String method, String path) {
    if (method == null || !WRITE_METHODS.contains(method.toUpperCase())) {
      return Optional.empty();
    }
    PathContainer container = PathContainer.parsePath(path);
    PathPattern.PathMatchInfo match = TENURE_WRITE.matchAndExtract(container);
    if (match == null || SHARED_WITH_RANGE.stream().anyMatch(p -> p.matches(container))) {
      return Optional.empty();
    }
    String id = match.getUriVariables().get("forestFileId");
    return id == null || id.isBlank()
        ? Optional.empty()
        : Optional.of(UriUtils.decode(id, StandardCharsets.UTF_8));
  }
}
