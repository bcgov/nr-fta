package ca.bc.gov.nrs.fta.tenure.dto;

import java.math.BigDecimal;

/**
 * Request body for adding a cutting permit to a tenure ({@code POST
 * /api/fta/tenures/{id}/cutting-permits}) — the fields the ESF tenure-application create
 * ({@code FTA_XML_PROCESS.process_harvest_cp}) takes, checked as FTA902 checks them.
 *
 * @param cuttingPermitId       CP ID, up to 3 characters; required
 * @param forestDistrict        district ORG_UNIT_NO; required
 * @param location              up to 50 characters; optional
 * @param tenureTerm            term in months; required, at most 48 (60 for A11)
 * @param harvestArea           hectares; optional
 * @param markingMethodCode     compliance (marking) method; required
 * @param markingInstrumentCode required unless the method is E
 * @param salvageTypeCode       optional; SSS for an A31, or a salvage licence's CP
 * @param cascadeSplitCode      optional; the district's default when blank
 * @param deciduous             Deciduous (Y/N)
 * @param catastrophic          Catastrophic (Y/N)
 * @param cruiseBased           Cruise Based (Y/N)
 */
public record CuttingPermitCreateRequest(
    String cuttingPermitId,
    String forestDistrict,
    String location,
    Integer tenureTerm,
    BigDecimal harvestArea,
    String markingMethodCode,
    String markingInstrumentCode,
    String salvageTypeCode,
    String cascadeSplitCode,
    boolean deciduous,
    boolean catastrophic,
    boolean cruiseBased) {}
