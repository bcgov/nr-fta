package ca.bc.gov.nrs.fta.shared.dto;

import java.util.List;

/**
 * Application metadata returned to the SPA landing/dashboard. Advertises the
 * business lines this instance serves so the frontend can render navigation
 * without hard-coding it.
 */
public record WelcomeDto(String application, String description, List<BusinessArea> businessAreas) {

  public record BusinessArea(String id, String name, String path) {}
}
