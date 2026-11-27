package ca.bc.gov.nrs.fta.integration;

import java.util.List;

/**
 * Sends FTA's e-mails — legacy FTA's CmsgViewService over NRS Common Messaging, here plain SMTP
 * to the government mail relay ({@link SmtpEmailSender}).
 */
public interface EmailSender {

  /**
   * Sends one HTML message to all the recipients, from FTA's configured sender address
   * ({@code fta.email.from}).
   *
   * @throws IntegrationNotConfiguredException when e-mail is not configured (no sender)
   * @throws IntegrationException when the send fails
   */
  void send(String subject, String htmlBody, List<String> recipients, List<Attachment> attachments);

  /** A file attached to the message. */
  record Attachment(String fileName, String contentType, byte[] content) {}
}
