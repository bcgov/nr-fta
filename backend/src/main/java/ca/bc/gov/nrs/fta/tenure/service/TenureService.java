package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
import ca.bc.gov.nrs.fta.tenure.dto.TenureSearchCriteria;
import ca.bc.gov.nrs.fta.tenure.dto.TenureSummaryDto;
import org.springframework.stereotype.Service;

/**
 * Tenure search business logic.
 *
 * <p>Ports the legacy Oracle package {@code THE.FTA_001_TENR_SRCH} (the common
 * tenure search). The rows themselves come from a {@link TenureSearchSource},
 * of which there are two — one calling the legacy package, one querying the
 * {@code THE} tables directly — selected by {@code fta.data-access.mode}. See
 * that interface for why both exist and which is expected to survive.
 *
 * <p>This class keeps the signature the controller has always called, so the
 * choice of source is invisible above this line.
 */
@Service
public class TenureService {

  private final TenureSearchSource source;

  public TenureService(TenureSearchSource source) {
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
    return source.search(criteria, page, size);
  }
}
