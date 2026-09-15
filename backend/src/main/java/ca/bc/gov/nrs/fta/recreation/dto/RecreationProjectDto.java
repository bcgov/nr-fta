package ca.bc.gov.nrs.fta.recreation.dto;

/**
 * The FTA701 Recreation Project parent record.
 *
 * <p>Mirrors {@code THE.RECREATION_PROJECT} plus the values the screen derives
 * rather than stores. Dates are ISO strings, as legacy formats them with
 * {@code sil_date_conversion.convert_to_char(..., 'YYYY-MM-DD')}.
 *
 * <p>Five fields are read-only because nothing writes them:
 * {@code projectTypeCode}/{@code projectType} and
 * {@code projectLength}/{@code projectArea} come from
 * {@code FTA_REC_PROJECT_TOMBSTONE} (a sum over the project's mapped features),
 * and {@code definedCampsites} is a count. Legacy threads a
 * {@code p_recreation_project_code} through nine signatures; there is no such
 * column on the table and nothing ever writes it, so it is absent here.
 *
 * @param forestFileId            the primary key
 * @param projectName             mandatory on save
 * @param projectTypeCode         derived — {@code RECREATION_MAP_FEATURE_CODE}
 * @param projectType             derived — that code's description
 * @param projectLength           derived — SUM of feature_length, in km
 * @param projectArea             derived — SUM of feature_area, in ha
 * @param definedCampsites        derived — COUNT of defined campsites
 * @param assocFilesExist         derived — {@code Y} when the file appears in
 *                                {@code ASSOCIATED_USE} on either side
 * @param riskRatingCode          {@code RECREATION_RISK_RATING_CODE}
 * @param projectEstablishedDate  editable only by Recreation headquarters
 * @param siteLocation            labelled "Closest Community"; stored upper-cased
 * @param utmZone                 7 to 11; other zones cannot be projected
 * @param utmNorthing             UTM northing
 * @param utmEasting              UTM easting
 * @param rightOfWay              mandatory for trail projects, read-only otherwise
 * @param featureCode             "Significant Recreation Feature"
 * @param userDaysCode            {@code RECREATION_USER_DAYS_CODE}
 * @param maintainStdCode         {@code RECREATION_MAINTAIN_STD_CODE}
 * @param campHostInd             blank, {@code Y} or {@code N}
 * @param overflowCampsites       overflow campsite count
 * @param lowMobilityAccessInd    mandatory
 * @param recreationViewInd       "Display to Website"
 * @param resourceFeatureInd      mandatory; constrained to {@code Y}/{@code N}
 * @param controlAccessCode       {@code RECREATION_CONTROL_ACCESS_CODE}
 * @param lastRecInspectionDate   last recreation inspection
 * @param lastHzrdTreeAssessDate  last hazard tree assessment
 * @param archImpactAssessInd     AIA indicator; {@code Y}/{@code N}
 * @param archImpactDate          AIA date
 * @param bordenNo                Borden number
 * @param aiaComment              held in {@code RECREATION_COMMENT} under the
 *                                {@code AIA} type, not on the project row
 * @param siteDescription         "Field Note"
 * @param revisionCount           for optimistic locking on save
 */
public record RecreationProjectDto(
    String forestFileId,
    String projectName,
    String projectTypeCode,
    String projectType,
    java.math.BigDecimal projectLength,
    java.math.BigDecimal projectArea,
    Integer definedCampsites,
    String assocFilesExist,
    String riskRatingCode,
    String projectEstablishedDate,
    String siteLocation,
    Integer utmZone,
    Long utmNorthing,
    Long utmEasting,
    java.math.BigDecimal rightOfWay,
    String featureCode,
    String userDaysCode,
    String maintainStdCode,
    String campHostInd,
    Integer overflowCampsites,
    String lowMobilityAccessInd,
    String recreationViewInd,
    String resourceFeatureInd,
    String controlAccessCode,
    String lastRecInspectionDate,
    String lastHzrdTreeAssessDate,
    String archImpactAssessInd,
    String archImpactDate,
    String bordenNo,
    String aiaComment,
    String siteDescription,
    Long revisionCount) {}
