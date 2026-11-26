package ca.bc.gov.nrs.fta.integration;

import java.util.List;

/**
 * Sends FTA's e-mails — legacy FTA's CmsgViewService over NRS Common Messaging (CMSG), here BC
 * Gov's Common Hosted Email Service ({@link ChesEmailSender}), CMSG's successor.
 */
public interface EmailSender {

  /**
   * Sends one HTML message to all the recipients, from FTA's sender address (legacy CMSG's
   * {@code fta@gov.bc.ca}).
   *
   * @throws IntegrationNotConfiguredException when the e-mail service is not configured
   * @throws IntegrationException when the send fails
   */
  void send(String subject, String htmlBody, List<String> recipients, List<Attachment> attachments);

  /** A file attached to the message. */
  record Attachment(String fileName, String contentType, byte[] content) {}
}
