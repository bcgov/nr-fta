package ca.bc.gov.nrs.fta.apt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.apt.dto.AptPartitionDto;
import ca.bc.gov.nrs.fta.apt.dto.AptPartitionRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

/** The partition service's SQL port of APT_PARTITIONS_GET, APT_PARTITION_INS_UPD and _DEL. */
@DisplayName("Unit Test | AptPartitionService")
class AptPartitionServiceTest {

  private static final String USER = "IDIR\\JSMITH";

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final AptPartitionService service = new AptPartitionService(jdbc);

  private static AptPartitionDto partition(long id, long muhId) {
    return new AptPartitionDto(id, muhId, "A", "Type A", BigDecimal.TEN, null, null, null, USER,
        null, null, BigDecimal.TEN, null, null, null);
  }

  private static AptPartitionRequest request(Long muhId) {
    return new AptPartitionRequest(muhId, " A ", BigDecimal.TEN, null, "  ");
  }

  @SuppressWarnings("unchecked")
  private void found(AptPartitionDto... rows) {
    when(jdbc.query(eq(AptPartitionService.LIST_SQL), any(SqlParameterSource.class),
        any(RowMapper.class))).thenReturn(List.of(rows));
  }

  private static void assertStatus(Runnable call, HttpStatus status, String reason) {
    assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
      assertThat(e.getStatusCode()).isEqualTo(status);
      assertThat(e.getReason()).isEqualTo(reason);
    });
  }

  @Test
  void listNeedsAnIdOrADetermination() {
    assertStatus(() -> service.list(null, null), HttpStatus.BAD_REQUEST,
        "Give a partition id or a management unit history id.");
  }

  @Test
  void theListKeepsTheRoutinesTotalsAndOrder() {
    assertThat(AptPartitionService.LIST_SQL)
        .contains("SUM(apa.allowable_annual_cut) AS ttl_part_apt_aac")
        .contains("CROSS JOIN (SELECT SUM(allowable_annual_cut) AS ttl_aac")
        .endsWith("ORDER BY ap.apt_partition_st");
  }

  @Test
  void createUsesThePartSequenceAndTheUser() {
    when(jdbc.queryForObject(contains("the.part_seq.NEXTVAL"), anyMap(), eq(Long.class)))
        .thenReturn(12L);
    found(partition(12, 3));

    assertThat(service.create(request(3L), USER).id()).isEqualTo(12L);

    verify(jdbc).update(eq(AptPartitionService.INSERT_SQL),
        argThat((SqlParameterSource p) -> Long.valueOf(12).equals(p.getValue("id"))
            && "A".equals(p.getValue("type"))
            && p.getValue("comments") == null
            && USER.equals(p.getValue("user"))));
  }

  @Test
  void aTypeAlreadyInTheDeterminationIs409() {
    when(jdbc.queryForObject(contains("the.part_seq.NEXTVAL"), anyMap(), eq(Long.class)))
        .thenReturn(12L);
    when(jdbc.update(eq(AptPartitionService.INSERT_SQL), any(SqlParameterSource.class)))
        .thenThrow(AptManagementUnitSqlTest.duplicate());

    assertStatus(() -> service.create(request(3L), USER), HttpStatus.CONFLICT,
        AptPartitionService.TYPE_USED);
  }

  @Test
  void aTypeNotInTheSubsetIsTheTriggers400() {
    found(partition(12, 3));
    when(jdbc.update(eq(AptPartitionService.UPDATE_SQL), any(SqlParameterSource.class)))
        .thenThrow(AptManagementUnitSqlTest.triggerError());

    assertStatus(() -> service.update(12L, request(null), USER), HttpStatus.BAD_REQUEST,
        "ORA-20000: The Partition Type does not exist on the Partition Effective Date");
  }

  @Test
  void anUpdateStaysWithItsDetermination() {
    found(partition(12, 3));

    service.update(12L, request(99L), USER);

    verify(jdbc).update(eq(AptPartitionService.UPDATE_SQL),
        argThat((SqlParameterSource p) -> Long.valueOf(3).equals(p.getValue("muhId"))));
  }

  @Test
  void deletingAPartitionWithApportionmentsIs409() {
    found(partition(12, 3));
    when(jdbc.update(eq(AptPartitionService.DELETE_SQL), any(SqlParameterSource.class)))
        .thenThrow(AptManagementUnitSqlTest.childRecord());

    assertStatus(() -> service.delete(12L), HttpStatus.CONFLICT, AptPartitionService.REFERENCED);
  }

  @Test
  void deletingAnUnknownPartitionIs404() {
    found();
    assertStatus(() -> service.delete(12L), HttpStatus.NOT_FOUND, "Partition 12 not found.");
  }
}
