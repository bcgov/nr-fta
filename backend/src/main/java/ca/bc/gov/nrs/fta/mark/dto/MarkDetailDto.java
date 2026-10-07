package ca.bc.gov.nrs.fta.mark.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Rich detail of a single Private Timber Mark, assembled from the legacy Oracle
 * private-mark packages against the shared {@code THE} schema.
 *
 * <p>The tombstone/application fields mirror the {@code GET} SELECT of
 * {@code THE.FTA_510_PRIVATE_MARK} (PRIVATE_MARK_CERTIFICATE + PROV_FOREST_USE +
 * HAULING_AUTHORITY, plus the main FOREST_FILE_CLIENT 'A' client). The nested
 * collections mirror {@code THE.FTA_511_MARK_LAND_INDEX.rec_land_index}
 * (land index), {@code THE.FTA_513_PM_CLIENT.rec_assoc_clients_results}
 * (associated clients), the {@code TMBR_MARK_AMEND} amendment history read
 * by FTA_510.GET, and {@code THE.FTA_970_FOREST_NOTE.forest_note_results}
 * (notes on the mark's forest file).
 *
 * <p>See {@code fta-archive/fta/database/ddl/pkg/FTA_510_PRIVATE_MARK.PKS},
 * {@code FTA_511_MARK_LAND_INDEX.PKS} and {@code FTA_513_PM_CLIENT.PKS}.
 */
public record MarkDetailDto(
    String timberMark,
    String certificate,
    /** The mark's forest file; null for an application that has not been given one yet. */
    String forestFileId,
    String fileTypeCode,
    String markStatusCode,
    LocalDate markStatusDate,
    LocalDate markApplicationDate,
    LocalDate markIssueDate,
    LocalDate markExpiryDate,
    LocalDate markCancelDate,
    /** Initial term in months (legacy "Initial Term (mths)"). */
    Integer tenureTerm,
    String forestDistrict,
    String orgUnitCode,
    String clientNumber,
    String clientLocnCode,
    String clientName,
    String markingMethodCode,
    String markingInstrumentCode,
    String crownGrantedAcqDesc,
    LocalDate grantedAcqrdDate,
    String permitBlockLocn,
    BigDecimal permitBlockArea,
    /** Legacy "Legal": the legal description, or proof of Crown grant. */
    String proofOfCrownOrLegal,
    // "<code> - <description>" for the coded fields above, null when unresolved.
    String markStatusDesc,
    String fileTypeDesc,
    String districtDesc,
    /** The district's region (ORG_UNIT.ROLLUP_REGION_NO), as legacy Sil_Get_Region_No. */
    String regionDesc,
    String markingMethodDesc,
    String markingInstrumentDesc,
    /** Legacy "LTO PID" — stored as BCAA_FOLIO_NUMBER. */
    String bcaaFolioNumber,
    String mgmtUnitTypeCode,
    String mgmtUnitId,
    String mgmtUnitDesc,
    String cascadeSplitCode,
    String cascadeSplitDesc,
    /** Legacy "Reg/Comp": characters 1-2 and 3-5 of MAP_REFERENCE_ID. */
    String mapReferenceReg,
    String mapReferenceComp,
    LocalDate markExtendDate,
    Integer markExtendCount,
    LocalDate markAmendDate,
    /** The outstanding (PI or HN) amendment's status, as legacy "Amendment Status". */
    String outstandingAmendStatus,
    String outstandingAmendStatusDesc,
    /** PRIVATE_MARK_CERTIFICATE.REVISION_COUNT — sent back on update, for optimistic locking. */
    Long revisionCount,
    /** The outstanding amendment's TMBR_MARK_AMEND.REVISION_COUNT, likewise. */
    Long amendRevisionCount,
    /**
     * Whether the old TIMBER_MARK table has the mark — TMBR_MARK_AMEND's parent (TMA_TM_FK),
     * so an amendment needs it. Trigger FTA_SYNC_PMC_TM creates it; some marks lack it.
     */
    boolean timberMarkRecord,
    List<LandIndex> landIndex,
    List<AssociatedClient> clients,
    List<Amendment> amendments,
    List<Note> notes,
    /** What the current user may change; set per request, null until then. */
    MarkEditRules editRules) {

  /** This record with its sub-lists replaced — the header is read first, the lists after. */
  public MarkDetailDto withLists(
      List<LandIndex> landIndex,
      List<AssociatedClient> clients,
      List<Amendment> amendments,
      List<Note> notes) {
    return new MarkDetailDto(
        timberMark, certificate, forestFileId, fileTypeCode, markStatusCode, markStatusDate,
        markApplicationDate, markIssueDate, markExpiryDate, markCancelDate, tenureTerm,
        forestDistrict, orgUnitCode, clientNumber, clientLocnCode, clientName,
        markingMethodCode, markingInstrumentCode, crownGrantedAcqDesc, grantedAcqrdDate,
        permitBlockLocn, permitBlockArea, proofOfCrownOrLegal, markStatusDesc, fileTypeDesc,
        districtDesc,
        regionDesc, markingMethodDesc, markingInstrumentDesc, bcaaFolioNumber,
        mgmtUnitTypeCode, mgmtUnitId, mgmtUnitDesc, cascadeSplitCode, cascadeSplitDesc,
        mapReferenceReg, mapReferenceComp, markExtendDate, markExtendCount, markAmendDate,
        outstandingAmendStatus, outstandingAmendStatusDesc, revisionCount, amendRevisionCount,
        timberMarkRecord, landIndex, clients, amendments, notes, editRules);
  }

  /** This record with the current user's edit rules attached. */
  public MarkDetailDto withEditRules(MarkEditRules rules) {
    return new MarkDetailDto(
        timberMark, certificate, forestFileId, fileTypeCode, markStatusCode, markStatusDate,
        markApplicationDate, markIssueDate, markExpiryDate, markCancelDate, tenureTerm,
        forestDistrict, orgUnitCode, clientNumber, clientLocnCode, clientName,
        markingMethodCode, markingInstrumentCode, crownGrantedAcqDesc, grantedAcqrdDate,
        permitBlockLocn, permitBlockArea, proofOfCrownOrLegal, markStatusDesc, fileTypeDesc,
        districtDesc, regionDesc, markingMethodDesc, markingInstrumentDesc, bcaaFolioNumber,
        mgmtUnitTypeCode, mgmtUnitId, mgmtUnitDesc, cascadeSplitCode, cascadeSplitDesc,
        mapReferenceReg, mapReferenceComp, markExtendDate, markExtendCount, markAmendDate,
        outstandingAmendStatus, outstandingAmendStatusDesc, revisionCount, amendRevisionCount,
        timberMarkRecord, landIndex, clients, amendments, notes, rules);
  }

  /**
   * One parcel land-index entry — mirrors
   * {@code THE.FTA_511_MARK_LAND_INDEX.rec_land_index}.
   */
  public record LandIndex(
      String primaryLandIndexCode,
      String secondaryLandIndexCode,
      String primaryLandIndexCodeDesc,
      String secondaryLandIndexCodeDesc,
      String markLandIndexDesc,
      LocalDate indexDeactivateDate,
      Long markLandIndexSkey,
      Integer revisionCount) {}

  /**
   * One associated client — mirrors
   * {@code THE.FTA_513_PM_CLIENT.rec_assoc_clients_results}.
   */
  public record AssociatedClient(
      String clientNumber,
      String clientLocnCode,
      String clientName,
      String clientCity,
      Long forClientLinkSkey,
      String fileClientType,
      String fileClientTypeDesc,
      LocalDate licenseeStartDt,
      LocalDate licenseeEndDate,
      Integer revisionCount) {}

  /** One amendment-history row — mirrors {@code THE.TMBR_MARK_AMEND}. */
  public record Amendment(
      LocalDate amendRequestDate,
      String prvMrkAmdStsSt,
      /** "<code> - <description>" from PRIVATE_MARK_AMEND_STATUS_CODE. */
      String prvMrkAmdStsDesc,
      Integer revisionCount,
      /** Who asked for it (REQUESTING_USERID). */
      String requestingUserid,
      /** The requested area, hectares (PERMIT_BLOCK_AREA). */
      BigDecimal permitBlockArea,
      /** The requested amendment changes (P_OF_C_OR_LEGAL). */
      String requestedChanges) {}

  /**
   * One note on the mark's forest file — mirrors
   * {@code THE.FTA_970_FOREST_NOTE.forest_note_results} ({@code PROVFOREST_NOTE}).
   */
  public record Note(
      String entryUserid,
      LocalDateTime entryTimestamp,
      String note) {}
}
