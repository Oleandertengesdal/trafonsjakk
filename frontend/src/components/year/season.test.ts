import { describe, expect, it } from 'vitest'
import { reviewYear } from './season'

describe('reviewYear', () => {
  it('features the ending year in December and the ended year in January', () => {
    expect(reviewYear(new Date(2026, 11, 1))).toEqual({ year: 2026, featured: true })
    expect(reviewYear(new Date(2027, 0, 31))).toEqual({ year: 2026, featured: true })
  })

  it('is the current year so far, without fanfare, the rest of the year', () => {
    expect(reviewYear(new Date(2026, 1, 1))).toEqual({ year: 2026, featured: false })
    expect(reviewYear(new Date(2026, 10, 30))).toEqual({ year: 2026, featured: false })
  })
})
