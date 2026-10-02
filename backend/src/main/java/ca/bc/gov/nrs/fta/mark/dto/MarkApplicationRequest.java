package ca.bc.gov.nrs.fta.mark.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for a new private mark application ({@code POST /api/fta/marks}) — the
 * FTA510 "Add New" form. The fields are the ones legacy's {@code ADD_NEW} stores
 * ({@code create_certificate} and, given a client, {@code CREATE_PRIVATE_MARK_CLIENT}).
 *
 * <p>Marking Requirements and Marking Instrument are not here: legacy's form asked for them
 * but {@code create_certificate} never stores them — they belong to the hauling authority,
 * created when the mark is issued.
 */
public record MarkApplicationRequest(
    LocalDate applicationDate,
    /** Initial term in months: 6, 12, 24, 36, 48 or 60. */
    Integer tenureTerm,
    /** ORG_UNIT_NO of the district. */
    String forestDistrict,
    String permitBlockLocn,
    /** Legacy "Legal"; stored upper-cased, as legacy does. */
    String proofOfCrownOrLegal,
    /** Legacy "LTO PID" — stored as BCAA_FOLIO_NUMBER. */
    String bcaaFolioNumber,
    BigDecimal permitBlockArea,
    String mgmtUnitTypeCode,
    String mgmtUnitId,
    String cascadeSplitCode,
    String mapReferenceReg,
    String mapReferenceComp,
    /** The mark holder; optional, as in legacy — with it, the location is required. */
    String clientNumber,
    String clientLocnCode) {}
