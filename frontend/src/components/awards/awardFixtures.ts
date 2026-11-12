import type { Award, AwardRace, AwardStanding, PlayerRef } from '../../api/types'

export const standing = (player: PlayerRef, over: Partial<AwardStanding> = {}): AwardStanding => ({
  player,
  games: 4,
  wins: 2,
  draws: 1,
  losses: 1,
  points: 2.5,
  expected: 1.6,
  overExpected: 0.9,
  ratingChange: 24,
  qualified: true,
  ...over,
})

export const race = (over: Partial<AwardRace> = {}): AwardRace => ({
  month: '2026-11',
  status: 'LIVE',
  endsAt: '2026-11-30T23:00:00Z',
  decidesAt: '2026-12-03T23:00:00Z',
  minGames: 3,
  playerOfMonth: { winner: null, standings: [] },
  mostActive: { winner: null, standings: [] },
  ...over,
})

export const award = (player: PlayerRef, over: Partial<Award> = {}): Award => ({
  month: '2026-10',
  kind: 'PLAYER_OF_MONTH',
  player,
  games: 7,
  points: 5,
  expected: 2.7,
  overExpected: 2.3,
  ratingChange: 61,
  decidedAt: '2026-11-03T23:07:00Z',
  ...over,
})
