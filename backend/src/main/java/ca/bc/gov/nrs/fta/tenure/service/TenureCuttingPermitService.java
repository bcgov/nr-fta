package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.tenure.dto.TenureCuttingPermitDto;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * A tenure's cutting permits — the legacy FTA901 Cutting Permit List.
 *
 * <p>Ports {@code FTA_901_CUT_PERM_LST.GET}: the file's harvesting authorities with their
 * primary timber mark, plus Fort St. John authorities (harvest type F) that have no mark yet,
 * ordered by cutting permit. Legacy's {@code (+)} outer joins are ANSI joins here, and its
 * {@code is_multi_mark} function — whether an FSJ authority's cut blocks carry more than one
 * mark — is an inline EXISTS.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class TenureCuttingPermitService {

  private static final String LIST_SQL =
      """
      SELECT ou.org_unit_code,
             DECODE(hva.harvest_type_code, 'F', hva.harvesting_authority_id,
                    hva.cutting_permit_id)                         AS cutting_permit_id,
             xref.timber_mark
               || CASE WHEN hva.harvest_type_code = 'F'
                        AND (SELECT COUNT(DISTINCT hau.timber_mark)
                               FROM the.cut_block cb
                               JOIN the.harvesting_hauling_xref hhx ON hhx.hva_skey = cb.hva_skey
                               JOIN the.hauling_authority hau
                                 ON hau.timber_mark = cb.timber_mark
                                AND hau.timber_mark = hhx.timber_mark
                              WHERE cb.hva_skey = hva.hva_skey) > 1
                       THEN '...' END                              AS timber_mark,
             hva.harvest_auth_status_code                          AS status_code,
             CASE WHEN msc.description IS NOT NULL
                  THEN hva.harvest_auth_status_code || ' - ' || msc.description
             END                                                   AS status_desc,
             hva.issue_date, hva.expiry_date, hva.extend_date,
             hva.salvage_type_code, hva.hva_skey,
             DECODE(hva.harvest_type_code, 'F', 'Y', 'N')          AS fsj_ind
        FROM the.harvesting_authority hva
        LEFT JOIN the.harvesting_hauling_xref xref ON xref.hva_skey = hva.hva_skey
        LEFT JOIN the.org_unit ou ON ou.org_unit_no = hva.forest_district
        LEFT JOIN the.harvest_auth_status_code msc
               ON msc.harvest_auth_status_code = hva.harvest_auth_status_code
       WHERE hva.forest_file_id = :forestFileId
         -- the primary mark; legacy's NVL(xref.primary_mark_ind, 'Y') also keeps an
         -- authority with no mark (its outer-joined xref is all null).
         AND NVL(xref.primary_mark_ind, 'Y') = 'Y'
         -- legacy's second branch (FSJ authorities without a mark) is the same rows the
         -- outer join above already returns, so it needs no UNION here.
       ORDER BY cutting_permit_id
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public TenureCuttingPermitService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** The file's cutting permits; empty when it has none (or does not exist). */
  public List<TenureCuttingPermitDto> list(String forestFileId) {
    return jdbc.query(
        LIST_SQL,
        new MapSqlParameterSource("forestFileId", forestFileId),
        (rs, rowNum) -> new TenureCuttingPermitDto(
            rs.getString("org_unit_code"),
            rs.getString("cutting_permit_id"),
            rs.getString("timber_mark"),
            rs.getString("status_code"),
            rs.getString("status_desc"),
            rs.getObject("issue_date", LocalDate.class),
            rs.getObject("expiry_date", LocalDate.class),
            rs.getObject("extend_date", LocalDate.class),
            rs.getString("salvage_type_code"),
            rs.getObject("hva_skey", Long.class),
            "Y".equals(rs.getString("fsj_ind"))));
  }
}
