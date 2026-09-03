# FTA Backend

Spring Boot backend service for the Forest Tenure Administration application.

## Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 21 | Runtime |
| Spring Boot | 3.5.x | Framework |
| Spring Security | 6.5.x | OAuth2 Resource Server + JWT |
| Oracle JDBC | 21.3.x (ojdbc11) | Database connectivity (TCPS to BC Gov shared Oracle) |
| Undertow | 2.3.x | Embedded HTTP server (Tomcat excluded) |
| JasperReports | 6.21.5 | On the classpath for future PDF reports — **no report code or endpoint exists yet** |
| Lombok | 1.18.x | Boilerplate reduction |
| Resilience4j | 2.3.x | Circuit breaker / retry |

## 🚀 Running Locally

See the [root README's Local Development section](../README.md#local-development) — both the direct (`mvn spring-boot:run`) and Docker Compose workflows are documented there in one place, alongside the property-file setup (`application-local.yml`, `jssecacerts` truststore).

## 🔧 Configuration

### Environment Variables

In OpenShift deployments these come from the K8s Secret built by `openshift.deploy.yml`. For local dev they live in `application-local.yml` (see root README for setup).

| Variable | Description             | Default |
|----------|-------------------------|---------|
| `SERVER_PORT` | Server port             | 8080 |
| `SPRING_PROFILES_ACTIVE` | Active profiles         | oracle |
| `KEYCLOAK_ISSUER_URI` | BC Gov SSO standard-realm issuer URI (JWKS is derived as `<issuer>/protocol/openid-connect/certs`) | - |
| `KEYCLOAK_CLIENT_ID` | CSS integration client id, checked as the token's `azp` | - |
| `DATABASE_HOST` | Oracle DB host          | - |
| `DATABASE_PORT` | Oracle listener port (TCPS) | 1543 |
| `DATABASE_SERVICE_NAME` | Oracle service name     | - |
| `DATABASE_USER` | DB username             | - |
| `DATABASE_PASSWORD` | DB password             | - |
| `TRUSTSTORE_PATH` | Path to `jssecacerts` JKS | /cert/jssecacerts |
| `KEYSTORE_SECRET` | Truststore passphrase   | - |

CORS origins are **not** read from an `ALLOWED_ORIGINS` variable. They come from
`ca.bc.gov.nrs.frontend.url` in `application.yml`, which is `http://localhost:3000`.
That only matters for local dev: in a deployment Caddy reverse-proxies `/api/*`
same-origin, and `server.forward-headers-strategy: framework` makes Spring
reconstruct the browser-facing URL, so the request never looks cross-origin.

### Spring Profiles

| Profile | Description |
|---------|-------------|
| `oracle` | Oracle datasource over TCPS (re-enables the `DataSourceAutoConfiguration` that `application.yml` excludes); required in all environments. |
| `local`  | Local-dev only. Loads `application-local.yml` so credentials don't need to be exported as env vars. Activate alongside `oracle` (`SPRING_PROFILES_ACTIVE=local,oracle`). |

## API Endpoints

Grouped by domain slice; see each `*/controller/` package for full
request/response shapes. All `/api/fta/**` routes are bearer-token-protected and
require `FTA_ADMIN` or `FTA_VIEWER`; writes are `FTA_ADMIN` only, and
`/api/fta/admin/**` is `FTA_ADMIN` for *every* method including GET. The
authoritative matrix is `security/ApiAuthorizationCustomizer`.

| Slice | Base paths | Methods |
|---|---|---|
| Actuator | `/actuator/health`, `/actuator/info`, `/actuator/prometheus` | Public; OpenShift probes + Prometheus scrape |
| Welcome | `/api/fta` | GET |
| Tenure | `/api/fta/tenures`, `/api/fta/roads` | GET, POST |
| Harvesting | `/api/fta/harvesting-authorities`, `/api/fta/cutting-permits`, `/api/fta/cut-blocks` | GET, POST (assign marks, suspend blocks, cut-block actions) |
| Inbox / applications | `/api/fta/inbox`, `/api/fta/applications`, `/api/fta/applications/{esfId}/actions`, `/api/fta/exhibit-a` | GET, POST (adjudication, Exhibit A upload) |
| Marks | `/api/fta/marks`, `/api/fta/timber-marks` | GET, POST |
| Range | `/api/fta/range-tenures`, `/api/fta/range-units` | GET |
| Reference search | `/api/fta/clients`, `/api/fta/management-units` | GET |
| Admin | `/api/fta/admin/audit`, `.../rates`, `.../billing`, `.../range-zones`, `.../org-unit-default`, `.../archive-tenures`, `/api/fta/marks/transfer` | GET, POST, PUT — `FTA_ADMIN` only |
| Oracle smoke test | `/internal/oracle` | GET; connectivity check |

## 🧪 Testing

```bash
# Unit tests (surefire)
mvn test

# With JaCoCo coverage + integration tests (failsafe)
mvn verify -Pcoverage

# Skip tests during build
mvn package -DskipTests
```

Current unit-test coverage is `JwtPrincipalUtilTest`, which pins the two
claim-mapping rules whose failure modes are silent — see
[../docs/architecture.md](../docs/architecture.md). The native SQL has **not**
been validated against a real `THE` schema; the app needs an Oracle datasource
to boot at all.

## 📁 Project Structure

```
backend/
├── src/main/java/ca/bc/gov/nrs/fta/
│   ├── FtaApiApplication.java  # Spring Boot entry point
│   ├── FtaApiConstants.java    # Shared constants
│   ├── configuration/          # Spring + Web + Security + CORS config beans
│   ├── security/               # Resource server, authorization matrix, CSRF,
│   │                           #   headers/CSP, role constants, @auth helper
│   ├── tenure/                 # ── domain slices ──────────────────────────
│   ├── mark/                   #    each with controller/ + service/ + dto/
│   ├── range/                  #
│   ├── shared/                 #    cross-cutting screens (audit, rates,
│   │                           #      billing, client + mgmt-unit search)
│   ├── dto/                    # Cross-slice records (Role, IdentityProvider)
│   ├── exception/              # @ControllerAdvice + custom exceptions
│   ├── util/                   # JwtPrincipalUtil and friends
│   ├── controller/             # Only OracleSmokeController
│   └── entity/, repository/    # Placeholder files only — see note below
└── src/main/resources/
    ├── application.yml         # Main config (always loaded)
    ├── application-oracle.yml  # `oracle` profile — datasource + TCPS truststore
    ├── application-local.yml   # `local` profile — credentials (gitignored)
    └── cert/jssecacerts        # Oracle TLS truststore (gitignored)
```

**There is no ORM layer.** Every service uses `NamedParameterJdbcTemplate` with
native SQL ported from the legacy `THE.FTA_*` PL/SQL packages — 34 services do,
and nothing in the tree declares `@Entity` or extends `JpaRepository`. The
`entity/` and `repository/` packages hold comment-only placeholder files left
behind when user-preference persistence was dropped; they compile to nothing.
`spring-boot-starter-data-jpa` is still a dependency, which is why Hibernate
appears in the logging config.

## Origins

This repo was scaffolded from [bcgov/quickstart-openshift](https://github.com/bcgov/quickstart-openshift), then specialised for FTA's needs:

- Database swapped from Postgres to BC Gov shared Oracle (TCPS connection, JKS truststore).
- Reports run via the embedded JasperReports library — no remote Jasper server.
- Per-PR redirect URIs handled via slot bucketing (see root README) — a Cognito-era workaround that may be removable under CSS.

Upstream conventions for build/deploy actions, OpenShift templates, and PR preview environments still apply where unmodified; check the quickstart for context if something looks unfamiliar.

## Resources

[NRM Architecture Confluence: GitHub Repository Best Practices](https://apps.nrs.gov.bc.ca/int/confluence/x/TZ_9CQ)
