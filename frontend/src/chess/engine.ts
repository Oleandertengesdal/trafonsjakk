// Talks to Stockfish (public/engine, GPLv3) running in a Web Worker, over the UCI text protocol.
import { parseInfoLine, scoreFromInfo, type EngineLine, type PositionEval } from './analysis'

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

/** Live analysis: how deep to go and how many alternatives to show. Deep enough to be trusted, and it stops. */
export const LIVE_DEPTH = 18
export const LIVE_LINES = 3

export class Engine {
  private lines: ((line: string) => void)[] = []
  private failure: ((e: Error) => void) | null = null
  /** The search running now (live analysis), and the queue of positions waiting to be searched. */
  private search: Promise<string> | null = null
  private queue: Promise<void> = Promise.resolve()
  private multiPv = 1

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

  /**
   * Evaluates one position (for the whole-game review); the score comes back from White's side. Waits its
   * turn behind, and stops, any live search, so one engine can do both.
   */
  evaluate(fen: string): Promise<PositionEval> {
    return this.enqueue(async () => {
      await this.halt()
      this.setLines(1)
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
    })
  }

  stop() {
    this.worker.postMessage('stop')
  }

  /**
   * Searches `fen` until `depth`, calling `onLine` every time one of the best `lines` continuations
   * improves. A new call stops the previous search first, so the newest position always wins.
   */
  analyse(fen: string, onLine: (line: EngineLine) => void, { depth = LIVE_DEPTH, lines = LIVE_LINES } = {}): Promise<void> {
    return this.enqueue(async () => {
      await this.halt()
      this.setLines(lines)
      const toMove = fen.split(' ')[1] === 'b' ? 'b' : 'w'
      this.worker.postMessage(`position fen ${fen}`)
      const search = this.until(`go depth ${depth}`, (line) => {
        const info = parseInfoLine(line, toMove)
        if (info) onLine(info)
        return line.startsWith('bestmove')
      })
      this.search = search
      search.then(
        () => this.search === search && (this.search = null),
        () => this.search === search && (this.search = null),
      )
    })
  }

  /** Runs engine commands one job at a time, in order. */
  private enqueue<T>(job: () => Promise<T>): Promise<T> {
    const next = this.queue.then(job)
    this.queue = next.then(
      () => {},
      () => {},
    )
    return next
  }

  private setLines(lines: number) {
    if (lines === this.multiPv) return
    this.worker.postMessage(`setoption name MultiPV value ${lines}`)
    this.multiPv = lines
  }

  /** Stops the live search, if any, and waits until the engine has really stopped. */
  async halt() {
    const running = this.search
    if (!running) return
    this.worker.postMessage('stop')
    await running.catch(() => {})
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
