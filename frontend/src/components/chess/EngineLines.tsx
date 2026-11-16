// The live Stockfish pieces shared by watching a live game and going through a finished one: the on/off
// switch, and the engine's best lines for the position on the board.
import type { ReactNode } from 'react'
import { numberedLine, pvToSan, scoreText, winPercent, type EngineLine } from '../../chess/analysis'
import { ENGINE_NAME, LIVE_DEPTH } from '../../chess/engine'
import type { LiveAnalysis } from '../../chess/useLiveAnalysis'
import { useI18n } from '../../i18n/I18nProvider'
import { cn } from '../../lib/cn'

export function EngineSwitch({ checked, onChange }: { checked: boolean; onChange: () => void }) {
  const e = useI18n().t.engine
  return (
    <label className="inline-flex cursor-pointer items-center gap-2 text-sm font-medium">
      <input type="checkbox" role="switch" className="peer sr-only" checked={checked} onChange={onChange} />
      <span className="text-muted peer-checked:text-ink">{e.switch}</span>
      <span
        aria-hidden
        className="relative h-6 w-10 rounded-full bg-line transition-colors peer-checked:bg-brand peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-accent after:absolute after:top-0.5 after:left-0.5 after:size-5 after:rounded-full after:bg-surface after:shadow-sm after:transition-transform peer-checked:after:translate-x-4"
      />
    </label>
  )
}

/**
 * What the engine says about the position: its best lines (scores from White's side) and how deep it has
 * looked. `intro` is shown while it's switched off. With `onPlay`, tapping a line plays its first move.
 */
export function EngineLines({
  analysis,
  finished,
  ply,
  fen,
  intro,
  onPlay,
}: {
  analysis: LiveAnalysis
  finished: boolean
  ply: number
  fen: string
  intro: ReactNode
  onPlay?: (uci: string) => void
}) {
  const { t } = useI18n()
  const e = t.engine
  if (analysis.status === 'off') return <>{intro}</>
  if (analysis.status === 'error') return <p className="text-[0.9375rem] text-muted">{t.analysis.failed}</p>
  if (finished) return <p className="text-[0.9375rem] text-muted">{e.gameOver}</p>
  if (analysis.status === 'loading') return <p className="text-[0.9375rem] text-muted">{t.analysis.loading}…</p>
  return (
    <div className="flex flex-col gap-3">
      {analysis.lines.length === 0 ? (
        <p className="text-[0.9375rem] text-muted">{e.thinking}</p>
      ) : (
        <ol aria-label={e.lines} className="flex flex-col divide-y divide-line">
          {analysis.lines.map((line) => (
            <EngineLineRow key={line.rank} line={line} fen={fen} ply={ply} onPlay={onPlay} />
          ))}
        </ol>
      )}
      <p className="flex flex-wrap justify-between gap-x-4 gap-y-1 text-[0.8125rem] text-faint">
        <span>{analysis.depth > 0 ? e.depth(analysis.depth, LIVE_DEPTH) : e.thinking}</span>
        <span>{ENGINE_NAME}</span>
      </p>
    </div>
  )
}

function EngineLineRow({ line, fen, ply, onPlay }: { line: EngineLine; fen: string; ply: number; onPlay?: (uci: string) => void }) {
  const { t, f } = useI18n()
  const moves = numberedLine(pvToSan(fen, line.pv, 8), ply)
  const score = (
    <span
      className={cn(
        'w-14 shrink-0 rounded-[7px] px-1.5 py-0.5 text-center font-display text-sm font-semibold tabular-nums',
        winPercent(line.score) >= 50 ? 'bg-[#f3e6d0] text-[#2e333a]' : 'bg-[#2e333a] text-[#f3e6d0]',
      )}
    >
      {scoreText(line.score, f.decimal1)}
    </span>
  )
  const text = (
    <span className="min-w-0 font-mono text-sm leading-relaxed">
      <strong className="font-semibold">{moves.first}</strong> <span className="text-muted">{moves.rest}</span>
    </span>
  )
  return (
    <li className="py-2 first:pt-0">
      {onPlay ? (
        <button
          type="button"
          onClick={() => onPlay(line.pv[0])}
          aria-label={`${scoreText(line.score, f.decimal1)} ${moves.first} ${moves.rest}. ${t.engine.play(moves.first)}`}
          className="-mx-1.5 flex w-[calc(100%+0.75rem)] items-baseline gap-3 rounded-[8px] px-1.5 py-1 text-left transition-colors hover:bg-raised"
        >
          {score}
          {text}
        </button>
      ) : (
        <div className="flex items-baseline gap-3">
          {score}
          {text}
        </div>
      )}
    </li>
  )
}
