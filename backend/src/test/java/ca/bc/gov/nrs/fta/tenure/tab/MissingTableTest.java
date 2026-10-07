package ca.bc.gov.nrs.fta.tenure.tab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLSyntaxErrorException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;

class MissingTableTest {

  /** Shaped as ojdbc's OracleDatabaseException: the position is in toString(), not the message. */
  static final class DriverError extends Exception {
    private final int position;
    private final String sql;

    DriverError(int position, String sql) {
      super("ORA-00942: table or view does not exist");
      this.position = position;
      this.sql = sql;
    }

    public int getErrorPosition() {
      return position;
    }

    public String getSql() {
      return sql;
    }

    @Override
    public String toString() {
      return "Error : 942, Position : " + position + ", Sql = " + sql;
    }
  }

  private static BadSqlGrammarException oracle(int code) {
    return new BadSqlGrammarException("query", "SELECT 1",
        new SQLSyntaxErrorException("ORA-" + code, "42000", code));
  }

  @Test
  void returnsTheListWhenTheQueryWorks() {
    List<String> notices = new ArrayList<>();
    assertEquals(List.of("a"), MissingTable.orEmpty(() -> List.of("a"), "Rows", notices));
    assertTrue(notices.isEmpty());
  }

  @Test
  void aMissingTableGivesAnEmptyListAndANotice() {
    List<String> notices = new ArrayList<>();
    List<String> rows = MissingTable.orEmpty(() -> {
      throw oracle(942);
    }, "Smart form applications", notices);
    assertTrue(rows.isEmpty());
    assertEquals(1, notices.size());
    assertTrue(notices.get(0).startsWith("Smart form applications could not be read"));
  }

  @Test
  void theNoticeNamesTheTableAtOraclesErrorPosition() {
    String sql = "SELECT 1\n  FROM the.cutting_permit_request cpr\n WHERE 1 = 1";
    int position = sql.indexOf("the.cutting");
    SQLSyntaxErrorException driver = new SQLSyntaxErrorException(
        "ORA-00942: table or view does not exist", "42000", 942,
        new DriverError(position, sql));
    List<String> notices = new ArrayList<>();
    MissingTable.orEmpty(() -> {
      throw new BadSqlGrammarException("query", sql, driver);
    }, "Smart form applications", notices);
    assertEquals("Smart form applications could not be read: table THE.CUTTING_PERMIT_REQUEST"
        + " is missing from this database or not granted to the application.", notices.get(0));
  }

  @Test
  void anyOtherErrorIsRethrown() {
    List<String> notices = new ArrayList<>();
    assertThrows(BadSqlGrammarException.class, () -> MissingTable.orEmpty(() -> {
      throw oracle(904);
    }, "Rows", notices));
    assertTrue(notices.isEmpty());
  }
}
