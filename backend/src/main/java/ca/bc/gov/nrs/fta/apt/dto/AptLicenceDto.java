package ca.bc.gov.nrs.fta.apt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A licence as APT sees it ({@code APT_LICENCES_GET}): the tenure's facts with APT's own
 * FOA type, replacement and comments ({@code APT_LICENCES}, which may not have a row yet).
 * APT2's field names, in camelCase.
 *
 * @param orgUnitName      the tenure's region
 * @param aptMgmtUnitSt    management unit type
 * @param mgmtUnitNumber   management unit number
 * @param mgmtUnitName     management unit name
 * @param fileStatusSt     file status code
 * @param fileStatus       file status description
 * @param fileStatusDate   file status date
 * @param forestFileId     the licence
 * @param legalEffectiveDt legal effective date
 * @param initialExpiryDt  initial expiry date
 * @param currentExpiryDt  current expiry date
 * @param tenureTerm       tenure term
 * @param clientNumber     licensee's client number
 * @param clientName       licensee
 * @param controlCompany   the licensee's controlling company, if any
 * @param aptFoaSt         APT's form of agreement type; null until set
 * @param replacementDate  replacement date
 * @param replacementTerm  replacement term (months)
 * @param comments         comments
 * @param addTimestamp     when APT's row was added
 * @param addUserid        who added it
 * @param updateTimestamp  when it was last updated
 * @param updateUserid     who last updated it
 */
public record AptLicenceDto(
    String orgUnitName,
    String aptMgmtUnitSt,
    String mgmtUnitNumber,
    String mgmtUnitName,
    String fileStatusSt,
    String fileStatus,
    LocalDate fileStatusDate,
    String forestFileId,
    LocalDate legalEffectiveDt,
    LocalDate initialExpiryDt,
    LocalDate currentExpiryDt,
    BigDecimal tenureTerm,
    String clientNumber,
    String clientName,
    String controlCompany,
    String aptFoaSt,
    LocalDate replacementDate,
    Integer replacementTerm,
    String comments,
    LocalDateTime addTimestamp,
    String addUserid,
    LocalDateTime updateTimestamp,
    String updateUserid) {}
