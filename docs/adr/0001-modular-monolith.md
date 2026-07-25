# ADR 0001 — One modular-monolith backend, not three services

- **Status:** Accepted
- **Date:** 2026-07-08
- **Context:** Greenfield rebuild of the legacy FTA application. Three business
  lines are in scope — Harvest Authorizations & Tenures, Range, and Private
  Timber Marks. (Parks & Recreation is not being migrated.)

## Decision

Build **one Spring Boot backend and one React frontend**, deployed as two
OpenShift services — matching the sibling FDS apps `nr-rept` and `nr-fsp-new`.
Structure the backend as a **modular monolith**: a single deployable whose
top-level Java packages are the module boundaries.

```
ca.bc.gov.nrs.fta
├── shared/   — the tenure spine + shared capabilities every line needs
├── tenure/   — Harvest Authorizations & Tenures
├── range/    — Range
└── mark/     — Private Timber Marks
```

Rule: `tenure`, `range`, and `mark` may depend on `shared`, but **never on each
other**. Cross-line interaction goes through the shared core.

## Why not three separate backends / three apps

The three lines are **not clean vertical slices** in the legacy system — they
share one "tenure spine" and a common framework:

- `FOREST_FILE` is referenced by ~111 of ~146 legacy PL/SQL packages;
  `PROV_FOREST_USE` by ~67. Range agreements and Private Timber Marks are
  *modelled on the same forest-file / provincial-forest-use backbone* as harvest
  tenures.
- All three share the client/party registry (`CLIENT` / `FOREST_CLIENT` /
  `CLIENT_LOCATION`), shared code lists, a shared billing/invoicing engine, and
  the shared `FTC_*` common framework — all in one Oracle schema (`THE`).

Splitting into three services now would not create a real seam; it would put a
network hop through the middle of the shared spine (a *distributed monolith*) —
paying the operational cost of microservices while keeping the coupling.

## Consequences

- **Positive:** independent evolution per line via enforced package boundaries;
  a single pipeline, auth setup, and deployment; UX can move freely between a
  forest file and its marks/range use (they are the same record underneath);
  future extraction of a line is a packaging change, not a rewrite.
- **Negative / watch-outs:** boundaries must be actively defended (an unchecked
  `range → tenure` import erodes the whole benefit). Consider adopting
  [Spring Modulith](https://spring.io/projects/spring-modulith) to verify module
  boundaries at build time once the modules have real content.

## Revisit if

A business line gains a **separate team with an independent release cadence and
budget** — team topology is the one driver that justifies eating the distributed
cost early. At that point, extract that module into its own service and stand up
the shared client / code-list / billing capabilities as shared services.
