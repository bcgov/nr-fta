package ca.bc.gov.nrs.fta.apt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.apt.dto.AptPartitionApportionmentDto;
import ca.bc.gov.nrs.fta.apt.dto.AptPartitionApportionmentRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

/** The apportionment service's SQL port of APT_PART_APPORTIONMENTS_GET, _APRTMT_INS_UPD, _DEL. */
@DisplayName("Unit Test | AptPartitionApportionmentService")
class AptPartitionApportionmentServiceTest {

  private static final String USER = "IDIR\\JSMITH";

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final AptPartitionApportionmentService service =
      new AptPartitionApportionmentService(jdbc);

  private static AptPartitionApportionmentDto apportionment(long id, long partId) {
    return new AptPartitionApportionmentDto(id, partId, "FL", "Forest licence", BigDecimal.TEN,
        null, null, null, USER, null, null, BigDecimal.TEN, null, null);
  }

  private static AptPartitionApportionmentRequest request(Long partId) {
    return new AptPartitionApportionmentRequest(partId, " FL ", BigDecimal.TEN, null, null);
  }

  @SuppressWarnings("unchecked")
  private void found(AptPartitionApportionmentDto... rows) {
    when(jdbc.query(eq(AptPartitionApportionmentService.LIST_SQL), any(SqlParameterSource.class),
        any(RowMapper.class))).thenReturn(List.of(rows));
  }

  private void foaUsed(long count) {
    when(jdbc.queryForObject(eq(AptPartitionApportionmentService.FOA_USED_SQL),
        any(SqlParameterSource.class), eq(Long.class))).thenReturn(count);
  }

  private static void assertStatus(Runnable call, HttpStatus status, String reason) {
    assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
      assertThat(e.getStatusCode()).isEqualTo(status);
      assertThat(e.getReason()).isEqualTo(reason);
    });
  }

  @Test
  void listNeedsAnIdOrAPartition() {
    assertStatus(() -> service.list(null, null), HttpStatus.BAD_REQUEST,
        "Give a partition apportionment id or a partition id.");
  }

  @Test
  void theListKeepsTheRoutinesJoinsAndTotals() {
    assertThat(AptPartitionApportionmentService.LIST_SQL)
        .contains("aftv.apt_mgmt_unit_st = mu.apt_mgmt_unit_st")
        .contains("SUM(aad.distribution_volume) AS ttl_apt_dist_dist_vol")
        .doesNotContain("ORDER BY");
  }

  @Test
  void createUsesTheSequenceAndTheUser() {
    foaUsed(0);
    when(jdbc.queryForObject(contains("the.prtaprtmt_seq.NEXTVAL"), anyMap(), eq(Long.class)))
        .thenReturn(21L);
    found(apportionment(21, 4));

    assertThat(service.create(request(4L), USER).id()).isEqualTo(21L);

    verify(jdbc).update(eq(AptPartitionApportionmentService.INSERT_SQL),
        argThat((SqlParameterSource p) -> Long.valueOf(21).equals(p.getValue("newId"))
            && "FL".equals(p.getValue("foa"))
            && USER.equals(p.getValue("user"))));
  }

  @Test
  void aFoaTypeAlreadyInThePartitionIs409() {
    foaUsed(1);

    assertStatus(() -> service.create(request(4L), USER), HttpStatus.CONFLICT,
        AptPartitionApportionmentService.FOA_USED);
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  void anUpdateChecksTheOtherApportionmentsOfItsOwnPartition() {
    found(apportionment(21, 4));
    foaUsed(0);

    service.update(21L, request(99L), USER);

    verify(jdbc).queryForObject(eq(AptPartitionApportionmentService.FOA_USED_SQL),
        argThat((SqlParameterSource p) -> Long.valueOf(21).equals(p.getValue("id"))
            && Long.valueOf(4).equals(p.getValue("partId"))),
        eq(Long.class));
    verify(jdbc).update(eq(AptPartitionApportionmentService.UPDATE_SQL),
        argThat((SqlParameterSource p) -> Long.valueOf(4).equals(p.getValue("partId"))));
  }

  @Test
  void deletingAnApportionmentWithDistributionsIs409() {
    found(apportionment(21, 4));
    when(jdbc.update(eq(AptPartitionApportionmentService.DELETE_SQL),
        any(SqlParameterSource.class))).thenThrow(AptManagementUnitSqlTest.childRecord());

    assertStatus(() -> service.delete(21L), HttpStatus.CONFLICT,
        AptPartitionApportionmentService.REFERENCED);
  }
}
