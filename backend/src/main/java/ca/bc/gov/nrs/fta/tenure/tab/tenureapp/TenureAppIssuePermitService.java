package ca.bc.gov.nrs.fta.tenure.tab.tenureapp;

import ca.bc.gov.nrs.fta.integration.DocumentStore;
import ca.bc.gov.nrs.fta.integration.DocumentStore.StoredDocument;
import ca.bc.gov.nrs.fta.integration.EmailSender;
import ca.bc.gov.nrs.fta.integration.IntegrationException;
import ca.bc.gov.nrs.fta.integration.IntegrationNotConfiguredException;
import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppIssuePermitRequest;
import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppIssuePermitResult;
import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppPermitFile;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Legacy FTA950's Issue Permit (Fta950xTenAppIssuePermitAction.handleUpload), step for step:
 *
 * <ol>
 *   <li>{@code FTA_950X_TEN_APP_ISSUE_PERMIT.mainline('ISSUE_PERMIT', …)} with {@code
 *       p_validate_only_no_transaction = 'TRUE'} — the issuance checks alone;
 *   <li>the permit file uploaded to document management under the tenure, titled with the
 *       document name ({@link DocumentStore}; legacy: DmViewService.uploadFile);
 *   <li>the package again with {@code 'FALSE'} and the document's link — the permit document
 *       records, the authority and file to HI, the application to ISS;
 *   <li>commit;
 *   <li>the e-mail to the recipients the user added ({@link EmailSender}; legacy: NRS
 *       Common Messaging) — subject "Forest Permit {file type} {file id}", a link to the
 *       document (legacy: the DMS viewer; here a time-limited presigned link, the bucket being
 *       private), and the file attached when Attach Document To Email is checked.
 * </ol>
 *
 * <p>The package (2,400 lines of CP / file / range / FSJ / FUP First Nations issuance rules) is
 * called rather than ported, so its rules stay exactly legacy's. Steps 1-3 share one
 * transaction: an error from either pass, or a failed upload, rolls everything back. (ISSUE_PERMIT
 * opens a cleanup of the application's earlier permit documents on both passes, but its cursor
 * keys on the application's GUID before it is read, so it deletes nothing; the validation pass
 * writes nothing.) As in legacy, a document uploaded before a failing second pass stays in the
 * store, and an e-mail failure comes after the commit, so the permit stays issued — the result
 * then carries the reason as {@code emailWarning}.
 */
@Service
public class TenureAppIssuePermitService {

  private static final Logger LOG = LoggerFactory.getLogger(TenureAppIssuePermitService.class);

  /** fta.web.warning.permitIssued. */
  static final String ISSUED = "The Permit has been successfully issued.";

  private final TenureAppIssuePermitDao dao;
  private final DocumentStore documents;
  private final EmailSender email;
  private final TransactionTemplate tx;

  public TenureAppIssuePermitService(
      TenureAppIssuePermitDao dao,
      DocumentStore documents,
      EmailSender email,
      PlatformTransactionManager transactionManager) {
    this.dao = dao;
    this.documents = documents;
    this.email = email;
    this.tx = new TransactionTemplate(transactionManager);
  }

  /**
   * Issues the permit for one of the file's applications.
   *
   * @throws ResponseStatusException 404 if the application or file is not found; 409 if the
   *     application is not approved or issued; 400 on a failed popup or issuance check; 503 when
   *     document storage is not configured; 502 when the upload fails
   */
  public TenureAppIssuePermitResult issue(
      String forestFileId,
      long tenureAppId,
      TenureAppIssuePermitRequest q,
      TenureAppPermitFile file,
      String userId) {
    String state = dao.applicationState(forestFileId, tenureAppId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Tenure application " + tenureAppId + " is not on this file."));
    if (!TenureAppRules.canIssue(state)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, TenureAppRules.issueBlockedReason(state));
    }
    String documentName = q == null ? null : q.documentName();
    List<String> recipients = q == null || q.recipients() == null ? List.of() : q.recipients();
    String error = TenureAppRules.validateIssue(
        documentName,
        file == null ? null : file.fileName(),
        file == null ? 0 : file.size(),
        recipients);
    if (error != null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error);
    }
    Long hvaSkey = q.hvaSkey();
    if (hvaSkey != null && !dao.hvaOnFile(forestFileId, hvaSkey)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "The harvesting authority is not on this file.");
    }
    String fileType = dao.fileType(forestFileId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenure not found."));

    StoredDocument doc = tx.execute(status -> {
      // The checks alone (legacy passed no document yet).
      fail(dao.issuePermit(forestFileId, fileType, tenureAppId, hvaSkey, null, userId, true));
      StoredDocument stored = upload(forestFileId, documentName, file);
      if (!TenureAppRules.linkFits(stored.link())) {
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY,
            "Document storage returned a link longer than " + TenureAppRules.MAX_URI
                + " bytes.");
      }
      // The issuance itself, recording the document's link.
      fail(dao.issuePermit(
          forestFileId, fileType, tenureAppId, hvaSkey, stored.link(), userId, false));
      return stored;
    });

    String emailWarning = null;
    try {
      email.send(
          subject(fileType, forestFileId),
          body(forestFileId, doc.viewerUrl()),
          recipients,
          q.attachDocument()
              ? List.of(new EmailSender.Attachment(
                  file.fileName(), "application/pdf", file.content()))
              : List.of());
    } catch (IntegrationException e) {
      LOG.warn("Permit for application {} issued, e-mail not sent: {}", tenureAppId,
          e.getMessage(), e);
      emailWarning = "The e-mail to the recipients could not be sent: " + e.getMessage();
    }
    return new TenureAppIssuePermitResult(tenureAppId, ISSUED, emailWarning);
  }

  private StoredDocument upload(String forestFileId, String title, TenureAppPermitFile file) {
    try {
      return documents.upload(forestFileId, title, file.fileName(), file.content());
    } catch (IntegrationNotConfiguredException e) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), e);
    } catch (IntegrationException e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
    }
  }

  /** ApplicationResources email.subject: "Forest Permit {0} {1}". */
  static String subject(String fileType, String forestFileId) {
    return "Forest Permit " + fileType + " " + forestFileId;
  }

  /** ApplicationResources email.body. */
  static String body(String forestFileId, String viewerUrl) {
    return "The following link will provide access to the issued permit for " + forestFileId
        + ". <br><br> " + viewerUrl;
  }

  /** Throws 400 with the package's message (rolling the transaction back) when it gave one. */
  private static void fail(String error) {
    String message = TenureAppLegacyMessages.readable(error);
    if (message != null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
  }
}
