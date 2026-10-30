package ca.bc.gov.nrs.fta.tenure.suspension;

import ca.bc.gov.nrs.fta.tenure.suspension.SuspensionDtos.SuspensionCode;
import java.util.List;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** The suspension partition codes (Part 13, Part 15), without the expired ones legacy refused. */
@Component
public class PartitionCodes {

  private static final String SQL =
      """
      SELECT under_partition_code AS code, description
        FROM the.under_partition_code
       WHERE effective_date <= SYSDATE
         AND expiry_date > SYSDATE
       ORDER BY under_partition_code
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public PartitionCodes(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<SuspensionCode> current() {
    return jdbc.query(SQL, (rs, n) ->
        new SuspensionCode(rs.getString("code"), rs.getString("description")));
  }

  public boolean isCurrent(String code) {
    return current().stream().anyMatch(c -> c.code().equals(code));
  }
}
