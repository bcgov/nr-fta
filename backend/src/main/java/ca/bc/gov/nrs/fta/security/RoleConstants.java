package ca.bc.gov.nrs.fta.security;

import ca.bc.gov.nrs.fta.dto.Role;
import org.springframework.stereotype.Component;


/**
 * Exposes role constants as Spring beans for use in SpEL expressions and security configuration.
 *
 * <p>The string constants {@link #ADMIN_AUTHORITY} and {@link #VIEWER_AUTHORITY} match
 * the CSS/Keycloak role names and are used in {@code ApiAuthorizationCustomizer} for
 * URL-level access control via {@code hasAuthority()} / {@code hasAnyAuthority()}.
 *
 * <p>A role FAM grants for a district arrives as {@code <CODE>_DISTRICT-<ORG_UNIT_CODE>};
 * {@link TokenRoles#authoritiesFrom} derives the bare code below from it, so the URL rules match
 * scoped and unscoped holders alike. Where the district matters ({@code FTA_ADMIN},
 * {@code FTA_RANGE_DISTRICT_ADMIN}), ask {@link LoggedUserHelper}.
 */
@Component("roles")
public class RoleConstants {

    /** CSS role / Spring authority for full read-write access. */
    public static final String ADMIN_AUTHORITY = "FTA_ADMIN";

    /**
     * CSS role / Spring authority for timber mark administrators: a narrow set of reads
     * (tenure and timber mark search and their details, private marks) plus private-mark
     * writes.
     */
    public static final String TIMBER_MARK_HEADQUARTERS_AUTHORITY =
        "FTA_TIMBER_MARK_HEADQUARTERS_ADMIN";

    /**
     * CSS role / Spring authority for timber mark district users: everything
     * {@link #TIMBER_MARK_HEADQUARTERS_AUTHORITY} may do, and their certificate print marks an issued
     * mark issued (HN to HI) — legacy FTA510's district print.
     */
    public static final String TIMBER_MARK_DISTRICT_AUTHORITY = "FTA_TIMBER_MARK_DISTRICT_ADMIN";

    /**
     * CSS role / Spring authority for harvest authorization senior staff: everything
     * {@link #HARVEST_AUTH_DISTRICT_AUTHORITY} may do, plus legacy's Senior Admin and Feature
     * Admin rights (terms after issue, status rollbacks, postponements, suspensions, relabel
     * and delete).
     */
    public static final String HARVEST_AUTH_SENIOR_AUTHORITY = "FTA_HARVEST_AUTH_SENIOR_ADMIN";

    /**
     * CSS role / Spring authority for harvest authorization staff: edits cutting permits and
     * cut blocks. Despite the name it is not limited to one district; like the timber mark
     * roles it names legacy's organization level, not a geographic scope.
     */
    public static final String HARVEST_AUTH_DISTRICT_AUTHORITY = "FTA_HARVEST_AUTH_DISTRICT_ADMIN";

    /**
     * CSS role / Spring authority for apportionment (APT) editors: every read and write under
     * {@code /api/fta/apt/**}, for any org unit, and nothing else in FTA — legacy APT2 was an
     * application of its own. Legacy APT2's {@code APT_EDITOR}.
     */
    public static final String APT_AUTHORITY = "FTA_APT_ADMIN";

    /**
     * CSS role / Spring authority for apportionment (APT) readers: every read under
     * {@code /api/fta/apt/**}, reports included, and nothing else in FTA. Legacy APT2's
     * {@code APT_VIEWER}.
     */
    public static final String APT_VIEWER_AUTHORITY = "FTA_APT_VIEWER";

    /**
   * CSS role / Spring authority for range headquarters staff: everything
   * {@link #RANGE_DISTRICT_AUTHORITY} may do, in every district, plus the range admin and billing
   * screens (631 Manage Zone, 640 Archive Tenures, 680/685/686/690/695 billing, 699 Range Rates).
   * Any grant counts as all districts.
   */
  public static final String RANGE_HEADQUARTERS_AUTHORITY = "FTA_RANGE_HEADQUARTERS_ADMIN";

  /**
   * CSS role / Spring authority for range district staff: reads range tenures and range units,
   * and edits range files administered by its district (legacy {@code FTA_DIST_RANGE_TECH}).
   * FAM may grant it district-scoped ({@code FTA_RANGE_DISTRICT_ADMIN_DISTRICT-DCC}); unscoped,
   * it covers every district. See {@link LoggedUserHelper#mayEditRangeDistrict}.
   */
  public static final String RANGE_DISTRICT_AUTHORITY = "FTA_RANGE_DISTRICT_ADMIN";

  /** CSS role / Spring authority for read-only access. */
    public static final String VIEWER_AUTHORITY = "FTA_VIEWER";

    public final Role FTA_ADMIN = Role.FTA_ADMIN;
    public final Role FTA_HARVEST_AUTH_SENIOR_ADMIN = Role.FTA_HARVEST_AUTH_SENIOR_ADMIN;
    public final Role FTA_HARVEST_AUTH_DISTRICT_ADMIN = Role.FTA_HARVEST_AUTH_DISTRICT_ADMIN;
    public final Role FTA_RANGE_HEADQUARTERS_ADMIN = Role.FTA_RANGE_HEADQUARTERS_ADMIN;
    public final Role FTA_RANGE_DISTRICT_ADMIN = Role.FTA_RANGE_DISTRICT_ADMIN;
    public final Role FTA_TIMBER_MARK_HEADQUARTERS_ADMIN = Role.FTA_TIMBER_MARK_HEADQUARTERS_ADMIN;
    public final Role FTA_TIMBER_MARK_DISTRICT_ADMIN = Role.FTA_TIMBER_MARK_DISTRICT_ADMIN;
    public final Role FTA_APT_ADMIN = Role.FTA_APT_ADMIN;
    public final Role FTA_APT_VIEWER = Role.FTA_APT_VIEWER;
    public final Role FTA_VIEWER = Role.FTA_VIEWER;
}
