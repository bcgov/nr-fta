/**
 * Where a forest file's detail page is. Legacy FTA100 redirected every range file (a file type in
 * RANGE_FILE_TYPE_CODE) to the range tenure screen, FTA100R, so a range file opens `/range/{id}`
 * and any other file `/tenures/{id}`. The tenure page redirects a range file it is handed, so a
 * link that cannot tell still lands right.
 */
export function tenureDetailPath(forestFileId: string, rangeFile = false): string {
  const id = encodeURIComponent(forestFileId);
  return rangeFile ? `/range/${id}` : `/tenures/${id}`;
}
