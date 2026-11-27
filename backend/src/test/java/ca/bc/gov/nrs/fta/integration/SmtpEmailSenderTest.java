package ca.bc.gov.nrs.fta.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.integration.EmailSender.Attachment;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

/** What {@link SmtpEmailSender} hands the (mocked) {@link JavaMailSender}. */
class SmtpEmailSenderTest {

  private static final byte[] PDF = "%PDF-1.4".getBytes();

  private JavaMailSender mail;

  @BeforeEach
  void setUp() {
    mail = mock(JavaMailSender.class);
    when(mail.createMimeMessage())
        .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
  }

  private MimeMessage sent() {
    ArgumentCaptor<MimeMessage> msg = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mail).send(msg.capture());
    return msg.getValue();
  }

  @Test
  void oneHtmlMessageToAllRecipientsWithTheAttachment() throws Exception {
    SmtpEmailSender sender = new SmtpEmailSender(mail, " fta@example.com ");

    sender.send("Forest Permit B07 A12345", "<p>Link: é</p>",
        List.of("a@example.com", "b@example.com"),
        List.of(new Attachment("permit.pdf", "application/pdf", PDF)));

    MimeMessage m = sent();
    m.saveChanges();
    assertThat(m.getFrom()).extracting(Address::toString).containsExactly("fta@example.com");
    assertThat(Arrays.stream(m.getRecipients(RecipientType.TO)).map(Address::toString))
        .containsExactly("a@example.com", "b@example.com");
    assertThat(m.getSubject()).isEqualTo("Forest Permit B07 A12345");

    List<Part> parts = leaves(m);
    Part html = parts.stream().filter(p -> isType(p, "text/html")).findFirst().orElseThrow();
    assertThat(html.getContent()).isEqualTo("<p>Link: é</p>");
    Part file = parts.stream()
        .filter(p -> Part.ATTACHMENT.equalsIgnoreCase(disposition(p)))
        .findFirst().orElseThrow();
    assertThat(file.getFileName()).isEqualTo("permit.pdf");
    assertThat(file.getContentType()).startsWith("application/pdf");
    try (InputStream in = file.getInputStream()) {
      assertThat(in.readAllBytes()).isEqualTo(PDF);
    }
  }

  @Test
  void withoutAttachmentsTheMessageIsPlainHtml() throws Exception {
    new SmtpEmailSender(mail, "fta@example.com")
        .send("S", "<b>hi</b>", List.of("a@example.com"), List.of());

    MimeMessage m = sent();
    m.saveChanges();
    assertThat(m.getContentType()).startsWith("text/html");
    assertThat(m.getContent()).isEqualTo("<b>hi</b>");
  }

  @Test
  void noSenderIsNotConfiguredAndSendsNothing() {
    SmtpEmailSender sender = new SmtpEmailSender(mail, "  ");

    assertThatThrownBy(() -> sender.send("S", "b", List.of("a@example.com"), List.of()))
        .isInstanceOf(IntegrationNotConfiguredException.class)
        .hasMessageContaining("FTA_EMAIL_FROM");
    verify(mail, never()).send(any(MimeMessage.class));
  }

  @Test
  void aRefusedSendIsAnIntegrationException() {
    doThrow(new MailSendException("Mail server connection failed"))
        .when(mail).send(any(MimeMessage.class));
    SmtpEmailSender sender = new SmtpEmailSender(mail, "fta@example.com");

    assertThatThrownBy(() -> sender.send("S", "b", List.of("a@example.com"), List.of()))
        .isExactlyInstanceOf(IntegrationException.class)
        .hasMessageContaining("mail server");
  }

  @Test
  void aRefusedLoginIsAnIntegrationException() {
    doThrow(new MailAuthenticationException("535"))
        .when(mail).send(any(MimeMessage.class));
    SmtpEmailSender sender = new SmtpEmailSender(mail, "fta@example.com");

    assertThatThrownBy(() -> sender.send("S", "b", List.of("a@example.com"), null))
        .isExactlyInstanceOf(IntegrationException.class)
        .hasMessageContaining("login");
  }

  private static List<Part> leaves(Part p) throws Exception {
    List<Part> out = new ArrayList<>();
    Object c = p.getContent();
    if (c instanceof Multipart mp) {
      for (int i = 0; i < mp.getCount(); i++) {
        BodyPart bp = mp.getBodyPart(i);
        out.addAll(leaves(bp));
      }
    } else {
      out.add(p);
    }
    return out;
  }

  private static boolean isType(Part p, String type) {
    try {
      return p.isMimeType(type);
    } catch (Exception e) {
      return false;
    }
  }

  private static String disposition(Part p) {
    try {
      return p.getDisposition();
    } catch (Exception e) {
      return null;
    }
  }
}
