package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.shared.csv.CsvWriter;
import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
import ca.bc.gov.nrs.fta.tenure.dto.TenureSearchCriteria;
import ca.bc.gov.nrs.fta.tenure.dto.TenureSummaryDto;
import org.springframework.stereotype.Service;

/**
 * Tenure search business logic.
 *
 * <p>Ports the legacy Oracle package {@code THE.FTA_001_TENR_SRCH} (the common
 * tenure search) as SQL against the {@code THE} tables; see
 * {@link TenureSearchTableSource}. The package itself is not called.
 */
@Service
public class TenureService {

  private final TenureSearchTableSource source;

  public TenureService(TenureSearchTableSource source) {
    this.source = source;
  }

  /**
   * Common tenure search — mirrors {@code FTA_001_TENR_SRCH.mainline}.
   *
   * @param criteria the screen's criteria; any field may be null or blank
   * @param page     0-indexed page number
   * @param size     rows per page
   */
  public PagedResponse<TenureSummaryDto> search(
      TenureSearchCriteria criteria, int page, int size) {
    return search(criteria, page, size, null);
  }

  /**
   * {@link #search(TenureSearchCriteria, int, int)} in a results-table column sort.
   *
   * @param sort {@code <column>,asc|desc} (see {@code ColumnSort}); null keeps the
   *             criteria's {@code sortBy} order
   */
  public PagedResponse<TenureSummaryDto> search(
      TenureSearchCriteria criteria, int page, int size, String sort) {
    return source.search(criteria, page, size, sort);
  }

  /**
   * Every matching tenure as CSV rows — the same criteria and order as
   * {@link #search}, with no paging.
   *
   * @param criteria the screen's criteria; any field may be null or blank
   * @param csv      the writer the rows are streamed to
   */
  public void exportCsv(TenureSearchCriteria criteria, CsvWriter csv) {
    exportCsv(criteria, null, csv);
  }

  /** {@link #exportCsv(TenureSearchCriteria, CsvWriter)} in the table's column sort. */
  public void exportCsv(TenureSearchCriteria criteria, String sort, CsvWriter csv) {
    source.exportCsv(criteria, sort, csv);
  }
}
