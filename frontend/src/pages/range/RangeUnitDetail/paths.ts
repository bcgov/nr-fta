/** The path segment that opens FTA630's Add New instead of a unit (ids are "RU" + a number). */
export const NEW_RANGE_UNIT = 'new';

/** A range unit's page (FTA630), or Add New for {@link NEW_RANGE_UNIT}. */
export const rangeUnitPath = (unitId: string) => `/range-unit/${encodeURIComponent(unitId)}`;
