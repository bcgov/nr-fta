package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
import ca.bc.gov.nrs.fta.tenure.dto.TenureSearchCriteria;
import ca.bc.gov.nrs.fta.tenure.dto.TenureSummaryDto;

/**
 * Where tenure-search rows come from.
 *
 * <p>Two implementations, chosen by {@code fta.data-access.mode}:
 *
 * <ul>
 *   <li>{@code table} — {@link TenureSearchTableSource}, native SQL over the
 *       {@code THE} tables. The default, and the intended end state. Needs
 *       {@code SELECT} on each table, held via
 *       {@code FSA_FTA_READ_WRITE_ROLE}.</li>
 *   <li>{@code package} — {@link TenureSearchPackageSource}, calling
 *       {@code THE.FTA_001_TENR_SRCH.MAINLINE}. Packages run with definer's
 *       rights (as {@code THE}), so {@code EXECUTE} on the package is enough
 *       and no table grants are needed. Kept as a fallback for an account that
 *       holds package privileges alone.</li>
 * </ul>
 *
 * <p>The seam is deliberately this thin. The package implementation is meant to
 * be deleted wholesale once the table path is trusted: drop the class, drop the
 * property, and inline the table source back into {@link TenureService}.
 */
public interface TenureSearchSource {

  /**
   * Common tenure search — the legacy {@code FTA_001_TENR_SRCH} screen.
   *
   * @param criteria the screen's criteria; any field may be null or blank
   * @param page     0-indexed page number
   * @param size     rows per page
   * @return this page's rows plus the totals the pagination control needs
   */
  PagedResponse<TenureSummaryDto> search(TenureSearchCriteria criteria, int page, int size);
}
