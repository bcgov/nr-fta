/** Hands a fetched file (a generated PDF, say) to the browser as a download. */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  // Released after the click has handed the file to the browser's download.
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
