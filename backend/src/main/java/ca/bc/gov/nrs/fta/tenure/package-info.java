/**
 * Harvest Authorizations &amp; Tenures module — the largest FTA business line
 * (forest files, provincial forest uses, cutting permits, blocks, roads,
 * hauling, tenure applications; legacy screens {@code FTA_9xx} / {@code FTA_100_TENURE}).
 *
 * <p>Depends on {@code ca.bc.gov.nrs.fta.shared} only. Must not reference the
 * {@code range} or {@code mark} modules directly — cross-line interaction goes
 * through the shared core.
 */
package ca.bc.gov.nrs.fta.tenure;
