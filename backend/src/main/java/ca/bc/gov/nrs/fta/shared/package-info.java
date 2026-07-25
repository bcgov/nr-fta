/**
 * Shared core module — the "tenure spine" and cross-cutting capabilities that
 * every business line depends on (client/party registry, code lists, common
 * DTOs, application metadata).
 *
 * <p>In the legacy FTA system, Harvest/Tenures, Range, and Private Timber Marks
 * all hang off the same {@code FOREST_FILE} / {@code PROV_FOREST_USE} / {@code CLIENT}
 * tables and a shared {@code FTC_*} framework. This module is where that shared
 * core lives so the three business modules ({@code tenure}, {@code range},
 * {@code mark}) depend on {@code shared} only — never on each other.
 *
 * <p>Architecture: this application is a <strong>modular monolith</strong>. The
 * four top-level domain packages ({@code shared}, {@code tenure}, {@code range},
 * {@code mark}) are the module boundaries. Keeping them clean now is what makes
 * a future extraction into a separate service a packaging change rather than a
 * rewrite. See {@code /docs/adr/0001-modular-monolith.md}.
 */
package ca.bc.gov.nrs.fta.shared;
