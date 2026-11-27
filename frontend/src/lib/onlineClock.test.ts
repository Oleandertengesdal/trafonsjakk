import { describe, expect, it } from 'vitest'
import type { OnlineClock } from '../api/types'
import { DAILY_PRESETS, LIVE_PRESETS, formatClock, isLow, presetLabel, remainingMs } from './onlineClock'

const clock = (over: Partial<OnlineClock> = {}): OnlineClock => ({
  whiteMs: 300_000,
  blackMs: 280_000,
  running: true,
  toMove: 'WHITE',
  serverTime: '2026-12-24T18:00:00Z',
  firstMoveBy: null,
  deadline: null,
  ...over,
})

describe('formatClock', () => {
  it.each([
    [600_000, '10:00'],
    [65_000, '1:05'],
    [20_000, '0:20'],
    [19_999, '0:19.9'],
    [9_450, '0:09.4'],
    [0, '0:00.0'],
    [-500, '0:00.0'],
    [3_723_000, '1:02:03'],
  ])('%i ms → %s', (ms, text) => {
    expect(formatClock(ms)).toBe(text)
  })
})

describe('remainingMs', () => {
  it('counts down the side to move from when the answer arrived', () => {
    const c = clock()
    expect(remainingMs(c, 'WHITE', 1_000, 4_500)).toBe(296_500)
    // The other side's clock stands still
    expect(remainingMs(c, 'BLACK', 1_000, 4_500)).toBe(280_000)
  })

  it('stands still before both first moves, when the game is over, and never goes below zero', () => {
    expect(remainingMs(clock({ firstMoveBy: '2026-12-24T18:01:00Z' }), 'WHITE', 0, 60_000)).toBe(300_000)
    expect(remainingMs(clock({ running: false }), 'WHITE', 0, 60_000)).toBe(300_000)
    expect(remainingMs(clock(), 'WHITE', 0, 10_000_000)).toBe(0)
  })

  it('counts a daily game down to its deadline', () => {
    const twoDays = 2 * 24 * 3600_000
    const c = clock({ whiteMs: twoDays, blackMs: twoDays, deadline: '2026-12-26T18:00:00Z', firstMoveBy: '2026-12-26T18:00:00Z' })
    expect(remainingMs(c, 'WHITE', 0, 1_000)).toBe(twoDays - 1_000)
    expect(remainingMs(c, 'BLACK', 0, 1_000)).toBe(twoDays)
  })
})

describe('isLow', () => {
  it('warns under ten percent or twenty seconds, whichever is less', () => {
    expect(isLow(19_000, 600)).toBe(true)
    expect(isLow(21_000, 600)).toBe(false)
    expect(isLow(17_000, 180)).toBe(true)
    expect(isLow(19_000, 180)).toBe(false)
  })
})

describe('presets', () => {
  it('offers the usual time controls, with labels like the server', () => {
    expect(LIVE_PRESETS.map(presetLabel)).toEqual(['3+2', '5+3', '10+5', '15+10', '30+0'])
    expect(DAILY_PRESETS).toEqual([1, 2, 3, 7])
  })
})
