package ca.bc.gov.nrs.fta.security;

import ca.bc.gov.nrs.fta.dto.Role;
import org.springframework.stereotype.Component;


/**
 * Exposes role constants as Spring beans for use in SpEL expressions and security configuration.
 *
 * <p>The string constants {@link #ADMIN_AUTHORITY} and {@link #VIEWER_AUTHORITY} match
 * the Cognito group names and are used in {@code ApiAuthorizationCustomizer} for
 * URL-level access control via {@code hasAuthority()} / {@code hasAnyAuthority()}.
 */
@Component("roles")
public class RoleConstants {

    /** Cognito group / Spring authority for full read-write access. */
    public static final String ADMIN_AUTHORITY = "FTA_ADMIN";

    /** Cognito group / Spring authority for read-only access. */
    public static final String VIEWER_AUTHORITY = "FTA_VIEWER";

    public final Role FTA_ADMIN = Role.FTA_ADMIN;
    public final Role FTA_VIEWER = Role.FTA_VIEWER;
}
