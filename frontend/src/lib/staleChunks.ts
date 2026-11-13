// After a deploy, the old page still asks for the old JavaScript chunks, which no longer exist.
// The fix is a reload, which gets the new version. Done once automatically; if it happens again
// within a minute something else is wrong, and the error page takes over.

const KEY = 'tf-reloaded-for-update'
const WINDOW_MS = 60_000

export function isStaleChunkError(error: unknown): boolean {
  const message = error instanceof Error ? `${error.name} ${error.message}` : String(error)
  return /Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module|Unable to preload CSS|ChunkLoadError/i.test(
    message,
  )
}

/** Reloads the page unless it was already reloaded for this in the last minute. Returns whether it did. */
export function reloadForUpdate(): boolean {
  try {
    const last = Number(sessionStorage.getItem(KEY) ?? 0)
    if (Date.now() - last < WINDOW_MS) return false
    sessionStorage.setItem(KEY, String(Date.now()))
  } catch {
    return false
  }
  window.location.reload()
  return true
}
