package ca.bc.gov.nrs.fta.tenure.dto;

import java.time.LocalDate;

/**
 * A single ESF tenure-application row in the FTA300 Inbox worklist.
 *
 * <p>Mirrors the {@code rec_inbox} record returned by the legacy Oracle package
 * {@code THE.FTA_300N_INBOX} (see
 * {@code fta-archive/fta/database/ddl/pkg/fta_300n_inbox.pks}) — the columns the
 * inbox {@code GET} cursor opens for pending ({@code TENURE_APPLICATION_STATE_CODE
 * = 'INB'}) electronic submissions awaiting adjudication. Field order and names
 * follow the record declaration; VARCHAR2 indicator columns stay as
 * {@code String}, {@code SUBMISSION_DATE} is exposed as a {@link LocalDate}.
 */
public record InboxDto(
    Long tenureAppId,
    Long submissionId,
    Double maxX,
    Double minX,
    Double maxY,
    Double minY,
    String clientNumber,
    Long revisionCount,
    Long taiRevisionCount,
    String forestFileId,
    String forestFileIdDisplay,
    String currentAssignedTo,
    String tenureApplicationType,
    String orgUnitName,
    Long orgUnitNo,
    Long fileBctsOrg,
    String bctsOrgCode,
    String licensee,
    LocalDate submissionDate,
    String adjudicationInd,
    String jobMemo,
    String adjReportInd,
    String bctsFileInd,
    String applicationTypeCode,
    String tenureAppPurpCode,
    String exhAImageInd,
    String fileActionLink,
    String approveEnabledInd,
    String rejectEnabledInd,
    String esfHyperlinkInd,
    String exhAActionInd,
    String bctsEsfHyperlinkInd,
    String bctsExhAActionInd,
    String fileBubbleHelp,
    String regenInProgressInd,
    String imageMimeTypeCode,
    Long hvaSkey,
    String hvaId) {}
