/**
 * One page of results plus the metadata Carbon's `Pagination` needs.
 *
 * Mirrors the backend `PagedResponse`, which uses the same envelope as the
 * sibling applications. Defined once here rather than per service so every
 * paginated search speaks the same shape.
 *
 * `page.number` is 0-indexed, following the backend; Carbon's `Pagination` is
 * 1-indexed, so pages convert at that boundary.
 */
export interface PageableResponse<T> {
  content: T[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

/** Rows per page a search asks for when the user hasn't chosen. */
export const DEFAULT_PAGE_SIZE = 10;

/** The page sizes offered in the results footer. */
export const PAGE_SIZES = [10, 25, 50, 100];
