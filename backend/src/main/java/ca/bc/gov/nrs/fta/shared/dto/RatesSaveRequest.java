package ca.bc.gov.nrs.fta.shared.dto;

import java.util.Map;

/**
 * Request body for saving one calendar year of standard Range Billing rates and fees ({@code PUT
 * /api/fta/admin/rates}). Mirrors the {@code SAVE} inputs of {@code THE.FTA_699_RATEFEE}: the 12
 * values, which the server writes to all 24 of the year's {@code RANGE_BILL_RATE} rows.
 *
 * @param calendarYear    the year being saved; required
 * @param updateTimestamp the update_timestamp the year was read with, or null for a year not yet
 *                        entered (the 24 rows are then inserted)
 * @param rates           the 12 values as typed, keyed by rate type code (GGR ... NHI); text, so
 *                        the server can report a blank or malformed value as legacy did
 */
public record RatesSaveRequest(
    Integer calendarYear,
    String updateTimestamp,
    Map<String, String> rates) {}
