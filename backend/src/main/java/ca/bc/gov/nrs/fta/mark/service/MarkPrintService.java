package ca.bc.gov.nrs.fta.mark.service;

import ca.bc.gov.nrs.fta.mark.dto.MarkDetailDto;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * FTA510 "Print": the FTA402 certificate and, for a district user, the acknowledgement that
 * goes with it.
 *
 * <p>Legacy produced the report and then ran {@code FTA_510_PRIVATE_MARK.ISSUE_MARK}, which
 * changes anything only when the user's organization is a district: an approved amendment
 * (HN) becomes HI, or else the issued-but-unprinted mark (HN) becomes HI, with its forest
 * file and tenure term. Here that district behaviour belongs to the
 * {@code FTA_TIMBER_MARK_DISTRICT_ADMIN} role; everyone else's print just prints, as
 * Headquarters' did. Legacy offered Print only once the status began with H.
 */
@Service
public class MarkPrintService {

  private final NamedParameterJdbcTemplate jdbc;
  private final MarkDetailService markDetailService;
  private final MarkCertificateReport report;
  private final MarkUpdateService markUpdateService;

  public MarkPrintService(
      NamedParameterJdbcTemplate jdbc,
      MarkDetailService markDetailService,
      MarkCertificateReport report,
      MarkUpdateService markUpdateService) {
    this.jdbc = jdbc;
    this.markDetailService = markDetailService;
    this.report = report;
    this.markUpdateService = markUpdateService;
  }

  /**
   * The certificate PDF; for a district user, also HN to HI.
   *
   * @throws ResponseStatusException 404 if the mark does not exist; 409 if it has no
   *     certificate to print yet, or changed while printing
   */
  @Transactional
  public byte[] print(String id, boolean byCertificate, boolean districtUser, String userId) {
    MarkDetailDto mark = (byCertificate
            ? markDetailService.findByCertificate(id)
            : markDetailService.findByMarkNumber(id))
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Private mark not found."));
    if (!printable(mark)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "A certificate can be printed once the mark is issued.");
    }
    byte[] pdf = report.render(mark.certificate());
    if (pdf == null) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "This mark has no certificate yet — it needs its forest file and main client.");
    }
    if (districtUser) {
      acknowledge(mark, userId);
    }
    return pdf;
  }

  /** Legacy's getDisablePrint: Print only once the status begins with H. */
  public static boolean printable(MarkDetailDto mark) {
    return mark.markStatusCode() != null && mark.markStatusCode().startsWith("H");
  }

  /** ISSUE_MARK, district branch. mainline skips it for a cancelled (HX) mark. */
  private void acknowledge(MarkDetailDto mark, String userId) {
    if ("HX".equals(mark.markStatusCode()) || mark.timberMark() == null) {
      return;
    }
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("mark", mark.timberMark())
        .addValue("revision", mark.revisionCount())
        .addValue("amendRevision", mark.amendRevisionCount())
        .addValue("userId", userId);

    if ("HN".equals(mark.outstandingAmendStatus())) {
      int n = jdbc.update(
          """
          UPDATE the.tmbr_mark_amend
             SET prv_mrk_amd_sts_st = 'HI',
                 update_timestamp   = SYSDATE,
                 update_userid      = :userId,
                 revision_count     = revision_count + 1
           WHERE timber_mark = :mark
             AND prv_mrk_amd_sts_st = 'HN'
             AND revision_count = :amendRevision
          """,
          p);
      if (n == 0) {
        throw changedElsewhere();
      }
      jdbc.update(
          """
          UPDATE the.private_mark_certificate
             SET private_mark_cancel_date = NULL
           WHERE timber_mark = :mark AND revision_count = :revision
          """,
          p);
    } else if ("HN".equals(mark.markStatusCode())) {
      int n = jdbc.update(
          """
          UPDATE the.private_mark_certificate
             SET private_mark_status_code = 'HI',
                 private_mark_status_date = SYSDATE,
                 update_timestamp         = SYSDATE,
                 update_userid            = :userId,
                 revision_count           = revision_count + 1
           WHERE timber_mark = :mark AND revision_count = :revision
          """,
          p);
      if (n == 0) {
        throw changedElsewhere();
      }
      jdbc.update(
          """
          UPDATE the.prov_forest_use
             SET file_status_st   = 'HI',
                 update_timestamp = SYSDATE,
                 update_userid    = :userId,
                 revision_count   = revision_count + 1
           WHERE forest_file_id = :mark
          """,
          p);
      markUpdateService.updateTerm(mark, userId);
    }
  }

  private static ResponseStatusException changedElsewhere() {
    return new ResponseStatusException(
        HttpStatus.CONFLICT,
        "This mark was changed by someone else since you opened it. Reload and try again.");
  }
}
