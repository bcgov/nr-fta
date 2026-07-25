package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.tenure.dto.UploadExhibitARequest;
import java.util.Base64;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write operations for the Exhibit A image upload.
 *
 * <p>Ports the {@code UPLOAD} path of the legacy {@code
 * FTA_307_UPLOAD_EXHIBIT_A} package to native UPDATEs via {@link
 * NamedParameterJdbcTemplate}. Mirrors the package body: it stamps the
 * mime-type / requested-by metadata on {@code THE.TENURE_APPLICATION} and
 * writes the image BLOB to {@code THE.TENURE_APPLICATION_IMAGE}, bumping both
 * revision counts and enforcing the optimistic-lock check on the caller-
 * supplied revision counts. Runs against the shared {@code THE} Oracle schema —
 * there is no local database, so it is exercised only in a deployed environment.
 */
@Service
public class UploadExhibitAWriteService {

  private final NamedParameterJdbcTemplate jdbc;

  public UploadExhibitAWriteService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String UPDATE_APPLICATION_SQL =
      """
      UPDATE the.tenure_application
      SET image_mime_type_code = 'PDF',
          image_create_requested_by = :userId,
          image_create_requested_date = SYSDATE,
          update_userid = :userId,
          update_timestamp = SYSDATE,
          revision_count = revision_count + 1
      WHERE tenure_app_id = :tenureAppId
        AND revision_count = :revisionCount
      """;

  private static final String UPDATE_IMAGE_SQL =
      """
      UPDATE the.tenure_application_image
      SET exhibit_a_image = :image,
          update_userid = :userId,
          update_timestamp = SYSDATE,
          revision_count = revision_count + 1
      WHERE tenure_app_id = :tenureAppId
        AND revision_count = :taiRevisionCount
      """;

  /**
   * Upload the Exhibit A image for a tenure application. Returns the total
   * number of rows updated across the application and image tables.
   *
   * @param tenureAppId the tenure application id (from the request path)
   * @param request     the upload payload (revision counts + base64 image)
   * @param userId      the authenticated user id (audit columns)
   */
  @Transactional
  public int upload(String tenureAppId, UploadExhibitARequest request, String userId) {
    byte[] image =
        request.imageBase64() != null ? Base64.getDecoder().decode(request.imageBase64()) : null;

    MapSqlParameterSource appParams = new MapSqlParameterSource()
        .addValue("tenureAppId", tenureAppId)
        .addValue("revisionCount", request.revisionCount())
        .addValue("userId", userId);
    int appRows = jdbc.update(UPDATE_APPLICATION_SQL, appParams);

    MapSqlParameterSource imageParams = new MapSqlParameterSource()
        .addValue("tenureAppId", tenureAppId)
        .addValue("taiRevisionCount", request.taiRevisionCount())
        .addValue("image", image)
        .addValue("userId", userId);
    int imageRows = jdbc.update(UPDATE_IMAGE_SQL, imageParams);

    return appRows + imageRows;
  }
}
