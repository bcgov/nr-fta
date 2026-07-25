/**
 * Private Timber Marks module — mark registration, amendment, land index,
 * cancellation, transfer and redesignation (legacy screens {@code FTA_5xx} /
 * {@code FTA_101_CHANGE_MARK_DESIG} / {@code FTA_230_MARKTRANFER}).
 *
 * <p>Timber marks share the tenure and client model in the legacy system, so
 * this module depends on {@code ca.bc.gov.nrs.fta.shared} and must not reference
 * {@code tenure} or {@code range} directly.
 */
package ca.bc.gov.nrs.fta.mark;
