import { describe, expect, it } from 'vitest'
import { toResult } from './gameResult'

describe('toResult', () => {
  it.each([
    ['WIN', true, 'WHITE_WIN'],
    ['WIN', false, 'BLACK_WIN'],
    ['LOSS', true, 'BLACK_WIN'],
    ['LOSS', false, 'WHITE_WIN'],
    ['DRAW', true, 'DRAW'],
    ['DRAW', false, 'DRAW'],
  ] as const)('%s as %s white → %s', (outcome, iAmWhite, expected) => {
    expect(toResult(outcome, iAmWhite)).toBe(expected)
  })
})
