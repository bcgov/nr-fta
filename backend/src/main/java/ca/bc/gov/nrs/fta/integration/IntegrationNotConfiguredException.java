package ca.bc.gov.nrs.fta.integration;

/**
 * An outside service is needed but its endpoint is not configured for this deployment. The
 * message names the environment variables to set.
 */
public class IntegrationNotConfiguredException extends IntegrationException {

  private static final long serialVersionUID = 1L;

  public IntegrationNotConfiguredException(String message) {
    super(message);
  }
}
