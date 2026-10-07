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
| `USER_LOOKUP_BASE_URL` | nr-user-lookup-api base URL (IDIR directory) | - |
| `USER_LOOKUP_TOKEN_URL` | Keycloak token endpoint for `client_credentials` | - |
| `USER_LOOKUP_CLIENT_ID` | FTA's Keycloak service-account client id | - |
| `USER_LOOKUP_CLIENT_SECRET` | FTA's Keycloak service-account secret | - |
| `USER_LOOKUP_SCOPE` | Optional explicit scope request (normally blank) | - |
| `USER_LOOKUP_CACHE_TTL` | How long a resolved name is cached (ISO-8601) | `PT12H` |

CORS origins are **not** read from an `ALLOWED_ORIGINS` variable. They come from
`ca.bc.gov.nrs.frontend.url` in `application.yml`, which is `http://localhost:3000`.
That only matters for local dev: in a deployment Caddy reverse-proxies `/api/*`
same-origin, and `server.forward-headers-strategy: framework` makes Spring
reconstruct the browser-facing URL, so the request never looks cross-origin.

### User names (nr-user-lookup-api)

FTA's records name people by IDIR id — every write records `IDIR\USERNAME`, upper-cased, as
legacy did. Screens show the person's name instead, resolved from
nr-user-lookup-api (bcgov/nr-user-lookup-api), the shared BC Gov IDIR directory. The setup follows REPT (client, token source, provisioning) and FSP (batch resolve):

- `user/UserLookupClient` calls `GET /api/v1/user-lookup/idir-account-detail`, authenticated
  with FTA's own Keycloak `client_credentials` token (`user/ClientCredentialsTokenSource`, cached
  until ~60s before expiry). The caller's token is not forwarded.
- `POST /api/fta/users/resolve` (`{"userIds": ["IDIR\\JSMITH", ...]}` → `{"IDIR\\JSMITH":
  "Jane Smith"}`) resolves a screen's ids in one call, open to every role that reads FTA.
  `user/UserDirectoryService` caches names in memory; ids it can't resolve are left out and
  the screen shows the id.
- The frontend's `<UserName userId=…>` (`components/UserName`, `lib/userNameStore`) batches
  every id on screen into that one request and caches names for the browser session.

Unconfigured (blank `USER_LOOKUP_*`, e.g. PR previews and local runs) is fine: names show as
their IDIR ids. Set token-url / client-id / client-secret together or none — a partial set fails
at startup.

**Keycloak provisioning.** `.github/scripts/ensure-keycloak-service-account.sh` idempotently
creates the `nr-fta-backend` confidential service-account client and assigns the
`user-lookup:idir:read` scope as a **default** client scope. It runs from
`reusable-deploy.yml` before the backend deploy and hands the client id/secret to the deploy
step as masked outputs. The scope itself is owned by nr-user-lookup-api — the script errors if
it is missing. The step is skipped when `KEYCLOAK_SA_CLIENT_ID` isn't set.

GitHub settings per environment (dev/test/prod):
- secrets `KEYCLOAK_SA_CLIENT_ID`, `KEYCLOAK_SA_CLIENT_SECRET` (an admin service account with
  realm-management `manage-clients`) and `USER_LOOKUP_BASE_URL`;
- variable `KEYCLOAK_SA_ISSUER_URI` — the **`forests`** realm, where nr-user-lookup-api's
  service accounts live (as for REPT), **not** `KEYCLOAK_ISSUER_URI`, the `standard` realm users
  sign in through: that environment's loginproxy issuer ending in `/realms/forests`. The token
  URL is derived from it.

### Certificate signatures (FTA402)

The Registered Timber Mark Certificate is signed by the official who issued the mark, as legacy
signed it: their scanned signature, name and title, chosen by matching their IDIR within the
mark's `PRIVATE_MARK_ACTIVATED_USERID`. The scanned signatures are **not in this repository** —
it is public, and they would let anyone forge a certificate. They live in GitHub secrets and
every TEST/PROD deploy puts them in OpenShift; there is no manual step.

They start as a local folder (kept out of any git checkout) holding the images and a
`signatories.properties`:

```properties
# key: an IDIR username, found anywhere in the issuing user's id
JSMITH.name=Jane Smith
JSMITH.title=Registrar of Timber Marks
JSMITH.image=js_signature.png
```

The legacy images are in fta-archive, `JCRS/FTA/Images/*_signature.png.data` (plain PNGs —
rename each to `.png`); the legacy names and titles are in the FTA402 report there.

To set or change them, from the repo root with `gh` logged in as a repo admin:

```sh
.github/scripts/set-certificate-signatures.sh <folder>
```

It zips the folder, base64-encodes it and splits it into the repo secrets
`CERTIFICATE_SIGNATURES_1..4` (GitHub caps a secret at 48 KB). The next TEST/PROD deploy
(`merge.yml` → `reusable-deploy.yml`) joins them, checks they unzip, and passes them to this
template, which keeps them in the Secret `nr-fta-certificate-signatures-<zone>`, mounted at
`/signatures` (`CERTIFICATE_SIGNATURES_DIR`). A hash of them sits on the pod template, so a
change restarts the backend, which logs `Certificate signatures: N signatories loaded.` at the
first print.

PR previews are deployed without signatures, as is any environment while the secrets are unset:
certificates then print a blank signature line over "Registrar of Timber Marks". The font is
legacy's Arial, as embedded Liberation Sans (metric-compatible; `src/main/resources/fonts`).

### Spring Profiles

| Profile | Description |
|---------|-------------|
| `oracle` | Oracle datasource over TCPS (re-enables the `DataSourceAutoConfiguration` that `application.yml` excludes); required in all environments. |
| `local`  | Local-dev only. Loads `application-local.yml` so credentials don't need to be exported as env vars. Activate alongside `oracle` (`SPRING_PROFILES_ACTIVE=local,oracle`). |

## API Endpoints

Grouped by domain slice; see each `*/controller/` package for full
request/response shapes. All `/api/fta/**` routes are bearer-token-protected and
require `FTA_ADMIN` or `FTA_VIEWER`; writes are `FTA_ADMIN` only, and
`/api/fta/admin/**` is `FTA_ADMIN` for *every* method including GET.
`FTA_TIMBER_MARK_HEADQUARTERS_ADMIN` and `FTA_TIMBER_MARK_DISTRICT_ADMIN` get an allow-list instead
(the same one; a district user's certificate print also moves the mark from HN to HI): GET on tenure and timber
mark search, the tenure / cutting-permit / private-mark details, the code lists
and the client type-ahead, plus `POST /api/fta/marks`. The authoritative matrix
is `security/ApiAuthorizationCustomizer`.

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
