package ca.bc.gov.nrs.fta.apt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.apt.dto.AptCommitmentDto;
import ca.bc.gov.nrs.fta.apt.dto.AptCommitmentRequest;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

/**
 * Commitment writes against {@code APT_COMMITMENTS} and its journal, as
 * {@code APT_COMMITMENT_INS_UPD}, {@code APT_COMMITMENT_DEL} and {@code APT_COMMITMENT_JN_INS}
 * made them.
 */
@DisplayName("Unit Test | AptCommitmentService")
class AptCommitmentServiceTest {

  private static final String USER = "IDIR\\JSMITH";
  private static final LocalDate D = LocalDate.of(2026, 1, 1);
  private static final String JOURNAL = "INSERT INTO the.apt_commitments_jn";

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final JdbcTemplate plain = mock(JdbcTemplate.class);
  private final AptCommitmentService service = new AptCommitmentService(jdbc);

  private static AptCommitmentRequest request(String reason) {
    return new AptCommitmentRequest(5L, " a12345 ", " C ", " FL ", new BigDecimal("1000"), "Y",
        D, D, null, reason);
  }

  private static AptCommitmentDto stored(long id) {
    return new AptCommitmentDto(id, "A12345", "Client", "C", "Conventional", "FL",
        new BigDecimal("1000"), "Y", D, D, null, 5L, null, null);
  }

  private void givenStored(long id) {
    doReturn(List.of(stored(id))).when(jdbc)
        .query(contains("ac.id = :id"), any(SqlParameterSource.class), any(RowMapper.class));
  }

  private SqlParameterSource journalled() {
    ArgumentCaptor<SqlParameterSource> p = ArgumentCaptor.forClass(SqlParameterSource.class);
    verify(jdbc).update(contains(JOURNAL), p.capture());
    return p.getValue();
  }

  private static SQLException ora(int code, String message) {
    return new SQLException(message + "\n\nhttps://docs.oracle.com/error-help/db/ora-0" + code
        + "/", "23000", code);
  }

  @Test
  void createTakesItsIdFromCommSeqAndJournalsAnInsert() {
    when(jdbc.getJdbcTemplate()).thenReturn(plain);
    when(plain.queryForObject(contains("comm_seq.NEXTVAL"), eq(Long.class))).thenReturn(42L);
    givenStored(42L);

    AptCommitmentDto saved = service.create(request(" first "), USER);

    assertThat(saved.id()).isEqualTo(42L);
    ArgumentCaptor<SqlParameterSource> row = ArgumentCaptor.forClass(SqlParameterSource.class);
    verify(jdbc).update(contains("INSERT INTO the.apt_commitments ("), row.capture());
    assertThat(row.getValue().getValue("id")).isEqualTo(42L);
    assertThat(row.getValue().getValue("forestFileId")).isEqualTo("A12345");
    assertThat(row.getValue().getValue("partition")).isEqualTo("C");
    assertThat(row.getValue().getValue("commitType")).isEqualTo("FL");

    SqlParameterSource jn = journalled();
    assertThat(jn.getValue("operation")).isEqualTo("INS");
    assertThat(jn.getValue("id")).isEqualTo(42L);
    assertThat(jn.getValue("user")).isEqualTo(USER);
    assertThat(jn.getValue("reason")).isEqualTo("first");
    assertThat(jn.getValue("appln")).isEqualTo("APTM6300");
    assertThat(jn.getValue("forestFileId")).isEqualTo("A12345");
    assertThat(jn.getSqlType("determinationDate")).isEqualTo(Types.DATE);
  }

  @Test
  void aSavedCommitmentTheReadCannotSeeStillComesBack() {
    when(jdbc.getJdbcTemplate()).thenReturn(plain);
    when(plain.queryForObject(anyString(), eq(Long.class))).thenReturn(7L);

    AptCommitmentDto saved = service.create(request(null), USER);

    assertThat(saved.id()).isEqualTo(7L);
    assertThat(saved.forestFileId()).isEqualTo("A12345");
    assertThat(journalled().getValue("reason")).isNull();
  }

  @Test
  void aDuplicateGetsTheProceduresMessageAnd409() {
    when(jdbc.getJdbcTemplate()).thenReturn(plain);
    when(plain.queryForObject(anyString(), eq(Long.class))).thenReturn(7L);
    when(jdbc.update(contains("INSERT INTO the.apt_commitments ("), any(SqlParameterSource.class)))
        .thenThrow(new DuplicateKeyException("dup",
            ora(1, "ORA-00001: unique constraint (THE.COMM_PK) violated")));

    assertThatThrownBy(() -> service.create(request(null), USER))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(e.getReason()).isEqualTo(AptCommitmentService.DUPLICATE);
        });
    verify(jdbc, never()).update(contains(JOURNAL), any(SqlParameterSource.class));
  }

  @Test
  void anUnknownLicenceIsOraclesOwn400() {
    when(jdbc.getJdbcTemplate()).thenReturn(plain);
    when(plain.queryForObject(anyString(), eq(Long.class))).thenReturn(7L);
    when(jdbc.update(contains("INSERT INTO the.apt_commitments ("), any(SqlParameterSource.class)))
        .thenThrow(new DataIntegrityViolationException("fk", ora(2291,
            "ORA-02291: integrity constraint (THE.COMM_LIC_FK) violated - parent key not found")));

    assertThatThrownBy(() -> service.create(request(null), USER))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(e.getReason()).isEqualTo(
              "ORA-02291: integrity constraint (THE.COMM_LIC_FK) violated - parent key not found");
        });
  }

  @Test
  void anInvalidRequestNeverReachesTheDatabase() {
    AptCommitmentRequest noAac = new AptCommitmentRequest(5L, "A12345", "C", "FL", null, "Y", D,
        D, null, null);

    assertThatThrownBy(() -> service.create(noAac, USER))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getReason()).isEqualTo("AAC is required."));
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  void updateJournalsTheNewValuesWithTheReason() {
    givenStored(9L);
    when(jdbc.update(contains("UPDATE the.apt_commitments"), any(SqlParameterSource.class)))
        .thenReturn(1);

    service.update(9L, request("fix"), USER);

    SqlParameterSource jn = journalled();
    assertThat(jn.getValue("operation")).isEqualTo("UPD");
    assertThat(jn.getValue("id")).isEqualTo(9L);
    assertThat(jn.getValue("reason")).isEqualTo("fix");
    assertThat(jn.getValue("aac")).isEqualTo(new BigDecimal("1000"));
  }

  @Test
  void updatingAMissingCommitmentIs404AndInsertsNothing() {
    assertThatThrownBy(() -> service.update(9L, request("fix"), USER))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  void deleteJournalsTheBeforeImageAfterDeleting() {
    MapSqlParameterSource before = new MapSqlParameterSource()
        .addValue("id", 9L, Types.NUMERIC)
        .addValue("forestFileId", "A12345", Types.VARCHAR)
        .addValue("partition", "C", Types.VARCHAR);
    doReturn(List.of(before)).when(jdbc)
        .query(contains("FROM the.apt_commitments\n"), any(SqlParameterSource.class),
            any(RowMapper.class));
    when(jdbc.update(contains("DELETE FROM the.apt_commitments"), any(SqlParameterSource.class)))
        .thenReturn(1);

    service.delete(9L, "  gone  ", USER);

    InOrder order = inOrder(jdbc);
    order.verify(jdbc).update(contains("DELETE FROM the.apt_commitments"),
        any(SqlParameterSource.class));
    order.verify(jdbc).update(contains(JOURNAL), any(SqlParameterSource.class));
    SqlParameterSource jn = journalled();
    assertThat(jn.getValue("operation")).isEqualTo("DEL");
    assertThat(jn.getValue("forestFileId")).isEqualTo("A12345");
    assertThat(jn.getValue("reason")).isEqualTo("gone");
    assertThat(jn.getValue("appln")).isEqualTo("APTM6300");
  }

  @Test
  void deletingAMissingCommitmentIs404() {
    assertThatThrownBy(() -> service.delete(9L, null, USER))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
          assertThat(e.getReason()).isEqualTo("Commitment 9 was not found.");
        });
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  void aCommitmentAnotherRecordUsesIs409() {
    doReturn(List.of(new MapSqlParameterSource("id", 9L))).when(jdbc)
        .query(contains("FROM the.apt_commitments\n"), any(SqlParameterSource.class),
            any(RowMapper.class));
    when(jdbc.update(contains("DELETE FROM the.apt_commitments"), any(SqlParameterSource.class)))
        .thenThrow(new DataIntegrityViolationException("child", ora(2292,
            "ORA-02292: integrity constraint (THE.EAAC_CMT_FK) violated - child record found")));

    assertThatThrownBy(() -> service.delete(9L, null, USER))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(e.getReason()).startsWith("ORA-02292");
        });
    verify(jdbc, never()).update(contains(JOURNAL), any(SqlParameterSource.class));
  }

  @Test
  void aMalformedHistoryRowIdIs400BeforeTheQuery() {
    assertThatThrownBy(() -> service.history(9L, "' OR 1=1 --"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    verify(jdbc, never()).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
  }
}
