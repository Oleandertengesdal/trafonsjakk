// The opening explorer's sums: a tree of the moves played in the group's games, with how each move scored,
// and the opening each game reached (named from lichess's opening list). Positions are merged when the
// same one is reached by different move orders (transpositions), as on lichess.
import { Chess } from 'chess.js'
import type { GameResult, PlayerRef } from '../api/types'
import { positionKey } from './openingKey'

export interface ExplorerGame {
  id: number
  white: PlayerRef
  black: PlayerRef
  result: GameResult
  playedAt: string
  /** The name someone typed when logging the game, if any. */
  opening: string | null
  /** The first moves, as SAN separated by spaces; null when the moves weren't recorded. */
  moves: string | null
}

/** Opening names by position key: [ECO code, name]. */
export type OpeningBook = Record<string, [string, string]>

export interface Tally {
  games: number
  whiteWins: number
  draws: number
  blackWins: number
  /** Points for `me` when the explorer is for one player; otherwise for White. */
  points: number
}

export interface MoveRow {
  san: string
  uci: string
  tally: Tally
}

export interface PositionNode {
  key: string
  tally: Tally
  /** The moves played from here, most played first. */
  moves: MoveRow[]
  /** Games that reached this position, newest first. */
  games: ExplorerGame[]
}

export interface Opening {
  eco: string
  name: string
}

export interface NamedOpening {
  /** The family ("Sicilian Defense"), without the variation. */
  name: string
  eco: string
  tally: Tally
  /** The line most of these games took to first reach the name, for the board. */
  line: string[]
}

export interface Explorer {
  nodes: Map<string, PositionNode>
  /** Families, most played first. Games without recorded moves count under the name that was typed. */
  openings: NamedOpening[]
  /** Games with moves that the explorer could read. */
  withMoves: number
  /** Games in the explorer altogether (moves or a typed name). */
  total: number
}

export const START_KEY = positionKey(new Chess().fen())

function emptyTally(): Tally {
  return { games: 0, whiteWins: 0, draws: 0, blackWins: 0, points: 0 }
}

function add(t: Tally, result: GameResult, me: number | null, game: ExplorerGame) {
  t.games++
  if (result === 'WHITE_WIN') t.whiteWins++
  else if (result === 'BLACK_WIN') t.blackWins++
  else t.draws++
  const meWhite = me === null || game.white.id === me
  t.points += result === 'DRAW' ? 0.5 : (result === 'WHITE_WIN') === meWhite ? 1 : 0
}

/** The score as a share of the points: 0–1. */
export function score(t: Tally): number {
  return t.games ? t.points / t.games : 0
}

/** The family of a lichess name: "Sicilian Defense: Najdorf Variation" → "Sicilian Defense". */
export function family(name: string): string {
  return name.split(':')[0].trim()
}

/**
 * Builds the explorer from games. With `me`, scores are that player's points; with `colour` too, only
 * their games with that colour count.
 */
export function buildExplorer(
  games: ExplorerGame[],
  book: OpeningBook,
  { me = null, colour = null }: { me?: number | null; colour?: 'white' | 'black' | null } = {},
): Explorer {
  const nodes = new Map<string, PositionNode>()
  const moveRows = new Map<string, Map<string, MoveRow>>()
  const families = new Map<string, { name: string; eco: string; tally: Tally; lines: Map<string, number> }>()
  let withMoves = 0
  let total = 0

  const node = (key: string) => {
    let n = nodes.get(key)
    if (!n) {
      n = { key, tally: emptyTally(), moves: [], games: [] }
      nodes.set(key, n)
    }
    return n
  }

  for (const g of games) {
    if (colour && me !== null && (colour === 'white' ? g.white.id : g.black.id) !== me) continue
    total++
    let named: { eco: string; name: string; line: string[] } | null = null
    if (g.moves) {
      const chess = new Chess()
      let key = START_KEY
      const seen = new Set<string>()
      const record = (k: string) => {
        if (seen.has(k)) return
        seen.add(k)
        const n = node(k)
        add(n.tally, g.result, me, g)
        n.games.push(g)
      }
      record(key)
      const sans = g.moves.split(' ')
      let read = 0
      for (const san of sans) {
        let move
        try {
          move = chess.move(san)
        } catch {
          break
        }
        const next = positionKey(chess.fen())
        if (!seen.has(next)) {
          let rows = moveRows.get(key)
          if (!rows) moveRows.set(key, (rows = new Map()))
          let row = rows.get(move.san)
          if (!row) rows.set(move.san, (row = { san: move.san, uci: move.from + move.to + (move.promotion ?? ''), tally: emptyTally() }))
          add(row.tally, g.result, me, g)
        }
        read++
        key = next
        record(key)
        const hit = book[key]
        if (hit) named = { eco: hit[0], name: hit[1], line: sans.slice(0, read) }
      }
      if (read > 0) withMoves++
    }
    // A recorded line names the game; otherwise the name typed when it was logged
    const name = named ? family(named.name) : g.opening?.trim()
    // The line where the family's name first appears (not the deepest variation), and its code
    const first = named ? firstLine(named.line, book, family(named.name)) : null
    if (named && first) named.eco = book[playLine(first)!.key]?.[0] ?? named.eco
    if (!name) continue
    let f = families.get(name.toLowerCase())
    if (!f) families.set(name.toLowerCase(), (f = { name, eco: named?.eco ?? '', tally: emptyTally(), lines: new Map() }))
    if (named && !f.eco) {
      // The book's spelling wins over a typed one
      f.eco = named.eco
      f.name = name
    }
    add(f.tally, g.result, me, g)
    if (first) f.lines.set(first.join(' '), (f.lines.get(first.join(' ')) ?? 0) + 1)
  }

  for (const [key, rows] of moveRows) {
    node(key).moves = [...rows.values()].sort((a, b) => b.tally.games - a.tally.games || a.san.localeCompare(b.san))
  }
  for (const n of nodes.values()) n.games.sort((a, b) => b.playedAt.localeCompare(a.playedAt) || b.id - a.id)

  const openings: NamedOpening[] = [...families.values()]
    .map((f) => ({
      name: f.name,
      eco: f.eco,
      tally: f.tally,
      line: [...f.lines.entries()].sort((a, b) => b[1] - a[1])[0]?.[0].split(' ') ?? [],
    }))
    .sort((a, b) => b.tally.games - a.tally.games || a.name.localeCompare(b.name))

  return { nodes, openings, withMoves, total }
}

/** The shortest start of `line` whose position is named `fam` in the book. */
function firstLine(line: string[], book: OpeningBook, fam: string): string[] {
  const chess = new Chess()
  for (let i = 0; i < line.length; i++) {
    chess.move(line[i])
    const hit = book[positionKey(chess.fen())]
    if (hit && family(hit[1]) === fam) return line.slice(0, i + 1)
  }
  return line
}

/** The deepest named position along a line (for the heading above the board). */
export function openingOf(line: string[], book: OpeningBook): Opening | null {
  const chess = new Chess()
  let found: Opening | null = book[START_KEY] ? { eco: book[START_KEY][0], name: book[START_KEY][1] } : null
  for (const san of line) {
    try {
      chess.move(san)
    } catch {
      break
    }
    const hit = book[positionKey(chess.fen())]
    if (hit) found = { eco: hit[0], name: hit[1] }
  }
  return found
}

/** The position after a line, and its key; null if the line isn't legal. */
export function playLine(line: string[]): { fen: string; key: string; lastUci: string | null } | null {
  const chess = new Chess()
  let last: string | null = null
  for (const san of line) {
    try {
      const m = chess.move(san)
      last = m.from + m.to
    } catch {
      return null
    }
  }
  return { fen: chess.fen(), key: positionKey(chess.fen()), lastUci: last }
}
