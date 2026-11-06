package ca.bc.gov.nrs.fta.apt.service;

import ca.bc.gov.nrs.fta.apt.dto.AptRelatedClientCreateRequest;
import ca.bc.gov.nrs.fta.apt.dto.AptRelatedClientDto;
import java.sql.SQLException;
import java.util.List;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Client relationships (linkages): which corporation controls which licensee. APT2's Related
 * Clients screen (APTM9200), over {@code THE.APT_RELATED_CLIENTS} directly; the SQL is what
 * APT2's {@code APT_RELATED_CLIENTS_GET / _INS / _DEL} ran.
 *
 * <p>The table has no audit columns, no sequence and no triggers, so no user is written.
 */
@Service
public class AptRelatedClientService {

  /** The relationship APT2 created: shown as CONTROLS, stored as CORP. */
  public static final String DEFAULT_RELATIONSHIP = "CORP";

  /** {@code APT_RELATED_CLIENTS_INS}'s {@code record_exist}; 409. */
  static final String EXISTS_MSG =
      "The client relationship you are trying to create is already exist in the database";
  /** {@code client_num_invalid}; 400. */
  static final String CLIENT_INVALID_MSG = "The client number is not valid";
  /** {@code parent_num_invalid}; 400. */
  static final String PARENT_INVALID_MSG = "The controlling client number is not valid";
  /** ORA-00001 on the insert (in practice REL_CLI_UK: one parent per licensee); 409. */
  static final String DUPLICATE_MSG = "Record existed with the same Licensee and Relationship.";

  static final String LIST_SQL = """
      SELECT arc.client_number,
             fc1.client_name            AS client_name,
             arc.client_number_parented_by,
             fc2.client_name            AS client_name_parented_by,
             arc.apt_client_rel_cd
        FROM the.apt_related_clients arc
        JOIN the.forest_client fc1 ON fc1.client_number = arc.client_number
        JOIN the.forest_client fc2 ON fc2.client_number = arc.client_number_parented_by
       ORDER BY fc2.client_name, fc1.client_name
      """;

  static final String EXISTS_SQL = """
      SELECT COUNT(*)
        FROM the.apt_related_clients
       WHERE client_number = :child
         AND client_number_parented_by = :parent
         AND apt_client_rel_cd = :rel
      """;

  static final String CLIENT_EXISTS_SQL = """
      SELECT COUNT(*) FROM the.forest_client WHERE client_number = :clientNumber
      """;

  static final String INSERT_SQL = """
      INSERT INTO the.apt_related_clients (
        client_number, client_number_parented_by, apt_client_rel_cd
      ) VALUES (
        :child, :parent, :rel
      )
      """;

  static final String DELETE_SQL = """
      DELETE FROM the.apt_related_clients
       WHERE client_number = :child
         AND client_number_parented_by = :parent
         AND apt_client_rel_cd = :rel
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public AptRelatedClientService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Every relationship, ordered by controlling corporation then licensee name. */
  public List<AptRelatedClientDto> list() {
    return jdbc.query(LIST_SQL, (rs, i) -> new AptRelatedClientDto(
        rs.getString("CLIENT_NUMBER"),
        rs.getString("CLIENT_NAME"),
        rs.getString("CLIENT_NUMBER_PARENTED_BY"),
        rs.getString("CLIENT_NAME_PARENTED_BY"),
        rs.getString("APT_CLIENT_REL_CD")));
  }

  /**
   * Links a licensee to its controlling corporation, checking as {@code APT_RELATED_CLIENTS_INS}
   * did and in its order: the same three keys again (409), an unknown licensee (400), an
   * unknown corporation (400). A licensee may have only one parent per relationship (unique
   * index REL_CLI_UK); that collision is 409 with the routine's own message.
   *
   * @return the keys of the relationship created (the relationship code defaulted)
   */
  @Transactional
  public AptRelatedClientCreateRequest create(AptRelatedClientCreateRequest request) {
    String child = required(request == null ? null : request.clientNumber(),
        "Licensee client number");
    String parent = required(request.parentClientNumber(), "Corporation client number");
    String rel = request.relationshipCode() == null || request.relationshipCode().isBlank()
        ? DEFAULT_RELATIONSHIP : request.relationshipCode().trim();
    MapSqlParameterSource keys = keys(child, parent, rel);
    try {
      if (count(EXISTS_SQL, keys) > 0) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, EXISTS_MSG);
      }
      if (count(CLIENT_EXISTS_SQL, new MapSqlParameterSource("clientNumber", child)) == 0) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, CLIENT_INVALID_MSG);
      }
      if (count(CLIENT_EXISTS_SQL, new MapSqlParameterSource("clientNumber", parent)) == 0) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PARENT_INVALID_MSG);
      }
      jdbc.update(INSERT_SQL, keys);
    } catch (DuplicateKeyException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, DUPLICATE_MSG, e);
    } catch (DataAccessException e) {
      throw databaseError(e);
    }
    return new AptRelatedClientCreateRequest(child, parent, rel);
  }

  /**
   * Removes one relationship, keyed by all three codes; deleting one that doesn't exist is not
   * an error ({@code APT_RELATED_CLIENTS_DEL}).
   */
  @Transactional
  public void delete(String clientNumber, String parentClientNumber, String relationshipCode) {
    String child = required(clientNumber, "clientNumber");
    String parent = required(parentClientNumber, "parentClientNumber");
    String rel = required(relationshipCode, "relationshipCode");
    try {
      jdbc.update(DELETE_SQL, keys(child, parent, rel));
    } catch (DataAccessException e) {
      throw databaseError(e);
    }
  }

  private int count(String sql, MapSqlParameterSource params) {
    Integer n = jdbc.queryForObject(sql, params, Integer.class);
    return n == null ? 0 : n;
  }

  private static MapSqlParameterSource keys(String child, String parent, String rel) {
    return new MapSqlParameterSource()
        .addValue("child", child)
        .addValue("parent", parent)
        .addValue("rel", rel);
  }

  /**
   * Any other database error, as the routines' {@code WHEN OTHERS} reported it: Oracle's own
   * message, 409 for a blocked delete (ORA-02292), otherwise 400 (e.g. ORA-12899 for a
   * relationship code longer than the column).
   */
  static ResponseStatusException databaseError(DataAccessException e) {
    Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
    String msg = cause.getMessage() == null ? e.getMessage() : cause.getMessage().trim();
    boolean childRows = cause instanceof SQLException sql && sql.getErrorCode() == 2292;
    return new ResponseStatusException(
        childRows ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST, msg, e);
  }

  private static String required(String v, String what) {
    if (v == null || v.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, what + " is required.");
    }
    return v.trim();
  }
}
