// Talks to Stockfish (public/engine, GPLv3) running in a Web Worker, over the UCI text protocol.
import { scoreFromInfo, type PositionEval } from './analysis'

export const ENGINE_URL = '/engine/stockfish-18-lite-single.js'
export const ENGINE_NAME = 'Stockfish 18'
/** Search depth per position: strong enough to judge club games, quick on a phone. */
export const DEPTH = 14
/** …but never more than this per position, in case a position is very complex. */
export const MAX_MS = 1500

/** The bit of Worker the engine needs; tests pass a fake. */
export interface EngineWorker {
  postMessage(message: string): void
  terminate(): void
  onmessage: ((e: MessageEvent) => void) | null
  onerror: ((e: ErrorEvent) => void) | null
}

export class Engine {
  private lines: ((line: string) => void)[] = []
  private failure: ((e: Error) => void) | null = null

  private constructor(private readonly worker: EngineWorker) {
    worker.onmessage = (e: MessageEvent) => {
      const text = typeof e.data === 'string' ? e.data : String(e.data)
      for (const line of text.split('\n')) for (const l of [...this.lines]) l(line)
    }
    worker.onerror = (e: ErrorEvent) => this.failure?.(new Error(e.message || 'Engine failed'))
  }

  /** Starts the engine and waits until it's ready. */
  static async load(create: () => EngineWorker = () => new Worker(ENGINE_URL) as unknown as EngineWorker): Promise<Engine> {
    const engine = new Engine(create())
    await engine.until('uci', (l) => l === 'uciok')
    await engine.until('isready', (l) => l === 'readyok')
    engine.worker.postMessage('ucinewgame')
    return engine
  }

  /** Evaluates one position; the score comes back from White's side. */
  async evaluate(fen: string): Promise<PositionEval> {
    const toMove = fen.split(' ')[1] === 'b' ? 'b' : 'w'
    let score: PositionEval['score'] | null = null
    this.worker.postMessage(`position fen ${fen}`)
    const done = await this.until(`go depth ${DEPTH} movetime ${MAX_MS}`, (line) => {
      if (line.startsWith('info') && !line.includes(' upperbound') && !line.includes(' lowerbound')) {
        score = scoreFromInfo(line, toMove) ?? score
      }
      return line.startsWith('bestmove')
    })
    const best = done.split(' ')[1]
    return { score: score ?? { cp: 0 }, best: best && best !== '(none)' ? best : null }
  }

  stop() {
    this.worker.postMessage('stop')
  }

  terminate() {
    this.lines = []
    this.worker.terminate()
  }

  /** Sends a command and resolves with the first line that `done` accepts. */
  private until(command: string, done: (line: string) => boolean): Promise<string> {
    return new Promise((resolve, reject) => {
      const listener = (line: string) => {
        if (done(line.trim())) {
          this.lines = this.lines.filter((l) => l !== listener)
          this.failure = null
          resolve(line.trim())
        }
      }
      this.lines.push(listener)
      this.failure = reject
      this.worker.postMessage(command)
    })
  }
}
