import { useCallback, useEffect, useState } from 'react';

import { tenureDetailPath } from '@/routes/tenurePaths';
import { getRangeFileTypes } from '@/services/codeLists';

/**
 * The detail path of a file known by its type, for a list of files of mixed types (Tenure
 * Search): a range file type opens the range tenure page. Until the range file types load, or if
 * they fail to, every file opens the tenure page, which redirects a range file itself.
 */
export function useTenurePath(): (
  forestFileId: string,
  fileTypeCode: string | null | undefined,
) => string {
  const [rangeTypes, setRangeTypes] = useState<ReadonlySet<string>>(new Set());

  useEffect(() => {
    let cancelled = false;
    getRangeFileTypes()
      .then((types) => {
        if (!cancelled) setRangeTypes(new Set(types.map((t) => t.code)));
      })
      .catch(() => {
        // Not worth a toast: the tenure page redirects a range file.
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return useCallback(
    (forestFileId, fileTypeCode) =>
      tenureDetailPath(forestFileId, !!fileTypeCode && rangeTypes.has(fileTypeCode)),
    [rangeTypes],
  );
}
