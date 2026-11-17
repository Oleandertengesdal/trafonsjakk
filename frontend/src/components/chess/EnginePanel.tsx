// What Stockfish says about the position on the board: the evaluation, how deep it has looked, and its
// three best continuations (tap one to play it, when the board allows your own moves). While the game is
// being reviewed it shows the progress instead.
import { memo } from 'react'
import { Cpu } from 'lucide-react'
import { numberedLine, pvToSan, scoreText, winPercent, type EngineLine } from '../../chess/analysis'
import { ENGINE_NAME, LIVE_DEPTH } from '../../chess/engine'
import type { StockfishState } from '../../chess/useStockfish'
import { useI18n } from '../../i18n/I18nProvider'
import { cn } from '../../lib/cn'
import { Button } from '../ui/Button'

export function EnginePanel({
  state,
  finished,
  ply,
  fen,
  onPlay,
}: {
  state: StockfishState
  /** The position on the board is mate or a draw: nothing to think about. */
  finished: boolean
  /** Moves played to reach the position, to number the lines ("14... Nf6"). */
  ply: number
  fen: string
  onPlay?: (uci: string) => void
}) {
  const { t } = useI18n()
  const e = t.engine
  if (state.status === 'off') return null

  let body
  if (state.status === 'error') body = <p className="text-[0.9375rem] text-muted">{t.analysis.failed}</p>
  else if (state.status === 'loading') body = <Progress label={e.starting} />
  else if (state.status === 'reviewing') body = <Progress label={e.reviewing(state.done + 1, state.total)} value={state.done / state.total} />
  else if (finished) body = <p className="text-[0.9375rem] text-muted">{e.gameOver}</p>
  else
    body = (
      <ol aria-label={e.lines} aria-busy={state.lines.length === 0} className="flex flex-col">
        {[1, 2, 3].map((rank) => {
          const line = state.lines.find((l) => l.rank === rank)
          return line ? (
            <LineRow key={rank} line={line} fen={fen} ply={ply} onPlay={onPlay} />
          ) : (
            // Keeps its place, so the panel doesn't jump while the engine starts
            <li key={rank} aria-hidden className="flex h-9 items-center gap-3">
              <span className="h-6 w-13 shrink-0 animate-pulse rounded-[7px] bg-raised motion-reduce:animate-none" />
              <span className="h-3 flex-1 animate-pulse rounded bg-raised motion-reduce:animate-none" />
            </li>
          )
        })}
      </ol>
    )

  const depth = state.status === 'ready' && !finished ? state.depth : 0
  return (
    <section aria-label={e.title} className="rounded-control bg-page px-3 py-2.5">
      {body}
      <p className="mt-1.5 flex justify-between gap-3 text-[0.75rem] text-faint">
        <span>{ENGINE_NAME}</span>
        {state.status === 'ready' && !finished && <span className="tabular-nums">{depth > 0 ? e.depth(depth, LIVE_DEPTH) : e.thinking}</span>}
      </p>
    </section>
  )
}

function Progress({ label, value }: { label: string; value?: number }) {
  return (
    <div className="flex flex-col gap-2 py-1">
      <p className="text-sm text-muted" aria-live="polite">
        {label}
      </p>
      <div
        role="progressbar"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={value === undefined ? undefined : Math.round(value * 100)}
        className="h-1 overflow-hidden rounded-full bg-raised"
      >
        <div
          className={cn('h-full rounded-full bg-accent transition-[width] duration-300', value === undefined && 'w-1/4 animate-pulse motion-reduce:animate-none')}
          style={value === undefined ? undefined : { width: `${Math.max(4, value * 100)}%` }}
        />
      </div>
    </div>
  )
}

const LineRow = memo(function LineRow({ line, fen, ply, onPlay }: { line: EngineLine; fen: string; ply: number; onPlay?: (uci: string) => void }) {
  const { t, f } = useI18n()
  const moves = numberedLine(pvToSan(fen, line.pv, 8), ply)
  const score = scoreText(line.score, f.decimal1)
  const content = (
    <>
      <span
        className={cn(
          'w-13 shrink-0 rounded-[7px] py-0.5 text-center font-display text-sm font-semibold tabular-nums',
          winPercent(line.score) >= 50 ? 'bg-[#f3e6d0] text-[#2e333a] ring-1 ring-inset ring-[#c9b78f]' : 'bg-[#2e333a] text-[#f3e6d0]',
        )}
      >
        {score}
      </span>
      <span className={cn('min-w-0 truncate font-mono text-sm', line.rank > 1 && 'text-muted')}>
        <strong className={cn('font-semibold', line.rank === 1 ? 'text-ink' : 'text-muted')}>{moves.first}</strong> {moves.rest}
      </span>
    </>
  )
  return (
    <li>
      {onPlay ? (
        <button
          type="button"
          onClick={() => onPlay(line.pv[0])}
          title={t.engine.play(moves.first)}
          aria-label={`${score} ${moves.first} ${moves.rest}. ${t.engine.play(moves.first)}`}
          className="-mx-1.5 flex h-9 w-[calc(100%+0.75rem)] items-center gap-3 rounded-[8px] px-1.5 text-left transition-colors hover:bg-raised"
        >
          {content}
        </button>
      ) : (
        <div className="flex h-9 items-center gap-3">{content}</div>
      )}
    </li>
  )
})

/** The one switch for everything Stockfish: review, live lines, your own moves. */
export function AnalyseButton({ on, onClick }: { on: boolean; onClick: () => void }) {
  const e = useI18n().t.engine
  return (
    <Button variant={on ? 'primary' : 'secondary'} size="sm" aria-pressed={on} title={e.toggleHint} onClick={onClick}>
      <Cpu aria-hidden className="size-4" />
      {e.toggle}
    </Button>
  )
}
