package ca.bc.gov.nrs.fta.shared.dto;

import java.time.LocalDate;

/**
 * Data for the FTA402 Private Mark Certificate report.
 *
 * <p>Mirrors the {@code rec_FTA402} record returned by the legacy Oracle
 * package {@code THE.FTA_402_PKG} (see
 * {@code fta-archive/fta/database/ddl/pkg/FTA_402_PKG.PKS}): the timber-mark
 * certificate columns the report emits for a private mark.
 *
 * <p>Date-typed record columns map to {@link LocalDate}; the legacy
 * {@code GRANTED_ACQRD_DATE} column is a {@code VARCHAR2(10)} in the record and
 * is kept as a {@code String}.
 */
public record AuditReportDto(
    String timberMark,
    LocalDate markIssueDate,
    LocalDate markExpiryDate,
    String fileTypeDesc,
    String grantedAcqrdDate,
    String crownGrantedAcqDesc,
    LocalDate markAmendDate,
    String amendedUserid,
    String activatedUserid,
    String district,
    String region,
    String mainLicensee,
    String address1,
    String address2,
    String address3,
    String city,
    String province,
    String country,
    String postalCode,
    String pOfCOrLegal,
    String mapReferenceId,
    Integer secondaryClientCount) {}
