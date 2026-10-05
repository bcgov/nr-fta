package ca.bc.gov.nrs.fta.tenure.tab.assocclients;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Legacy FTA920's (Associated Clients, file level) gates and the checks that need no database
 * — computed once, returned on the GET (to enable the buttons and say why not) and enforced on
 * every write.
 *
 * <p>The gates, from {@code FTA_920_ASSO_CLIENTS.mainline} GET's {@code p_save_ok},
 * {@code check_authority} and the form's {@code noSaveWhenStatusPE}:
 * <ul>
 *   <li>a private mark file is view only (its clients are the private mark's, FTA513);</li>
 *   <li>a PA file: "Cannot attach client at this time";</li>
 *   <li>an expired or recreation file type: view only;</li>
 *   <li>adding/updating also needs {@code FTA_FILE_LEVEL_AUTHORITY} to allow the file type at
 *       the FILE level for Headquarters (every FTA_ADMIN gets Headquarters' rules), and the file
 *       not to be PE;</li>
 *   <li>deleting needs only the first three, and only a C or S type client
 *       ({@code Fta920AssoClientsForm.isDeleteEnabled}).</li>
 * </ul>
 * Legacy's file-ownership checks compare the user's org unit to the file's; there are no org
 * units here, so they always pass.
 *
 * @param canEdit       whether clients may be added or updated
 * @param editReason    why not, when {@code canEdit} is false
 * @param canDelete     whether C and S type clients may be deleted
 * @param deleteReason  why not, when {@code canDelete} is false
 */
public record AssocClientsRules(
    boolean canEdit, String editReason, boolean canDelete, String deleteReason) {

  /** Types that need a start date (save_forest_client / update_rec). */
  static final Set<String> START_REQUIRED = Set.of("A", "B", "C");

  /** Types that need an end date. */
  static final Set<String> END_REQUIRED = Set.of("C", "P");

  /** Licensee types: no end date, and at most one row per client among them. */
  static final Set<String> LICENSEES = Set.of("A", "B");

  /** The client types legacy lets a user delete. */
  static final Set<String> DELETABLE = Set.of("C", "S");

  /** File types whose Main Licensee must be the District (or TSO) Manager. */
  static final Set<String> MANAGER_FILE_TYPES = Set.of("C01", "B40");

  static final String PRIVATE_MARK =
      "This is a Private Mark — its clients are maintained on the private mark.";
  static final String STATUS_PA = "Cannot attach client at this time (the file is PA).";
  static final String UNSUPPORTED = "You may only view as file type is not supported.";
  static final String STATUS_PE = "No updates can be performed on this file when the status is PE.";
  static final String NOT_AUTHORIZED = "You are not authorized to attach clients at the FILE level.";
  static final String MAIN_REQUIRED = "At least one Main Licensee (A type) required.";
  static final String O_TYPE = "O-Type clients can only be attached at the Cut Block level.";

  /**
   * The gates for a file.
   *
   * @param fileStatusCode     PROV_FOREST_USE.FILE_STATUS_ST
   * @param privateMark        FTA_VALID_PRIVATE_MARK_TYPE
   * @param unsupportedType    not FTA_CURRENT_FILE_TYPE, or FTA_VALID_RECREATION_FILE_TYPE
   * @param fileLevelAuthority FTA_FILE_LEVEL_AUTHORITY has the type at FILE level for HQ
   */
  public static AssocClientsRules of(
      String fileStatusCode,
      boolean privateMark,
      boolean unsupportedType,
      boolean fileLevelAuthority) {
    String status = fileStatusCode == null ? "" : fileStatusCode.trim().toUpperCase();
    String viewOnly = privateMark ? PRIVATE_MARK
        : status.startsWith("PA") ? STATUS_PA
        : unsupportedType ? UNSUPPORTED
        : null;
    if (viewOnly != null) {
      return new AssocClientsRules(false, viewOnly, false, viewOnly);
    }
    String edit = status.startsWith("PE") ? STATUS_PE
        : !fileLevelAuthority ? NOT_AUTHORIZED
        : null;
    return new AssocClientsRules(edit == null, edit, true, null);
  }

  /** Whether a client of this type may be deleted, given the gate. */
  public boolean canDelete(String fileClientType) {
    return canDelete && DELETABLE.contains(fileClientType);
  }

  /**
   * The form's field checks ({@code Fta920AssoClientsForm}'s Save validators) and the PL/SQL's
   * date rules by client type, shared by add and update. Arguments are trimmed, blank as null.
   */
  public static List<String> validateFields(
      String clientNumber, String clientLocnCode, String type, LocalDate start, LocalDate end) {
    List<String> e = new ArrayList<>();
    if (type == null) {
      e.add("Client Type is mandatory.");
    }
    if (start != null && end != null && start.isAfter(end)) {
      e.add("Licensee Start Date must be less than or equal to Licensee End Date.");
    }
    if (clientNumber == null) {
      e.add("Client Number is mandatory.");
    }
    if (clientLocnCode == null) {
      e.add("Client Location Code is mandatory.");
    } else if (!clientLocnCode.matches("\\d{1,2}")) {
      e.add("Client Location Code field must be between 0 and 99.");
    }
    if ("O".equals(type)) {
      e.add(O_TYPE);
    }
    if (type != null) {
      e.addAll(dateRules(type, start, end));
    }
    return e;
  }

  /** save_forest_client / update_rec's date rules, with their messages. */
  static List<String> dateRules(String type, LocalDate start, LocalDate end) {
    List<String> e = new ArrayList<>();
    if (START_REQUIRED.contains(type) && start == null) {
      e.add("Licensee Start Date is required.");
    }
    if (END_REQUIRED.contains(type) && end == null) {
      e.add("Licensee End Date is required.");
    } else if ("M".equals(type) && start != null && end == null) {
      e.add("Licensee End Date must be entered when Licensee Start Date is.");
    }
    if (LICENSEES.contains(type) && end != null) {
      e.add("Licensee End Date must be blank.");
    } else if (end != null && start == null) {
      e.add("If Licensee Start Date is blank, Licensee End Date must be blank.");
    }
    return e;
  }

  /**
   * A licensee's (A/B) start date: on or after the latest previous licensee's (C) end date, and
   * on or after the current main licensee's start date (the other A, not this row).
   */
  static String licenseeStartProblem(
      String type, LocalDate start, LocalDate previousEnd, LocalDate mainStart) {
    if (!LICENSEES.contains(type) || start == null) {
      return null;
    }
    if (previousEnd != null && start.isBefore(previousEnd)) {
      return "Licensee Start Date must be after previous Licensee End Date.";
    }
    if (mainStart != null && start.isBefore(mainStart)) {
      return "Licensee Start Date must be after current Licensee Start Date.";
    }
    return null;
  }

  /**
   * C01/B40 files: only the District Manager (the TSO Manager for a BCTS-funded B40) of the
   * file's district may be attached, and only as the Main Licensee (save_forest_client).
   */
  static String managerProblem(String fileType, boolean bctsFunded, boolean clientIsManager,
                               String type) {
    if (!MANAGER_FILE_TYPES.contains(fileType) || (clientIsManager && "A".equals(type))) {
      return null;
    }
    return "B40".equals(fileType) && bctsFunded
        ? "For BCTS funded B40 files only the TSO Manager may be the Main Licensee."
        : "For " + fileType + " files only the District Manager may be the Main Licensee.";
  }

  /** save_forest_client's S rule: only on a single-mark file type, and only one. */
  static String sTypeProblem(String type, boolean singleMarkFileType, long otherS) {
    if (!"S".equals(type)) {
      return null;
    }
    if (!singleMarkFileType) {
      return "You may not add S type clients at the file level unless the file is a single"
          + " mark file type.";
    }
    return otherS > 0
        ? "Only one S type client allowed. You may update the client number/locn of the"
            + " existing S client instead."
        : null;
  }

  /** update_rec's rules on what a Main (A) or Previous (C) licensee may change. */
  static List<String> updateIdentityRules(
      String previousType, String previousNumber, String newNumber, String newType) {
    List<String> e = new ArrayList<>();
    if ("A".equals(previousType) || "C".equals(previousType)) {
      if (!Objects.equals(previousNumber, newNumber)) {
        e.add("Cannot change the Client Number for Main or Previous Licensee (A or C type).");
      } else if (!Objects.equals(previousType, newType)) {
        e.add("Cannot change the Client Type for Main or Previous Licensee (A or C type).");
      }
    }
    if ("A".equals(previousType) && !"A".equals(newType)) {
      e.add(MAIN_REQUIRED);
    }
    return e;
  }
}
