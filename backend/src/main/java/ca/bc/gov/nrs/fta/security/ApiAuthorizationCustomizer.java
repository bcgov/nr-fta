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
 *   <li><strong>FTA_TIMBER_MARK_HEADQUARTERS_ADMIN</strong> and
 *       <strong>FTA_TIMBER_MARK_DISTRICT_ADMIN</strong> (identical here; they differ only in
 *       what printing a certificate does) — an allow-list: {@code GET} on the endpoints behind
 *       Tenure Search, Timber Mark Search, the tenure / cutting-permit / private-mark details
 *       and the code lists those screens read, plus creating a mark application
 *       ({@code POST /api/fta/marks}), a note on a mark ({@code POST /api/fta/marks/{id}/notes}),
 *       printing its certificate ({@code POST /api/fta/marks/{id}/print}), adding a land
 *       index ({@code POST /api/fta/marks/{id}/land-index}), a client
 *       ({@code POST /api/fta/marks/{id}/clients}) or an amendment
 *       ({@code POST /api/fta/marks/{id}/amendments}), submitting it to Headquarters
 *       ({@code POST /api/fta/marks/{id}/submit}), and saving it
 *       ({@code PUT /api/fta/marks/{id}}). Every other endpoint is 403. Mirrors the page
 *       allow-list in the frontend's {@code routes/access.ts}.</li>
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

    // Mark application — FTA_ADMIN or FTA_TIMBER_MARK_HEADQUARTERS_ADMIN. Exact path, so the mark
    // transfer (/api/fta/marks/transfer, an admin screen) stays FTA_ADMIN only.
    // Must appear BEFORE the generic POST /api/** admin-only rule.
    authorize
        .requestMatchers(HttpMethod.POST, "/api/fta/marks")
        .hasAnyAuthority(
            RoleConstants.ADMIN_AUTHORITY, RoleConstants.TIMBER_MARK_HEADQUARTERS_AUTHORITY,
            RoleConstants.TIMBER_MARK_DISTRICT_AUTHORITY);

    // Notes on a private mark — FTA_ADMIN or FTA_TIMBER_MARK_HEADQUARTERS_ADMIN, like the mark
    // application. Must appear BEFORE the generic POST /api/** admin-only rule.
    authorize
        .requestMatchers(
            HttpMethod.POST,
            "/api/fta/marks/*/notes",
            "/api/fta/marks/*/print",
            "/api/fta/marks/*/land-index",
            "/api/fta/marks/*/clients",
            "/api/fta/marks/*/amendments",
            "/api/fta/marks/*/submit")
        .hasAnyAuthority(
            RoleConstants.ADMIN_AUTHORITY, RoleConstants.TIMBER_MARK_HEADQUARTERS_AUTHORITY,
            RoleConstants.TIMBER_MARK_DISTRICT_AUTHORITY);

    // Saving a private mark (FTA510) — FTA_ADMIN or either timber mark role; which fields the
    // save accepts is MarkEditRules. Must appear BEFORE the generic PUT /api/** rule.
    authorize
        .requestMatchers(HttpMethod.PUT, "/api/fta/marks/*")
        .hasAnyAuthority(
            RoleConstants.ADMIN_AUTHORITY, RoleConstants.TIMBER_MARK_HEADQUARTERS_AUTHORITY,
            RoleConstants.TIMBER_MARK_DISTRICT_AUTHORITY);

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

    // Reads open to FTA_TIMBER_MARK_HEADQUARTERS_ADMIN as well — only what its screens call. Must
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
            RoleConstants.TIMBER_MARK_HEADQUARTERS_AUTHORITY,
            RoleConstants.TIMBER_MARK_DISTRICT_AUTHORITY);

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
