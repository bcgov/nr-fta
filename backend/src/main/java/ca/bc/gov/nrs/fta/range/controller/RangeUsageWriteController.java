package ca.bc.gov.nrs.fta.range.controller;

import ca.bc.gov.nrs.fta.range.dto.RangeUsageDtos.CopyRequest;
import ca.bc.gov.nrs.fta.range.dto.RangeUsageDtos.Options;
import ca.bc.gov.nrs.fta.range.dto.RangeUsageDtos.Result;
import ca.bc.gov.nrs.fta.range.dto.RangeUsageDtos.SaveRequest;
import ca.bc.gov.nrs.fta.range.service.RangeUsageWriteService;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FTA613R Range Usage editing on the range tenure page.
 *
 * <ul>
 *   <li>{@code GET .../usage/options} — the term's years, the reason codes, and whether an
 *       application is in the Inbox;
 *   <li>{@code PUT .../usage/{year}} — Save: add the year or update it;
 *   <li>{@code POST .../usage/{year}/copy} — Copy the year's usage to other years.
 * </ul>
 *
 * <p>The writes are open to FTA_ADMIN and both range roles by the URL rules, and limited to the
 * files the user may edit by the service.
 */
@RestController
@RequestMapping("/api/fta/range-tenures/{forestFileId}/usage")
public class RangeUsageWriteController {

  private final RangeUsageWriteService service;

  public RangeUsageWriteController(RangeUsageWriteService service) {
    this.service = service;
  }

  @GetMapping("/options")
  public Options options(@PathVariable String forestFileId) {
    return service.options(forestFileId);
  }

  @PutMapping("/{calendarYear}")
  public Result save(
      @PathVariable String forestFileId,
      @PathVariable int calendarYear,
      @RequestBody SaveRequest request,
      JwtAuthenticationToken principal) {
    return service.save(
        forestFileId, calendarYear, request, JwtPrincipalUtil.getAuditUserId(principal));
  }

  @PostMapping("/{calendarYear}/copy")
  public Result copy(
      @PathVariable String forestFileId,
      @PathVariable int calendarYear,
      @RequestBody CopyRequest request,
      JwtAuthenticationToken principal) {
    return service.copy(
        forestFileId, calendarYear, request, JwtPrincipalUtil.getAuditUserId(principal));
  }
}
