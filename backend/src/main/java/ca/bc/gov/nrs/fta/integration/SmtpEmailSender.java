package ca.bc.gov.nrs.fta.integration;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * {@link EmailSender} straight over SMTP, through Spring's {@link JavaMailSender}, to the
 * government mail relay — legacy FTA sent through NRS Common Messaging.
 *
 * <p>One MIME message to all recipients, HTML body (UTF-8), files attached.
 *
 * <h3>Configuration</h3>
 * <ul>
 *   <li>{@code spring.mail.host} / {@code .port}: the government relay
 *       {@code apps.smtp.gov.bc.ca} on port 25, which needs no login.
 *   <li>{@code fta.email.from} — the sender address ({@code FTA_EMAIL_FROM}); configuration only,
 *       never defaulted in code
 * </ul>
 * Without a sender, every send fails with a message naming {@code FTA_EMAIL_FROM}.
 */
@Component
public class SmtpEmailSender implements EmailSender {

  private static final Logger LOG = LoggerFactory.getLogger(SmtpEmailSender.class);

  static final String NOT_CONFIGURED =
      "E-mail is not configured for this environment (set FTA_EMAIL_FROM).";

  private final JavaMailSender mail;
  private final String from;

  public SmtpEmailSender(JavaMailSender mail, @Value("${fta.email.from:}") String from) {
    this.mail = mail;
    this.from = from == null ? "" : from.trim();
    LOG.info(StringUtils.hasText(this.from)
        ? "SMTP e-mail active"
        : "SMTP e-mail inactive (FTA_EMAIL_FROM unset) — e-mails will fail");
  }

  @Override
  public void send(
      String subject, String htmlBody, List<String> recipients, List<Attachment> attachments) {
    if (!StringUtils.hasText(from)) {
      throw new IntegrationNotConfiguredException(NOT_CONFIGURED);
    }
    List<Attachment> files = attachments == null ? List.of() : attachments;
    MimeMessage message;
    try {
      message = mail.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, !files.isEmpty(), "UTF-8");
      helper.setFrom(from);
      helper.setTo(recipients.toArray(String[]::new));
      helper.setSubject(subject);
      helper.setText(htmlBody, true);
      for (Attachment a : files) {
        helper.addAttachment(a.fileName(), new ByteArrayResource(a.content()), a.contentType());
      }
    } catch (MessagingException e) {
      throw new IntegrationException("The e-mail could not be composed: " + e.getMessage(), e);
    }
    try {
      mail.send(message);
    } catch (MailAuthenticationException e) {
      throw new IntegrationException("The mail server refused FTA's login.", e);
    } catch (MailSendException e) {
      throw new IntegrationException("The mail server refused or could not take the message.", e);
    } catch (MailException e) {
      throw new IntegrationException("The mail server could not be reached.", e);
    }
    LOG.info("E-mail \"{}\" sent to {} recipient(s)", subject, recipients.size());
  }
}
