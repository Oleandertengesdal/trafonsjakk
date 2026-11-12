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
    accuracy: mine.length ? mine.reduce((s, m) => s + m.accuracy, 0) / mine.length : 100,
    blunders: count('blunder'),
    mistakes: count('mistake'),
    inaccuracies: count('inaccuracy'),
  }
}

function uciOf(fen: string, san: string): string {
  const chess = new Chess(fen)
  const m = chess.move(san)
  return m.from + m.to + (m.promotion ?? '')
}

/** "??", "?" or "?!" */
export const glyph: Record<Judgement, string> = { blunder: '??', mistake: '?', inaccuracy: '?!' }
