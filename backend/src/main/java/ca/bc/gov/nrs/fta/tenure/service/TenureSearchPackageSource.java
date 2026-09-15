package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
import ca.bc.gov.nrs.fta.tenure.dto.TenureSearchCriteria;
import ca.bc.gov.nrs.fta.tenure.dto.TenureSummaryDto;
import java.sql.Types;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlInOutParameter;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Component;

/**
 * Tenure search via the legacy package {@code THE.FTA_001_TENR_SRCH.MAINLINE}.
 *
 * <p><b>Why this exists.</b> This predates the table grants, when the account
 * held {@code EXECUTE} on the FTA packages and nothing else. PL/SQL packages
 * here carry no {@code AUTHID CURRENT_USER}, so they run with definer's rights —
 * as {@code THE} — and read the tables on our behalf, which made this the only
 * mode that returned rows.
 *
 * <p>The table grants have since landed and {@code table} is now the default.
 * This class is kept as a fallback: set {@code FTA_DATA_ACCESS_MODE=package} to
 * switch back without a rebuild if the table path misbehaves. Delete it once
 * the table path is trusted.
 *
 * <p>Note this path has never been exercised successfully against the database —
 * its last run failed with {@code PLS-00306} from a parameter-ordering bug that
 * has since been fixed but not retested. Treat it as untested, not as known
 * good.
 *
 * <p><b>Two details worth knowing.</b> Procedure metadata lookup is switched off
 * ({@code withoutProcedureColumnMetaDataAccess}) because it reads
 * {@code ALL_ARGUMENTS}, which a package-only account may not see. Every
 * parameter is therefore declared by hand — and with metadata off Spring binds
 * <b>positionally, in declaration order</b>, so the declarations below must
 * match the spec's parameter order exactly. And every parameter is
 * {@code IN OUT VARCHAR2} in the spec, so
 * each is declared as {@link SqlInOutParameter} even though only the cursor and
 * {@code p_error_message} are read back.
 *
 * <p>{@code p_action} is {@code 'GET'}: the package body compares it against
 * that single literal, and any other value returns no cursor.
 */
@Component
@ConditionalOnProperty(name = "fta.data-access.mode", havingValue = "package")
public class TenureSearchPackageSource implements TenureSearchSource {

  private static final Logger LOGGER = LoggerFactory.getLogger(TenureSearchPackageSource.class);

  /** The cursor parameter, and the only output that carries rows. */
  private static final String RESULTS = "p_search_results";

  /** Diagnostic the package fills in rather than raising. */
  private static final String ERROR_MESSAGE = "p_error_message";

  /**
   * Every {@code IN OUT VARCHAR2} on {@code MAINLINE}, in spec order.
   *
   * <p>Order <b>is</b> significant. With metadata access off, Spring keeps the
   * declared parameters in declaration order and emits a positional
   * {@code (?, ?, …)} call string — it binds by position, not by name. So this
   * list must match the spec exactly, and the cursor must be declared after it
   * (see the constructor).
   */
  private static final List<String> IN_OUT_PARAMS = List.of(
      "p_action",
      "p_forest_file_id",
      "p_file_type_code",
      "p_admin_org_unit_no",
      "p_client_name",
      "p_client_number",
      "p_client_locn_code",
      "p_file_client_type",
      "p_file_status_st",
      "p_mgmt_unit_type",
      "p_mgmt_unit_id",
      "p_issue_date_from",
      "p_issue_date_to",
      "p_expiry_date_from",
      "p_expiry_date_to",
      "p_assoc_file_id",
      "p_file_source",
      "p_tenure_type",
      "p_file_name",
      "p_sort_by_org_ind",
      "p_sort_by_client_ind",
      "p_sort_by_filetype_ind",
      "p_salvage_ind",
      "p_cash_sale_ind",
      "p_map_notn_type_cd",
      "p_trace_ind",
      ERROR_MESSAGE);

  /**
   * Maps {@code rec_tenure_results}.
   *
   * <p>The dates are {@code VARCHAR2(10)} in the record, not DATEs, so they are
   * parsed here rather than read as dates. An unparseable value becomes null: a
   * malformed date in one row should not fail the whole search.
   */
  private static final RowMapper<TenureSummaryDto> ROW_MAPPER = (rs, rowNum) -> new TenureSummaryDto(
      rs.getString("ORG_UNIT_CODE"),
      rs.getString("CLIENT_NUMBER"),
      rs.getString("CLIENT_LOCN_CODE"),
      rs.getString("CLIENT_NAME"),
      rs.getString("FOREST_FILE_ID"),
      rs.getString("FILE_TYPE_CODE"),
      rs.getString("FILE_CLIENT_TYPE_DESC"),
      rs.getString("MGMT_UNIT_TYPE"),
      rs.getString("MGMT_UNIT_ID"),
      rs.getString("FILE_STATUS_ST"),
      rs.getString("FILE_STATUS_DESC"),
      toLocalDate(rs.getString("FILE_ISSUE_DATE")),
      toLocalDate(rs.getString("FILE_EXPIRY_DATE")));

  private final SimpleJdbcCall call;

  public TenureSearchPackageSource(DataSource dataSource) {
    SimpleJdbcCall configured = new SimpleJdbcCall(dataSource)
        .withSchemaName("THE")
        .withCatalogName("FTA_001_TENR_SRCH")
        .withProcedureName("MAINLINE")
        .withoutProcedureColumnMetaDataAccess();

    // Declaration order is bind order. These 27 take positions 1-27 and the
    // cursor position 28, exactly as the spec declares them. Declaring the
    // cursor first binds it to p_action, which fails with PLS-00306.
    for (String name : IN_OUT_PARAMS) {
      configured = configured.declareParameters(new SqlInOutParameter(name, Types.VARCHAR));
    }

    // OUT rather than IN OUT, though the spec says IN OUT: an SqlInOutParameter
    // would have Spring supply an input value for the cursor. Legacy FTA did the
    // same — its descriptor marked p_search_results INOUT, but its JDBC layer
    // explicitly skipped setting a value for any OracleTypes.CURSOR parameter
    // and only registered it as an out parameter.
    configured = configured.declareParameters(
        new SqlOutParameter(RESULTS, Types.REF_CURSOR, ROW_MAPPER));

    this.call = configured;
  }

  @Override
  @SuppressWarnings("unchecked")
  public PagedResponse<TenureSummaryDto> search(
      TenureSearchCriteria criteria, int page, int size) {

    Map<String, Object> in = new java.util.HashMap<>();
    // Declared parameters must all be supplied, so start them all null and set
    // the ones the screen actually carries. The criteria record was shaped
    // against this parameter list, so the mapping is one for one.
    IN_OUT_PARAMS.forEach(name -> in.put(name, null));
    in.put("p_action", "GET");
    in.put("p_admin_org_unit_no", blankToNull(criteria.adminOrgUnitNo()));
    in.put("p_forest_file_id", blankToNull(criteria.forestFileId()));
    in.put("p_file_type_code", blankToNull(criteria.fileTypeCode()));
    in.put("p_tenure_type", blankToNull(criteria.tenureType()));
    in.put("p_file_status_st", blankToNull(criteria.fileStatus()));
    in.put("p_client_number", blankToNull(criteria.clientNumber()));
    in.put("p_client_locn_code", blankToNull(criteria.clientLocnCode()));
    in.put("p_client_name", blankToNull(criteria.clientName()));
    in.put("p_file_client_type", blankToNull(criteria.fileClientType()));
    in.put("p_mgmt_unit_type", blankToNull(criteria.mgmtUnitType()));
    in.put("p_mgmt_unit_id", blankToNull(criteria.mgmtUnitId()));
    in.put("p_file_source", blankToNull(criteria.fileSource()));
    in.put("p_assoc_file_id", blankToNull(criteria.assocFileId()));
    in.put("p_file_name", blankToNull(criteria.fileName()));
    in.put("p_issue_date_from", blankToNull(criteria.issueDateFrom()));
    in.put("p_issue_date_to", blankToNull(criteria.issueDateTo()));
    in.put("p_expiry_date_from", blankToNull(criteria.expiryDateFrom()));
    in.put("p_expiry_date_to", blankToNull(criteria.expiryDateTo()));
    in.put("p_salvage_ind", blankToNull(criteria.salvageInd()));
    in.put("p_cash_sale_ind", blankToNull(criteria.cashSaleInd()));
    in.put("p_map_notn_type_cd", blankToNull(criteria.mapNotationTypeCode()));
    // Sort is three mutually exclusive indicators rather than one column name;
    // the package defaults to org order when none is set.
    in.put("p_sort_by_client_ind",
        TenureSearchCriteria.SORT_CLIENT.equals(criteria.sortBy()) ? "Y" : null);
    in.put("p_sort_by_filetype_ind",
        TenureSearchCriteria.SORT_FILE_TYPE.equals(criteria.sortBy()) ? "Y" : null);
    in.put("p_sort_by_org_ind",
        TenureSearchCriteria.SORT_ORG.equals(criteria.sortBy()) ? "Y" : null);
    in.put("p_trace_ind", "N");

    Map<String, Object> out = call.execute(in);

    String error = (String) out.get(ERROR_MESSAGE);
    if (error != null && !error.isBlank()) {
      // The package reports failure through a parameter instead of raising, so
      // an empty result and a filled-in message is its way of saying "no".
      LOGGER.warn("FTA_001_TENR_SRCH.MAINLINE reported: {}", error);
    }

    List<TenureSummaryDto> rows = (List<TenureSummaryDto>) out.get(RESULTS);
    // MAINLINE opens one cursor over the entire result set — there is no
    // parameter to bound it — so the page is cut here rather than in SQL. The
    // whole set crosses the wire on every request; that cost is one more reason
    // the table path is the default.
    return PagedResponse.ofFullList(rows == null ? List.of() : rows, page, size);
  }

  /** {@code yyyy-mm-dd} per the legacy record; null when absent or malformed. */
  private static LocalDate toLocalDate(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value.trim());
    } catch (DateTimeParseException e) {
      LOGGER.debug("Unparseable date from FTA_001_TENR_SRCH: {}", value);
      return null;
    }
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
