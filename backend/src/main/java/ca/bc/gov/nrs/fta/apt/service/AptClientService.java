package ca.bc.gov.nrs.fta.apt.service;

import ca.bc.gov.nrs.fta.apt.dto.AptClientDto;
import java.sql.Types;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * The client list APT's related-client search picks from.
 *
 * <p>APT keeps its own search (APT2's {@code APT_CLIENTS_GET}, ported here to SQL) rather than
 * FTA's client type-ahead, because that is the list APT validated relationships against.
 */
@Service
public class AptClientService {

  /**
   * {@code APT_CLIENTS_GET} as it was: a contains-match on both, a missing argument matching
   * anything ({@code '%' || NULL || '%'} is {@code '%%'}), no ordering. A {@code %} or
   * {@code _} typed by the user is a wildcard, as it was in APT2.
   */
  static final String SEARCH_SQL = """
      SELECT client_number,
             client_name
        FROM the.forest_client
       WHERE client_number LIKE '%' || :clientNumber || '%'
         AND UPPER(client_name) LIKE '%' || UPPER(:clientName) || '%'
      """;

  private static final RowMapper<AptClientDto> CLIENT = (rs, i) ->
      new AptClientDto(rs.getString("CLIENT_NUMBER"), rs.getString("CLIENT_NAME"));

  private final NamedParameterJdbcTemplate jdbc;

  public AptClientService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Clients whose number contains {@code clientNumber} and whose name contains
   * {@code clientName} (both upper-cased, as APT2 did).
   *
   * <p>At least one is required: a missing argument means "anything", so with neither it would
   * return every forest client.
   */
  public List<AptClientDto> search(String clientNumber, String clientName) {
    String number = upper(clientNumber);
    String name = upper(clientName);
    if (number == null && name == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Enter a client number or name to search.");
    }
    return jdbc.query(SEARCH_SQL, new MapSqlParameterSource()
        .addValue("clientNumber", number, Types.VARCHAR)
        .addValue("clientName", name, Types.VARCHAR), CLIENT);
  }

  private static String upper(String v) {
    return v == null || v.isBlank() ? null : v.trim().toUpperCase(Locale.ROOT);
  }
}
