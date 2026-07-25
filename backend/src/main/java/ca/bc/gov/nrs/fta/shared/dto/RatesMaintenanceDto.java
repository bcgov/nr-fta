package ca.bc.gov.nrs.fta.shared.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single standard Range Billing rate/fee row.
 *
 * <p>Mirrors the {@code RANGE_BILL_RATE} row selected by the {@code c_rate}
 * cursor in the legacy Oracle package {@code THE.FTA_699_RATEFEE} (see
 * {@code fta-archive/fta/database/ddl/pkg/FTA_699_RATEFEE.PKS}). Though the
 * legacy screen surfaces 12 distinct rate types, the underlying structure is 24
 * database rows per calendar year (keyed by range file type, range rate type
 * and revenue classification); each such row maps to one instance of this DTO.
 */
public record RatesMaintenanceDto(
    Long rangeBillRateId,
    Integer calendarYear,
    LocalDate updateTimestamp,
    String rangeFileTypeCode,
    String rangeRateTypeCode,
    String revenueClassnCode,
    BigDecimal rangeRate,
    String updateUserid,
    String rngTenrRateDesc) {}
