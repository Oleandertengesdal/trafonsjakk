import { describe, expect, it } from 'vitest'
import { scrubUrl, startErrorReporting } from './errorReporting'

describe('error reporting', () => {
  it('never sends what comes after ? or # (reset tokens, invite codes)', () => {
    expect(scrubUrl('https://x.app/reset-password?token=abc123')).toBe('https://x.app/reset-password')
    expect(scrubUrl('/register?code=K7QX4-MZP2R#top')).toBe('/register')
    expect(scrubUrl('/games/12')).toBe('/games/12')
  })

  it('stays off without a DSN', () => {
    expect(() => startErrorReporting(undefined)).not.toThrow()
    expect(() => startErrorReporting('')).not.toThrow()
  })
})
