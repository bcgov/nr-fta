package ca.bc.gov.nrs.fta.tenure.dto;

import java.time.LocalDate;

/**
 * Richer detail view of a single tenure application, used by the FTA952
 * application-detail screen.
 *
 * <p>Mirrors the file-level header parameters and the tenure-application record
 * columns returned by the legacy Oracle package {@code THE.FTA_952X_TAMF_DET}
 * (see {@code fta-archive/fta/database/ddl/pkg/FTA_952X_TAMF_DET.PKS}): the
 * {@code mainline} header parameters ({@code p_forest_file_id},
 * {@code p_file_type_cd}, {@code p_file_type_desc}, {@code p_admin_org},
 * {@code p_licencee}, {@code p_status}, {@code p_status_date},
 * {@code p_award_date}, {@code p_expiry_date}, {@code p_tenure_app_id},
 * {@code p_ten_app_type}) plus the identifying columns from the
 * {@code tenure_application} record the GET procedure reads.
 */
public record ApplicationDetailDto(
    String tenureAppId,
    String forestFileId,
    String fileTypeCode,
    String fileTypeDesc,
    String adminOrg,
    String licencee,
    String clientNumber,
    String status,
    LocalDate statusDate,
    LocalDate awardDate,
    LocalDate expiryDate,
    String tenureAppType,
    String description,
    String purposeDesc,
    String harvestTypeCode) {}
