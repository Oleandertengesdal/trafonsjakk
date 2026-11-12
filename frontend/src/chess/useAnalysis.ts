import { useCallback, useEffect, useRef, useState } from 'react'
import { positions, terminalScore, type PositionEval } from './analysis'
import { DEPTH, Engine } from './engine'

export type AnalysisState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'running'; done: number; total: number }
  | { status: 'done'; evals: PositionEval[] }
  | { status: 'error' }

const PREFIX = 'tf-analysis:v1:'

/** A short, stable key for a move list (FNV-1a), so the same game isn't analysed twice on this device. */
export function cacheKey(sans: string[]): string {
  let h = 0x811c9dc5
  const text = sans.join(' ')
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i)
    h = Math.imul(h, 0x01000193)
  }
  return `${PREFIX}d${DEPTH}:${sans.length}:${(h >>> 0).toString(36)}`
}

function readCache(key: string, length: number): PositionEval[] | null {
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return null
    const evals = JSON.parse(raw) as PositionEval[]
    return Array.isArray(evals) && evals.length === length + 1 ? evals : null
  } catch {
    return null
  }
}

function writeCache(key: string, evals: PositionEval[]) {
  try {
    localStorage.setItem(key, JSON.stringify(evals))
  } catch {
    // Storage full or blocked: the analysis still shows, it just isn't remembered
  }
}

/**
 * Runs Stockfish over every position of a game, on request. Results are kept in this browser, so opening
 * the game again shows the analysis straight away.
 */
export function useAnalysis(sans: string[]) {
  const key = cacheKey(sans)
  const [state, setState] = useState<AnalysisState>(() => {
    const cached = readCache(key, sans.length)
    return cached ? { status: 'done', evals: cached } : { status: 'idle' }
  })
  const run = useRef<{ cancelled: boolean; engine: Engine | null } | null>(null)

  const stop = useCallback(() => {
    if (run.current) {
      run.current.cancelled = true
      run.current.engine?.terminate()
      run.current = null
    }
  }, [])

  // Stop the engine when leaving the page
  useEffect(() => stop, [stop])

  const start = useCallback(async () => {
    stop()
    const me = { cancelled: false, engine: null as Engine | null }
    run.current = me
    setState({ status: 'loading' })
    try {
      const engine = await Engine.load()
      if (me.cancelled) {
        engine.terminate()
        return
      }
      me.engine = engine
      const all = positions(sans)
      const evals: PositionEval[] = []
      for (const p of all) {
        if (me.cancelled) return
        setState({ status: 'running', done: evals.length, total: all.length })
        const final = terminalScore(p)
        evals.push(final ? { score: final, best: null } : await engine.evaluate(p.fen))
      }
      if (me.cancelled) return
      engine.terminate()
      run.current = null
      writeCache(key, evals)
      setState({ status: 'done', evals })
    } catch {
      if (!me.cancelled) {
        me.engine?.terminate()
        run.current = null
        setState({ status: 'error' })
      }
    }
  }, [key, sans, stop])

  const cancel = useCallback(() => {
    stop()
    setState({ status: 'idle' })
  }, [stop])

  return { state, start, cancel }
}
