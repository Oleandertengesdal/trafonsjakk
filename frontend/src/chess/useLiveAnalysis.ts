import { useCallback, useEffect, useRef, useState } from 'react'
import type { EngineLine } from './analysis'
import { Engine } from './engine'

export type LiveAnalysis =
  | { status: 'off' }
  | { status: 'loading' }
  | { status: 'error' }
  /** `lines` is empty until the engine has its first thought about this position. */
  | { status: 'thinking'; depth: number; lines: EngineLine[] }

/** How often the screen follows the engine: it reports many times a second. */
const REFRESH_MS = 250

/**
 * Stockfish thinking about one position at a time, live: give it a FEN and it searches until deep enough,
 * reporting its best lines as they improve. A new FEN (a new move, or stepping back) starts over. The
 * engine is started when `enabled` turns on and stopped when it turns off or the page closes.
 */
export function useLiveAnalysis(fen: string | null, enabled: boolean): LiveAnalysis {
  const engine = useRef<Engine | null>(null)
  const [loaded, setLoaded] = useState(0)
  const [failed, setFailed] = useState(false)
  const [result, setResult] = useState<{ fen: string; depth: number; lines: EngineLine[] } | null>(null)

  // Start the engine while analysis is on
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

  // Think about the position on the board
  useEffect(() => {
    const e = engine.current
    if (!enabled || !e || !fen) return
    let current = true
    const best = new Map<number, EngineLine>()
    let timer: number | null = null
    const flush = () => {
      timer = null
      if (!current) return
      const lines = [...best.values()].sort((a, b) => a.rank - b.rank)
      setResult({ fen, depth: lines[0]?.depth ?? 0, lines })
    }
    e.analyse(fen, (line) => {
      if (!current) return
      best.set(line.rank, line)
      timer ??= window.setTimeout(flush, REFRESH_MS)
    }).catch(() => current && setFailed(true))
    return () => {
      current = false
      if (timer !== null) window.clearTimeout(timer)
    }
  }, [fen, enabled, loaded])

  if (!enabled) return { status: 'off' }
  if (failed) return { status: 'error' }
  if (!loaded) return { status: 'loading' }
  return result && result.fen === fen ? { status: 'thinking', depth: result.depth, lines: result.lines } : { status: 'thinking', depth: 0, lines: [] }
}

const SWITCH_KEY = 'tf-live-analysis'

/** The Stockfish switch, remembered on this device (the same choice for live games and finished ones). */
export function useEngineSwitch(): [boolean, () => void] {
  const [on, setOn] = useState(() => {
    try {
      return localStorage.getItem(SWITCH_KEY) === 'on'
    } catch {
      return false
    }
  })
  const toggle = useCallback(() => {
    setOn((was) => {
      try {
        localStorage.setItem(SWITCH_KEY, was ? 'off' : 'on')
      } catch {
        // Not remembered, that's all
      }
      return !was
    })
  }, [])
  return [on, toggle]
}
