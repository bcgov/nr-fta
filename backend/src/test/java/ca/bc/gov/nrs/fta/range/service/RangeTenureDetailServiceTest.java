package ca.bc.gov.nrs.fta.range.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.range.dto.RangeTenureDetailDto;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@DisplayName("Unit Test | RangeTenureDetailService")
class RangeTenureDetailServiceTest {

  @Test
  @DisplayName("only E01 and H01 licences are replaceable")
  void licenceReplaceable() {
    assertThat(RangeTenureDetailService.licenceReplaceableInd("E01")).isEqualTo("Y");
    assertThat(RangeTenureDetailService.licenceReplaceableInd("H01")).isEqualTo("Y");
    assertThat(RangeTenureDetailService.licenceReplaceableInd("E02")).isEqualTo("N");
    assertThat(RangeTenureDetailService.licenceReplaceableInd(null)).isEqualTo("N");
  }

  @Test
  @DisplayName("the replacement label names the January 1 after the initial expiry")
  void replacementLabel() {
    assertThat(RangeTenureDetailService.replacementLabel(10, LocalDate.of(2027, 12, 31)))
        .isEqualTo("This tenure will be replaced on Jan. 1, 2028 for 10 years");
    assertThat(RangeTenureDetailService.replacementLabel(null, LocalDate.of(2027, 12, 31)))
        .isNull();
  }

  @Test
  @DisplayName("a whole-year calendar term raises no warning")
  void termWarningNone() {
    assertThat(RangeTenureDetailService.termWarning(
        LocalDate.of(2018, 1, 1), LocalDate.of(2027, 12, 31), 10, 0)).isNull();
    assertThat(RangeTenureDetailService.termWarning(null, null, null, null)).isNull();
  }

  @Test
  @DisplayName("odd months or an effective date off January 1 raise the warning")
  void termWarningRaised() {
    assertThat(RangeTenureDetailService.termWarning(
        LocalDate.of(2018, 1, 1), LocalDate.of(2028, 6, 30), 10, 6))
        .startsWith("Tenure term data retrieved does not meet range requirements.")
        .endsWith("Effective: 2018-01-01  Expiry: 2028-06-30 Term: 10 yr 6 mo");
    assertThat(RangeTenureDetailService.termWarning(
        LocalDate.of(2018, 4, 1), LocalDate.of(2028, 3, 31), 10, 0))
        .isNotNull();
  }

  @Test
  @DisplayName("an expiry off December 31 alone raises no warning, as in legacy")
  void termWarningExpiryAlone() {
    assertThat(RangeTenureDetailService.termWarning(
        LocalDate.of(2018, 1, 1), LocalDate.of(2027, 6, 30), 10, 0)).isNull();
  }

  @Test
  @DisplayName("range-ness comes from RANGE_FILE_TYPE_CODE and the zone falls back to the file's")
  void headerSql() {
    assertThat(RangeTenureDetailService.HEADER_SQL)
        .contains("the.range_file_type_code")
        .contains("ELSE pfu.district_admin_zone")
        .contains("forest_file_client_type_code = 'B'")
        .contains("the.provforest_note")
        .contains("the.forest_mgmt_unit");
  }

  @Test
  @DisplayName("the Range Usage tab is bounded by the term's years, ascending")
  @SuppressWarnings("unchecked")
  void usageBoundedByTerm() throws Exception {
    NamedParameterJdbcTemplate jdbc = jdbcWithHeader(
        LocalDate.of(2018, 1, 1), LocalDate.of(2032, 12, 31));

    RangeTenureDetailDto detail = new RangeTenureDetailService(jdbc, mock(RangeFileAccess.class)).getDetail("RAN001");

    assertThat(detail).isNotNull();
    assertThat(RangeTenureDetailService.USAGE_SQL)
        .contains("rp.calendar_year >= :startYear")
        .contains("rp.calendar_year <= :expiryYear")
        .contains("ORDER BY rp.calendar_year\n");
    assertThat(RangeTenureDetailService.USAGE_HISTORY_SQL)
        .contains("ORDER BY rp.calendar_year DESC");

    ArgumentCaptor<MapSqlParameterSource> params =
        ArgumentCaptor.forClass(MapSqlParameterSource.class);
    verify(jdbc).query(eq(RangeTenureDetailService.USAGE_SQL), params.capture(),
        any(RowMapper.class));
    assertThat(params.getValue().getValue("startYear")).isEqualTo(2018);
    assertThat(params.getValue().getValue("expiryYear")).isEqualTo(2032);
    verify(jdbc).query(eq(RangeTenureDetailService.USAGE_HISTORY_SQL),
        any(MapSqlParameterSource.class), any(RowMapper.class));
  }

  @Test
  @DisplayName("with no term dates the Range Usage tab is empty, as legacy's null bounds match nothing")
  @SuppressWarnings("unchecked")
  void usageWithoutTerm() throws Exception {
    NamedParameterJdbcTemplate jdbc = jdbcWithHeader(null, null);

    RangeTenureDetailDto detail = new RangeTenureDetailService(jdbc, mock(RangeFileAccess.class)).getDetail("RAN001");

    assertThat(detail.rangeUsage()).isEmpty();
    verify(jdbc, never()).query(eq(RangeTenureDetailService.USAGE_SQL),
        any(MapSqlParameterSource.class), any(RowMapper.class));
  }

  @Test
  @DisplayName("an unknown file is null")
  void unknownFile() {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    assertThat(new RangeTenureDetailService(jdbc, mock(RangeFileAccess.class)).getDetail("NOPE")).isNull();
  }

  @Test
  @DisplayName("canEdit is RangeFileAccess on the file's two districts")
  void canEditFromBothDistricts() throws Exception {
    NamedParameterJdbcTemplate jdbc = jdbcWithHeader(null, null);
    RangeFileAccess access = mock(RangeFileAccess.class);
    when(access.mayEdit("DCC", "DMH")).thenReturn(true);

    assertThat(new RangeTenureDetailService(jdbc, access).getDetail("RAN001").canEdit()).isTrue();
    assertThat(new RangeTenureDetailService(jdbc, mock(RangeFileAccess.class))
        .getDetail("RAN001").canEdit()).isFalse();
  }

  /** A template whose header query maps one row with the given term dates. */
  @SuppressWarnings("unchecked")
  private static NamedParameterJdbcTemplate jdbcWithHeader(
      LocalDate issueDate, LocalDate usageExpiry) throws Exception {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    ResultSet rs = mock(ResultSet.class);
    when(rs.getString("forest_file_id")).thenReturn("RAN001");
    when(rs.getString("file_type_code")).thenReturn("E01");
    when(rs.getString("admin_org_unit_code")).thenReturn("DCC");
    when(rs.getString("district_org_unit_code")).thenReturn("DMH");
    when(rs.getObject("issue_date", LocalDate.class)).thenReturn(issueDate);
    when(rs.getObject("usage_expiry_date", LocalDate.class)).thenReturn(usageExpiry);
    when(jdbc.query(eq(RangeTenureDetailService.HEADER_SQL), any(MapSqlParameterSource.class),
        any(RowMapper.class)))
        .thenAnswer(inv -> List.of(((RowMapper<Object>) inv.getArgument(2)).mapRow(rs, 0)));
    return jdbc;
  }
}
