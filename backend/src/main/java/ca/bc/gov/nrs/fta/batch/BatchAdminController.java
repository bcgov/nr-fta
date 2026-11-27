package ca.bc.gov.nrs.fta.batch;

import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The batch jobs, for operators — {@code /api/fta/admin/batch} (FTA_ADMIN only, by the generic
 * {@code /api/fta/admin/**} rule in {@code ApiAuthorizationCustomizer}):
 * <ul>
 *   <li>{@code GET /} — every job, with its cron and its last run on this pod</li>
 *   <li>{@code GET /{job}} — one job</li>
 *   <li>{@code POST /{job}/run} — run it now (legacy operators re-ran a job from Automate).
 *       202 with the started run, or 409 when it is already running (its lock is held)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/fta/admin/batch")
public class BatchAdminController {

  static final String ALREADY_RUNNING = "The job is already running.";

  private final Map<String, BatchJob> jobs;
  private final BatchJobRunner runner;
  private final BatchRunHistory history;
  private final Environment env;

  public BatchAdminController(
      List<BatchJob> jobs,
      BatchJobRunner runner,
      BatchRunHistory history,
      Environment env) {
    this.jobs = jobs.stream().collect(Collectors.toMap(
        BatchJob::name, Function.identity(), (a, b) -> a, TreeMap::new));
    this.runner = runner;
    this.history = history;
    this.env = env;
  }

  /** A job, its schedule and its last run on this pod. */
  public record JobView(String name, String legacyName, String cron, BatchRun lastRun) {}

  @GetMapping
  public List<JobView> list() {
    return jobs.values().stream().map(this::view).toList();
  }

  @GetMapping("/{job}")
  public JobView get(@PathVariable String job) {
    return view(find(job));
  }

  @PostMapping("/{job}/run")
  public ResponseEntity<BatchRun> run(@PathVariable String job, JwtAuthenticationToken principal) {
    BatchJob j = find(job);
    String who = principal == null ? "" : JwtPrincipalUtil.getAuditUserId(principal);
    return runner.startManual(j, who)
        .map(r -> ResponseEntity.status(HttpStatus.ACCEPTED).body(r))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, ALREADY_RUNNING));
  }

  private BatchJob find(String name) {
    BatchJob j = jobs.get(name);
    if (j == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No batch job " + name + ".");
    }
    return j;
  }

  private JobView view(BatchJob j) {
    return new JobView(j.name(), j.legacyName(),
        env.getProperty("fta.batch." + j.name() + ".cron", "-"),
        history.last(j.name()).orElse(null));
  }
}
