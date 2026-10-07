package ca.bc.gov.nrs.fta.mark.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which parts of a private mark the current user may change, and to what — the
 * port of the legacy FTA510 protection logic ({@code Fta510PrivateMarkForm.setProtectionStates}
 * and the {@code p_disable_save_ind} of {@code FTA_510_PRIVATE_MARK.GET}).
 *
 * <p>Legacy decides by organization level (Headquarters, District, Region). This app has
 * no organization levels, so every user who may edit private marks ({@code FTA_ADMIN} and
 * both timber mark roles, district included) gets the Headquarters rules, except where a
 * district role is held to less (Status, the marking codes, Submit to HQ). The record is
 * computed once, here, and serves both the detail page (which fields open for editing) and the update
 * endpoint (which fields it accepts), so the two cannot disagree.
 *
 * @param editable          whether the mark can be saved at all
 * @param reason            why not, when {@code editable} is false — null for a status that
 *                          can't be saved or a view-only mark type (both already show)
 * @param applicationDate   Application Date
 * @param term              Initial Term
 * @param location          District and the application fields: Geographic Location, Legal,
 *                          LTO PID, Area, Management Unit, Cascade, Reg/Comp
 * @param marking           Marking Requirements and Marking Instrument
 * @param markType          Mark Type, and the Assign Mark that issues the mark with it
 * @param branch            legacy "Branch Functions": Issued, Expired, Extended, Cancelled,
 *                          Crown Granted Date and Description
 * @param status            Status
 * @param statusOptions     the statuses Status may be set to, current first
 * @param amendmentStatus   the outstanding amendment's status
 * @param amendmentStatusOptions the values it may be set to
 * @param landIndex         adding a land index (FTA511) — a separate gate from the rest: legacy's
 *                          {@code FTA_511_MARK_LAND_INDEX} allows it at any status but HX, DV and
 *                          DD, where FTA510's save is narrower
 * @param landIndexReason   why not, when {@code landIndex} is false — shown beside the
 *                          disabled Add button
 * @param clients           adding an associated client (FTA513) — its own gate too:
 *                          {@code FTA_513_PM_CLIENT} allows it at HI, PI or PA only
 * @param clientsReason     why not, when {@code clients} is false
 * @param amendments        requesting an amendment (FTA512) — {@code FTA_512_MARK_AMEND} allows
 *                          it only on an issued (HI) mark with no amendment outstanding
 * @param amendmentsReason  why not, when {@code amendments} is false — except while an
 *                          amendment is outstanding, which the table already shows
 * @param submit            Submit to HQ (FTA510): a district sends its PA application to
 *                          Headquarters, which makes it PI
 * @param submitReason      why not, when {@code submit} is false
 * @param landIndexUpdate   updating a land index already on the mark (FTA511's save of an
 *                          existing row): Headquarters at any status; a district as it may add
 * @param clientsUpdate     updating an associated client already on the mark (FTA513's
 *                          save of an existing row): Headquarters at any status; a district
 *                          as it may add
 */
public record MarkEditRules(
    boolean editable,
    String reason,
    boolean applicationDate,
    boolean term,
    boolean location,
    boolean marking,
    boolean markType,
    boolean branch,
    boolean status,
    List<String> statusOptions,
    boolean amendmentStatus,
    List<String> amendmentStatusOptions,
    boolean landIndex,
    String landIndexReason,
    boolean clients,
    String clientsReason,
    boolean amendments,
    String amendmentsReason,
    boolean submit,
    String submitReason,
    boolean landIndexUpdate,
    boolean clientsUpdate) {

  /** Legacy Headquarters may save a mark in these statuses ({@code FTA_510.GET}). */
  private static final Set<String> SAVEABLE_STATUSES = Set.of("HI", "HX", "PI", "PA", "HN");

  /** Mark types legacy only ever shows ({@code FTA_510.GET}: "B15,B16 may only be viewed"). */
  private static final Set<String> VIEW_ONLY_TYPES = Set.of("B15", "B16");

  /**
   * Status changes, from the current status. Only the transitions the legacy save actually
   * writes are offered: the form's validators allow a few more (HI to PI, PI to HN), but
   * {@code FTA_510_PRIVATE_MARK.SAVE} has no branch for HI to PI, and PI to HN issues the
   * mark, which needs Assign Mark — not ported.
   */
  private static final Map<String, List<String>> STATUS_TRANSITIONS = Map.of(
      "PI", List.of("PI", "DV", "EE"),
      "HI", List.of("HI", "HX"),
      "HX", List.of("HX", "HI", "PI", "DD", "DV"));

  /** The values an outstanding amendment may take ({@code amendStatusCertainValues}). */
  private static final List<String> AMENDMENT_STATUSES = List.of("PI", "HN", "DV");

  /** Statuses at which FTA_511 refuses land index changes ("…due to status of mark"). */
  private static final Set<String> LAND_INDEX_LOCKED = Set.of("HX", "DV", "DD");

  /** Statuses at which FTA_513 lets Headquarters change a mark's clients. */
  private static final Set<String> CLIENTS_OPEN = Set.of("HI", "PI", "PA");

  private static MarkEditRules none(
      String reason,
      boolean landIndex,
      String landIndexReason,
      boolean clients,
      String clientsReason,
      boolean amendments,
      String amendmentsReason,
      String submitReason,
      boolean landIndexUpdate,
      boolean clientsUpdate) {
    return new MarkEditRules(
        false, reason, false, false, false, false, false, false, false, List.of(), false,
        List.of(), landIndex, landIndexReason, clients, clientsReason, amendments,
        amendmentsReason, submitReason == null, submitReason, landIndexUpdate, clientsUpdate);
  }

  /**
   * The rules for {@code mark} and a user who may ({@code userCanEdit}) or may not edit
   * private marks, at Headquarters level.
   */
  public static MarkEditRules of(MarkDetailDto mark, boolean userCanEdit) {
    return of(mark, userCanEdit, false);
  }

  /**
   * The rules for {@code mark} and a user who may ({@code userCanEdit}) or may not edit
   * private marks; {@code districtUser} is the {@code FTA_TIMBER_MARK_DISTRICT_ADMIN} role,
   * legacy's district organization level, which alone submits applications to Headquarters.
   * Every other editor is Headquarters, which may always change the status, and the marking
   * codes once the mark has them.
   */
  public static MarkEditRules of(MarkDetailDto mark, boolean userCanEdit, boolean districtUser) {
    String status = mark.markStatusCode();
    boolean viewOnlyType =
        mark.fileTypeCode() != null && VIEW_ONLY_TYPES.contains(mark.fileTypeCode());
    // A view-only mark type (B15/B16) disables every action without a reason: the type is
    // on screen, and a note saying so isn't wanted.
    // FTA_511_MARK_LAND_INDEX.mainline GET: Headquarters only, not for HX/DV/DD, not B15/B16.
    // Null when a land index may be added.
    String landIndexReason = !userCanEdit
        ? "Your role cannot add a land index."
        : status != null && LAND_INDEX_LOCKED.contains(status)
            ? "Land index cannot be changed while the mark is " + status + "."
            : null;
    boolean landIndex = landIndexReason == null && !viewOnlyType;
    // FTA_513_PM_CLIENT.mainline GET, Headquarters: not B15/B16, and only at HI, PI or PA.
    String clientsReason = !userCanEdit
        ? "Your role cannot add a client."
        : viewOnlyType
            ? null
            : status == null || !CLIENTS_OPEN.contains(status)
                ? "Clients can be added only while the mark is HI, PI or PA."
                : null;
    boolean clients = clientsReason == null && userCanEdit && !viewOnlyType;
    // FTA_512_MARK_AMEND.mainline GET: not B15/B16; only an HI mark, and only when no
    // amendment is outstanding. Legacy looks for a PI one; an approved one not yet printed
    // (HN) is outstanding too, and a second would leave the mark with two. That case
    // disables the request without a reason: the table already shows the amendment.
    String amendmentsReason = !userCanEdit
        ? "Your role cannot request an amendment."
        : viewOnlyType
            ? null
            : mark.outstandingAmendStatus() == null
                && (!"HI".equals(status) || mark.timberMark() == null)
                    ? "Amendments can be requested only while the mark is HI (Issued)."
                    : mark.outstandingAmendStatus() == null && !mark.timberMarkRecord()
                        ? "Timber mark " + mark.timberMark() + " has no TIMBER_MARK record,"
                            + " which an amendment needs. Ask for a data fix."
                        : null;
    boolean amendments = amendmentsReason == null && !viewOnlyType
        && mark.outstandingAmendStatus() == null;
    // FTA_510.GET: "enable the Submit to HQ only if status is PA" and the user is not
    // Headquarters; its SUBMIT refuses an application without a client.
    String submitReason = !userCanEdit
        ? "Your role cannot submit applications."
        : !districtUser
            ? "Only a district submits applications to Headquarters."
            : !"PA".equals(status)
                ? "Only an application in PA status can be submitted."
                : mark.clientNumber() == null
                    ? "Add the main licensee on the Associated clients tab first."
                    : null;

    if (!userCanEdit) {
      return none(
          "Your role cannot edit private marks.",
          landIndex,
          landIndexReason,
          clients,
          clientsReason,
          amendments,
          amendmentsReason,
          submitReason,
          false,
          false);
    }
    if (viewOnlyType) {
      return none(null, landIndex, landIndexReason, clients, clientsReason, amendments,
          amendmentsReason, submitReason, false, false);
    }
    // Headquarters: the timber mark roles other than the district's (and FTA_ADMIN).
    boolean headquarters = !districtUser;
    // Correcting a land index or client already on the mark: Headquarters at any status
    // (legacy held updates to the add gates); a district still as it may add.
    boolean landIndexUpdate = headquarters || landIndex;
    boolean clientsUpdate = headquarters || clients;
    // The marking codes live on HAULING_AUTHORITY, which exists once the mark is issued
    // (HN on). Headquarters may change them at any status from then; a district only until
    // the certificate is printed and the mark is issued (HN).
    boolean markingOpen = mark.timberMark() != null
        && (headquarters || "HN".equals(status));
    if (status == null || !SAVEABLE_STATUSES.contains(status)) {
      if (!headquarters || status == null) {
        // No reason: the status is on screen, and a note saying so isn't wanted.
        return none(null, landIndex, landIndexReason, clients, clientsReason, amendments,
            amendmentsReason, submitReason, landIndexUpdate, clientsUpdate);
      }
      // Headquarters keeps the marking codes and Status at a status legacy can't save.
      List<String> options = STATUS_TRANSITIONS.getOrDefault(status, List.of(status));
      boolean open = options.size() > 1;
      return new MarkEditRules(
          markingOpen || open, null, false, false, false, markingOpen, false, false, open,
          open ? options : List.of(status), false, List.of(), landIndex, landIndexReason,
          clients, clientsReason, amendments, amendmentsReason, submitReason == null,
          submitReason, landIndexUpdate, clientsUpdate);
    }

    // setProtectionStates, Headquarters, existing record, opened from the FTA500 list:
    // an outstanding amendment is worked here (state5), otherwise state3. Without a
    // client neither the branch nor the status can be worked (state1), and only then
    // are the application date and client open.
    boolean amendmentOutstanding = mark.outstandingAmendStatus() != null;
    boolean hasClient = mark.clientNumber() != null;

    // Legacy leaves Status open beside an outstanding amendment, but its save only acts on
    // one of the two (a status change wins and the amendment is dropped), so here an
    // outstanding amendment is worked first and Status waits for it.
    List<String> statusOptions = STATUS_TRANSITIONS.getOrDefault(status, List.of(status));

    // Assign Mark (legacy p_disable_assign_mark_ind): only before a mark type is set, and only
    // with a client. Legacy shows Headquarters a PA application as PI, so both qualify.
    boolean markTypeOpen = hasClient
        && mark.fileTypeCode() == null
        && mark.timberMark() == null
        && ("PI".equals(status) || "PA".equals(status));
    // Headquarters may always change the status, cancelled (HX) included: the client and
    // outstanding-amendment holds are a district's.
    boolean statusOpen = statusOptions.size() > 1
        && (headquarters || (hasClient && !amendmentOutstanding));

    return new MarkEditRules(
        true,
        null,
        !hasClient,
        !amendmentOutstanding,
        true,
        // Legacy wrote the marking codes only at HI; see markingOpen for who may now.
        markingOpen,
        markTypeOpen,
        hasClient,
        statusOpen,
        statusOpen ? statusOptions : List.of(status),
        amendmentOutstanding,
        amendmentOutstanding ? AMENDMENT_STATUSES : List.of(),
        landIndex,
        landIndexReason,
        clients,
        clientsReason,
        amendments,
        amendmentsReason,
        submitReason == null,
        submitReason,
        landIndexUpdate,
        clientsUpdate);
  }
}
