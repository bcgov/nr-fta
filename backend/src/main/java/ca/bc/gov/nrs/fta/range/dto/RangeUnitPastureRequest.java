package ca.bc.gov.nrs.fta.range.dto;

/**
 * FTA630's pasture Save: a pasture to add ({@code POST}) or update ({@code PUT}).
 *
 * @param pastureId            the pasture id; on an update the path's id is used
 * @param pastureName          the Pasture Description (INITCAP'd by the package)
 * @param pastureRevisionCount the pasture's revision count as read; required on an update
 * @param revisionCount        the range unit's revision count as read, which updating pasture
 *                             '00' checks as it renames the unit
 */
public record RangeUnitPastureRequest(
    String pastureId,
    String pastureName,
    Integer pastureRevisionCount,
    Integer revisionCount) {}
