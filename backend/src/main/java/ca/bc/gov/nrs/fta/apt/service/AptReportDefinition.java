package ca.bc.gov.nrs.fta.apt.service;

import ca.bc.gov.nrs.fta.apt.dto.AptReportParams;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * The nine APT PDF reports, rendered in the backend by {@link AptReportRenderer} from
 * {@code reports/apt/<template>.jrxml}. The template names and parameter names are APT2's
 * (from its {@code ReportService}), so the legacy templates can be dropped in as they are
 * once found. That includes APT2's mix of {@code in_rpt_date} and {@code in_report_date};
 * align these with the templates' declared parameters when they are added.
 *
 * <p>APT2 dropped APTR018 (merger impacts), APTR055 (licence AAC credits), APTR060 and
 * APTR065 (TSA exceptions); they are not offered here either.
 *
 * <p>Every report also gets {@code user}, added by {@link AptReportService}.
 */
public enum AptReportDefinition {
  APTR011("APTR011_TSA_AAC_Apt_Commits",
      Param.date("in_rpt_date"), Param.managementUnit("IN_MGMT_UNIT_ID")),
  APTR012("APTR012_RegionAAC_Etc",
      Param.date("in_report_date"), Param.orgUnit("IN_ORG_UNIT_NO")),
  APTR013("APTR013_reg_aac_apt_comm_by_tsa",
      Param.date("in_report_date"), Param.orgUnit("IN_ORG_UNIT_NO")),
  APTR017("APTR017_AAC_summary_by_tsa"),
  APTR021("APTR021_TreeFarmLicenceSummary", Param.date("in_report_date")),
  APTR032("APTR032_prov_summary", Param.date("in_rpt_date")),
  APTR033("APTR033_prov_summary_by_reg", Param.date("in_report_date")),
  APTR041("APTR041_master"),
  APTR043("APTR043_provincial_linkage_aac");

  private final String template;
  private final List<Param> params;

  AptReportDefinition(String template, Param... params) {
    this.template = template;
    this.params = List.of(params);
  }

  /** The report's template name: {@code reports/apt/<template>.jrxml} on the classpath. */
  public String template() {
    return template;
  }

  /** The report for a code such as {@code APTR011} or {@code aptr011}. */
  public static Optional<AptReportDefinition> of(String code) {
    if (code == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(valueOf(code.trim().toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /**
   * This report's own parameters, in declaration order, from the request's inputs.
   *
   * @throws ResponseStatusException 400 naming the first input the report needs but lacks,
   *     or when a management unit or region id is not a positive integer (as APT2 checks)
   */
  public Map<String, String> reportParams(AptReportParams in) {
    AptReportParams args = in == null ? new AptReportParams(null, null, null) : in;
    Map<String, String> out = new LinkedHashMap<>();
    for (Param p : params) {
      String value = p.value().apply(args);
      if (value == null || value.isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            name() + " needs " + p.label() + ".");
      }
      out.put(p.name(), value.trim());
    }
    return out;
  }

  /** One report input: its parameter name, what the user is told it is, and how it is read. */
  private record Param(String name, String label,
      Function<AptReportParams, String> value) {

    static Param date(String name) {
      return new Param(name, "a report date (reportDate)",
          a -> a.reportDate() == null ? null
              : a.reportDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
    }

    static Param managementUnit(String name) {
      return new Param(name, "a management unit (managementUnitId)",
          a -> positive(a.managementUnitId(), AptManagementUnitRules.MANAGEMENT_UNIT_ID_POSITIVE));
    }

    static Param orgUnit(String name) {
      return new Param(name, "a region (orgUnitNo)",
          a -> positive(a.orgUnitNo(), "Organization Unit ID must be a positive integer."));
    }

    /** The id as text; null when absent; 400 when zero or negative. */
    private static String positive(Number id, String message) {
      if (id == null) {
        return null;
      }
      if (id.longValue() < 1) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
      }
      return String.valueOf(id);
    }
  }
}
