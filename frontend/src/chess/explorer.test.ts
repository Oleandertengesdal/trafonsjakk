import { Chess } from 'chess.js'
import { describe, expect, it } from 'vitest'
import type { PlayerRef } from '../api/types'
import { buildExplorer, family, openingOf, playLine, score, START_KEY, type ExplorerGame, type OpeningBook } from './explorer'
import { positionKey } from './openingKey'

const kari: PlayerRef = { id: 1, username: 'kari', realName: 'Kari', rating: 1500, rd: 80, provisional: false }
const ola: PlayerRef = { id: 2, username: 'ola', realName: 'Ola', rating: 1500, rd: 80, provisional: false }

const keyAfter = (line: string) => {
  const c = new Chess()
  line.split(' ').forEach((m) => c.move(m))
  return positionKey(c.fen())
}

const BOOK: OpeningBook = {
  [keyAfter('e4')]: ['B00', "King's Pawn Game"],
  [keyAfter('e4 c5')]: ['B20', 'Sicilian Defense'],
  [keyAfter('e4 c5 Nf3 d6')]: ['B50', 'Sicilian Defense: Modern Variations'],
  [keyAfter('d4 d5')]: ['D00', "Queen's Pawn Game"],
}

let id = 0
const game = (moves: string | null, result: ExplorerGame['result'], white = kari, black = ola, opening: string | null = null): ExplorerGame => ({
  id: ++id,
  white,
  black,
  result,
  playedAt: `2026-11-${String(10 + id).padStart(2, '0')}T18:00:00Z`,
  opening,
  moves,
})

describe('the opening explorer', () => {
  const games = [
    game('e4 c5 Nf3 d6 d4', 'WHITE_WIN'),
    game('e4 c5 Nf3 Nc6', 'DRAW', ola, kari),
    game('e4 e5', 'BLACK_WIN'),
    game('d4 d5 c4', 'WHITE_WIN', ola, kari),
    game(null, 'DRAW', kari, ola, 'Sicilian Defense'),
    game('Nf3 c5 e4', 'BLACK_WIN'), // reaches the Sicilian by another move order
  ]

  it('counts the moves played from each position and how they scored', () => {
    const e = buildExplorer(games, BOOK)
    const start = e.nodes.get(START_KEY)!
    expect(start.tally.games).toBe(5)
    expect(start.moves.map((m) => [m.san, m.tally.games])).toEqual([
      ['e4', 3],
      ['d4', 1],
      ['Nf3', 1],
    ])
    const e4 = start.moves[0].tally
    expect([e4.whiteWins, e4.draws, e4.blackWins]).toEqual([1, 1, 1])
    expect(score(e4)).toBeCloseTo(0.5)
    expect(e.withMoves).toBe(5)
    expect(e.total).toBe(6)
  })

  it('merges transpositions', () => {
    const e = buildExplorer(games, BOOK)
    const sicilian = e.nodes.get(keyAfter('e4 c5 Nf3'))!
    expect(sicilian.tally.games).toBe(3) // two via 1. e4 c5 2. Nf3, one via 1. Nf3 c5 2. e4
    expect(sicilian.moves.map((m) => m.san).sort()).toEqual(['Nc6', 'd6'])
  })

  it('names the openings, by family, including games with only a typed name', () => {
    const e = buildExplorer(games, BOOK)
    const sicilian = e.openings.find((o) => o.name === 'Sicilian Defense')!
    // Two with moves, one typed. (1. Nf3 c5 2. e4 reaches a position the little book here has no name for)
    expect(sicilian.tally.games).toBe(3)
    expect(sicilian.eco).toBe('B20')
    expect(sicilian.line).toEqual(['e4', 'c5'])
    expect(e.openings[0].name).toBe('Sicilian Defense')
    expect(e.openings.map((o) => o.name)).toContain("King's Pawn Game")
  })

  it("scores from one player's side, and only their colour when asked", () => {
    const all = buildExplorer(games, BOOK, { me: kari.id })
    expect(all.nodes.get(START_KEY)!.tally.points).toBe(1 + 0.5 + 0 + 0 + 0) // won, drew, lost, lost (as black), lost
    const asWhite = buildExplorer(games, BOOK, { me: kari.id, colour: 'white' })
    expect(asWhite.total).toBe(4)
    expect(asWhite.nodes.get(START_KEY)!.moves.map((m) => m.san)).toEqual(['e4', 'Nf3'])
  })

  it('stops reading a game at an illegal move', () => {
    const e = buildExplorer([game('e4 e5 Ke3', 'DRAW')], BOOK)
    expect(e.nodes.get(keyAfter('e4 e5'))!.moves).toEqual([])
  })

  it('names lines and plays them', () => {
    expect(openingOf(['e4', 'c5', 'Nf3', 'd6', 'd4'], BOOK)).toEqual({ eco: 'B50', name: 'Sicilian Defense: Modern Variations' })
    expect(openingOf([], BOOK)).toBeNull()
    expect(family('Sicilian Defense: Najdorf Variation')).toBe('Sicilian Defense')
    expect(playLine(['e4', 'e5'])?.lastUci).toBe('e7e5')
    expect(playLine(['e4', 'Ke7'])).toBeNull()
  })
})
