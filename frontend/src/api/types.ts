// DTOs mirroring the Spring Boot API (see docs/API.md).

export type Role = 'USER' | 'ADMIN'
export type GameResult = 'WHITE_WIN' | 'BLACK_WIN' | 'DRAW'
export type GameStatus = 'PENDING' | 'CONFIRMED' | 'DISPUTED' | 'VOIDED'
/** Over the board (logged afterwards) or online (played on the site). Each has its own rating. */
export type GameMode = 'OTB' | 'ONLINE'
export type TournamentFormat = 'ROUND_ROBIN' | 'SINGLE_ELIMINATION' | 'SWISS'
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
  /** The separate online rating. */
  online: OnlineRating
}

export interface OnlineRating {
  rating: number
  rd: number
  provisional: boolean
  /** Rated online games played. */
  games: number
  peak: number
  rank: number | null
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
  canEditMoves: boolean
  commentCount: number
  kudosCount: number
  kudosGivenByMe: boolean
  canGiveKudos: boolean
  mode: GameMode
  /** Online games: "10+5" or "3d". */
  timeControl: string | null
}

export interface GameComment {
  id: number
  /** The thread's first comment, for replies. */
  parentId: number | null
  /** Null for a deleted comment kept because it has replies. */
  author: PlayerRef | null
  replyTo: PlayerRef | null
  body: string
  createdAt: string
  editedAt: string | null
  deleted: boolean
  likeCount: number
  likedByMe: boolean
  likedBy: PlayerRef[]
  canEdit: boolean
  canDelete: boolean
  canLike: boolean
  replies: GameComment[]
}

export interface CommentLikes {
  likeCount: number
  likedByMe: boolean
  likedBy: PlayerRef[]
}

export interface GameContext {
  expected: { white: number; black: number }
  upset: boolean
  meeting: number | null
  headToHead: { games: number; whiteWins: number; draws: number; blackWins: number; firstPlayedAt: string | null }
  recent: { id: number; playedAt: string; whiteId: number; result: GameResult }[]
}

export interface Kudos {
  count: number
  givenByMe: boolean
  givers: PlayerRef[]
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface YearGameRef {
  id: number
  white: PlayerRef
  black: PlayerRef
  result: GameResult
  playedAt: string
}

/** A win against the odds; `expected` is the winner's expected score (0–1) before the game. */
export interface YearUpset {
  gameId: number
  playedAt: string
  winner: PlayerRef
  loser: PlayerRef
  expected: number
  winnerRating: number
  loserRating: number
}

export interface YearStreak {
  player: PlayerRef
  length: number
  from: string
  to: string
}

export interface YearBuzz {
  game: YearGameRef
  kudos: number
  comments: number
}

/** "Årets sjakk" for one player. With `games: 0` everything else is empty. */
export interface PlayerYear {
  year: number
  /** The year is over. */
  complete: boolean
  player: PlayerRef
  games: number
  wins: number
  draws: number
  losses: number
  points: number
  ratingStart: number | null
  ratingEnd: number | null
  ratingPeak: number | null
  ratingByMonth: { month: string; rating: number }[]
  bestMonth: { month: string; games: number; wins: number; draws: number; losses: number; overExpected: number; ratingChange: number } | null
  biggestUpset: YearUpset | null
  rival: { opponent: PlayerRef; games: number; wins: number; draws: number; losses: number } | null
  longestWinStreak: YearStreak | null
  favoriteOpening: { name: string; games: number; scorePct: number } | null
  /** 1 = Monday … 7 = Sunday */
  busiestWeekday: { day: number; games: number } | null
  monthsActive: number
  mostKudos: YearBuzz | null
  gamesWithMoves: number
  rankByGames: number | null
  players: number
  awards: { month: string; kind: AwardKind }[]
}

export interface GroupYear {
  year: number
  complete: boolean
  games: number
  players: number
  whiteWins: number
  draws: number
  blackWins: number
  gamesWithMoves: number
  kudos: number
  comments: number
  busiestMonth: { month: string; games: number } | null
  busiestWeekday: { day: number; games: number } | null
  gameOfTheYear: YearBuzz | null
  giantKiller: YearUpset | null
  awardLeaders: { player: PlayerRef; playerOfMonth: number; mostActive: number }[]
  mostGames: { player: PlayerRef; games: number }[]
  climbers: { player: PlayerRef; change: number; start: number; end: number }[]
  longestWinStreak: YearStreak | null
  /** `a` has at least as many wins as `b`. */
  topRivalry: { a: PlayerRef; b: PlayerRef; games: number; aWins: number; draws: number; bWins: number } | null
}

export type AwardKind = 'PLAYER_OF_MONTH' | 'MOST_ACTIVE' | 'MOST_ACCURATE'

/** A decided monthly award. `overExpected` = points − expected (from Glicko-2 before each game). */
export interface Award {
  /** "2026-10" */
  month: string
  kind: AwardKind
  player: PlayerRef
  games: number
  points: number
  expected: number
  overExpected: number
  ratingChange: number
  /** MOST_ACCURATE only: average accuracy over the month's analysed games (`games` counts those). */
  accuracy: number | null
  decidedAt: string
}

export interface AwardStanding {
  player: PlayerRef
  games: number
  wins: number
  draws: number
  losses: number
  points: number
  expected: number
  overExpected: number
  ratingChange: number
  /** Played at least `minGames`. */
  qualified: boolean
}

export interface AwardCategory {
  /** Set once the month is decided and someone qualified. */
  winner: Award | null
  standings: AwardStanding[]
}

export interface AwardRace {
  month: string
  status: 'LIVE' | 'DECIDING' | 'DECIDED'
  endsAt: string
  decidesAt: string
  minGames: number
  playerOfMonth: AwardCategory
  mostActive: AwardCategory
  mostAccurate: AccuracyCategory
}

export interface AccuracyStanding {
  player: PlayerRef
  /** Analysed games with at least 10 of their own moves. */
  games: number
  accuracy: number
  qualified: boolean
}

export interface AccuracyCategory {
  winner: Award | null
  standings: AccuracyStanding[]
}

/** One position as the engine saw it; the same shape as the website's PositionEval. */
export interface SavedEval {
  score: { cp: number } | { mate: number; by: 'w' | 'b' }
  best: string | null
}

/** A game's engine analysis, saved by the first browser that reviewed it. Accuracy computed by the server. */
export interface SavedAnalysis {
  gameId: number
  depth: number
  evals: SavedEval[]
  white: { moves: number; accuracy: number }
  black: { moves: number; accuracy: number }
  analysedAt: string
}

export interface GameAccuracy {
  gameId: number
  accuracy: number
  colour: 'WHITE' | 'BLACK'
  result: GameResult
  opponent: PlayerRef
  playedAt: string
}

/** A player's accuracy over their analysed, confirmed games. `recent` is oldest first. */
export interface PlayerAccuracy {
  games: number
  average: number | null
  best: GameAccuracy | null
  recent: GameAccuracy[]
}

/**
 * "Played like" fitted to the group's ratings: rating ≈ intercept + slope × accuracy, kept between `low` and
 * `high` (the lowest and highest rating in the fit). All null until enough games.
 */
export interface Calibration {
  samples: number
  minSamples: number
  intercept: number | null
  slope: number | null
  low: number | null
  high: number | null
}

export interface RatingPoint {
  date: string
  rating: number
  rd: number
  gameId: number | null
  opponent: string | null
  result: 'W' | 'D' | 'L' | null
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
  ratingHistory: RatingPoint[]
  /** The online rating's own line. */
  onlineRatingHistory: RatingPoint[]
  /** Where the online line starts (the over-the-board rating it began from), or null. */
  onlineStartRating: number | null
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
  /** Swiss: the planned number of rounds (requested at creation, settled when it starts). */
  swissRounds: number | null
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
  /** Swiss: rounds sat out (each worth a point) and the Buchholz tie-break; 0 and null otherwise. */
  byes: number
  buchholz: number | null
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
  maxUses: number
  useCount: number
  uses: { player: PlayerRef; usedAt: string }[]
  status: 'ACTIVE' | 'USED' | 'EXPIRED' | 'REVOKED'
}

export interface CreatedInvite {
  id: number
  code: string
  expiresAt: string
  maxUses: number
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
  /** The person deleted their account; it can't be switched back on. */
  deleted: boolean
}

export type LiveStatus = 'LIVE' | 'FINISHED' | 'ABANDONED'

/** A game being played right now. `version` counts saves; `canRecord`: you're a player (or an admin). */
export interface LiveGame {
  id: number
  white: PlayerRef
  black: PlayerRef
  status: LiveStatus
  moves: string | null
  version: number
  startedAt: string
  updatedAt: string
  gameId: number | null
  canRecord: boolean
}

export interface RivalryRecord {
  games: number
  aWins: number
  draws: number
  bWins: number
  aPoints: number
}

/** Two players against each other, from A's side. Confirmed games only; `games` newest first. */
export interface Rivalry {
  a: PlayerRef
  b: PlayerRef
  overall: RivalryRecord
  aAsWhite: RivalryRecord
  aAsBlack: RivalryRecord
  currentStreak: { side: 'A' | 'B' | null; length: number }
  longestStreak: { side: 'A' | 'B' | null; length: number }
  /** The biggest win against a higher-rated opponent (rating gap before the game). */
  biggestUpset: { gameId: number; winner: 'A' | 'B'; gap: number; playedAt: string } | null
  /** Their best-played analysed games against each other. */
  bestGames: { gameId: number; playedAt: string; aAccuracy: number; bAccuracy: number; average: number }[]
  games: Game[]
}

// ---- online play ------------------------------------------------------------------------------------

export type Colour = 'WHITE' | 'BLACK'
export type ChallengeColour = Colour | 'RANDOM'
export type ClockKind = 'LIVE' | 'DAILY'
export type OnlineStatus = 'ACTIVE' | 'FINISHED' | 'ABORTED'
export type EndReason =
  | 'CHECKMATE'
  | 'STALEMATE'
  | 'REPETITION'
  | 'FIFTY_MOVES'
  | 'INSUFFICIENT_MATERIAL'
  | 'RESIGNATION'
  | 'AGREEMENT'
  | 'TIMEOUT'
  | 'TIMEOUT_VS_INSUFFICIENT'

export interface TimeControl {
  kind: ClockKind
  initialSeconds: number
  incrementSeconds: number
  daysPerMove: number
  /** "10+5" or "3d" */
  label: string
}

/** Players carry their online rating. {@code opponent} null: open to anyone. */
export interface Challenge {
  id: number
  createdBy: PlayerRef
  opponent: PlayerRef | null
  /** The colour the creator plays. */
  colour: ChallengeColour
  rated: boolean
  timeControl: TimeControl
  createdAt: string
  expiresAt: string
}

export interface ChallengeRequest {
  opponentId: number | null
  colour: ChallengeColour
  rated: boolean
  kind: ClockKind
  initialSeconds?: number
  incrementSeconds?: number
  daysPerMove?: number
}

/** As of {@code serverTime}; the page counts down from there. */
export interface OnlineClock {
  whiteMs: number
  blackMs: number
  running: boolean
  toMove: Colour
  serverTime: string
  /** Before both first moves: when the next one has to come, or the game is cancelled. */
  firstMoveBy: string | null
  /** Daily games: when the side to move has to have moved. */
  deadline: string | null
}

export interface OnlineGame {
  id: number
  white: PlayerRef
  black: PlayerRef
  rated: boolean
  timeControl: TimeControl
  status: OnlineStatus
  result: GameResult | null
  endReason: EndReason | null
  moves: string | null
  plies: number
  /** "e2e4" */
  lastMove: string | null
  fen: string
  turn: Colour
  clock: OnlineClock
  drawOfferBy: number | null
  /** Null for a spectator. */
  myColour: Colour | null
  canAbort: boolean
  /** The confirmed game it became (rated games with a result). */
  gameId: number | null
  ratingChange: { white: RatingDelta; black: RatingDelta } | null
  startedAt: string
  endedAt: string | null
  version: number
}

export interface Lobby {
  challengesToMe: Challenge[]
  myChallenges: Challenge[]
  openChallenges: Challenge[]
  myGames: OnlineGame[]
  /** My games where it's my move, clock games first. */
  myTurn: OnlineGame[]
  /** Every game being played. */
  playing: OnlineGame[]
  recent: OnlineGame[]
}
