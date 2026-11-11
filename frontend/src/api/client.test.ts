import { beforeEach, describe, expect, it, vi } from 'vitest'

type Handler = (url: string, init: RequestInit) => Response | Promise<Response>
type Call = { url: string; method: string; headers: Headers; body: unknown }

let calls: Call[]

/** Fresh copy of the client module (it caches the CSRF and refresh promises). */
async function load(handler: Handler) {
  calls = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init: RequestInit = {}) => {
      calls.push({ url, method: init.method ?? 'GET', headers: new Headers(init.headers), body: init.body })
      return handler(url, init)
    }),
  )
  vi.resetModules()
  return import('./client')
}

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const empty = (status: number) => new Response(null, { status })
const setCsrf = (v = 'csrf-1') => (document.cookie = `XSRF-TOKEN=${encodeURIComponent(v)}; path=/`)
const callsTo = (path: string) => calls.filter((c) => c.url === path)

describe('api client', () => {
  beforeEach(() => setCsrf())

  it('sends same-origin requests without a CSRF header for reads', async () => {
    const { api } = await load(() => json(200, { ok: true }))
    await expect(api('/api/games')).resolves.toEqual({ ok: true })
    const [c] = calls
    expect(c.headers.get('Accept')).toBe('application/json')
    expect(c.headers.has('X-XSRF-TOKEN')).toBe(false)
    expect((vi.mocked(fetch).mock.calls[0][1] as RequestInit).credentials).toBe('same-origin')
  })

  it('echoes the decoded CSRF cookie in a header on writes', async () => {
    setCsrf('a b/c')
    const { api, json: body } = await load(() => json(201, { id: 1 }))
    await api('/api/games', { method: 'post', body: body({ x: 1 }) })
    const [c] = calls
    expect(c.method).toBe('POST')
    expect(c.headers.get('X-XSRF-TOKEN')).toBe('a b/c')
    expect(c.headers.get('Content-Type')).toBe('application/json')
    expect(c.body).toBe('{"x":1}')
  })

  it('fetches a CSRF cookie first when there is none, once for parallel writes', async () => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
    const { api } = await load((url) => {
      if (url === '/api/auth/csrf') {
        setCsrf('fresh')
        return empty(204)
      }
      return json(200, {})
    })
    await Promise.all([api('/api/invites', { method: 'POST' }), api('/api/invites', { method: 'POST' })])
    expect(callsTo('/api/auth/csrf')).toHaveLength(1)
    expect(calls[0].url).toBe('/api/auth/csrf')
    expect(callsTo('/api/invites').map((c) => c.headers.get('X-XSRF-TOKEN'))).toEqual(['fresh', 'fresh'])
  })

  it('recovers after fetching the CSRF cookie failed once', async () => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
    let csrfAttempts = 0
    const { api, ApiError } = await load((url) => {
      if (url === '/api/auth/csrf') {
        csrfAttempts++
        if (csrfAttempts === 1) throw new TypeError('Failed to fetch') // server waking up / offline
        setCsrf('second-try')
        return empty(204)
      }
      return json(201, { id: 7 })
    })
    const first = await api('/api/invites', { method: 'POST' }).catch((e: unknown) => e)
    expect(first).toBeInstanceOf(ApiError)
    expect((first as InstanceType<typeof ApiError>).status).toBe(0)

    // Back online: the next write must try again rather than reuse the failed attempt
    await expect(api('/api/invites', { method: 'POST' })).resolves.toEqual({ id: 7 })
    expect(csrfAttempts).toBe(2)
  })

  it('refreshes the session once on 401 and retries the request', async () => {
    let gamesCalls = 0
    const { api } = await load((url) => {
      if (url === '/api/auth/refresh') return empty(204)
      gamesCalls++
      return gamesCalls === 1 ? empty(401) : json(200, ['g'])
    })
    await expect(api('/api/games')).resolves.toEqual(['g'])
    expect(callsTo('/api/auth/refresh')).toHaveLength(1)
    expect(callsTo('/api/auth/refresh')[0].headers.get('X-XSRF-TOKEN')).toBe('csrf-1')
  })

  it('shares one refresh between parallel requests that hit 401', async () => {
    let refreshed = false
    const { api } = await load(async (url) => {
      if (url === '/api/auth/refresh') {
        await new Promise((r) => setTimeout(r, 5))
        refreshed = true
        return empty(204)
      }
      return refreshed ? json(200, url) : empty(401)
    })
    const results = await Promise.all([api('/api/a'), api('/api/b'), api('/api/c')])
    expect(results).toEqual(['/api/a', '/api/b', '/api/c'])
    expect(callsTo('/api/auth/refresh')).toHaveLength(1)
  })

  it('signs out when the session cannot be refreshed', async () => {
    const { api, ApiError, setSessionExpiredHandler } = await load(() => empty(401))
    const expired = vi.fn()
    setSessionExpiredHandler(expired)
    const err = await api('/api/games').catch((e: unknown) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect((err as InstanceType<typeof ApiError>).status).toBe(401)
    expect(expired).toHaveBeenCalledTimes(1)
  })

  it('does not try to refresh after a failed login', async () => {
    const { api } = await load(() => json(401, { code: 'auth.bad_credentials', detail: 'Wrong username or password' }))
    const expired = vi.fn()
    const err = await api('/api/auth/login', { method: 'POST', body: '{}' }).catch((e: unknown) => e)
    expect((err as { code: string }).code).toBe('auth.bad_credentials')
    expect(callsTo('/api/auth/refresh')).toHaveLength(0)
    expect(expired).not.toHaveBeenCalled()
  })

  it('parses problem details into code, params and field errors', async () => {
    const { api, ApiError } = await load(() =>
      json(400, {
        detail: 'Validation failed',
        code: 'validation',
        params: { max: 5 },
        errors: { username: 'size must be between 3 and 20' },
        errorCodes: { username: 'Size' },
      }),
    )
    const err = (await api('/api/auth/register', { method: 'POST', body: '{}' }).catch((e: unknown) => e)) as InstanceType<
      typeof ApiError
    >
    expect(err).toBeInstanceOf(ApiError)
    expect(err.status).toBe(400)
    expect(err.message).toBe('Validation failed')
    expect(err.code).toBe('validation')
    expect(err.params).toEqual({ max: 5 })
    expect(err.fieldErrors).toEqual({ username: 'size must be between 3 and 20' })
    expect(err.fieldCodes).toEqual({ username: 'Size' })
  })

  it('keeps the status when an error is not JSON (a proxy page)', async () => {
    const { api } = await load(() => new Response('<html>Bad gateway</html>', { status: 502 }))
    const err = (await api('/api/games').catch((e: unknown) => e)) as { status: number; code: string | null }
    expect(err.status).toBe(502)
    expect(err.code).toBeNull()
  })

  it('reports a network failure as status 0', async () => {
    const { api } = await load(() => {
      throw new TypeError('Failed to fetch')
    })
    await expect(api('/api/games')).rejects.toMatchObject({ status: 0 })
  })

  it('resolves 204 and empty bodies to undefined', async () => {
    const { api } = await load((url) => (url === '/api/a' ? empty(204) : new Response('', { status: 200 })))
    await expect(api('/api/a', { method: 'DELETE' })).resolves.toBeUndefined()
    await expect(api('/api/b')).resolves.toBeUndefined()
  })

  it('retries a write once with a fresh CSRF cookie when the cookie disappeared', async () => {
    let posts = 0
    const { api } = await load((url) => {
      if (url === '/api/auth/csrf') {
        setCsrf('rotated')
        return empty(204)
      }
      posts++
      if (posts === 1) {
        document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
        return empty(403)
      }
      return json(200, { ok: true })
    })
    await expect(api('/api/games/1/confirm', { method: 'POST' })).resolves.toEqual({ ok: true })
    expect(callsTo('/api/games/1/confirm').map((c) => c.headers.get('X-XSRF-TOKEN'))).toEqual(['csrf-1', 'rotated'])
  })
})
