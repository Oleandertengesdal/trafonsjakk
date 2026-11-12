// Deployment config is security-relevant: check it like code.
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

const root = process.cwd()
const vercel = JSON.parse(readFileSync(join(root, 'vercel.json'), 'utf8')) as {
  rewrites: { source: string; destination: string }[]
  headers: { source: string; headers: { key: string; value: string }[] }[]
}
const allPages = vercel.headers.find((h) => h.source === '/(.*)')!.headers
const header = (key: string) => allPages.find((h) => h.key.toLowerCase() === key.toLowerCase())?.value ?? ''

describe('vercel.json', () => {
  it('proxies the API first, then falls back to the SPA for everything else', () => {
    expect(vercel.rewrites[0].source).toBe('/api/:path*')
    expect(vercel.rewrites[0].destination).toMatch(/^https:\/\/[^/]+\/api\/:path\*$/)
    const spa = vercel.rewrites[1]
    expect(spa.destination).toBe('/index.html')
    expect(new RegExp('^' + spa.source + '$').test('/api/games')).toBe(false)
    expect(new RegExp('^' + spa.source + '$').test('/games/12')).toBe(true)
  })

  it('has a strict Content-Security-Policy', () => {
    const csp = Object.fromEntries(
      header('Content-Security-Policy')
        .split(';')
        .map((d) => d.trim().split(/\s+/))
        .map(([k, ...v]) => [k, v]),
    )
    expect(csp['default-src']).toEqual(["'self'"])
    // No inline scripts, no eval, no CDNs. WebAssembly is allowed for the Stockfish engine, which is served
    // from our own origin and runs in a worker; 'wasm-unsafe-eval' doesn't allow JavaScript eval.
    expect(csp['script-src']).toEqual(["'self'", "'wasm-unsafe-eval'"])
    expect(csp['script-src']).not.toContain("'unsafe-eval'")
    expect(csp['worker-src']).toEqual(["'self'"])
    // The API is same-origin through the rewrite; Sentry receives error reports when they're switched on
    expect(csp['connect-src']).toEqual(["'self'", 'https://*.sentry.io'])
    expect(csp['object-src']).toEqual(["'none'"])
    expect(csp['frame-ancestors']).toEqual(["'none'"])
    expect(csp['base-uri']).toEqual(["'self'"])
    expect(csp['form-action']).toEqual(["'self'"])
  })

  it('never lets a browser keep an old service worker', () => {
    const rule = vercel.headers.find((h) => h.source === '/sw.js')
    expect(rule?.headers).toContainEqual({ key: 'Cache-Control', value: 'no-cache' })
  })

  it('is installable as an app', () => {
    const manifest = JSON.parse(readFileSync(join(root, 'public/manifest.webmanifest'), 'utf8')) as {
      display: string
      start_url: string
      icons: { sizes: string; purpose: string; src: string }[]
    }
    expect(manifest.display).toBe('standalone')
    expect(manifest.start_url).toBe('/')
    expect(manifest.icons.map((i) => `${i.sizes} ${i.purpose}`)).toEqual(expect.arrayContaining(['192x192 any', '512x512 any', '512x512 maskable']))
    for (const icon of manifest.icons) expect(existsSync(join(root, 'public', icon.src))).toBe(true)
    const html = readFileSync(join(root, 'index.html'), 'utf8')
    expect(html).toContain('rel="manifest"')
    expect(html).toContain('rel="apple-touch-icon"')
  })

  it('serves the engine files from our own origin with a long cache', () => {
    const rule = vercel.headers.find((h) => h.source === '/engine/(.*)')
    expect(rule?.headers).toContainEqual({ key: 'Cache-Control', value: 'public, max-age=31536000, immutable' })
  })

  it('sets the other security headers on every page', () => {
    expect(header('X-Frame-Options')).toBe('DENY')
    expect(header('X-Content-Type-Options')).toBe('nosniff')
    expect(header('Referrer-Policy')).toBe('strict-origin-when-cross-origin')
    expect(header('Strict-Transport-Security')).toMatch(/max-age=\d{8,}/)
    expect(header('Permissions-Policy')).toContain('camera=()')
  })
})

describe('index.html', () => {
  const html = readFileSync(join(root, 'index.html'), 'utf8')

  it('has no inline scripts (the CSP would block them) and no third-party resources', () => {
    for (const tag of html.match(/<script\b[^>]*>[\s\S]*?<\/script>/g) ?? []) {
      expect(tag).toMatch(/\ssrc="\/[^/]/)
      expect(tag.replace(/<script\b[^>]*>/, '').replace('</script>', '').trim()).toBe('')
    }
    expect(html).not.toMatch(/(src|href)="(https?:)?\/\//)
    expect(html).not.toMatch(/\son[a-z]+=/i)
  })

  it('starts in Norwegian', () => {
    expect(html).toMatch(/<html lang="nb">/)
  })
})
