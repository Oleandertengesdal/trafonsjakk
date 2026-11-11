import { describe, expect, it } from 'vitest'
import { finalState, formatMoves, moveSquares, moveStats, parseMoves, positionAfter, splitMovetext } from './moves'

describe('parseMoves', () => {
  it('reads plain moves', () => {
    expect(parseMoves('e4 e5 Nf3 Nc6')).toEqual({ ok: true, sans: ['e4', 'e5', 'Nf3', 'Nc6'] })
  })

  it('reads numbered moves, castling and checks', () => {
    const r = parseMoves('1. e4 e5 2. Nf3 Nc6 3. Bc4 Nf6 4. O-O Bc5 5. d3 d6')
    expect(r.ok && r.sans).toEqual(['e4', 'e5', 'Nf3', 'Nc6', 'Bc4', 'Nf6', 'O-O', 'Bc5', 'd3', 'd6'])
  })

  it('accepts how people actually type: 0-0, lowercase-free coordinates, missing x and +', () => {
    const r = parseMoves('1.e4 d5 2.exd5 Qxd5 3.Nc3 Qa5 4.d4 c6 5.Nf3 Bg4 6.Be2 e6 7.0-0')
    expect(r.ok).toBe(true)
    expect(r.ok && r.sans.at(-1)).toBe('O-O')
    const coords = parseMoves('e2e4 e7e5 g1f3')
    expect(coords.ok && coords.sans).toEqual(['e4', 'e5', 'Nf3'])
  })

  it('reads a full PGN with headers, comments, variations, annotations and the result', () => {
    const pgn = `[Event "Klubbkveld"]
[White "Kari"]
[Black "Ola"]

1. e4 {Kongebonde} e5 2. Nf3!? Nc6 (2... d6 3. d4) 3. Bb5 a6?! $6 4. Ba4 Nf6 1-0`
    const r = parseMoves(pgn)
    expect(r.ok && r.sans).toEqual(['e4', 'e5', 'Nf3', 'Nc6', 'Bb5', 'a6', 'Ba4', 'Nf6'])
  })

  it('reads black-to-move numbering like "2... Nc6"', () => {
    const r = parseMoves('1. e4 e5 2. Nf3 2... Nc6 3. Bb5')
    expect(r.ok && r.sans).toEqual(['e4', 'e5', 'Nf3', 'Nc6', 'Bb5'])
  })

  it('says exactly which move is illegal and keeps the moves before it', () => {
    const r = parseMoves('1. e4 e5 2. Nf3 Nc6 3. Bb5 Ke7 4. O-O Qe8')
    expect(r.ok).toBe(true)
    const bad = parseMoves('1. e4 e5 2. Ke3 Nc6')
    expect(bad).toEqual({ ok: false, sans: ['e4', 'e5'], ply: 2, token: 'Ke3' })
  })

  it('treats empty input as no moves', () => {
    expect(parseMoves('')).toEqual({ ok: true, sans: [] })
    expect(parseMoves('   \n ')).toEqual({ ok: true, sans: [] })
    expect(parseMoves('1-0')).toEqual({ ok: true, sans: [] })
  })

  it('rejects text that is not moves', () => {
    const r = parseMoves('we played the italian')
    expect(r.ok).toBe(false)
    expect(!r.ok && r.ply).toBe(0)
  })
})

describe('formatMoves', () => {
  it('writes standard numbered notation', () => {
    expect(formatMoves(['e4', 'e5', 'Nf3'])).toBe('1. e4 e5 2. Nf3')
    expect(formatMoves([])).toBe('')
  })

  it('round-trips through parseMoves', () => {
    const sans = ['d4', 'Nf6', 'c4', 'g6', 'Nc3', 'Bg7', 'e4', 'd6', 'Nf3', 'O-O']
    const r = parseMoves(formatMoves(sans))
    expect(r.ok && r.sans).toEqual(sans)
  })
})

describe('finalState', () => {
  it('spots checkmate and who won', () => {
    expect(finalState(['f3', 'e5', 'g4', 'Qh4#'])).toEqual({ kind: 'checkmate', result: 'BLACK_WIN' })
    expect(finalState(['e4', 'e5', 'Bc4', 'Nc6', 'Qh5', 'Nf6', 'Qxf7#'])).toEqual({ kind: 'checkmate', result: 'WHITE_WIN' })
  })

  it('spots stalemate as a draw', () => {
    // Sam Loyd's ten-move stalemate
    const sans = parseMoves('e3 a5 Qh5 Ra6 Qxa5 h5 h4 Rah6 Qxc7 f6 Qxd7+ Kf7 Qxb7 Qd3 Qxb8 Qh7 Qxc8 Kg6 Qe6')
    expect(sans.ok).toBe(true)
    expect(finalState(sans.ok ? sans.sans : [])).toEqual({ kind: 'stalemate', result: 'DRAW' })
  })

  it('says nothing for an ongoing position', () => {
    expect(finalState(['e4'])).toEqual({ kind: 'ongoing', turn: 'b' })
    expect(finalState([])).toEqual({ kind: 'ongoing', turn: 'w' })
  })
})

describe('positionAfter', () => {
  it('gives the FEN after a number of half-moves', () => {
    expect(positionAfter(['e4', 'e5'], 0)).toBe('rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1')
    expect(positionAfter(['e4', 'e5'], 1).startsWith('rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b')).toBe(true)
  })
})

describe('splitMovetext (for showing stored moves without loading chess.js)', () => {
  it('pairs moves by number', () => {
    expect(splitMovetext('1. e4 e5 2. Nf3 Nc6 3. Bb5')).toEqual([
      { n: 1, white: 'e4', black: 'e5' },
      { n: 2, white: 'Nf3', black: 'Nc6' },
      { n: 3, white: 'Bb5', black: null },
    ])
  })

  it('returns null for free text it cannot read', () => {
    expect(splitMovetext('we played the italian')).toBeNull()
    expect(splitMovetext('')).toBeNull()
  })
})

describe('moveStats', () => {
  it('counts captures, checks, castling and promotions per side', () => {
    const r = parseMoves('1. e4 d5 2. exd5 Qxd5 3. Nc3 Qe5+ 4. Be2 Bg4 5. Nf3 Bxf3 6. O-O Bxe2 7. Qxe2 Nc6 8. d3 O-O-O')
    expect(r.ok).toBe(true)
    const s = moveStats(r.ok ? r.sans : [])
    expect(s.plies).toBe(16)
    expect(s.moves).toBe(8)
    expect(s.white).toEqual({ captures: 2, checks: 0, castled: 'short', promotions: 0 })
    expect(s.black).toEqual({ captures: 3, checks: 1, castled: 'long', promotions: 0 })
    expect(s.end).toEqual({ kind: 'ongoing', turn: 'w' })
  })

  it('counts promotions and checkmate', () => {
    const s = moveStats(['h4', 'g5', 'hxg5', 'f6', 'gxf6', 'Nc6', 'fxe7', 'Nf6', 'exf8=Q+'])
    expect(s.white.promotions).toBe(1)
    expect(s.white.checks).toBe(1)
    expect(s.white.captures).toBe(4)
    expect(s.moves).toBe(5)
    expect(moveStats(['f3', 'e5', 'g4', 'Qh4#']).end).toEqual({ kind: 'checkmate', result: 'BLACK_WIN' })
    expect(moveStats(['f3', 'e5', 'g4', 'Qh4#']).black.checks).toBe(1)
  })

  it('handles no moves', () => {
    expect(moveStats([])).toMatchObject({ plies: 0, moves: 0, white: { captures: 0 }, black: { captures: 0 } })
  })
})

describe('moveSquares', () => {
  it('gives the from/to squares of each half-move for highlighting', () => {
    expect(moveSquares(['e4', 'e5', 'Nf3'])).toEqual([
      { from: 'e2', to: 'e4' },
      { from: 'e7', to: 'e5' },
      { from: 'g1', to: 'f3' },
    ])
  })
})
