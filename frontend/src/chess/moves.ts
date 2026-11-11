// Chess logic for recording moves: parse what people type or paste, check every move is legal,
// and write it back in standard notation. Uses chess.js, so import this only from lazily loaded code.
import { Chess } from 'chess.js'
import type { GameResult } from '../api/types'

export type ParseResult = { ok: true; sans: string[] } | { ok: false; sans: string[]; ply: number; token: string }

export type FinalState =
  | { kind: 'checkmate'; result: Exclude<GameResult, 'DRAW'> }
  | { kind: 'stalemate' | 'draw'; result: 'DRAW' }
  | { kind: 'ongoing'; turn: 'w' | 'b' }

const RESULT_TOKENS = new Set(['1-0', '0-1', '1/2-1/2', '½-½', '*'])

/** The move tokens of a PGN or a plain list: no headers, comments, variations, numbers, NAGs or result. */
function tokens(text: string): string[] {
  let body = text
    .replace(/^\s*\[[^\]]*\]\s*$/gm, ' ') // [Tag "value"] headers
    .replace(/\{[^}]*\}/g, ' ') // {comments}
    .replace(/;[^\n]*/g, ' ') // ; comments
  // (variations), possibly nested
  let previous
  do {
    previous = body
    body = body.replace(/\([^()]*\)/g, ' ')
  } while (body !== previous)
  return body
    .split(/\s+/)
    .flatMap((t) => t.split(/(?<=\d\.)(?=[^.\d])/)) // "1.e4" -> "1." "e4"
    .map((t) => t.replace(/^\d+\.+/, '')) // move numbers "12." / "12..."
    .map((t) => t.replace(/[!?]+$/, '')) // annotations
    .filter((t) => t !== '' && !/^\$\d+$/.test(t) && !RESULT_TOKENS.has(t))
}

/** Reads moves typed or pasted in almost any common form, checking each is legal. */
export function parseMoves(text: string): ParseResult {
  const chess = new Chess()
  const sans: string[] = []
  const list = tokens(text)
  for (let i = 0; i < list.length; i++) {
    const token = list[i].replace(/0-0-0/g, 'O-O-O').replace(/0-0/g, 'O-O')
    let move
    try {
      move = chess.move(token, { strict: false })
    } catch {
      move = null
    }
    if (!move) return { ok: false, sans, ply: i, token: list[i] }
    sans.push(move.san)
  }
  return { ok: true, sans }
}

/** "1. e4 e5 2. Nf3" */
export function formatMoves(sans: string[]): string {
  const parts: string[] = []
  sans.forEach((san, i) => {
    if (i % 2 === 0) parts.push(`${i / 2 + 1}.`)
    parts.push(san)
  })
  return parts.join(' ')
}

/** The position (FEN) after the first {@code ply} half-moves. */
export function positionAfter(sans: string[], ply: number): string {
  const chess = new Chess()
  for (const san of sans.slice(0, ply)) chess.move(san)
  return chess.fen()
}

/** How the moves end: checkmate (and who won), a drawn position, or still going (and whose turn). */
export function finalState(sans: string[]): FinalState {
  const chess = new Chess()
  for (const san of sans) chess.move(san)
  if (chess.isCheckmate()) return { kind: 'checkmate', result: chess.turn() === 'w' ? 'BLACK_WIN' : 'WHITE_WIN' }
  if (chess.isStalemate()) return { kind: 'stalemate', result: 'DRAW' }
  if (chess.isDraw()) return { kind: 'draw', result: 'DRAW' }
  return { kind: 'ongoing', turn: chess.turn() }
}

export { splitMovetext } from './movetext'

export interface SideStats {
  captures: number
  checks: number
  castled: 'short' | 'long' | null
  promotions: number
}

export interface MoveStats {
  plies: number
  /** Full moves, as numbered in the score sheet. */
  moves: number
  white: SideStats
  black: SideStats
  end: FinalState
}

/** Simple numbers about a game's moves (who captured, checked, castled, promoted). */
export function moveStats(sans: string[]): MoveStats {
  const side = (): SideStats => ({ captures: 0, checks: 0, castled: null, promotions: 0 })
  const white = side()
  const black = side()
  sans.forEach((san, i) => {
    const s = i % 2 === 0 ? white : black
    if (san.includes('x')) s.captures++
    if (/[+#]$/.test(san)) s.checks++
    if (san.includes('=')) s.promotions++
    if (san.startsWith('O-O-O')) s.castled = 'long'
    else if (san.startsWith('O-O')) s.castled = 'short'
  })
  return { plies: sans.length, moves: Math.ceil(sans.length / 2), white, black, end: finalState(sans) }
}

/** The from/to squares of every half-move, for highlighting the last move on the board. */
export function moveSquares(sans: string[]): { from: string; to: string }[] {
  const chess = new Chess()
  return sans.map((san) => {
    const m = chess.move(san)
    return { from: m.from, to: m.to }
  })
}
