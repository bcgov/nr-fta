package ca.bc.gov.nrs.fta.tenure.tab.tenureapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.integration.DocumentStore;
import ca.bc.gov.nrs.fta.integration.DocumentStore.StoredDocument;
import ca.bc.gov.nrs.fta.integration.EmailSender;
import ca.bc.gov.nrs.fta.integration.EmailSender.Attachment;
import ca.bc.gov.nrs.fta.integration.IntegrationException;
import ca.bc.gov.nrs.fta.integration.IntegrationNotConfiguredException;
import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppIssuePermitRequest;
import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppIssuePermitResult;
import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppPermitFile;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

/** Orchestration of legacy FTA950's Upload and Issue: check, upload, issue, commit, e-mail. */
class TenureAppIssuePermitServiceTest {

  private static final String FILE = "A12345";
  private static final long APP = 77L;
  private static final String USER = "IDIR\\JSMITH";
  private static final byte[] PDF = {'%', 'P', 'D', 'F'};
  private static final StoredDocument DOC = new StoredDocument(
      "obj-1", "https://coms/api/v1/object/obj-1", "https://viewer/obj-1");

  private TenureAppIssuePermitDao dao;
  private DocumentStore documents;
  private EmailSender email;
  private PlatformTransactionManager tm;
  private TenureAppIssuePermitService service;

  @BeforeEach
  void setUp() {
    dao = mock(TenureAppIssuePermitDao.class);
    documents = mock(DocumentStore.class);
    email = mock(EmailSender.class);
    tm = mock(PlatformTransactionManager.class);
    when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    service = new TenureAppIssuePermitService(dao, documents, email, tm);

    when(dao.applicationState(FILE, APP)).thenReturn(Optional.of("APP"));
    when(dao.fileType(FILE)).thenReturn(Optional.of("B07"));
    when(dao.hvaOnFile(FILE, 5L)).thenReturn(true);
    when(dao.issuePermit(anyString(), anyString(), eq(APP), any(), any(), anyString(),
        anyBoolean())).thenReturn("");
    when(documents.upload(FILE, "CP 12 permit", "permit.pdf", PDF)).thenReturn(DOC);
  }

  private static TenureAppIssuePermitRequest request(boolean attach, String... to) {
    return new TenureAppIssuePermitRequest(5L, "CP 12 permit", List.of(to), attach);
  }

  private static TenureAppPermitFile pdf() {
    return new TenureAppPermitFile("permit.pdf", PDF);
  }

  @Test
  void checksThenUploadsThenIssuesWithTheLinkThenCommitsThenEmails() {
    TenureAppIssuePermitResult r = service.issue(
        FILE, APP, request(true, "a@gov.bc.ca", "b@gov.bc.ca"), pdf(), USER);

    assertThat(r.tenureAppId()).isEqualTo(APP);
    assertThat(r.message()).isEqualTo("The Permit has been successfully issued.");
    assertThat(r.emailWarning()).isNull();

    InOrder order = inOrder(dao, documents, tm, email);
    order.verify(dao).issuePermit(FILE, "B07", APP, 5L, null, USER, true);
    order.verify(documents).upload(FILE, "CP 12 permit", "permit.pdf", PDF);
    order.verify(dao).issuePermit(FILE, "B07", APP, 5L, DOC.link(), USER, false);
    order.verify(tm).commit(any(TransactionStatus.class));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Attachment>> att = ArgumentCaptor.forClass(List.class);
    order.verify(email).send(
        eq("Forest Permit B07 A12345"),
        eq("The following link will provide access to the issued permit for A12345. <br><br> "
            + "https://viewer/obj-1"),
        eq(List.of("a@gov.bc.ca", "b@gov.bc.ca")),
        att.capture());
    assertThat(att.getValue()).singleElement().satisfies(a -> {
      assertThat(a.fileName()).isEqualTo("permit.pdf");
      assertThat(a.contentType()).isEqualTo("application/pdf");
      assertThat(a.content()).isEqualTo(PDF);
    });
  }

  @Test
  void attachDocumentUncheckedSendsTheLinkOnly() {
    service.issue(FILE, APP, request(false, "a@gov.bc.ca"), pdf(), USER);
    verify(email).send(anyString(), anyString(), eq(List.of("a@gov.bc.ca")), eq(List.of()));
  }

  @Test
  void aFailedCheckStopsBeforeTheUpload() {
    when(dao.issuePermit(FILE, "B07", APP, 5L, null, USER, true))
        .thenReturn("fta.web.error.user.custom.msg:~W,The CP has no cut blocks.;");

    assertThatThrownBy(() -> service.issue(FILE, APP, request(true, "a@gov.bc.ca"), pdf(), USER))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(e.getReason()).isEqualTo("The CP has no cut blocks.");
        });
    verifyNoInteractions(documents, email);
    verify(tm).rollback(any());
    verify(tm, never()).commit(any());
  }

  @Test
  void aFailedIssuanceRollsBackAndSendsNothing() {
    when(dao.issuePermit(FILE, "B07", APP, 5L, DOC.link(), USER, false))
        .thenReturn("fta.web.error.user.custom.msg:~W,Already issued.;");

    assertThatThrownBy(() -> service.issue(FILE, APP, request(true, "a@gov.bc.ca"), pdf(), USER))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Already issued.");
    verify(tm).rollback(any());
    verifyNoInteractions(email);
  }

  @Test
  void storageNotConfiguredIs503AndRollsBack() {
    when(documents.upload(any(), any(), any(), any()))
        .thenThrow(new IntegrationNotConfiguredException("Document storage is not configured."));

    assertThatThrownBy(() -> service.issue(FILE, APP, request(true, "a@gov.bc.ca"), pdf(), USER))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
          assertThat(e.getReason()).isEqualTo("Document storage is not configured.");
        });
    verify(dao, never()).issuePermit(any(), any(), anyLong(), any(), any(), any(), eq(false));
    verify(tm).rollback(any());
    verifyNoInteractions(email);
  }

  @Test
  void uploadFailureIs502() {
    when(documents.upload(any(), any(), any(), any()))
        .thenThrow(new IntegrationException("Document storage refused the upload (HTTP 403)."));

    assertThatThrownBy(() -> service.issue(FILE, APP, request(true, "a@gov.bc.ca"), pdf(), USER))
        .isInstanceOfSatisfying(ResponseStatusException.class, e ->
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    verify(tm).rollback(any());
  }

  @Test
  void aLinkTooLongForTheColumnRollsBack() {
    when(documents.upload(any(), any(), any(), any()))
        .thenReturn(new StoredDocument("x", "h".repeat(2001), "v"));

    assertThatThrownBy(() -> service.issue(FILE, APP, request(true, "a@gov.bc.ca"), pdf(), USER))
        .isInstanceOf(ResponseStatusException.class);
    verify(dao, never()).issuePermit(any(), any(), anyLong(), any(), any(), any(), eq(false));
    verify(tm).rollback(any());
  }

  @Test
  void anEmailFailureAfterTheCommitKeepsThePermitIssued() {
    doThrow(new IntegrationException("The e-mail service refused the message (HTTP 400)."))
        .when(email).send(any(), any(), any(), any());

    TenureAppIssuePermitResult r =
        service.issue(FILE, APP, request(true, "a@gov.bc.ca"), pdf(), USER);

    assertThat(r.message()).isEqualTo("The Permit has been successfully issued.");
    assertThat(r.emailWarning()).isEqualTo(
        "The e-mail to the recipients could not be sent: The e-mail service refused the message"
            + " (HTTP 400).");
    verify(tm).commit(any());
  }

  @Test
  void popupChecksRunBeforeAnything() {
    assertThatThrownBy(() -> service.issue(
            FILE, APP, new TenureAppIssuePermitRequest(5L, "Permit", List.of(), true), pdf(),
            USER))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(e.getReason()).isEqualTo("Recipients is mandatory.");
        });
    assertThatThrownBy(() -> service.issue(
            FILE, APP, request(true, "a@gov.bc.ca"), null, USER))
        .hasMessageContaining("File Name is mandatory.");
    verify(dao, never()).issuePermit(any(), any(), anyLong(), any(), any(), any(), anyBoolean());
    verifyNoInteractions(documents, email, tm);
  }

  @Test
  void notApprovedOrIssuedIs409() {
    when(dao.applicationState(FILE, APP)).thenReturn(Optional.of("INB"));
    assertThatThrownBy(() -> service.issue(FILE, APP, request(true, "a@gov.bc.ca"), pdf(), USER))
        .isInstanceOfSatisfying(ResponseStatusException.class, e ->
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
  }

  @Test
  void applicationNotOnTheFileIs404() {
    when(dao.applicationState(FILE, APP)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.issue(FILE, APP, request(true, "a@gov.bc.ca"), pdf(), USER))
        .isInstanceOfSatisfying(ResponseStatusException.class, e ->
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void anAuthorityOffTheFileIsRefused() {
    when(dao.hvaOnFile(FILE, 5L)).thenReturn(false);
    assertThatThrownBy(() -> service.issue(FILE, APP, request(true, "a@gov.bc.ca"), pdf(), USER))
        .hasMessageContaining("The harvesting authority is not on this file.");
    verifyNoInteractions(documents);
  }

  @Test
  void noAuthorityPassesNull() {
    service.issue(FILE, APP,
        new TenureAppIssuePermitRequest(null, "CP 12 permit", List.of("a@gov.bc.ca"), false),
        pdf(), USER);
    verify(dao).issuePermit(eq(FILE), eq("B07"), eq(APP), isNull(), isNull(), eq(USER), eq(true));
  }
}
