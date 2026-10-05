package ca.bc.gov.nrs.fta.tenure.tab.details;

import java.util.List;
import java.util.Map;

/**
 * The Details tab of the tenure page — legacy FTA100, as {@code FTA_100_TENURE.GET} returns it.
 *
 * @param forestFileId the file
 * @param values       every value the screen shows, keyed by the package parameter's name in
 *                     camelCase without its {@code p_} ({@code p_file_status_st} →
 *                     {@code fileStatusSt}); dates are {@code yyyy-mm-dd}; blank is {@code ""}
 * @param descriptions "CODE - description" for each coded value, by the same keys
 * @param layout       which of the screen's sections and fields legacy shows for this file
 * @param rules        whether it can be saved, why not, and which fields open for editing
 * @param revisions    the revision counts (and harvesting-authority key) the save is locked on
 *                     — send them back unchanged with the PUT
 * @param notices      the package's warnings on reading the file (legacy's yellow messages)
 */
public record TenureDetailsDto(
    String forestFileId,
    Map<String, String> values,
    Map<String, String> descriptions,
    TenureDetailsLayout layout,
    TenureDetailsRules rules,
    Map<String, String> revisions,
    List<String> notices) {}
