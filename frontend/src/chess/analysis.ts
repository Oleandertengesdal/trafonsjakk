// Turns engine evaluations of every position in a game into a review: who was better when, which moves
// were mistakes and by how much, what was better, and an accuracy per player. Pure functions; the engine
// itself lives in engine.ts.
import { Chess } from 'chess.js'

/**
 * An evaluation from White's point of view. `cp` is centipawns (+ is good for White). A mate is `mate`
 * moves away (0 = the game is over) for the side in `by`.
 */
export type Score = { cp: number } | { mate: number; by: 'w' | 'b' }

/** The engine's view of one position: the score and the best move (UCI, e.g. "e2e4"), null when the game is over. */
export interface PositionEval {
  score: Score
  best: string | null
}

export type Judgement = 'blunder' | 'mistake' | 'inaccuracy'

export interface MoveReview {
  /** 1 for White's first move. */
  ply: number
  side: 'w' | 'b'
  san: string
  /** Win chance (0–100) for the side that moved, before and after the move. */
  winBefore: number
  winAfter: number
  judgement: Judgement | null
  /** The engine's choice in the position before the move, as SAN; null if the move played was it. */
  bestSan: string | null
  /** Pawns lost by the move (from the mover's side), when no mate is involved. */
  pawnsLost: number | null
  /** The move let the opponent mate in this many moves. */
  allowedMate: number | null
  /** The mover had a mate in this many moves and the move lost it. */
  missedMate: number | null
  /** 0–100, from how much winning chance the move gave away. */
  accuracy: number
}

export interface SideSummary {
  /** Moves this side played. */
  moves: number
  accuracy: number
  blunders: number
  mistakes: number
  inaccuracies: number
}

export interface GameReview {
  moves: MoveReview[]
  white: SideSummary
  black: SideSummary
}

/** Lichess's thresholds: a move that drops your winning chance by this many percentage points. */
const DROP = { inaccuracy: 10, mistake: 20, blunder: 30 } as const

/** White's chance of winning, 0–100, from a score (the same curve lichess uses). */
export function winPercent(score: Score): number {
  if ('mate' in score) return score.by === 'w' ? 100 : 0
  const cp = Math.max(-1000, Math.min(1000, score.cp))
  return 50 + 50 * (2 / (1 + Math.exp(-0.00368208 * cp)) - 1)
}

/** Accuracy of one move from the drop in winning chance (lichess's formula), 0–100. */
export function moveAccuracy(winBefore: number, winAfter: number): number {
  const drop = Math.max(0, winBefore - winAfter)
  return Math.max(0, Math.min(100, 103.1668 * Math.exp(-0.04354 * drop) - 3.1669 + 1))
}

/**
 * Parses a UCI "info … score cp 31 …" or "score mate -3" line, given whose turn it is, into a Score from
 * White's side. Returns null for lines without a score.
 */
export function scoreFromInfo(line: string, toMove: 'w' | 'b'): Score | null {
  const m = /\bscore (cp|mate) (-?\d+)/.exec(line)
  if (!m) return null
  const n = Number(m[2])
  if (m[1] === 'cp') return { cp: toMove === 'w' ? n : -n }
  const mover = n > 0 ? toMove : toMove === 'w' ? 'b' : 'w'
  return { mate: Math.abs(n), by: mover }
}

/** "+1.3", "−0.4", "0.0", "#3" (White mates), "−#2" (Black mates), "1–0" / "0–1" for a finished mate. */
export function scoreText(score: Score, decimal: (n: number) => string): string {
  if ('mate' in score) {
    if (score.mate === 0) return score.by === 'w' ? '1–0' : '0–1'
    return `${score.by === 'w' ? '' : '−'}#${score.mate}`
  }
  const pawns = Math.round(score.cp / 10) / 10
  return pawns > 0 ? `+${decimal(pawns)}` : pawns < 0 ? `−${decimal(-pawns)}` : decimal(0)
}

/** Every position of a game (FEN), from the start to after the last move, and whose turn it is in each. */
export function positions(sans: string[]): { fen: string; toMove: 'w' | 'b'; over: 'checkmate' | 'draw' | null }[] {
  const chess = new Chess()
  const out = [describe(chess)]
  for (const san of sans) {
    chess.move(san)
    out.push(describe(chess))
  }
  return out
}

function describe(chess: Chess) {
  const over = chess.isCheckmate() ? ('checkmate' as const) : chess.isDraw() || chess.isStalemate() ? ('draw' as const) : null
  return { fen: chess.fen(), toMove: chess.turn(), over }
}

/** The position after playing `sans` from `fen` (which must all be legal), like an entry of positions(). */
export function playFrom(fen: string, sans: string[]): { fen: string; toMove: 'w' | 'b'; over: 'checkmate' | 'draw' | null } {
  const chess = new Chess(fen)
  for (const san of sans) chess.move(san)
  return describe(chess)
}

/** The score of a finished position, without asking the engine. */
export function terminalScore(p: { toMove: 'w' | 'b'; over: 'checkmate' | 'draw' | null }): Score | null {
  if (p.over === 'checkmate') return { mate: 0, by: p.toMove === 'w' ? 'b' : 'w' }
  if (p.over === 'draw') return { cp: 0 }
  return null
}

/** "e7e8q" → SAN in the position `fen`, or null if it isn't a legal move there. */
export function uciToSan(fen: string, uci: string): string | null {
  try {
    const chess = new Chess(fen)
    const move = chess.move({ from: uci.slice(0, 2), to: uci.slice(2, 4), promotion: uci.length > 4 ? uci[4] : undefined })
    return move.san
  } catch {
    return null
  }
}

/** One of the engine's best continuations while it searches a position (live analysis). */
export interface EngineLine {
  /** 1 for the engine's first choice, 2 for the second, … */
  rank: number
  depth: number
  score: Score
  /** The moves, as UCI ("e2e4"). */
  pv: string[]
}

/**
 * Parses a UCI "info depth 18 … multipv 2 score cp 31 … pv e2e4 e7e5" line into an EngineLine, given
 * whose turn it is. Returns null for anything else, including bounds (not a real score yet).
 */
export function parseInfoLine(line: string, toMove: 'w' | 'b'): EngineLine | null {
  if (!line.startsWith('info ') || / (upper|lower)bound\b/.test(line)) return null
  const pvAt = line.indexOf(' pv ')
  const depth = /\bdepth (\d+)/.exec(line)
  const score = scoreFromInfo(line, toMove)
  if (pvAt < 0 || !depth || !score) return null
  const rank = /\bmultipv (\d+)/.exec(line)
  const pv = line.slice(pvAt + 4).trim().split(/\s+/).filter((m) => /^[a-h][1-8][a-h][1-8][qrbn]?$/.test(m))
  return pv.length ? { rank: rank ? Number(rank[1]) : 1, depth: Number(depth[1]), score, pv } : null
}

/** A line of UCI moves from `fen` as SAN, stopping at `max` moves or at the first move that isn't legal. */
export function pvToSan(fen: string, pv: string[], max = 10): string[] {
  const out: string[] = []
  try {
    const chess = new Chess(fen)
    for (const uci of pv.slice(0, max)) {
      const move = chess.move({ from: uci.slice(0, 2), to: uci.slice(2, 4), promotion: uci.length > 4 ? uci[4] : undefined })
      out.push(move.san)
    }
  } catch {
    // An illegal move (a stale line for another position): keep what was legal
  }
  return out
}

/**
 * Moves played after `afterPly` half-moves, numbered the usual way: "14... Nf6 15. Bg5 h6". `first` is the
 * first move with its number, `rest` the others.
 */
export function numberedLine(sans: string[], afterPly: number): { first: string; rest: string } {
  const parts = sans.map((san, i) => {
    const ply = afterPly + i + 1
    const n = Math.ceil(ply / 2)
    if (ply % 2 === 1) return `${n}. ${san}`
    return i === 0 ? `${n}... ${san}` : san
  })
  return { first: parts[0] ?? '', rest: parts.slice(1).join(' ') }
}

/** Reviews a game from evaluations of all its positions (`evals[i]` is the position after `i` moves). */
export function reviewGame(sans: string[], evals: PositionEval[]): GameReview {
  const pos = positions(sans)
  const moves: MoveReview[] = []
  for (let ply = 1; ply <= sans.length; ply++) {
    const side = pos[ply - 1].toMove
    const before = evals[ply - 1].score
    const after = evals[ply].score
    const forSide = (w: number) => (side === 'w' ? w : 100 - w)
    const winBefore = forSide(winPercent(before))
    const winAfter = forSide(winPercent(after))
    const drop = winBefore - winAfter

    const bestUci = evals[ply - 1].best
    const playedUci = uciOf(pos[ply - 1].fen, sans[ply - 1])
    const isBest = bestUci !== null && bestUci === playedUci
    const bestSan = !isBest && bestUci ? uciToSan(pos[ply - 1].fen, bestUci) : null

    const mateFor = (s: Score, who: 'w' | 'b') => ('mate' in s && s.by === who ? s.mate : null)
    const other = side === 'w' ? 'b' : 'w'
    const allowedMate = mateFor(before, other) === null && mateFor(after, other) !== null && mateFor(after, other)! > 0 ? mateFor(after, other) : null
    const hadMate = mateFor(before, side)
    const missedMate = hadMate !== null && hadMate > 0 && mateFor(after, side) === null ? hadMate : null

    let judgement: Judgement | null = null
    if (!isBest) {
      if (drop >= DROP.blunder) judgement = 'blunder'
      else if (drop >= DROP.mistake) judgement = 'mistake'
      else if (drop >= DROP.inaccuracy) judgement = 'inaccuracy'
      // Throwing away a forced mate is at least a mistake, unless the position is still easily won
      if (missedMate !== null && winAfter < 90 && judgement !== 'blunder') judgement = 'mistake'
      if (allowedMate !== null) judgement = 'blunder'
    }

    const pawnsLost =
      'cp' in before && 'cp' in after ? Math.max(0, ((side === 'w' ? 1 : -1) * (before.cp - after.cp)) / 100) : null

    moves.push({
      ply,
      side,
      san: sans[ply - 1],
      winBefore,
      winAfter,
      judgement,
      bestSan,
      pawnsLost,
      allowedMate,
      missedMate,
      accuracy: moveAccuracy(winBefore, winAfter),
    })
  }
  return { moves, white: summary(moves, 'w'), black: summary(moves, 'b') }
}

function summary(moves: MoveReview[], side: 'w' | 'b'): SideSummary {
  const mine = moves.filter((m) => m.side === side)
  const count = (j: Judgement) => mine.filter((m) => m.judgement === j).length
  return {
    moves: mine.length,
    accuracy: mine.length ? mine.reduce((s, m) => s + m.accuracy, 0) / mine.length : 100,
    blunders: count('blunder'),
    mistakes: count('mistake'),
    inaccuracies: count('inaccuracy'),
  }
}

/**
 * Accuracy → rating, roughly where players of that strength tend to land in one game. There's no agreed
 * formula: these points follow the published rules of thumb (casual players score in the 70s, club players
 * in the low to mid 80s, 90+ is very clean play), and studies find that one game's accuracy explains only a
 * small part of the difference between players' ratings.
 */
const STRENGTH: [accuracy: number, rating: number][] = [
  [50, 400],
  [60, 700],
  [70, 1000],
  [75, 1200],
  [80, 1450],
  [85, 1700],
  [90, 2000],
  [95, 2400],
  [98, 2700],
]

/** Fewer moves than this and one slip decides everything: no estimate. */
export const STRENGTH_MIN_MOVES = 10

/**
 * "Played like a 1450": a rough rating for one side's play in one game, from its accuracy, to the nearest
 * 50 and between 400 and 2800. Null for short games. One game can be several hundred points off.
 */
export function playedLike(side: Pick<SideSummary, 'accuracy' | 'moves'>): number | null {
  if (side.moves < STRENGTH_MIN_MOVES) return null
  const a = side.accuracy
  let rating: number
  if (a <= STRENGTH[0][0]) {
    rating = STRENGTH[0][1]
  } else {
    const i = STRENGTH.findIndex(([acc]) => acc >= a)
    if (i === -1) {
      const [[a1, r1], [a2, r2]] = STRENGTH.slice(-2)
      rating = r2 + ((a - a2) * (r2 - r1)) / (a2 - a1)
    } else {
      const [a1, r1] = STRENGTH[i - 1]
      const [a2, r2] = STRENGTH[i]
      rating = r1 + ((a - a1) * (r2 - r1)) / (a2 - a1)
    }
  }
  return Math.round(Math.max(400, Math.min(2800, rating)) / 50) * 50
}

function uciOf(fen: string, san: string): string {
  const chess = new Chess(fen)
  const m = chess.move(san)
  return m.from + m.to + (m.promotion ?? '')
}

/** "??", "?" or "?!" */
export const glyph: Record<Judgement, string> = { blunder: '??', mistake: '?', inaccuracy: '?!' }
