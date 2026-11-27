package ca.bc.gov.nrs.fta.integration;

/**
 * Where FTA files the documents it issues — legacy FTA's document management (DmViewService over
 * the NRS DMS REST API), here an S3-compatible bucket ({@link S3DocumentStore}).
 */
public interface DocumentStore {

  /**
   * Files a document under a tenure, as legacy {@code DmViewService.uploadFile}.
   *
   * @param forestFileId  the tenure it belongs to (legacy's per-tenure folder)
   * @param documentTitle the title the user gave it (legacy's DM document title)
   * @param fileName      the uploaded file's own name
   * @param content       the file
   * @return its stored link and the link people are sent to view it
   * @throws IntegrationNotConfiguredException when the store is not configured
   * @throws IntegrationException when the upload fails
   */
  StoredDocument upload(String forestFileId, String documentTitle, String fileName, byte[] content);

  /**
   * A filed document.
   *
   * @param id        the store's id for it (the object key)
   * @param link      its stable link, recorded in the database (legacy's DM self link)
   * @param viewerUrl where a person opens it, e-mailed (legacy's DMS viewer URL + item id; here a
   *                  time-limited presigned URL)
   */
  record StoredDocument(String id, String link, String viewerUrl) {}
}
