package ca.bc.gov.nrs.fta.tenure.dto;

import java.util.List;

/**
 * Request body for archiving tenures ({@code POST /api/fta/admin/archive-tenures}).
 *
 * <p>The FTA640 screen is an explicit multi-select: the user picks the exact
 * forest files to archive. The archive is therefore scoped to the listed
 * {@code forestFileIds} and never to a broad org/expiry-year filter — only the
 * still-active files among those listed are archived, so the operation can
 * never affect more tenures than the user selected.
 */
public record ArchiveTenuresRequest(List<String> forestFileIds) {}
