package ca.bc.gov.nrs.fta.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

/**
 * URL-level authorization rules — the single source of truth for all backend access control.
 *
 * <h3>Rule ordering (Spring Security evaluates top-to-bottom, first match wins)</h3>
 * <ol>
 *   <li>Specific API rules are declared first so they are never shadowed by broader
 *       {@code permitAll()} patterns below.</li>
 *   <li>Static-asset and infrastructure {@code permitAll()} rules are last so they
 *       cannot accidentally expose an API path.</li>
 * </ol>
 *
 * <h3>Role matrix</h3>
 * <ul>
 *   <li><strong>FTA_ADMIN</strong> — full CRUD on every {@code /api/**} endpoint</li>
 *   <li><strong>FTA_VIEWER</strong> — {@code GET /api/**} + {@code POST /api/reports/**}
 *       (report generation); all other write methods are rejected with 403</li>
 *   <li><strong>FTA_TIMBER_MARK_ADMIN</strong> — an allow-list: {@code GET} on the endpoints
 *       behind Tenure Search, Timber Mark Search, the tenure / cutting-permit / private-mark
 *       details and the code lists those screens read, plus {@code POST /api/fta/marks} (mark
 *       application). Every other endpoint is 403. Mirrors the page allow-list in the
 *       frontend's {@code routes/access.ts}.</li>
 *   <li><strong>No recognized role</strong> — rejected (403) for any {@code /api/**} endpoint</li>
 * </ul>
 *
 * <p>Admin endpoints ({@code /api/fta/admin/**}) require {@code FTA_ADMIN} for
 * <em>all</em> HTTP methods, including {@code GET}.
 *
 * <p>A role granted with a FAM scope counts as the role itself here: a token carrying
 * {@code FTA_ADMIN_DISTRICT-DCC} holds the {@code FTA_ADMIN} authority, derived by
 * {@link TokenRoles#authoritiesFrom}. These rules decide <em>whether</em> a user may call an
 * endpoint; which districts they may act on is a question for the endpoint, via
 * {@link LoggedUserHelper#administersDistrict}.
 */
@Component
public class ApiAuthorizationCustomizer implements
    Customizer<
        AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
        > {

  @Override
  public void customize(
      AuthorizeHttpRequestsConfigurer<HttpSecurity>
          .AuthorizationManagerRequestMatcherRegistry authorize
  ) {

    // ── API rules (must come first — never shadowed by permitAll below) ─

    // Admin endpoints — FTA_ADMIN only for ALL HTTP methods (including GET)
    authorize
        .requestMatchers("/api/fta/admin/**")
        .hasAuthority(RoleConstants.ADMIN_AUTHORITY);

    // Report generation — both roles can trigger a POST report
    // Must appear BEFORE the generic POST /api/** admin-only rule
    authorize
        .requestMatchers(HttpMethod.POST, "/api/reports/**")
        .hasAnyAuthority(RoleConstants.ADMIN_AUTHORITY, RoleConstants.VIEWER_AUTHORITY);

    // Mark application — FTA_ADMIN or FTA_TIMBER_MARK_ADMIN. Exact path, so the mark
    // transfer (/api/fta/marks/transfer, an admin screen) stays FTA_ADMIN only.
    // Must appear BEFORE the generic POST /api/** admin-only rule.
    authorize
        .requestMatchers(HttpMethod.POST, "/api/fta/marks")
        .hasAnyAuthority(
            RoleConstants.ADMIN_AUTHORITY, RoleConstants.TIMBER_MARK_ADMIN_AUTHORITY);

    // Write operations — FTA_ADMIN only
    authorize
        .requestMatchers(HttpMethod.POST, "/api/**")
        .hasAuthority(RoleConstants.ADMIN_AUTHORITY);

    authorize
        .requestMatchers(HttpMethod.PUT, "/api/**")
        .hasAuthority(RoleConstants.ADMIN_AUTHORITY);

    authorize
        .requestMatchers(HttpMethod.DELETE, "/api/**")
        .hasAuthority(RoleConstants.ADMIN_AUTHORITY);

    // Reads open to FTA_TIMBER_MARK_ADMIN as well — only what its screens call. Must
    // appear BEFORE the generic GET /api/** rule, which excludes that role.
    authorize
        .requestMatchers(
            HttpMethod.GET,
            "/api/fta/code-lists/**",
            "/api/fta/clients/suggest",    // client type-ahead on the search screens
            "/api/fta/tenures",            // Tenure Search (+ /export, detail below)
            "/api/fta/tenures/*",
            "/api/fta/timber-marks",       // Timber Mark Search
            "/api/fta/timber-marks/export",
            "/api/fta/cutting-permits/*",  // the detail Timber Mark Search opens
            "/api/fta/marks",              // Application/Amendment List
            "/api/fta/marks/*")            // private mark detail
        .hasAnyAuthority(
            RoleConstants.ADMIN_AUTHORITY,
            RoleConstants.VIEWER_AUTHORITY,
            RoleConstants.TIMBER_MARK_ADMIN_AUTHORITY);

    // Read operations — FTA_ADMIN and FTA_VIEWER
    authorize
        .requestMatchers(HttpMethod.GET, "/api/**")
        .hasAnyAuthority(RoleConstants.ADMIN_AUTHORITY, RoleConstants.VIEWER_AUTHORITY);

    // ── Infrastructure / public routes (declared last) ──────────────

    // CORS pre-flight — must be permitted so browsers can negotiate auth headers
    authorize
        .requestMatchers(HttpMethod.OPTIONS, "/**")
        .permitAll();

    // Static assets served by the SPA
    authorize
        .requestMatchers(
            HttpMethod.GET,
            "/",
            "/index.html",
            "/manifest.json",
            "/robots.txt",
            "/favicon.ico",
            "/sw.js",
            "/*.js",
            "/*.css",
            "/*.png",
            "/*.svg",
            "/*.ico",
            "/*.woff2",
            "/assets/**",
            "/icons/**",
            "/screenshots/**"
        )
        .permitAll();

    // Actuator health / info probes
    authorize
        .requestMatchers(HttpMethod.GET, "/actuator/**")
        .permitAll();

    authorize
        .requestMatchers("/error")
        .permitAll();

    // SPA fallback — allows the browser to receive index.html for any
    // client-side route (e.g. /pub/fta/authCallback after the Keycloak redirect).
    // API paths are already matched and enforced above, so this cannot
    // expose any protected /api/** endpoint.
    authorize
        .requestMatchers(HttpMethod.GET, "/**")
        .permitAll();

    // ── Deny everything else ────────────────────────────────────────
    authorize
        .anyRequest().denyAll();
  }
}
