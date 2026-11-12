package ca.bc.gov.nrs.fta.tenure.dto;

/**
 * One district's row of the FTA640 count: how many active grazing and hay
 * tenures the archive would change.
 *
 * @param district     "CODE - name" of the district
 * @param grazingCount active E01–E03 tenures expiring in the year
 * @param hayCount     active H01–H03 tenures expiring in the year
 */
public record ArchiveTenuresSummaryDto(String district, long grazingCount, long hayCount) {}
