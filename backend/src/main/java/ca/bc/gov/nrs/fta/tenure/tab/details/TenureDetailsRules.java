package ca.bc.gov.nrs.fta.tenure.tab.details;

import java.util.List;

/**
 * What a Headquarters FTA Senior Admin may change on FTA100 for this file — the same rules the
 * PUT applies (see {@link TenureDetailsForm}).
 *
 * @param editable          whether Save is enabled ({@code p_disable_save_ind}, a file type)
 * @param reason            why not, when {@code editable} is false
 * @param fields            the value keys that open for editing (as in
 *                          {@link TenureDetailsDto#values()})
 * @param conditionalFields keys that open only on another value: FUP First Nations fields when
 *                          FUP Type is FN, Pulpwood File when Purpose is PA
 * @param overrideStatuses  target statuses whose change needs a District Override Reason
 *                          ({@code FTA100_EDIT_STATUS_CHANGE}); a held file (H…, not HN) needs
 *                          one too when Effective Date, the term, Ext. Count or Initial Expiry
 *                          Date change
 * @param statusList        which option list Status offers: {@code statuses} or
 *                          {@code recreationStatuses}
 * @param purposeList       which option list Purpose offers: {@code forLicenceToCut},
 *                          {@code specialUse} or {@code occLicenceToCut}
 */
public record TenureDetailsRules(
    boolean editable,
    String reason,
    List<String> fields,
    List<String> conditionalFields,
    List<String> overrideStatuses,
    String statusList,
    String purposeList) {}
