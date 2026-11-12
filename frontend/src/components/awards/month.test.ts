import { describe, expect, it } from 'vitest'
import { nextMonth, previousMonth } from './month'

describe('month stepping', () => {
  it('crosses year boundaries', () => {
    expect(previousMonth('2026-01')).toBe('2025-12')
    expect(previousMonth('2026-11')).toBe('2026-10')
    expect(nextMonth('2025-12')).toBe('2026-01')
    expect(nextMonth('2026-09')).toBe('2026-10')
  })

  it('stops at the first month with standings', () => {
    expect(previousMonth('2000-01')).toBeNull()
    expect(previousMonth('2000-02')).toBe('2000-01')
  })
})
