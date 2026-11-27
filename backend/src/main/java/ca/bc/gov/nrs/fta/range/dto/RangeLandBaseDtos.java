package ca.bc.gov.nrs.fta.range.dto;

import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import java.util.List;

/** Request and response bodies of the FTA615R Associated Land Base editors. */
public final class RangeLandBaseDtos {

  private RangeLandBaseDtos() {}

  /**
   * Add a land base record, or update one (legacy's edit row and Save). The areas and dates are
   * text, as legacy's form took them, so legacy's own messages apply to a bad value. Dates are
   * yyyy-MM-dd.
   *
   * @param revisionCount the row's revision as read; ignored on an add
   */
  public record SaveRequest(
      String description,
      String rangeLandBasePid,
      String rangeLandBaseId,
      String rangeLandOwnershipTypeCode,
      String rangeLandPurposeCode,
      String fencedPasture,
      String unfencedPasture,
      String forageProduction,
      String landBaseComment,
      String leaseStartDate,
      String leaseEndDate,
      Integer revisionCount) {}

  /**
   * Remove (de-activate) a land base record. Legacy required the record to carry a comment; the
   * comment given here is stored on the record before it is de-activated.
   */
  public record RemoveRequest(String landBaseComment, Integer revisionCount) {}

  /** The current ownership types and land purposes. */
  public record Options(List<CodeOptionDto> ownershipTypes, List<CodeOptionDto> purposes) {}

  /** What a save or remove did, for the confirmation. */
  public record Result(String message) {}
}
