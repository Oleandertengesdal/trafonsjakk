// Fetch wrapper: same-origin cookies, CSRF header, and one silent refresh on 401.

/**
 * An API failure. `code` is the server's stable error code (translated by `useErrorText`);
 * `message` is an English fallback for logs.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string | null
  readonly params: Record<string, unknown>
  readonly fieldErrors: Record<string, string>
  readonly fieldCodes: Record<string, string>

  constructor(
    status: number,
    message: string,
    opts: {
      code?: string | null
      params?: Record<string, unknown>
      fieldErrors?: Record<string, string>
      fieldCodes?: Record<string, string>
    } = {},
  ) {
    super(message)
    this.status = status
    this.code = opts.code ?? null
    this.params = opts.params ?? {}
    this.fieldErrors = opts.fieldErrors ?? {}
    this.fieldCodes = opts.fieldCodes ?? {}
  }
}

const CSRF_COOKIE = 'XSRF-TOKEN'
const UNSAFE = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])
const NO_REFRESH = ['/api/auth/login', '/api/auth/register', '/api/auth/refresh', '/api/auth/csrf']

function readCookie(name: string): string | null {
  const match = document.cookie.split('; ').find((c) => c.startsWith(name + '='))
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null
}

let csrfPromise: Promise<void> | null = null
async function ensureCsrf(): Promise<void> {
  if (readCookie(CSRF_COOKIE)) return
  // Cleared on failure too: a failed attempt (offline, server waking up) must not be reused forever.
  csrfPromise ??= fetch('/api/auth/csrf', { credentials: 'same-origin' })
    .then(() => undefined)
    .finally(() => {
      csrfPromise = null
    })
  return csrfPromise
}

let refreshPromise: Promise<boolean> | null = null
async function refreshSession(): Promise<boolean> {
  refreshPromise ??= (async () => {
    try {
      await ensureCsrf()
      const res = await fetch('/api/auth/refresh', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'X-XSRF-TOKEN': readCookie(CSRF_COOKIE) ?? '' },
      })
      return res.ok
    } catch {
      return false
    } finally {
      setTimeout(() => (refreshPromise = null), 0)
    }
  })()
  return refreshPromise
}

/** Called when the session can't be refreshed; the auth provider subscribes to it. */
let onSessionExpired: () => void = () => {}
export function setSessionExpiredHandler(fn: () => void) {
  onSessionExpired = fn
}

async function send(path: string, init: RequestInit): Promise<Response> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (UNSAFE.has(method)) {
    await ensureCsrf()
    headers.set('X-XSRF-TOKEN', readCookie(CSRF_COOKIE) ?? '')
  }
  return fetch(path, { ...init, method, headers, credentials: 'same-origin' })
}

const isRecord = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v)

async function toError(res: Response): Promise<ApiError> {
  let message = `HTTP ${res.status}`
  let code: string | null = null
  let params: Record<string, unknown> = {}
  let fieldErrors: Record<string, string> = {}
  let fieldCodes: Record<string, string> = {}
  try {
    const body: unknown = await res.json()
    if (isRecord(body)) {
      if (typeof body.detail === 'string' && body.detail) message = body.detail
      if (typeof body.code === 'string') code = body.code
      if (isRecord(body.params)) params = body.params
      if (isRecord(body.errors)) fieldErrors = body.errors as Record<string, string>
      if (isRecord(body.errorCodes)) fieldCodes = body.errorCodes as Record<string, string>
    }
  } catch {
    /* not JSON (e.g. a proxy error page) */
  }
  return new ApiError(res.status, message, { code, params, fieldErrors, fieldCodes })
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  let res: Response
  try {
    res = await send(path, init)
  } catch {
    throw new ApiError(0, 'Network error')
  }

  if (res.status === 401 && !NO_REFRESH.some((p) => path.startsWith(p))) {
    if (await refreshSession()) {
      res = await send(path, init)
    } else {
      onSessionExpired()
    }
  }
  if (res.status === 403 && UNSAFE.has((init.method ?? 'GET').toUpperCase()) && !readCookie(CSRF_COOKIE)) {
    // CSRF cookie was missing or rotated: fetch a fresh one and retry once.
    await ensureCsrf()
    res = await send(path, init)
  }
  if (!res.ok) throw await toError(res)
  if (res.status === 204) return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const json = (body: unknown) => JSON.stringify(body)
