package ca.bc.gov.nrs.fta.tenure.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Request body for suspending cut blocks within a cutting permit ({@code POST
 * /api/fta/cutting-permits/{cpId}/suspend-blocks}). The cutting permit id comes
 * from the path, not the body.
 *
 * <p>Fields mirror the input parameters of the legacy {@code
 * THE.FTA_912_SUSPEND_PERMIT} package (the {@code ADD} / {@code SUSPEND_ALL}
 * paths driven by {@code MAINLINE} with {@code P_ACTION = 'SUSPEND'}). When
 * {@code suspendAllBlocks} is set the legacy {@code SUSPEND_ALL} proc suspends
 * every eligible block on the permit; otherwise {@code cbSkeys} names the
 * specific blocks to suspend via the {@code ADD} proc.
 */
public record SuspendBlocksRequest(
    String forestFileId,
    List<String> cbSkeys,
    boolean suspendAllBlocks,
    String partitionCode,
    String suspOrderNumber,
    LocalDate suspStartDate,
    LocalDate suspEndDate,
    String reason) {}
