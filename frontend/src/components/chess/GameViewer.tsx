// Replays a game on a board: step through with buttons, arrow keys or by tapping a move, plus a few
// numbers about the game. Loaded lazily (chess.js and the board), like the move recorder.
import { useMemo, useRef, useState, useEffect, type KeyboardEvent } from 'react'
import { Chessboard } from 'react-chessboard'
import { ChevronFirst, ChevronLast, ChevronLeft, ChevronRight, Cpu, FlipVertical2 } from 'lucide-react'
import { glyph, reviewGame, scoreText, uciToSan, winPercent, type GameReview, type Judgement, type PositionEval } from '../../chess/analysis'
import { moveSquares, moveStats, parseMoves, positionAfter } from '../../chess/moves'
import { useAnalysis, type AnalysisState } from '../../chess/useAnalysis'
import { useI18n } from '../../i18n/I18nProvider'
import { cn } from '../../lib/cn'
import { Button } from '../ui/Button'
import { EvalBar, EvalGraph } from './EvalGraph'

export interface GameViewerProps {
  moves: string
  orientation: 'white' | 'black'
  whiteName: string
  blackName: string
}

/** "12. Nf3" for White's move, "12... Nf6" for Black's. */
function label(sans: string[], ply: number) {
  return numbered(ply, sans[ply - 1])
}

function numbered(ply: number, san: string) {
  const n = Math.floor((ply - 1) / 2) + 1
  return ply % 2 === 1 ? `${n}. ${san}` : `${n}... ${san}`
}

const glyphColour: Record<Judgement, string> = { blunder: 'text-loss', mistake: 'text-gold', inaccuracy: 'text-muted' }

export default function GameViewer({ moves, orientation, whiteName, blackName }: GameViewerProps) {
  const { t, f } = useI18n()
  const v = t.viewer
  const parsed = useMemo(() => parseMoves(moves), [moves])
  const sans = parsed.sans
  const squares = useMemo(() => moveSquares(sans), [sans])
  const stats = useMemo(() => moveStats(sans), [sans])
  const [ply, setPly] = useState(sans.length) // opens at the final position
  const [flipped, setFlipped] = useState(false)
  const listRef = useRef<HTMLOListElement>(null)
  const analysis = useAnalysis(sans)
  const evals = analysis.state.status === 'done' ? analysis.state.evals : null
  const review = useMemo(() => (evals && parsed.ok ? reviewGame(sans, evals) : null), [evals, sans, parsed.ok])

  const go = (p: number) => setPly(Math.max(0, Math.min(sans.length, p)))

  // Keep the current move visible in the list
  useEffect(() => {
    const el = listRef.current?.querySelector<HTMLElement>('[aria-current="true"]')
    if (el && listRef.current) {
      const list = listRef.current
      if (el.offsetTop < list.scrollTop || el.offsetTop > list.scrollTop + list.clientHeight - el.offsetHeight) {
        list.scrollTop = el.offsetTop - list.clientHeight / 2
      }
    }
  }, [ply])

  function onKeyDown(e: KeyboardEvent) {
    const next = { ArrowLeft: ply - 1, ArrowRight: ply + 1, Home: 0, End: sans.length, ArrowUp: ply - 1, ArrowDown: ply + 1 }[e.key]
    if (next !== undefined) {
      e.preventDefault()
      go(next)
    }
  }

  const last = ply > 0 ? squares[ply - 1] : null
  const highlight = { backgroundColor: 'rgb(226 177 74 / 0.5)' }
  const rows: { n: number; white: number; black: number | null }[] = []
  for (let i = 1; i <= sans.length; i += 2) rows.push({ n: (i + 1) / 2, white: i, black: i + 1 <= sans.length ? i + 1 : null })

  const moveButton = (p: number) => (
    <button
      type="button"
      onClick={() => go(p)}
      aria-current={p === ply ? 'true' : undefined}
      className={cn(
        'rounded px-1.5 py-0.5 text-left transition-colors',
        p === ply ? 'bg-brand text-on-accent' : 'hover:bg-raised',
      )}
    >
      {sans[p - 1]}
      {review?.moves[p - 1].judgement && (
        <span className={cn('ml-0.5 font-sans font-semibold', p === ply ? 'text-on-accent' : glyphColour[review.moves[p - 1].judgement!])}>
          {glyph[review.moves[p - 1].judgement!]}
        </span>
      )}
    </button>
  )

  if (!parsed.ok) {
    // Saved as text that isn't a legal game (older games, typos): show it as written
    return <p className="max-h-64 overflow-auto rounded-control bg-page px-3 py-2.5 text-[0.875rem] leading-relaxed whitespace-pre-wrap">{moves}</p>
  }

  const castled = (c: 'short' | 'long' | null) => (c === 'short' ? v.castledShort : c === 'long' ? v.castledLong : v.castledNone)
  const statRows: [string, string | number, string | number][] = [
    [v.captures, stats.white.captures, stats.black.captures],
    [v.checks, stats.white.checks, stats.black.checks],
    [v.castled, castled(stats.white.castled), castled(stats.black.castled)],
  ]
  if (stats.white.promotions + stats.black.promotions > 0) statRows.push([v.promotions, stats.white.promotions, stats.black.promotions])

  const whiteAtBottom = (orientation === 'white') !== flipped
  const here = evals?.[ply]
  const bestHere = here?.best ?? null
  const arrows = bestHere ? [{ startSquare: bestHere.slice(0, 2), endSquare: bestHere.slice(2, 4), color: 'rgb(43 106 75 / 0.85)' }] : []

  return (
    <div
      role="group"
      aria-label={v.title}
      tabIndex={0}
      onKeyDown={onKeyDown}
      className="grid gap-5 rounded-control focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-accent sm:grid-cols-[minmax(0,24rem)_1fr]"
    >
      <div className="flex flex-col gap-3">
        <div className="flex gap-2">
          {here && (
            <EvalBar
              white={winPercent(here.score)}
              whiteAtBottom={whiteAtBottom}
              label={t.analysis.evalBar(scoreText(here.score, f.decimal1))}
            />
          )}
        <div className="min-w-0 flex-1 overflow-hidden rounded-control border border-line">
          <Chessboard
            options={{
              id: 'game-viewer',
              position: positionAfter(sans, ply),
              boardOrientation: whiteAtBottom ? 'white' : 'black',
              allowDragging: false,
              allowDrawingArrows: false,
              arrows,
              animationDurationInMs: 150,
              lightSquareStyle: { backgroundColor: '#f3e6d0' },
              darkSquareStyle: { backgroundColor: '#b86a58' },
              squareStyles: last ? { [last.from]: highlight, [last.to]: highlight } : {},
            }}
          />
        </div>
        </div>
        <div className="flex items-center justify-between gap-2">
          <div className="flex gap-1">
            <Button variant="secondary" size="sm" aria-label={v.start} title={v.start} disabled={ply === 0} onClick={() => go(0)}>
              <ChevronFirst aria-hidden className="size-4.5" />
            </Button>
            <Button variant="secondary" size="sm" aria-label={v.prev} title={v.prev} disabled={ply === 0} onClick={() => go(ply - 1)}>
              <ChevronLeft aria-hidden className="size-4.5" />
            </Button>
            <Button variant="secondary" size="sm" aria-label={v.next} title={v.next} disabled={ply === sans.length} onClick={() => go(ply + 1)}>
              <ChevronRight aria-hidden className="size-4.5" />
            </Button>
            <Button variant="secondary" size="sm" aria-label={v.end} title={v.end} disabled={ply === sans.length} onClick={() => go(sans.length)}>
              <ChevronLast aria-hidden className="size-4.5" />
            </Button>
          </div>
          <Button variant="ghost" size="sm" aria-label={t.moves.flip} title={t.moves.flip} onClick={() => setFlipped((f) => !f)}>
            <FlipVertical2 aria-hidden className="size-4.5" />
          </Button>
        </div>
        {evals && review && (
          <EvalGraph
            wins={evals.map((e) => winPercent(e.score))}
            marks={review.moves.filter((m) => m.judgement).map((m) => ({ ply: m.ply, judgement: m.judgement! }))}
            ply={ply}
            label={t.analysis.graph}
            onSelect={go}
          />
        )}
      </div>

      <div className="flex min-w-0 flex-col gap-4">
        <div>
          <p data-testid="viewer-status" className="font-medium" aria-live="polite">
            {ply === 0 ? v.startPosition : v.afterMove(label(sans, ply))}
            {review && ply > 0 && review.moves[ply - 1].judgement && glyph[review.moves[ply - 1].judgement!]}
          </p>
          {review && evals && <MoveComment review={review} evals={evals} sans={sans} ply={ply} />}
          <p className="hidden text-[0.8125rem] text-faint sm:block">{v.keyboard}</p>
        </div>

        <ol
          ref={listRef}
          aria-label={t.moves.list}
          className="relative grid max-h-56 grid-cols-[auto_1fr_1fr] content-start gap-x-2 gap-y-0.5 overflow-y-auto font-mono text-sm tabular-nums"
        >
          {rows.map((r) => (
            <li key={r.n} className="col-span-3 grid grid-cols-subgrid items-center">
              <span className="pr-1 text-faint">{r.n}.</span>
              {moveButton(r.white)}
              {r.black ? moveButton(r.black) : <span />}
            </li>
          ))}
        </ol>

        <table aria-label={v.statsTitle} className="w-full text-sm">
          <caption className="mb-2 text-left text-[0.8125rem] text-muted">
            {v.length(stats.moves)}
            {stats.end.kind === 'checkmate' && `. ${v.endedMate}`}
            {stats.end.kind === 'stalemate' && `. ${v.endedStalemate}`}
          </caption>
          <thead>
            <tr className="text-[0.8125rem] text-muted">
              <th scope="col" className="pb-1 text-left font-normal">
                <span className="sr-only">{v.statsTitle}</span>
              </th>
              <th scope="col" className="pb-1 text-right font-normal">
                {whiteName}
              </th>
              <th scope="col" className="pb-1 text-right font-normal">
                {blackName}
              </th>
            </tr>
          </thead>
          <tbody>
            {statRows.map(([name, w, b]) => (
              <tr key={name} className="border-t border-line">
                <th scope="row" className="py-1.5 text-left font-normal text-muted">
                  {name}
                </th>
                <td className="py-1.5 text-right tabular-nums">{w}</td>
                <td className="py-1.5 text-right tabular-nums">{b}</td>
              </tr>
            ))}
          </tbody>
        </table>

        <AnalysisSection state={analysis.state} review={review} whiteName={whiteName} blackName={blackName} onStart={analysis.start} onCancel={analysis.cancel} />
      </div>
    </div>
  )
}

/** What the engine thinks of the move just played, and what it would play now. */
function MoveComment({ review, evals, sans, ply }: { review: GameReview; evals: PositionEval[]; sans: string[]; ply: number }) {
  const { t, f } = useI18n()
  const a = t.analysis
  const parts: string[] = []
  if (ply > 0) {
    const m = review.moves[ply - 1]
    if (m.judgement) {
      const detail =
        m.allowedMate !== null
          ? a.allowedMate(m.allowedMate)
          : m.missedMate !== null
            ? a.missedMate(m.missedMate)
            : m.pawnsLost !== null && m.pawnsLost >= 0.1
              ? a.cost(f.decimal1(m.pawnsLost), Math.round(m.pawnsLost * 10) === 10)
              : null
      parts.push(`${a.judgement[m.judgement]}${detail ? `: ${detail}` : ''}.`)
    }
    if (m.bestSan) {
      if (m.judgement) parts.push(a.better(numbered(ply, m.bestSan)))
    } else {
      parts.push(a.best)
    }
  }
  const score = evals[ply].score
  const next = evals[ply].best
  const nextSan = next ? uciToSan(positionAfter(sans, ply), next) : null
  return (
    <div data-testid="move-comment" className="mt-1 text-[0.9375rem]">
      {parts.length > 0 && (
        <p className={cn(ply > 0 && review.moves[ply - 1].judgement === 'blunder' && 'text-loss')}>
          {ply > 0 && review.moves[ply - 1].judgement && <span className="font-medium">{label(sans, ply)}{glyph[review.moves[ply - 1].judgement!]} </span>}
          {parts.join(' ')}
        </p>
      )}
      <p className="text-[0.8125rem] text-muted">
        {a.evaluation(scoreText(score, f.decimal1))}
        {nextSan && `. ${a.bestHere(numbered(ply + 1, nextSan))}`}
      </p>
    </div>
  )
}

/** The button to run the engine, its progress, and the result per player. */
function AnalysisSection({
  state,
  review,
  whiteName,
  blackName,
  onStart,
  onCancel,
}: {
  state: AnalysisState
  review: GameReview | null
  whiteName: string
  blackName: string
  onStart: () => void
  onCancel: () => void
}) {
  const { t, f } = useI18n()
  const a = t.analysis

  if (review) {
    const rows: [string, string | number, string | number][] = [
      [a.accuracy, f.pct(review.white.accuracy / 100), f.pct(review.black.accuracy / 100)],
      [a.blunders, review.white.blunders, review.black.blunders],
      [a.mistakes, review.white.mistakes, review.black.mistakes],
      [a.inaccuracies, review.white.inaccuracies, review.black.inaccuracies],
    ]
    return (
      <section aria-label={a.title} className="border-t border-line pt-4">
        <table aria-label={a.title} className="w-full text-sm">
          <caption className="mb-2 text-left text-[0.8125rem] text-muted">
            <Cpu aria-hidden className="mr-1.5 inline size-3.5 align-[-0.15em]" />
            {a.title}
          </caption>
          <thead>
            <tr className="text-[0.8125rem] text-muted">
              <th scope="col" className="pb-1 text-left font-normal">
                <span className="sr-only">{a.title}</span>
              </th>
              <th scope="col" className="pb-1 text-right font-normal">
                {whiteName}
              </th>
              <th scope="col" className="pb-1 text-right font-normal">
                {blackName}
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map(([name, w, b], i) => (
              <tr key={name} className="border-t border-line">
                <th scope="row" className="py-1.5 text-left font-normal text-muted">
                  {name}
                </th>
                <td className={cn('py-1.5 text-right tabular-nums', i === 0 && 'font-display text-base font-semibold')}>{w}</td>
                <td className={cn('py-1.5 text-right tabular-nums', i === 0 && 'font-display text-base font-semibold')}>{b}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <p className="mt-2 text-[0.75rem] text-faint">
          {a.analysed}. {a.credit},{' '}
          <a href="https://github.com/official-stockfish/Stockfish" target="_blank" rel="noreferrer" className="underline hover:text-accent">
            {a.source}
          </a>
          .
        </p>
      </section>
    )
  }

  return (
    <section aria-label={a.title} className="flex flex-col gap-2 border-t border-line pt-4">
      {state.status === 'running' || state.status === 'loading' ? (
        <>
          <p className="text-sm font-medium" aria-live="polite">
            {state.status === 'loading' ? a.loading : a.progress(state.done + 1, state.total)}
          </p>
          <div
            role="progressbar"
            aria-label={a.title}
            aria-valuemin={0}
            aria-valuemax={state.status === 'running' ? state.total : 1}
            aria-valuenow={state.status === 'running' ? state.done : 0}
            className="h-1.5 overflow-hidden rounded-full bg-raised"
          >
            <div
              className="h-full rounded-full bg-accent transition-[width] duration-200"
              style={{ width: state.status === 'running' ? `${(state.done / state.total) * 100}%` : '4%' }}
            />
          </div>
          <Button variant="ghost" size="sm" className="self-start" onClick={onCancel}>
            {a.cancel}
          </Button>
        </>
      ) : (
        <>
          {state.status === 'error' && <p className="text-sm text-loss">{a.failed}</p>}
          <Button variant="secondary" size="sm" className="self-start" onClick={onStart}>
            <Cpu aria-hidden className="size-4" />
            {state.status === 'error' ? a.retry : a.run}
          </Button>
          <p className="text-[0.8125rem] text-muted">{a.intro}</p>
        </>
      )}
    </section>
  )
}
