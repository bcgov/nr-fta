package ca.bc.gov.nrs.fta.mark.dto;

import java.time.LocalDate;

/**
 * Request body for updating an associated client already on a private mark ({@code PUT
 * /api/fta/marks/{id}/clients/{skey}}) — FTA513's save of an existing row.
 *
 * @param clientNumber      the client; required, and fixed for a Main or Previous Licensee
 * @param clientLocnCode    its location; required
 * @param fileClientType    FILE_CLIENT_TYPE_CODE; required, and fixed for A and C
 * @param licenseeStartDate required for A, B and C
 * @param licenseeEndDate   required for C and P; must be blank for A and B
 * @param revisionCount     the row's revision count as read, for optimistic locking
 */
public record MarkClientUpdateRequest(
    String clientNumber,
    String clientLocnCode,
    String fileClientType,
    LocalDate licenseeStartDate,
    LocalDate licenseeEndDate,
    Integer revisionCount) {}
