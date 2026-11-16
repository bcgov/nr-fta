/**
 * Apportionment (APT) — management units and their history, partitions, partition
 * apportionments, commitments, licences, related-client linkages and the APT reports. Ported
 * from the APT2 application ({@code nr-apt2}). APT2 called the {@code THE.APT_*} functions and
 * procedures; this module reads and writes the {@code THE.APT_*} tables directly instead,
 * reproducing those routines' rules, messages and journal rows in Java.
 *
 * <p>Every endpoint lives under {@code /api/fta/apt/**}: reads are open to every role that reads
 * FTA and to the two APT roles, {@code FTA_APT_VIEWER} and {@code FTA_APT_ADMIN} (APT2's
 * APT_VIEWER and APT_EDITOR), which reach nothing else; writes need {@code FTA_ADMIN} or
 * {@code FTA_APT_ADMIN} (see {@code ApiAuthorizationCustomizer}). No org-unit scoping, as in
 * APT2.
 * */
package ca.bc.gov.nrs.fta.apt;
