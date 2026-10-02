package ca.bc.gov.nrs.fta.shared.dto;

/**
 * One management unit — a Timber Supply Area, Tree Farm Licence and so on.
 *
 * <p>Richer than {@link CodeOptionDto} because a unit is identified by two
 * values, not one: the search criteria carry the type code and the unit id
 * separately, while the label a user picks from needs the unit's name. Keeping
 * the four fields apart lets the frontend compose the label and submit the
 * codes without parsing a joined string back out.
 *
 * @param mgmtUnitTypeCode the type, e.g. {@code T}; submitted as the search's
 *                         management unit type
 * @param mgmtUnitId       the unit within that type, e.g. {@code 01}
 * @param typeDescription  the type spelled out, e.g. {@code T - Timber Supply Area}
 * @param description      the unit's name, e.g. {@code Arrow TSA}
 */
public record ManagementUnitDto(
    String mgmtUnitTypeCode,
    String mgmtUnitId,
    String typeDescription,
    String description) {}
