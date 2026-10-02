package ca.bc.gov.nrs.fta.mark.dto;

import java.time.LocalDate;

/**
 * Request body for adding an associated client to a private mark ({@code POST
 * /api/fta/marks/{id}/clients}) — the FTA513 add row.
 *
 * @param clientNumber     the client; required
 * @param clientLocnCode   its location; required
 * @param fileClientType   FILE_CLIENT_TYPE_CODE (A main licensee, B licensee, C previous
 *                         licensee, …); required
 * @param licenseeStartDate required for A, B and C
 * @param licenseeEndDate  required for C and P; must be blank for A and B
 */
public record MarkClientRequest(
    String clientNumber,
    String clientLocnCode,
    String fileClientType,
    LocalDate licenseeStartDate,
    LocalDate licenseeEndDate) {}
