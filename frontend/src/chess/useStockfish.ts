import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { positions, terminalScore, type EngineLine, type PositionEval } from './analysis'
import { DEPTH, Engine } from './engine'

/** What the engine is doing for the board. */
export type StockfishState =
  | { status: 'off' }
  | { status: 'loading' }
  | { status: 'error' }
  /** Going through every position of the game once (the review: marks, graph, accuracy). */
  | { status: 'reviewing'; done: number; total: number }
  /** Thinking about the position on the board; `lines` is empty until its first thought. */
  | { status: 'ready'; depth: number; lines: EngineLine[] }

/** Below this depth the engine's first choice still jumps around: no arrow yet, so the board stays calm. */
export const ARROW_DEPTH = 8

/** How often the screen follows the engine, which reports many times a second. */
const REFRESH_MS = 300
/** Stepping quickly through moves shouldn't start a search for every one of them. */
const SETTLE_MS = 250

const PREFIX = 'tf-analysis:v1:'

/** A short, stable key for a move list (FNV-1a), so the same game isn't reviewed twice on this device. */
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
    // Storage full or blocked: the review still shows, it just isn't remembered
  }
}

/** Same lines, same depth: nothing new to draw. */
function signature(depth: number, lines: EngineLine[]) {
  return `${depth}|${lines.map((l) => `${l.rank}:${'cp' in l.score ? l.score.cp : `m${l.score.mate}`}:${l.pv.slice(0, 6).join(' ')}`).join('|')}`
}

/**
 * One Stockfish for one board. While `enabled`, it first reviews the whole game (`review`: its moves, each
 * position once, remembered on this device), then thinks about `fen`, the position on the board, reporting
 * its best lines as it looks deeper. A new position (a move, stepping back, your own move) starts over.
 * One engine does both, so the device never runs two at once; it rests while the page is hidden.
 */
export function useStockfish({ enabled, fen, review }: { enabled: boolean; fen: string | null; review?: string[] }) {
  const engine = useRef<Engine | null>(null)
  const [loaded, setLoaded] = useState(0)
  const [failed, setFailed] = useState(false)

  const key = review ? cacheKey(review) : null
  const cached = useMemo(() => (key && review ? readCache(key, review.length) : null), [key, review])
  const [computed, setComputed] = useState<{ key: string; evals: PositionEval[] } | null>(null)
  const evals = cached ?? (computed && computed.key === key ? computed.evals : null)
  const needsReview = !!review && !evals
  const [progress, setProgress] = useState<{ key: string; done: number } | null>(null)
  const [result, setResult] = useState<{ fen: string; depth: number; lines: EngineLine[] } | null>(null)

  // The engine itself, while analysis is on
  useEffect(() => {
    if (!enabled) return
    let cancelled = false
    Engine.load().then(
      (e) => {
        if (cancelled) return e.terminate()
        engine.current = e
        setFailed(false)
        setLoaded((n) => n + 1)
      },
      () => !cancelled && setFailed(true),
    )
    return () => {
      cancelled = true
      engine.current?.terminate()
      engine.current = null
    }
  }, [enabled])

  // The review: every position of the game, once
  useEffect(() => {
    const e = engine.current
    if (!enabled || !e || !needsReview || !review || !key) return
    let cancelled = false
    ;(async () => {
      await Promise.resolve()
      const all = positions(review)
      const out: PositionEval[] = []
      for (const p of all) {
        if (cancelled) return
        setProgress({ key, done: out.length })
        const final = terminalScore(p)
        out.push(final ? { score: final, best: null } : await e.evaluate(p.fen))
      }
      if (cancelled) return
      writeCache(key, out)
      setComputed({ key, evals: out })
    })().catch(() => !cancelled && setFailed(true))
    return () => {
      cancelled = true
    }
  }, [enabled, loaded, needsReview, review, key])

  // Thinking about the position on the board, once the review is done
  useEffect(() => {
    const e = engine.current
    if (!enabled || !e || !fen || needsReview) return
    let current = true
    let shown = ''
    const best = new Map<number, EngineLine>()
    let flushTimer: number | null = null
    const flush = () => {
      flushTimer = null
      if (!current) return
      const lines = [...best.values()].sort((a, b) => a.rank - b.rank)
      const depth = lines[0]?.depth ?? 0
      const sig = signature(depth, lines)
      if (sig === shown) return
      shown = sig
      setResult({ fen, depth, lines })
    }
    const search = () => {
      if (!current || document.visibilityState === 'hidden') return
      best.clear()
      e.analyse(fen, (line) => {
        if (!current) return
        best.set(line.rank, line)
        flushTimer ??= window.setTimeout(flush, REFRESH_MS)
      }).catch(() => current && setFailed(true))
    }
    const settle = window.setTimeout(search, SETTLE_MS)
    // Rest while the page is hidden, and carry on when it's back
    const onVisibility = () => (document.visibilityState === 'hidden' ? void e.halt() : search())
    document.addEventListener('visibilitychange', onVisibility)
    return () => {
      current = false
      window.clearTimeout(settle)
      if (flushTimer !== null) window.clearTimeout(flushTimer)
      document.removeEventListener('visibilitychange', onVisibility)
      void e.halt()
    }
  }, [enabled, loaded, fen, needsReview])

  let state: StockfishState
  if (!enabled) state = { status: 'off' }
  else if (failed) state = { status: 'error' }
  else if (!loaded) state = { status: 'loading' }
  else if (needsReview && review) state = { status: 'reviewing', done: progress?.key === key ? progress.done : 0, total: review.length + 1 }
  else state = result && result.fen === fen ? { status: 'ready', depth: result.depth, lines: result.lines } : { status: 'ready', depth: 0, lines: [] }
  return { state, evals }
}

const MODE_KEY = 'tf-analysis-mode'

/** Analysis on or off, remembered on this device (the same choice for live games and finished ones). */
export function useAnalysisMode(): [boolean, () => void] {
  const [on, setOn] = useState(() => {
    try {
      return localStorage.getItem(MODE_KEY) === 'on'
    } catch {
      return false
    }
  })
  const toggle = useCallback(() => {
    setOn((was) => {
      try {
        localStorage.setItem(MODE_KEY, was ? 'off' : 'on')
      } catch {
        // Not remembered, that's all
      }
      return !was
    })
  }, [])
  return [on, toggle]
}
