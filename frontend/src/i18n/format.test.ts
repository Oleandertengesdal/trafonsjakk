import { describe, expect, it } from 'vitest'
import { en } from './en'
import { makeFormatters, notation, points, signed, toLocalInput } from './format'
import { nb } from './nb'
import { roundName } from './rounds'

describe('signed', () => {
  it('uses a real minus sign and ± for zero', () => {
    expect(signed(12)).toBe('+12')
    expect(signed(-8)).toBe('−8')
    expect(signed(0)).toBe('±0')
  })
})

describe('points', () => {
  it('writes halves as ½', () => {
    expect(points(0)).toBe('0')
    expect(points(0.5)).toBe('½')
    expect(points(2.5)).toBe('2½')
    expect(points(3)).toBe('3')
  })
})

describe('notation', () => {
  it('is the same in every language', () => {
    expect(notation).toEqual({ WHITE_WIN: '1–0', BLACK_WIN: '0–1', DRAW: '½–½' })
  })
})

describe('makeFormatters', () => {
  it('follows Norwegian and English conventions', () => {
    const no = makeFormatters('nb')
    const gb = makeFormatters('en')
    expect(no.locale).toBe('nb-NO')
    expect(no.pct(0.6)).toMatch(/^60\s%$/) // nb puts a (non-breaking) space before %
    expect(gb.pct(0.6)).toBe('60%')
    expect(no.month('2026-03')).toBe('mars')
    expect(gb.month('2026-03')).toBe('March')
    expect(gb.decimal(1.5)).toBe('1.5')
    expect(no.decimal(1.5)).toBe('1,5')
  })

  it('describes recent times relatively and older ones by date', () => {
    const now = Date.parse('2026-06-15T12:00:00Z')
    const gb = makeFormatters('en')
    expect(gb.relative('2026-06-15T11:30:00Z', now)).toBe('30 minutes ago')
    expect(gb.relative('2026-06-14T12:00:00Z', now)).toBe('yesterday')
    expect(gb.relative('2026-01-01T12:00:00Z', now)).toBe(gb.date('2026-01-01T12:00:00Z'))
  })
})

describe('toLocalInput', () => {
  it('formats a date for datetime-local inputs in local time', () => {
    const d = new Date(2026, 4, 17, 9, 5)
    expect(toLocalInput(d)).toBe('2026-05-17T09:05')
  })
})

describe('roundName', () => {
  it('names the last knockout rounds', () => {
    expect(roundName(en, 'SINGLE_ELIMINATION', 4, 4)).toBe(en.round.final)
    expect(roundName(en, 'SINGLE_ELIMINATION', 3, 4)).toBe(en.round.semifinals)
    expect(roundName(nb, 'SINGLE_ELIMINATION', 2, 4)).toBe(nb.round.quarterfinals)
    expect(roundName(en, 'SINGLE_ELIMINATION', 1, 4)).toBe(en.round.numbered(1))
  })

  it('numbers round-robin rounds', () => {
    expect(roundName(nb, 'ROUND_ROBIN', 7, 7)).toBe(nb.round.numbered(7))
  })
})
