package ca.bc.gov.nrs.fta.shared.dto;

/**
 * Request body for the Org Unit Maintenance screen ({@code POST
 * /api/fta/admin/org-unit-default}). Sets the authenticated user's default org
 * unit.
 *
 * <p>The legacy {@code PKG_SIL_CODE_LISTS} package exposes only read cursors
 * (e.g. {@code GET_ORG_UNIT}) over {@code THE.ORG_UNIT}; there is no write proc
 * in the spec/body, so the persisted default is keyed by the audit user id and
 * resolves against {@code THE.ORG_UNIT} named in the spec. {@code orgUnitCode}
 * carries the value chosen in the form.
 */
public record OrgUnitMaintRequest(String orgUnitCode) {}
