package ca.bc.gov.nrs.fta.range.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.range.dto.RangeUnitDetailDto;
import ca.bc.gov.nrs.fta.security.LoggedUserHelper;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@DisplayName("Unit Test | RangeUnitDetailService")
class RangeUnitDetailServiceTest {

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final LoggedUserHelper auth = mock(LoggedUserHelper.class);
  private final RangeUnitDetailService service = new RangeUnitDetailService(jdbc, auth);

  @Test
  @DisplayName("canEdit is mayEditRangeDistrict on the unit's district (legacy p_ind)")
  void canEdit() throws Exception {
    stubUnit("DCC");
    when(auth.mayEditRangeDistrict("DCC")).thenReturn(true);
    assertThat(service.getRangeUnitDetail("RU1").canEdit()).isTrue();

    when(auth.mayEditRangeDistrict("DCC")).thenReturn(false);
    assertThat(service.getRangeUnitDetail("RU1").canEdit()).isFalse();
  }

  @Test
  @DisplayName("status reads code - description, and the district code is returned")
  void fields() throws Exception {
    stubUnit("DCC");
    RangeUnitDetailDto dto = service.getRangeUnitDetail("RU1");
    assertThat(dto.statusDescription()).isEqualTo("A - Active");
    assertThat(dto.districtCode()).isEqualTo("DCC");
    assertThat(dto.regionDescription()).isEqualTo("RCB - Cariboo Natural Resource Region");
  }

  @Test
  @DisplayName("the region is the district's rollup region, described from the org unit list")
  void regionSql() throws Exception {
    stubUnit("DCC");
    service.getRangeUnitDetail("RU1");
    org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.atLeastOnce())
        .query(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
    assertThat(sql.getAllValues()).anyMatch(s -> s.contains("rgn.org_unit_no = dist.rollup_region_no"));
  }

  @Test
  @DisplayName("no unit is null")
  void missing() {
    assertThat(service.getRangeUnitDetail("RU404")).isNull();
  }

  @SuppressWarnings("unchecked")
  private void stubUnit(String districtCode) throws Exception {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getString("range_unit_id")).thenReturn("RU1");
    when(rs.getString("status_code")).thenReturn("A");
    when(rs.getString("status_raw_desc")).thenReturn("Active");
    when(rs.getString("region_description")).thenReturn("RCB - Cariboo Natural Resource Region");
    when(rs.getString("district_code")).thenReturn(districtCode);
    when(jdbc.query(contains("FROM the.range_unit ru"), any(MapSqlParameterSource.class),
        any(RowMapper.class)))
        .thenAnswer(inv -> List.of(((RowMapper<RangeUnitDetailDto>) inv.getArgument(2))
            .mapRow(rs, 0)));
    when(jdbc.query(contains("range_unit_pasture"), any(MapSqlParameterSource.class),
        any(RowMapper.class))).thenReturn(List.of());
  }
}
