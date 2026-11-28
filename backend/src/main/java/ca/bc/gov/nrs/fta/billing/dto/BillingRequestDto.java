package ca.bc.gov.nrs.fta.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One FTA690 billing request ({@code FTA_RANGE_BILL_REQUEST}) and its outcome.
 *
 * @param status     {@code QUEUED} (not started), {@code PROCESSING} (started, not finished),
 *                   {@code COMPLETE} (invoiced) or {@code FAILED} (finished with a message — the
 *                   package rolls the invoices back)
 */
public record BillingRequestDto(
    long rangeBillRequestId,
    Long orgUnitNo,
    String orgUnitCode,
    String orgUnitName,
    Integer calendarYear,
    String submitUserid,
    LocalDate submitDate,
    LocalDateTime startDate,
    LocalDateTime endDate,
    String status,
    String messageText,
    Integer totalInvoices,
    String firstInvoiceNumber,
    String lastInvoiceNumber,
    BigDecimal totalInvoiceValue) {

  public static final String QUEUED = "QUEUED";
  public static final String PROCESSING = "PROCESSING";
  public static final String COMPLETE = "COMPLETE";
  public static final String FAILED = "FAILED";
}
