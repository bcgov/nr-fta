package ca.bc.gov.nrs.fta.shared.dto;

import java.util.List;

/**
 * One page of results plus the metadata a pagination control needs.
 *
 * <p>The JSON shape — {@code {content: [...], page: {size, number,
 * totalElements, totalPages}}} — deliberately matches the sibling applications
 * (nr-fsp-new's {@code PageableResponse}) and Spring Data's {@code Page}
 * serialization under {@code PageSerializationMode.VIA_DTO}, which this
 * application already enables. Keeping the envelope identical means a frontend
 * table component can be moved between these apps without an adapter.
 *
 * <p>{@code number} is 0-indexed, following Spring's convention. Carbon's
 * {@code Pagination} is 1-indexed, so the frontend converts at that boundary
 * rather than here.
 *
 * @param content this page's rows
 * @param page    where this page sits in the whole result set
 */
public record PagedResponse<T>(List<T> content, PageInfo page) {

  /**
   * @param size          rows per page that was asked for
   * @param number        0-indexed page number
   * @param totalElements rows matching the query across every page
   * @param totalPages    pages at this size
   */
  public record PageInfo(int size, int number, long totalElements, int totalPages) {}

  /**
   * Envelope for a source that paginated in the database — {@code content} is
   * already just this page's rows and {@code totalElements} came from a
   * separate {@code COUNT(*)}. Nothing is sliced here.
   */
  public static <T> PagedResponse<T> ofPage(
      List<T> content, int page, int size, long totalElements) {
    int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
    return new PagedResponse<>(content, new PageInfo(size, page, totalElements, totalPages));
  }

  /**
   * Envelope for a source that could not paginate and handed back every row —
   * the legacy PL/SQL packages open one cursor over the whole result set. The
   * slice happens here instead, which costs the full fetch but keeps the wire
   * contract identical whichever source served the request.
   */
  public static <T> PagedResponse<T> ofFullList(List<T> all, int page, int size) {
    int total = all.size();
    int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
    // Clamp both ends: a page past the end yields an empty slice rather than
    // an IndexOutOfBoundsException when the row count shrinks between calls.
    int from = Math.min(Math.max(page, 0) * size, total);
    int to = Math.min(from + size, total);
    return new PagedResponse<>(all.subList(from, to), new PageInfo(size, page, total, totalPages));
  }
}
