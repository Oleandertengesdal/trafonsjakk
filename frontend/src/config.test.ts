// Deployment config is security-relevant: check it like code.
import { readFileSync } from 'node:fs'
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
    expect(csp['script-src']).toEqual(["'self'"]) // no inline scripts, no eval, no CDNs
    expect(csp['connect-src']).toEqual(["'self'"]) // the API is same-origin through the rewrite
    expect(csp['object-src']).toEqual(["'none'"])
    expect(csp['frame-ancestors']).toEqual(["'none'"])
    expect(csp['base-uri']).toEqual(["'self'"])
    expect(csp['form-action']).toEqual(["'self'"])
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
