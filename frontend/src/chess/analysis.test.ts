import { describe, expect, it } from 'vitest'
import {
  moveAccuracy,
  parseInfoLine,
  moveWeights,
  playedLike,
  playerAccuracy,
  pvToSan,
  positions,
  reviewGame,
  scoreFromInfo,
  scoreText,
  terminalScore,
  uciToSan,
  winPercent,
  type PositionEval,
} from './analysis'

const dec = (n: number) => n.toFixed(1)

describe('winPercent', () => {
  it('is 50 for an equal position and approaches 0 or 100 as one side gets better', () => {
    expect(winPercent({ cp: 0 })).toBe(50)
    expect(winPercent({ cp: 100 })).toBeCloseTo(59.1, 1)
    expect(winPercent({ cp: -100 })).toBeCloseTo(40.9, 1)
    expect(winPercent({ cp: 5000 })).toBe(winPercent({ cp: 1000 })) // capped
    expect(winPercent({ mate: 3, by: 'w' })).toBe(100)
    expect(winPercent({ mate: 0, by: 'b' })).toBe(0)
  })
})

describe('scoreFromInfo', () => {
  it("turns the engine's view (side to move) into White's", () => {
    const line = 'info depth 12 seldepth 17 multipv 1 score cp 51 nodes 27872 nps 442412 time 63 pv c2c3 g8f6'
    expect(scoreFromInfo(line, 'w')).toEqual({ cp: 51 })
    expect(scoreFromInfo(line, 'b')).toEqual({ cp: -51 })
    expect(scoreFromInfo('info depth 9 score mate 3 pv d8h4', 'b')).toEqual({ mate: 3, by: 'b' })
    expect(scoreFromInfo('info depth 9 score mate -2 pv e1e2', 'w')).toEqual({ mate: 2, by: 'b' })
    expect(scoreFromInfo('info string NNUE evaluation using nn-123.nnue', 'w')).toBeNull()
  })
})

describe('scoreText', () => {
  it('writes pawns with a sign and mates with #', () => {
    expect(scoreText({ cp: 134 }, dec)).toBe('+1.3')
    expect(scoreText({ cp: -44 }, dec)).toBe('−0.4')
    expect(scoreText({ cp: 3 }, dec)).toBe('0.0')
    expect(scoreText({ mate: 3, by: 'w' }, dec)).toBe('#3')
    expect(scoreText({ mate: 2, by: 'b' }, dec)).toBe('−#2')
    expect(scoreText({ mate: 0, by: 'b' }, dec)).toBe('0–1')
  })
})

describe('positions', () => {
  it('lists every position and spots the end of the game', () => {
    const p = positions(['f3', 'e5', 'g4', 'Qh4#'])
    expect(p).toHaveLength(5)
    expect(p[0].toMove).toBe('w')
    expect(p[1].toMove).toBe('b')
    expect(p[4].over).toBe('checkmate')
    expect(terminalScore(p[4])).toEqual({ mate: 0, by: 'b' })
    expect(terminalScore(p[3])).toBeNull()
  })
})

describe('uciToSan', () => {
  it('converts engine moves to the notation people read', () => {
    const start = positions([])[0].fen
    expect(uciToSan(start, 'e2e4')).toBe('e4')
    expect(uciToSan(start, 'g1f3')).toBe('Nf3')
    expect(uciToSan('8/P7/8/8/8/8/8/k6K w - - 0 1', 'a7a8q')).toBe('a8=Q+')
    expect(uciToSan(start, 'e2e5')).toBeNull()
  })
})

describe('moveAccuracy', () => {
  it('is about 100 for a move that gives nothing away and falls with the drop', () => {
    expect(moveAccuracy(55, 55)).toBe(100)
    expect(moveAccuracy(55, 60)).toBe(100)
    expect(moveAccuracy(60, 40)).toBeGreaterThan(40)
    expect(moveAccuracy(60, 40)).toBeLessThan(45)
    expect(moveAccuracy(100, 0)).toBe(0)
  })
})

describe('reviewGame', () => {
  it("marks the fool's mate blunder, and knows the mating move was best", () => {
    const sans = ['f3', 'e5', 'g4', 'Qh4#']
    const evals: PositionEval[] = [
      { score: { cp: 20 }, best: 'e2e4' },
      { score: { cp: -50 }, best: 'e7e5' },
      { score: { cp: -40 }, best: 'b1c3' },
      { score: { mate: 1, by: 'b' }, best: 'd8h4' },
      { score: { mate: 0, by: 'b' }, best: null },
    ]
    const r = reviewGame(sans, evals)
    expect(r.moves.map((m) => m.judgement)).toEqual([null, null, 'blunder', null])
    const g4 = r.moves[2]
    expect(g4.allowedMate).toBe(1)
    expect(g4.bestSan).toBe('Nc3')
    expect(r.moves[0].bestSan).toBe('e4')
    expect(r.moves[1].bestSan).toBeNull() // e5 was the engine's move
    expect(r.moves[3].missedMate).toBeNull()
    expect(r.white.blunders).toBe(1)
    expect(r.black.blunders).toBe(0)
    expect(r.white.accuracy).toBeLessThan(r.black.accuracy)
  })

  it('grades by how much winning chance was given away, and says how many pawns it cost', () => {
    const sans = ['e4', 'e5', 'Nf3', 'Nc6']
    const evals: PositionEval[] = [
      { score: { cp: 30 }, best: 'e2e4' },
      { score: { cp: 30 }, best: 'c7c5' },
      { score: { cp: 150 }, best: 'b1c3' }, // after e5: Black lost 1.2 pawns, about 11 points of winning chance
      { score: { cp: -30 }, best: 'g8f6' }, // after Nf3: White lost 1.8, about 16 points
      { score: { cp: 300 }, best: 'f1b5' }, // after Nc6: Black lost 3.3, about 28 points
    ]
    const r = reviewGame(sans, evals)
    expect(r.moves[0].judgement).toBeNull()
    expect(r.moves[0].bestSan).toBeNull()
    expect(r.moves[1].judgement).toBe('inaccuracy')
    expect(r.moves[1].pawnsLost).toBeCloseTo(1.2)
    expect(r.moves[1].bestSan).toBe('c5')
    expect(r.moves[2].judgement).toBe('inaccuracy')
    expect(r.moves[2].pawnsLost).toBeCloseTo(1.8)
    expect(r.moves[3].judgement).toBe('mistake')
    expect(r.moves[3].pawnsLost).toBeCloseTo(3.3)
    expect(r.moves[3].bestSan).toBe('Nf6')
    expect(r.black).toMatchObject({ mistakes: 1, inaccuracies: 1, blunders: 0 })
  })

  it('counts a thrown-away mate as a mistake unless the position is still easily won', () => {
    const sans = ['e4']
    const missed = reviewGame(sans, [
      { score: { mate: 2, by: 'w' }, best: 'd2d4' },
      { score: { cp: 300 }, best: null },
    ])
    expect(missed.moves[0].missedMate).toBe(2)
    expect(missed.moves[0].judgement).toBe('mistake')

    const stillWon = reviewGame(sans, [
      { score: { mate: 2, by: 'w' }, best: 'd2d4' },
      { score: { cp: 900 }, best: null },
    ])
    expect(stillWon.moves[0].missedMate).toBe(2)
    expect(stillWon.moves[0].judgement).toBeNull()
  })

  it('reads the live search lines Stockfish prints', () => {
    expect(parseInfoLine('info depth 18 seldepth 24 multipv 2 score cp 31 nodes 9 nps 1 pv e2e4 e7e5 g1f3', 'w')).toEqual({
      rank: 2,
      depth: 18,
      score: { cp: 31 },
      pv: ['e2e4', 'e7e5', 'g1f3'],
    })
    expect(parseInfoLine('info depth 5 score mate 2 pv h5f7', 'b')).toEqual({ rank: 1, depth: 5, score: { mate: 2, by: 'b' }, pv: ['h5f7'] })
    expect(parseInfoLine('info depth 18 multipv 1 score cp 31 lowerbound pv e2e4', 'w')).toBeNull()
    expect(parseInfoLine('info depth 18 currmove e2e4 currmovenumber 1', 'w')).toBeNull()
    expect(parseInfoLine('info string NNUE evaluation enabled', 'w')).toBeNull()
  })

  it('writes an engine line as moves, up to the first one that isn’t legal', () => {
    const start = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1'
    expect(pvToSan(start, ['e2e4', 'e7e5', 'g1f3', 'b8c6'])).toEqual(['e4', 'e5', 'Nf3', 'Nc6'])
    expect(pvToSan(start, ['e2e4', 'e7e5', 'e1e3'])).toEqual(['e4', 'e5'])
    expect(pvToSan(start, ['e2e4', 'e7e5', 'g1f3'], 2)).toEqual(['e4', 'e5'])
  })
})

describe('playedLike', () => {
  it('turns accuracy into a rough rating, to the nearest 50', () => {
    expect(playedLike({ accuracy: 75, moves: 30 })).toBe(1200)
    expect(playedLike({ accuracy: 82, moves: 30 })).toBe(1550)
    expect(playedLike({ accuracy: 90, moves: 30 })).toBe(2000)
  })

  it('rises with accuracy and stays between 400 and 2800', () => {
    const ratings = [0, 30, 50, 55, 65, 72, 78, 83, 88, 93, 96, 98, 99.5, 100].map((accuracy) => playedLike({ accuracy, moves: 40 })!)
    expect(ratings).toEqual([...ratings].sort((a, b) => a - b))
    expect(ratings[0]).toBe(400)
    expect(ratings.at(-1)).toBe(2800)
    expect(ratings.every((r) => r % 50 === 0)).toBe(true)
  })

  it('gives no estimate for a short game', () => {
    expect(playedLike({ accuracy: 95, moves: 9 })).toBeNull()
    expect(playedLike({ accuracy: 95, moves: 10 })).not.toBeNull()
  })
})

describe('game accuracy (lichess)', () => {
  it('weighs moves by how much the game was swinging around them', () => {
    expect(moveWeights([50, 50, 50])).toEqual([0.5, 0.5])
    const w = moveWeights([50, 50, 90, 10, 50])
    expect(w).toHaveLength(4)
    expect(w[0]).toBe(0.5)
    expect(w[2]).toBe(12)
    expect(moveWeights([50])).toEqual([])
  })

  it('has one weight per move, also for long games', () => {
    const win = Array.from({ length: 81 }, (_, i) => 50 + 40 * Math.sin(i / 5))
    const w = moveWeights(win)
    expect(w).toHaveLength(80)
    expect(w.every((x) => x >= 0.5 && x <= 12)).toBe(true)
  })

  it('averages a weighted and a harmonic mean', () => {
    expect(playerAccuracy([100, 50], [1, 1])).toBeCloseTo((75 + 2 / (1 / 100 + 1 / 50)) / 2, 6)
    expect(playerAccuracy([], [])).toBe(100)
    // A move scored 0 counts as 1 in the harmonic mean, so it can't divide by zero
    expect(playerAccuracy([0, 100], [1, 1])).toBeGreaterThan(0)
  })

  it("doesn't let a long, quiet, won endgame hide the blunders", () => {
    const accuracies = [...Array(40).fill(100), 20, 20]
    const weights = [...Array(40).fill(0.5), 12, 12]
    const plain = accuracies.reduce((a, b) => a + b, 0) / accuracies.length
    expect(plain).toBeGreaterThan(95)
    expect(playerAccuracy(accuracies, weights)).toBeLessThan(75)
  })
})
