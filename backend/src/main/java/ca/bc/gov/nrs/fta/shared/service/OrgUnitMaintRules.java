package ca.bc.gov.nrs.fta.shared.service;

import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import java.util.ArrayList;
import java.util.List;

/**
 * SIL99 User Org Maintenance's checks and choices, from {@code Sil99UserOrgDefaultForm},
 * {@code Sil99UserOrgDefaultAction}, {@code SilUser} and the {@code THE.SIL_99_USER_ORG_DEFAULT}
 * package, with the database reads done by the caller.
 *
 * <p>Legacy listed the user's WebADE org units. Here a user's org units are what their
 * {@code FTA_ADMIN} grant covers: the districts a district-scoped grant names, or every district
 * for an unscoped one.
 */
final class OrgUnitMaintRules {

  /** The WebADE application acronym legacy filed FTA's defaults under (half the table's key). */
  static final String APPLICATION_ACRONYM = "FTA";

  /** {@code sil.web.error.noOrgsForUser}, without "in the Webade": there is no WebADE now. */
  static final String NO_ORGS_FOR_USER =
      "Invalid User: There are no org units associated to this user.";
  /** {@code sil.web.warning.save.success}. */
  static final String SAVE_SUCCESS = "Save successful.";
  /**
   * Legacy's radio buttons could only send one of the user's org units, and it sent nothing when
   * none was chosen, so it had no message for these; the package would have failed on the NOT
   * NULL column.
   */
  static final String ORG_UNIT_REQUIRED = "Default org unit is mandatory.";
  static final String NOT_YOUR_ORG_UNIT = "Org unit %s is not one of your org units.";

  /** The package's message keys ({@code ApplicationResources.properties}), with their text. */
  private static final String[][] PACKAGE_MESSAGES = {
      {"sil.web.usr.database.record.invalid",
          "No Records Found on table {2} in package/procedure {0}, {1}."},
      {"sil.web.usr.database.record.toomany",
          "Multiple records were found in table {2} when expecting an exact fetch by "
              + "package/procedure {0},{1}."},
      {"sil.web.usr.database.invalid.function", "Unknown database action - {0}."},
  };

  private OrgUnitMaintRules() {}

  /**
   * The user's org units, in code order.
   *
   * @param districts      every current district, as {@code CodeListService.districts()}
   * @param unscoped       whether the user is an unscoped {@code FTA_ADMIN}
   * @param adminDistricts the ORG_UNIT_CODEs a district-scoped grant names
   */
  static List<CodeOptionDto> userOrgUnits(
      List<CodeOptionDto> districts, boolean unscoped, List<String> adminDistricts) {
    if (unscoped) {
      return List.copyOf(districts);
    }
    List<CodeOptionDto> out = new ArrayList<>();
    for (CodeOptionDto d : districts) {
      if (adminDistricts.contains(codeOf(d))) {
        out.add(d);
      }
    }
    return out;
  }

  /**
   * The radio to check: the saved default when it is one of the user's org units, else the
   * first of them ({@code Sil99UserOrgDefaultForm.getDefaultOrgUnit}: "if user doesn't have a
   * default org, use the first one - that's what SilUser does"). A saved default that is no
   * longer one of them leaves none checked, as legacy did.
   */
  static String checked(List<CodeOptionDto> orgUnits, String saved) {
    if (!blank(saved).isEmpty()) {
      return contains(orgUnits, saved) ? saved.trim() : null;
    }
    return orgUnits.isEmpty() ? null : orgUnits.get(0).code();
  }

  /**
   * The user's default org unit for the other screens ({@code SilUser.getUser_org_unit}): the
   * saved one whatever it is, else the first of the user's org units, else none.
   */
  static String effective(List<CodeOptionDto> orgUnits, String saved) {
    if (!blank(saved).isEmpty()) {
      return saved.trim();
    }
    return orgUnits.isEmpty() ? null : orgUnits.get(0).code();
  }

  /** Save's checks; empty when the org unit may be saved as the user's default. */
  static List<String> validate(List<CodeOptionDto> orgUnits, String orgUnitNo) {
    if (orgUnits.isEmpty()) {
      return List.of(NO_ORGS_FOR_USER);
    }
    if (blank(orgUnitNo).isEmpty()) {
      return List.of(ORG_UNIT_REQUIRED);
    }
    if (!contains(orgUnits, orgUnitNo)) {
      return List.of(NOT_YOUR_ORG_UNIT.formatted(orgUnitNo.trim()));
    }
    return List.of();
  }

  /**
   * The package's {@code p_error_message} as text: {@code key:arg,arg,arg;} entries, each
   * replaced by its message with the arguments filled in. An unknown key is shown as sent.
   */
  static List<String> packageErrors(String raw) {
    List<String> out = new ArrayList<>();
    if (blank(raw).isEmpty()) {
      return out;
    }
    for (String entry : raw.split(";")) {
      String e = entry.trim();
      if (e.isEmpty()) {
        continue;
      }
      int colon = e.indexOf(':');
      String key = colon < 0 ? e : e.substring(0, colon);
      String[] args = colon < 0 ? new String[0] : e.substring(colon + 1).split(",");
      String text = e;
      for (String[] m : PACKAGE_MESSAGES) {
        if (m[0].equals(key)) {
          text = m[1];
          for (int i = 0; i < args.length; i++) {
            text = text.replace("{" + i + "}", args[i].trim());
          }
        }
      }
      out.add(text);
    }
    return out;
  }

  /** An option's ORG_UNIT_CODE: its label reads "CODE - name". */
  static String codeOf(CodeOptionDto o) {
    String d = o.description() == null ? "" : o.description();
    int i = d.indexOf(" - ");
    return i < 0 ? d.trim() : d.substring(0, i).trim();
  }

  private static boolean contains(List<CodeOptionDto> orgUnits, String orgUnitNo) {
    String no = blank(orgUnitNo);
    return orgUnits.stream().anyMatch(o -> o.code().equals(no));
  }

  private static String blank(String s) {
    return s == null ? "" : s.trim();
  }
}
