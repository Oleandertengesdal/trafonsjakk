import { describe, expect, it } from 'vitest'
import { Engine, type EngineWorker } from './engine'

/** Answers like Stockfish over UCI. `script` maps a "go" to the lines the engine prints. */
class FakeWorker implements EngineWorker {
  onmessage: ((e: MessageEvent) => void) | null = null
  onerror: ((e: ErrorEvent) => void) | null = null
  sent: string[] = []
  terminated = false
  constructor(private script: string[]) {}
  postMessage(message: string) {
    this.sent.push(message)
    const say = (...lines: string[]) => queueMicrotask(() => lines.forEach((l) => this.onmessage?.({ data: l } as MessageEvent)))
    if (message === 'uci') say('id name Stockfish 18 Lite', 'option name Hash type spin', 'uciok')
    else if (message === 'isready') say('readyok')
    else if (message.startsWith('go')) say(...this.script)
  }
  terminate() {
    this.terminated = true
  }
}

const BLACK_TO_MOVE = 'rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1'

describe('Engine', () => {
  it('starts with the UCI handshake and evaluates from White’s side', async () => {
    const w = new FakeWorker([
      'info depth 1 score cp 10 pv e7e5',
      'info depth 14 seldepth 18 score cp -35 upperbound pv c7c5', // a bound, not a result
      'info depth 14 seldepth 18 multipv 1 score cp -28 nodes 1 pv c7c5 g1f3',
      'bestmove c7c5 ponder g1f3',
    ])
    const engine = await Engine.load(() => w)
    expect(w.sent.slice(0, 3)).toEqual(['uci', 'isready', 'ucinewgame'])
    const result = await engine.evaluate(BLACK_TO_MOVE)
    expect(w.sent).toContain(`position fen ${BLACK_TO_MOVE}`)
    expect(w.sent.some((s) => s.startsWith('go depth'))).toBe(true)
    expect(result).toEqual({ score: { cp: 28 }, best: 'c7c5' })
    engine.terminate()
    expect(w.terminated).toBe(true)
  })

  it('reports mates for the right side and no move in a finished position', async () => {
    const engine = await Engine.load(() => new FakeWorker(['info depth 5 score mate -1 pv d8h4', 'bestmove (none)']))
    const result = await engine.evaluate(BLACK_TO_MOVE)
    expect(result).toEqual({ score: { mate: 1, by: 'w' }, best: null })
  })

  it('fails instead of hanging when the worker errors', async () => {
    const w = new FakeWorker([])
    w.postMessage = function (this: FakeWorker, message: string) {
      this.sent.push(message)
      queueMicrotask(() => this.onerror?.({ message: 'WebAssembly is not allowed' } as ErrorEvent))
    }
    await expect(Engine.load(() => w)).rejects.toThrow('WebAssembly is not allowed')
  })
})
