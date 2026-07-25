package ca.bc.gov.nrs.fta.mark.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * (associated clients), and the {@code TMBR_MARK_AMEND} amendment history read
 * by FTA_510.GET.
 *
 * <p>See {@code fta-archive/fta/database/ddl/pkg/FTA_510_PRIVATE_MARK.PKS},
 * {@code FTA_511_MARK_LAND_INDEX.PKS} and {@code FTA_513_PM_CLIENT.PKS}.
 */
public record MarkDetailDto(
    String timberMark,
    String certificate,
    String fileTypeCode,
    String markStatusCode,
    LocalDate markStatusDate,
    LocalDate markApplicationDate,
    LocalDate markIssueDate,
    LocalDate markExpiryDate,
    LocalDate markCancelDate,
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
    String proofOfCrownOrLegal,
    List<LandIndex> landIndex,
    List<AssociatedClient> clients,
    List<Amendment> amendments) {

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
      Integer revisionCount) {}
}
