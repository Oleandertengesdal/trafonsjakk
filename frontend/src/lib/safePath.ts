/** Only paths inside this app ("/games"), never another site ("//evil.example", "https://…"). */
export function safeAppPath(path: unknown): string | null {
  return typeof path === 'string' && /^\/(?![/\\])/.test(path) ? path : null
}
