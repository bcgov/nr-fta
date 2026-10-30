package ca.bc.gov.nrs.fta.tenure.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Request body for suspending cut blocks within a cutting permit ({@code POST
 * /api/fta/cutting-permits/{cpId}/suspend-blocks}), legacy FTA912. The cutting permit id comes
 * from the path, and the tenure from here because the id is unique only within one. When
 * {@code suspendAllBlocks} is set every eligible block is suspended; otherwise {@code cbSkeys}
 * names them.
 */
public record SuspendBlocksRequest(
    String forestFileId,
    List<Long> cbSkeys,
    boolean suspendAllBlocks,
    String partitionCode,
    String suspOrderNumber,
    LocalDate suspStartDate,
    LocalDate suspEndDate) {}
