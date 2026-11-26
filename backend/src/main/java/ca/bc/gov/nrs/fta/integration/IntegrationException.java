package ca.bc.gov.nrs.fta.integration;

/** A call to an outside service (document storage, e-mail) failed; the message says which. */
public class IntegrationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public IntegrationException(String message) {
    super(message);
  }

  public IntegrationException(String message, Throwable cause) {
    super(message, cause);
  }
}
