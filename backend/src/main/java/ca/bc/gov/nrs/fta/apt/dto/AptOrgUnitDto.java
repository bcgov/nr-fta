package ca.bc.gov.nrs.fta.apt.dto;

/**
 * A region or district from {@code APT_ORG_UNITS_GET}: the org units a management unit can
 * belong to, and the org-unit parameter of the APT reports.
 *
 * @param orgUnitNo   the key the APT tables and reports store
 * @param code        {@code ORG_UNIT_CODE}, e.g. {@code DCC} or {@code RSI}
 * @param name        {@code ORG_UNIT_NAME}
 * @param displayName {@code CODE - NAME}, as APT2 showed it
 */
public record AptOrgUnitDto(Integer orgUnitNo, String code, String name, String displayName) {}
