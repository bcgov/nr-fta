import { apiFetch, readErrorMessage } from './apiFetch';

/**
 * Downloads a CSV export from the API.
 *
 * Fetched rather than opened as a link: the export endpoints sit behind the
 * bearer token that {@link apiFetch} attaches, and a plain `<a href>` or
 * `window.open` carries no Authorization header, so the browser would be sent
 * to a 401. The response is read into a blob and handed to a temporary anchor,
 * which is what actually saves the file.
 *
 * The whole file therefore passes through browser memory. Exports are unbounded
 * by design, so a very large one costs the tab that memory — the alternative
 * (streaming to disk) needs the File System Access API and is not supported
 * everywhere.
 */
export async function downloadCsv(path: string): Promise<void> {
  const res = await apiFetch(path, { method: 'GET' });
  if (!res.ok) {
    const message = await readErrorMessage(res);
    throw new Error(message || `Export failed (${res.status})`);
  }

  const blob = await res.blob();
  // Prefer the filename the server chose (it carries the export date); fall
  // back to the last path segment when the header is absent or unparseable.
  const filename = filenameFrom(res.headers.get('content-disposition')) ?? 'export.csv';

  const url = URL.createObjectURL(blob);
  try {
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    // Firefox only honours a click on an element that is in the document.
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
  } finally {
    // Revoking immediately is safe: the download has already been started from
    // the blob, and holding the URL would pin the file in memory.
    URL.revokeObjectURL(url);
  }
}

/** The `filename` from a Content-Disposition header, if it has a usable one. */
function filenameFrom(header: string | null): string | null {
  if (!header) return null;
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (encoded?.[1]) {
    try {
      return decodeURIComponent(encoded[1]);
    } catch {
      // Malformed encoding — fall through to the plain form.
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(header);
  return plain?.[1] ?? null;
}
