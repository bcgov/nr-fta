package ca.bc.gov.nrs.fta.apt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** The APT code lists: the arguments APT2's lookup functions switched on. */
@DisplayName("Unit Test | AptLookupService")
class AptLookupServiceTest {

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final AptLookupService service = new AptLookupService(jdbc);

  private static String[] patterns(String level) {
    MapSqlParameterSource p = AptLookupService.orgUnitPatterns(level);
    return new String[] {(String) p.getValue("pattern1"), (String) p.getValue("pattern2")};
  }

  @Test
  void orgUnitLevelIsExactlyROrDElseBoth() {
    assertThat(patterns("R")).containsExactly("R__", "R__");
    assertThat(patterns("D")).containsExactly("D__", "D__");
    assertThat(patterns(null)).containsExactly("R__", "D__");
    assertThat(patterns("r")).containsExactly("R__", "D__");
    assertThat(patterns("X")).containsExactly("R__", "D__");
  }

  @Test
  void aBlankManagementUnitTypeListsEveryFoaType() {
    service.foaTypes(" ");
    verify(jdbc).query(eq(AptLookupService.FOA_TYPES_SQL),
        argThat((SqlParameterSource p) -> p.getValue("muType") == null),
        any(RowMapper.class));
  }

  @Test
  void partitionTypesNeedBothManagementUnitArgumentsToFilter() {
    service.partitionTypes("U", null);
    verify(jdbc).query(eq(AptLookupService.PART_TYPES_SQL), any(RowMapper.class));

    service.partitionTypes("U", "12");
    verify(jdbc).query(eq(AptLookupService.PART_TYPES_BY_MU_SQL),
        argThat((SqlParameterSource p) -> "U".equals(p.getValue("muType"))
            && "12".equals(p.getValue("muNumber"))),
        any(RowMapper.class));
  }
}
