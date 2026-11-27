package ca.bc.gov.nrs.fta.range.dto;

import java.time.LocalDate;
import java.util.List;

/** Request and response bodies of the range tenure's Assoc. Client tab (legacy FTA920R). */
public final class RangeClientsDtos {

  private RangeClientsDtos() {}

  /**
   * One row of "Licensee (Current &amp; Previous)": a {@code FOREST_FILE_CLIENT} of the file,
   * with the location's contact details as FTA920R's GET formats them.
   *
   * @param clientAddress  "address_1 address_2city, province postal_code country"
   * @param clientPhoneFax "Bus: … Res: … Cell:… Fax:…", n/a for a missing number
   */
  public record RangeClientRowDto(
      Long forestFileClientSkey,
      String clientNumber,
      String clientLocnCode,
      String clientName,
      String clientAddress,
      String clientPhoneFax,
      String clientEmail,
      String fileClientType,
      String fileClientTypeDesc,
      LocalDate licenseeStartDate,
      LocalDate licenseeEndDate) {}

  /**
   * The tab.
   *
   * @param rows     the file's clients: current licensees (no end date) first, then by end date
   *                 and update time, newest first
   * @param canEdit  whether Save is enabled (legacy's {@code p_save_ok})
   * @param warnings legacy's warnings, e.g. an application in the Inbox
   */
  public record RangeClientsListDto(
      List<RangeClientRowDto> rows, boolean canEdit, List<String> warnings) {}

  /**
   * One row of "Selected Clients from the Client System": the client asked for as A, and its
   * joint-venture partners from {@code V_RELATED_CLIENT_PUBLIC} as B.
   */
  public record RelatedClientDto(
      String clientNumber,
      String clientLocnCode,
      String clientName,
      String clientAddress,
      String clientPhoneFax,
      String clientEmail,
      String fileClientType,
      String fileClientTypeDesc) {}

  /**
   * {@code POST .../clients}: Assign/Transfer to Client. Dates are YYYY-MM-DD text, checked as
   * legacy's form did.
   *
   * @param licenseeStartDate       required
   * @param previousLicenseeEndDate optional; the package uses the start date when blank
   */
  public record RangeClientAssignRequest(
      String clientNumber,
      String clientLocnCode,
      String licenseeStartDate,
      String previousLicenseeEndDate) {}

  /** What Save returns: the refreshed tab and legacy's confirmation. */
  public record RangeClientAssignResult(RangeClientsListDto list, String message) {}
}
