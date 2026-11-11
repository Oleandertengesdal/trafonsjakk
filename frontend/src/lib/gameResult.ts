import type { GameResult } from '../api/types'

/** The result from the logger's point of view ("I won"), as on the log-game form. */
export type Outcome = 'WIN' | 'DRAW' | 'LOSS'

/** Turns "I won/drew/lost" into the absolute result, given which colour I played. */
export function toResult(outcome: Outcome, iAmWhite: boolean): GameResult {
  if (outcome === 'DRAW') return 'DRAW'
  return (outcome === 'WIN') === iAmWhite ? 'WHITE_WIN' : 'BLACK_WIN'
}
