// Watching a live game: the board follows the game as it's played, you can step back through the moves
// (and return to the live position), and Stockfish can analyse whatever position is on the board.
// Loaded lazily (chess.js, the board and the engine aren't needed elsewhere on the page).
import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react'
import { Chessboard } from 'react-chessboard'
import { ChevronFirst, ChevronLeft, ChevronRight, Cpu, FlipVertical2, Radio } from 'lucide-react'
import type { LiveGame } from '../../api/types'
import { numberedLine, positions, scoreText, terminalScore, winPercent } from '../../chess/analysis'
import { moveSquares, parseMoves } from '../../chess/moves'
import { useEngineSwitch, useLiveAnalysis } from '../../chess/useLiveAnalysis'
import { useI18n } from '../../i18n/I18nProvider'
import { cn } from '../../lib/cn'
import { Button } from '../ui/Button'
import { Panel } from '../ui/Panel'
import { EngineLines, EngineSwitch } from './EngineLines'
import { EvalBar } from './EvalGraph'

export default function LiveSpectator({ game }: { game: LiveGame }) {
  const { t, f } = useI18n()
  const l = t.live
  const v = t.viewer
  const sans = useMemo(() => parseMoves(game.moves ?? '').sans, [game.moves])
  const fens = useMemo(() => positions(sans), [sans])
  const squares = useMemo(() => moveSquares(sans), [sans])

  // null = following the game; a number = looking at the position after that many moves
  const [pinned, setPinned] = useState<number | null>(null)
  const ply = pinned === null ? sans.length : Math.min(pinned, sans.length)
  const following = pinned === null || pinned >= sans.length
  const behind = sans.length - ply
  const [flipped, setFlipped] = useState(false)
  const [analysing, toggleAnalysis] = useEngineSwitch()
  const here = fens[ply]
  const finished = here ? terminalScore(here) : null
  const analysis = useLiveAnalysis(finished ? null : (here?.fen ?? null), analysing)
  const listRef = useRef<HTMLOListElement>(null)

  const go = (p: number) => setPinned(p >= sans.length ? null : Math.max(0, p))
  // Keep the current move in view in the list
  useEffect(() => {
    const list = listRef.current
    const el = list?.querySelector<HTMLElement>('[aria-current="true"]')
    if (list && el) list.scrollTop = el.offsetTop - list.clientHeight / 2
  }, [ply])

  function onKeyDown(e: KeyboardEvent) {
    const next = { ArrowLeft: ply - 1, ArrowRight: ply + 1, Home: 0, End: sans.length }[e.key]
    if (next !== undefined) {
      e.preventDefault()
      go(next)
    }
  }

  const top = analysis.status === 'thinking' ? analysis.lines[0] : undefined
  const score = finished ?? top?.score ?? null
  const whiteAtBottom = !flipped
  const last = ply > 0 ? squares[ply - 1] : null
  const highlight = { backgroundColor: 'rgb(226 177 74 / 0.5)' }
  const arrows = top ? [{ startSquare: top.pv[0].slice(0, 2), endSquare: top.pv[0].slice(2, 4), color: 'rgb(43 106 75 / 0.85)' }] : []

  const rows: { n: number; white: number; black: number | null }[] = []
  for (let i = 1; i <= sans.length; i += 2) rows.push({ n: (i + 1) / 2, white: i, black: i + 1 <= sans.length ? i + 1 : null })
  const moveButton = (p: number) => (
    <button
      type="button"
      onClick={() => go(p)}
      aria-current={p === ply ? 'true' : undefined}
      className={cn('rounded px-1.5 py-0.5 text-left transition-colors', p === ply ? 'bg-brand text-on-accent' : 'hover:bg-raised')}
    >
      {sans[p - 1]}
    </button>
  )

  return (
    <div
      role="group"
      aria-label={v.title}
      tabIndex={0}
      onKeyDown={onKeyDown}
      className="grid gap-6 rounded-control focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-accent lg:grid-cols-[minmax(0,34rem)_1fr]"
    >
      <div className="flex flex-col gap-3">
        <div className="flex gap-2">
          {analysing && score && (
            <EvalBar white={winPercent(score)} whiteAtBottom={whiteAtBottom} label={t.analysis.evalBar(scoreText(score, f.decimal1))} />
          )}
          <div className="min-w-0 flex-1 overflow-hidden rounded-control border border-line">
            <Chessboard
              options={{
                id: 'live-spectator',
                position: here?.fen ?? 'start',
                boardOrientation: whiteAtBottom ? 'white' : 'black',
                allowDragging: false,
                allowDrawingArrows: false,
                arrows: analysing ? arrows : [],
                animationDurationInMs: 200,
                lightSquareStyle: { backgroundColor: '#f3e6d0' },
                darkSquareStyle: { backgroundColor: '#b86a58' },
                squareStyles: last ? { [last.from]: highlight, [last.to]: highlight } : {},
              }}
            />
          </div>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex gap-1">
            <Button variant="secondary" size="sm" aria-label={v.start} title={v.start} disabled={ply === 0} onClick={() => go(0)}>
              <ChevronFirst aria-hidden className="size-4.5" />
            </Button>
            <Button variant="secondary" size="sm" aria-label={v.prev} title={v.prev} disabled={ply === 0} onClick={() => go(ply - 1)}>
              <ChevronLeft aria-hidden className="size-4.5" />
            </Button>
            <Button variant="secondary" size="sm" aria-label={v.next} title={v.next} disabled={following} onClick={() => go(ply + 1)}>
              <ChevronRight aria-hidden className="size-4.5" />
            </Button>
          </div>
          {following ? (
            <span className="inline-flex items-center gap-1.5 text-sm font-medium text-accent">
              <Radio aria-hidden className="size-4" />
              {l.followingLive}
            </span>
          ) : (
            <Button size="sm" onClick={() => setPinned(null)}>
              <Radio aria-hidden className="size-4" />
              {l.returnToLive(behind)}
            </Button>
          )}
          <Button variant="ghost" size="sm" aria-label={t.moves.flip} title={t.moves.flip} onClick={() => setFlipped((x) => !x)}>
            <FlipVertical2 aria-hidden className="size-4.5" />
          </Button>
        </div>
        <p data-testid="spectator-status" aria-live="polite" className="text-sm text-muted">
          {ply === 0 ? v.startPosition : v.afterMove(numberedLine([sans[ply - 1]], ply - 1).first)}
        </p>
      </div>

      <div className="flex min-w-0 flex-col gap-6">
        <Panel title={t.engine.title} action={<EngineSwitch checked={analysing} onChange={toggleAnalysis} />}>
          <div className="px-5 pt-1 pb-5">
            <EngineLines
              analysis={analysis}
              finished={finished !== null}
              ply={ply}
              fen={here?.fen ?? ''}
              intro={
                <div className="flex flex-col gap-2">
                  <p className="text-[0.9375rem] text-muted">{l.analysisIntro}</p>
                  <p className="flex items-center gap-2 text-sm text-faint">
                    <Cpu aria-hidden className="size-4" />
                    {l.analysisFairPlay}
                  </p>
                </div>
              }
            />
          </div>
        </Panel>

        <Panel title={l.moveCount(Math.ceil(sans.length / 2))}>
          <div className="flex flex-col gap-3 px-5 pt-1 pb-5">
            {sans.length > 0 && (
              <ol
                ref={listRef}
                aria-label={t.moves.list}
                className="relative grid max-h-64 grid-cols-[auto_1fr_1fr] content-start gap-x-2 gap-y-0.5 overflow-y-auto font-mono text-sm tabular-nums"
              >
                {rows.map((r) => (
                  <li key={r.n} className="col-span-3 grid grid-cols-subgrid items-center">
                    <span className="pr-1 text-faint">{r.n}.</span>
                    {moveButton(r.white)}
                    {r.black ? moveButton(r.black) : <span />}
                  </li>
                ))}
              </ol>
            )}
            <p className="text-sm text-faint">{l.following}</p>
          </div>
        </Panel>
      </div>
    </div>
  )
}
