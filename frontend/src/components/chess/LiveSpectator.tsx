// Watching a live game: the board follows the game as it's played, you can step back through the moves
// (and return to the live position), and with "Analyse" on, Stockfish thinks about whatever position is on
// the board. Loaded lazily (chess.js, the board and the engine aren't needed elsewhere on the page).
import { useCallback, useMemo, useState, type KeyboardEvent } from 'react'
import { ChevronFirst, ChevronLeft, ChevronRight, FlipVertical2, Radio } from 'lucide-react'
import type { LiveGame } from '../../api/types'
import { numberedLine, positions, terminalScore } from '../../chess/analysis'
import { moveSquares, parseMoves } from '../../chess/moves'
import { ARROW_DEPTH, useAnalysisMode, useStockfish } from '../../chess/useStockfish'
import { useI18n } from '../../i18n/I18nProvider'
import { Button } from '../ui/Button'
import { Panel } from '../ui/Panel'
import { AnalysisBoard } from './AnalysisBoard'
import { AnalyseButton, EnginePanel } from './EnginePanel'
import { MoveList } from './MoveList'

export default function LiveSpectator({ game }: { game: LiveGame }) {
  const { t } = useI18n()
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
  const [analysing, toggleAnalysis] = useAnalysisMode()
  const here = fens[ply]
  const finished = here ? terminalScore(here) : null
  const engine = useStockfish({ enabled: analysing, fen: finished ? null : (here?.fen ?? null) })

  const go = useCallback((p: number) => setPinned(p >= sans.length ? null : Math.max(0, p)), [sans.length])

  function onKeyDown(e: KeyboardEvent) {
    const next = { ArrowLeft: ply - 1, ArrowRight: ply + 1, Home: 0, End: sans.length }[e.key]
    if (next !== undefined) {
      e.preventDefault()
      go(next)
    }
  }

  const top = engine.state.status === 'ready' ? engine.state.lines[0] : undefined
  const last = ply > 0 ? squares[ply - 1].from + squares[ply - 1].to : null

  return (
    <div
      role="group"
      aria-label={v.title}
      tabIndex={0}
      onKeyDown={onKeyDown}
      className="grid gap-6 rounded-control focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-accent lg:grid-cols-[minmax(0,34rem)_1fr]"
    >
      <div className="flex min-w-0 flex-col gap-3">
        <AnalysisBoard
          id="live-spectator"
          fen={here?.fen ?? 'start'}
          orientation={flipped ? 'black' : 'white'}
          lastMove={last}
          best={analysing && top && top.depth >= ARROW_DEPTH ? top.pv[0] : null}
          score={analysing ? (finished ?? top?.score ?? null) : null}
        />
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
          <div className="flex gap-1">
            <AnalyseButton on={analysing} onClick={toggleAnalysis} />
            <Button variant="ghost" size="sm" aria-label={t.moves.flip} title={t.moves.flip} onClick={() => setFlipped((x) => !x)}>
              <FlipVertical2 aria-hidden className="size-4.5" />
            </Button>
          </div>
        </div>
      </div>

      <div className="flex min-w-0 flex-col gap-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p data-testid="spectator-status" aria-live="polite" className="font-medium">
            {ply === 0 ? v.startPosition : v.afterMove(numberedLine([sans[ply - 1]], ply - 1).first)}
          </p>
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
        </div>

        {analysing ? (
          <EnginePanel state={engine.state} finished={finished !== null} ply={ply} fen={here?.fen ?? ''} />
        ) : (
          <p className="text-[0.875rem] text-muted">{l.analysisFairPlay}</p>
        )}

        <Panel title={l.moveCount(Math.ceil(sans.length / 2))}>
          <div className="flex flex-col gap-3 px-5 pt-1 pb-5">
            {sans.length > 0 && <MoveList sans={sans} ply={ply} review={null} onSelect={go} />}
            <p className="text-sm text-faint">{l.following}</p>
          </div>
        </Panel>
      </div>
    </div>
  )
}
