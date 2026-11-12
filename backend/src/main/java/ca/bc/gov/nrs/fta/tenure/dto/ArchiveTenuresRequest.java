package ca.bc.gov.nrs.fta.tenure.dto;

/**
 * Criteria for FTA640 Archive Tenures: the count ({@code GET
 * /api/fta/admin/archive-tenures}, as query parameters) and the archive
 * ({@code POST}, as the body).
 *
 * <p>As legacy {@code THE.FTA_640_ARCHIVETEN}, the archive covers every active
 * grazing (E01–E03) and hay (H01–H03) range tenure in the region (or the one
 * district) whose term expired in {@code expiryYear}. The user never picks
 * individual files.
 *
 * @param regionNo   ORG_UNIT_NO of the region; required
 * @param districtNo ORG_UNIT_NO of a district within that region; optional
 * @param expiryYear the year the tenures' terms expired; required
 */
public record ArchiveTenuresRequest(Long regionNo, Long districtNo, Integer expiryYear) {}
