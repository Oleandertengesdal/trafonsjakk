import type { Game, PlayerRef } from '../api/types'

export const player = (id: number, realName: string): PlayerRef => ({
  id,
  username: realName.toLowerCase().replace(/\s+/g, '_'),
  realName,
  rating: 1500,
  rd: 200,
  provisional: true,
})

export const kari = player(1, 'Kari Nordmann')
export const ola = player(2, 'Ola Hansen')

export function pendingGame(overrides: Partial<Game> = {}): Game {
  return {
    id: 42,
    white: kari,
    black: ola,
    result: 'WHITE_WIN',
    status: 'PENDING',
    playedAt: '2026-05-01T18:00:00Z',
    createdAt: '2026-05-01T18:30:00Z',
    reportedBy: kari,
    confirmedBy: null,
    confirmedAt: null,
    autoConfirmAt: '2026-05-03T18:30:00Z',
    opening: null,
    eco: null,
    notes: null,
    moves: null,
    disputeReason: null,
    tournament: null,
    ratingChange: null,
    canConfirm: false,
    canDispute: false,
    canWithdraw: false,
    ...overrides,
  }
}
