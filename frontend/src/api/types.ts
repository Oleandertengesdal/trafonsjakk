// DTOs mirroring the Spring Boot API (see docs/API.md).

export type Role = 'USER' | 'ADMIN'
export type GameResult = 'WHITE_WIN' | 'BLACK_WIN' | 'DRAW'
export type GameStatus = 'PENDING' | 'CONFIRMED' | 'DISPUTED' | 'VOIDED'
export type TournamentFormat = 'ROUND_ROBIN' | 'SINGLE_ELIMINATION'
export type TournamentStatus = 'REGISTRATION' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
export type MatchStatus = 'WAITING' | 'READY' | 'COMPLETED' | 'BYE'

export interface PlayerRef {
  id: number
  username: string
  realName: string
  rating: number
  rd: number
  provisional: boolean
}

export interface Me extends PlayerRef {
  role: Role
  ratedGames: number
}

export interface Profile extends PlayerRef {
  role: Role
  rank: number | null
  memberSince: string
  ratedGames: number
  peakRating: number
}

export interface RatingDelta {
  before: number
  after: number
}

export interface Game {
  id: number
  white: PlayerRef
  black: PlayerRef
  result: GameResult
  status: GameStatus
  playedAt: string
  createdAt: string
  reportedBy: PlayerRef
  confirmedBy: PlayerRef | null
  confirmedAt: string | null
  autoConfirmAt: string | null
  opening: string | null
  eco: string | null
  notes: string | null
  moves: string | null
  disputeReason: string | null
  tournament: { id: number; name: string; matchId: number; round: number } | null
  ratingChange: { white: RatingDelta; black: RatingDelta } | null
  canConfirm: boolean
  canDispute: boolean
  canWithdraw: boolean
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface LeaderboardRow {
  rank: number | null
  player: PlayerRef
  games: number
  wins: number
  draws: number
  losses: number
  lastPlayedAt: string | null
  trend: number
}

export interface Tally {
  games: number
  wins: number
  draws: number
  losses: number
  scorePct: number
}

export interface Stats {
  player: Profile
  totals: Tally & { winRate: number }
  white: Tally
  black: Tally
  favoriteColor: 'WHITE' | 'BLACK' | null
  streaks: {
    longestWin: number
    longestLoss: number
    longestUnbeaten: number
    current: { type: 'WIN' | 'DRAW' | 'LOSS' | null; length: number }
  }
  ratingHistory: { date: string; rating: number; rd: number; gameId: number | null; opponent: string | null; result: 'W' | 'D' | 'L' | null }[]
  peakRating: number
  lowestRating: number
  headToHead: (Tally & { opponent: PlayerRef; lastPlayedAt: string })[]
  nemesis: { opponent: PlayerRef; losses: number } | null
  favoriteVictim: { opponent: PlayerRef; wins: number } | null
  bestWin: { gameId: number; opponent: PlayerRef; opponentRating: number; playedAt: string } | null
  favoriteOpening: { name: string; games: number; scorePct: number } | null
  recentForm: ('W' | 'D' | 'L')[]
  activity: { month: string; games: number }[]
  tournamentWins: number
}

export interface TournamentSummary {
  id: number
  name: string
  format: TournamentFormat
  status: TournamentStatus
  doubleRoundRobin: boolean
  participantCount: number
  createdBy: PlayerRef
  winner: PlayerRef | null
  createdAt: string
  startedAt: string | null
  completedAt: string | null
}

export interface MatchGame {
  id: number
  whiteId: number
  blackId: number
  result: GameResult
  status: GameStatus
  playedAt: string
}

export interface Match {
  id: number
  round: number
  board: number
  white: PlayerRef | null
  black: PlayerRef | null
  status: MatchStatus
  result: GameResult | null
  winner: PlayerRef | null
  games: MatchGame[]
  openGameId: number | null
  needsReplay: boolean
}

export interface Standing {
  rank: number
  player: PlayerRef
  played: number
  wins: number
  draws: number
  losses: number
  points: number
  sonnebornBerger: number
}

export interface TournamentDetail {
  summary: TournamentSummary
  participants: { player: PlayerRef; seed: number | null }[]
  rounds: { number: number; name: string; matches: Match[] }[]
  standings: Standing[] | null
  currentRound: number | null
  totalRounds: number
  completedMatches: number
  totalMatches: number
  canManage: boolean
  canJoin: boolean
  joined: boolean
}

export interface MyMatch {
  matchId: number
  tournamentId: number
  tournamentName: string
  format: TournamentFormat
  round: number
  totalRounds: number
  /** English name from the server; the UI computes a localised one with `roundName()` */
  roundName: string
  white: PlayerRef
  black: PlayerRef
  needsReplay: boolean
}

export interface Invite {
  id: number
  createdBy: PlayerRef | null
  createdAt: string
  expiresAt: string
  usedBy: PlayerRef | null
  usedAt: string | null
  status: 'ACTIVE' | 'USED' | 'EXPIRED' | 'REVOKED'
}

export interface AdminUser {
  id: number
  username: string
  realName: string
  role: Role
  enabled: boolean
  createdAt: string
  ratedGames: number
  rating: number
}
