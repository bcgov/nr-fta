package ca.bc.gov.nrs.fta.apt.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.web.server.ResponseStatusException;

/** The routines' error convention, as the direct SQL reproduces it. */
@DisplayName("Unit Test | AptManagementUnitSql")
class AptManagementUnitSqlTest {

  static DuplicateKeyException duplicate() {
    return new DuplicateKeyException("insert", new SQLIntegrityConstraintViolationException(
        "ORA-00001: unique constraint (THE.PART_UK1) violated\n\nhttps://docs.oracle.com/x",
        "23000", 1));
  }

  static DataIntegrityViolationException childRecord() {
    return new DataIntegrityViolationException("delete", new SQLIntegrityConstraintViolationException(
        "ORA-02292: integrity constraint (THE.PAPT_PART_FK) violated - child record found\n",
        "23000", 2292));
  }

  static UncategorizedSQLException triggerError() {
    return new UncategorizedSQLException("insert", "INSERT", new SQLException(
        "ORA-20000: The Partition Type does not exist on the Partition Effective Date\n"
            + "ORA-06512: at \"THE.PART_BR_IUD_TRG\", line 6", "72000", 20000));
  }

  private static ResponseStatusException rse(RuntimeException e) {
    assertThat(e).isInstanceOf(ResponseStatusException.class);
    return (ResponseStatusException) e;
  }

  @Test
  void aDuplicateIsA409WithTheOracleError() {
    ResponseStatusException e = rse(AptManagementUnitSql.dbError(duplicate()));
    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(e.getReason()).isEqualTo("ORA-00001: unique constraint (THE.PART_UK1) violated");
  }

  @Test
  void aChildRecordIsA409() {
    assertThat(rse(AptManagementUnitSql.dbError(childRecord())).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void anyOtherOracleErrorIsA400WithItsFirstLine() {
    ResponseStatusException e = rse(AptManagementUnitSql.dbError(triggerError()));
    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(e.getReason())
        .isEqualTo("ORA-20000: The Partition Type does not exist on the Partition Effective Date");
  }

  @Test
  void theRoutinesOwnWordingReplacesItsCode() {
    ResponseStatusException e = rse(AptManagementUnitSql.dbError(duplicate(), 1, "Taken."));
    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(e.getReason()).isEqualTo("Taken.");
    assertThat(rse(AptManagementUnitSql.dbError(triggerError(), 1, "Taken.")).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void aTimeoutIsNotAnApiError() {
    QueryTimeoutException timeout = new QueryTimeoutException("slow",
        new SQLException("ORA-01013: user requested cancel", "72000", 1013));
    assertThat(AptManagementUnitSql.dbError(timeout)).isSameAs(timeout);
    assertThat(AptManagementUnitSql.isOracleError(timeout)).isFalse();
    assertThat(AptManagementUnitSql.isOracleError(triggerError())).isTrue();
  }

  @Test
  void blanksAreBoundAsNull() {
    assertThat(AptManagementUnitSql.str("  ")).isNull();
    assertThat(AptManagementUnitSql.str(" a ")).isEqualTo(" a ");
  }
}
