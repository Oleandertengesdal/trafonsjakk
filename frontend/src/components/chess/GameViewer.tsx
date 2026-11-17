// Replays a game on a board: step through with buttons, arrow keys or by tapping a move. With "Analyse"
// on, one Stockfish in the browser first reviews the game (marks, graph, accuracy) and then thinks about
// the position on the board, and you can try your own moves. Loaded lazily (chess.js and the board).
import { useCallback, useMemo, useState, type KeyboardEvent } from 'react'
import { Chess, type Square } from 'chess.js'
import { ChevronFirst, ChevronLast, ChevronLeft, ChevronRight, FlipVertical2, X } from 'lucide-react'
import {
  glyph,
  numberedLine,
  playFrom,
  positions,
  reviewGame,
  terminalScore,
  winPercent,
  type GameReview,
} from '../../chess/analysis'
import { moveSquares, moveStats, parseMoves } from '../../chess/moves'
import { ARROW_DEPTH, useAnalysisMode, useStockfish } from '../../chess/useStockfish'
import { useI18n } from '../../i18n/I18nProvider'
import { cn } from '../../lib/cn'
import { Button } from '../ui/Button'
import { AnalysisBoard } from './AnalysisBoard'
import { AnalyseButton, EnginePanel } from './EnginePanel'
import { EvalGraph } from './EvalGraph'
import { glyphColour } from './judgement'
import { MoveList } from './MoveList'

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

type Line = { from: number; moves: { san: string; uci: string }[] }

export default function GameViewer({ moves, orientation, whiteName, blackName }: GameViewerProps) {
  const { t } = useI18n()
  const v = t.viewer
  const e = t.engine
  const parsed = useMemo(() => parseMoves(moves), [moves])
  const sans = parsed.sans
  const squares = useMemo(() => moveSquares(sans), [sans])
  const gamePositions = useMemo(() => (parsed.ok ? positions(sans) : []), [parsed.ok, sans])
  const [ply, setPly] = useState(sans.length) // opens at the final position
  const [flipped, setFlipped] = useState(false)

  // Your own moves to try ("what if…?"): a line branching off the game after `from` moves
  const [line, setLine] = useState<Line | null>(null)
  const [selected, setSelected] = useState<string | null>(null)
  const shown = useMemo(
    () => (line ? playFrom(gamePositions[line.from].fen, line.moves.map((m) => m.san)) : gamePositions[ply]),
    [line, gamePositions, ply],
  )
  const finished = shown ? terminalScore(shown) : null
  const shownPly = line ? line.from + line.moves.length : ply

  const [analysing, toggleAnalysis] = useAnalysisMode()
  const on = analysing && parsed.ok
  const engine = useStockfish({ enabled: on, fen: shown && !finished ? shown.fen : null, review: parsed.ok ? sans : undefined })
  const evals = on ? engine.evals : null
  const review = useMemo(() => (evals && parsed.ok ? reviewGame(sans, evals) : null), [evals, sans, parsed.ok])

  const go = useCallback(
    (p: number) => {
      setSelected(null)
      setLine(null)
      setPly(Math.max(0, Math.min(sans.length, p)))
    },
    [sans.length],
  )

  /** A move made on the board: the game's own next move steps forward; anything else starts or extends your line. */
  const tryMove = useCallback(
    (from: string, to: string, promotion = 'q'): boolean => {
      if (!shown) return false
      let move
      try {
        move = new Chess(shown.fen).move({ from, to, promotion })
      } catch {
        return false
      }
      setSelected(null)
      if (!line && sans[ply] === move.san) {
        setPly(ply + 1)
        return true
      }
      const played = { san: move.san, uci: move.from + move.to }
      setLine((l) => ({ from: l?.from ?? ply, moves: [...(l?.moves ?? []), played] }))
      return true
    },
    [shown, line, sans, ply],
  )

  const onSquare = useCallback(
    (square: string) => {
      if (!shown) return
      if (selected && selected !== square && tryMove(selected, square)) return
      const piece = new Chess(shown.fen).get(square as Square)
      setSelected(piece && piece.color === shown.toMove && square !== selected ? square : null)
    },
    [shown, selected, tryMove],
  )

  const play = useCallback((uci: string) => tryMove(uci.slice(0, 2), uci.slice(2, 4), uci[4] ?? 'q'), [tryMove])
  const graph = useMemo(
    () =>
      evals && review
        ? {
            wins: evals.map((ev) => winPercent(ev.score)),
            marks: review.moves.filter((m) => m.judgement).map((m) => ({ ply: m.ply, judgement: m.judgement! })),
          }
        : null,
    [evals, review],
  )

  /** One move back in your own line; past its first move you're back in the game. */
  function back() {
    setSelected(null)
    setLine((l) => (l && l.moves.length > 1 ? { ...l, moves: l.moves.slice(0, -1) } : null))
  }

  function onKeyDown(ev: KeyboardEvent) {
    if (line && (ev.key === 'ArrowLeft' || ev.key === 'ArrowUp')) {
      ev.preventDefault()
      back()
      return
    }
    if (line && (ev.key === 'ArrowRight' || ev.key === 'ArrowDown')) return
    const next = { ArrowLeft: ply - 1, ArrowRight: ply + 1, Home: 0, End: sans.length, ArrowUp: ply - 1, ArrowDown: ply + 1 }[ev.key]
    if (next !== undefined) {
      ev.preventDefault()
      go(next)
    }
  }

  if (!parsed.ok) {
    // Saved as text that isn't a legal game (older games, typos): show it as written
    return <p className="max-h-64 overflow-auto rounded-control bg-page px-3 py-2.5 text-[0.875rem] leading-relaxed whitespace-pre-wrap">{moves}</p>
  }

  const whiteAtBottom = (orientation === 'white') !== flipped
  const lastMove = line ? line.moves[line.moves.length - 1].uci : ply > 0 ? squares[ply - 1].from + squares[ply - 1].to : null
  // The live engine has the say once it's thinking; until then (and off the board's own line) the review
  const top = engine.state.status === 'ready' ? engine.state.lines[0] : undefined
  const reviewed = !line && evals ? evals[ply] : undefined
  const score = on ? (finished ?? top?.score ?? reviewed?.score ?? null) : null
  const best = on ? ((top && top.depth >= ARROW_DEPTH ? top.pv[0] : null) ?? reviewed?.best ?? null) : null
  const judged = review && !line && ply > 0 ? review.moves[ply - 1].judgement : null

  return (
    <div
      role="group"
      aria-label={v.title}
      tabIndex={0}
      onKeyDown={onKeyDown}
      className="grid gap-5 rounded-control focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-accent sm:grid-cols-[minmax(0,26rem)_1fr]"
    >
      <div className="flex min-w-0 flex-col gap-3">
        <AnalysisBoard
          id="game-viewer"
          fen={shown?.fen ?? 'start'}
          orientation={whiteAtBottom ? 'white' : 'black'}
          lastMove={lastMove}
          best={best}
          score={score}
          interactive={on}
          selected={selected}
          onMove={tryMove}
          onSquare={onSquare}
        />
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex gap-1">
            <Button variant="secondary" size="sm" aria-label={v.start} title={v.start} disabled={!line && ply === 0} onClick={() => go(0)}>
              <ChevronFirst aria-hidden className="size-4.5" />
            </Button>
            <Button variant="secondary" size="sm" aria-label={v.prev} title={v.prev} disabled={!line && ply === 0} onClick={() => (line ? back() : go(ply - 1))}>
              <ChevronLeft aria-hidden className="size-4.5" />
            </Button>
            <Button variant="secondary" size="sm" aria-label={v.next} title={v.next} disabled={!!line || ply === sans.length} onClick={() => go(ply + 1)}>
              <ChevronRight aria-hidden className="size-4.5" />
            </Button>
            <Button variant="secondary" size="sm" aria-label={v.end} title={v.end} disabled={!line && ply === sans.length} onClick={() => go(sans.length)}>
              <ChevronLast aria-hidden className="size-4.5" />
            </Button>
          </div>
          <div className="flex gap-1">
            <AnalyseButton on={analysing} onClick={toggleAnalysis} />
            <Button variant="ghost" size="sm" aria-label={t.moves.flip} title={t.moves.flip} onClick={() => setFlipped((x) => !x)}>
              <FlipVertical2 aria-hidden className="size-4.5" />
            </Button>
          </div>
        </div>
        {graph && (
          <EvalGraph
            wins={graph.wins}
            marks={graph.marks}
            ply={line ? line.from : ply}
            label={t.analysis.graph}
            onSelect={go}
          />
        )}
      </div>

      <div className="flex min-w-0 flex-col gap-3">
        {line ? (
          <div data-testid="viewer-line" aria-live="polite" className="flex items-start justify-between gap-3 rounded-control bg-brand/8 px-3 py-2">
            <div className="min-w-0">
              <p className="text-[0.8125rem] text-muted">{line.from === 0 ? e.variationStart : e.variation(label(sans, line.from))}</p>
              <p className="font-mono text-sm font-medium">{lineText(line)}</p>
            </div>
            <button
              type="button"
              onClick={() => go(line.from)}
              aria-label={e.backToGame}
              title={e.backToGame}
              className="-mr-1 shrink-0 rounded-[8px] p-1.5 text-muted transition-colors hover:bg-raised hover:text-ink"
            >
              <X aria-hidden className="size-4.5" />
            </button>
          </div>
        ) : (
          <div aria-live="polite">
            <p data-testid="viewer-status" className="font-medium">
              {ply === 0 ? v.startPosition : v.afterMove(label(sans, ply))}
              {judged && <span className={glyphColour[judged]}>{glyph[judged]}</span>}
            </p>
            {review && ply > 0 && <MoveComment review={review} ply={ply} />}
          </div>
        )}

        <EnginePanel
          state={engine.state}
          finished={finished !== null}
          ply={shownPly}
          fen={shown?.fen ?? ''}
          onPlay={play}
        />

        <MoveList sans={sans} ply={line ? line.from : ply} review={review} onSelect={go} />

        {on && review ? <Accuracy review={review} whiteName={whiteName} blackName={blackName} /> : !on && <GameNumbers sans={sans} whiteName={whiteName} blackName={blackName} />}
        <p className="hidden text-[0.75rem] text-faint sm:block">{on ? e.tryMoves : v.keyboard}</p>
      </div>
    </div>
  )
}

function lineText(line: Line) {
  const text = numberedLine(
    line.moves.map((m) => m.san),
    line.from,
  )
  return `${text.first} ${text.rest}`.trim()
}

/** What the review says about the move just played, in one sentence. */
function MoveComment({ review, ply }: { review: GameReview; ply: number }) {
  const { t, f } = useI18n()
  const a = t.analysis
  const m = review.moves[ply - 1]
  if (!m.judgement)
    return m.bestSan ? null : (
      <p data-testid="move-comment" className="text-[0.875rem] text-muted">
        {a.best}
      </p>
    )
  const detail =
    m.allowedMate !== null
      ? a.allowedMate(m.allowedMate)
      : m.missedMate !== null
        ? a.missedMate(m.missedMate)
        : m.pawnsLost !== null && m.pawnsLost >= 0.1
          ? a.cost(f.decimal1(m.pawnsLost), Math.round(m.pawnsLost * 10) === 10)
          : null
  return (
    <p data-testid="move-comment" className={cn('text-[0.875rem]', m.judgement === 'blunder' ? 'text-loss' : 'text-muted')}>
      {a.judgement[m.judgement]}
      {detail && `: ${detail}`}.{m.bestSan && ` ${a.better(numbered(ply, m.bestSan))}`}
    </p>
  )
}

/** Accuracy and marks per player, once the game has been reviewed. */
function Accuracy({ review, whiteName, blackName }: { review: GameReview; whiteName: string; blackName: string }) {
  const { t, f } = useI18n()
  const a = t.analysis
  const rows = [
    { name: whiteName, side: review.white },
    { name: blackName, side: review.black },
  ]
  const marks = (s: GameReview['white']) =>
    (
      [
        ['blunder', s.blunders],
        ['mistake', s.mistakes],
        ['inaccuracy', s.inaccuracies],
      ] as const
    ).filter(([, n]) => n > 0)
  return (
    <section aria-label={a.title} className="border-t border-line pt-3">
      <table className="w-full table-fixed text-sm">
        <caption className="sr-only">{a.title}</caption>
        <colgroup>
          <col />
          <col className="w-16" />
          <col className="w-28" />
        </colgroup>
        <thead className="sr-only">
          <tr>
            <th scope="col">{a.player}</th>
            <th scope="col">{a.accuracy}</th>
            <th scope="col">{a.marks}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.name}>
              <th scope="row" className="truncate py-1 pr-3 text-left font-normal">
                {r.name}
              </th>
              <td className="py-1 pr-3 text-right font-display text-base font-semibold tabular-nums" title={a.accuracy}>
                {f.pct(r.side.accuracy / 100)}
              </td>
              <td className="py-1 text-right text-[0.8125rem] text-muted">
                {marks(r.side).length === 0
                  ? a.clean
                  : marks(r.side).map(([j, n]) => (
                      <span key={j} className="ml-2" title={a.judgement[j]}>
                        {n}
                        <span className={cn('ml-0.5 font-semibold', glyphColour[j])}>{glyph[j]}</span>
                      </span>
                    ))}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="mt-1.5 text-[0.75rem] text-faint">
        {a.accuracy}. {a.credit},{' '}
        <a href="https://github.com/official-stockfish/Stockfish" target="_blank" rel="noreferrer" className="underline hover:text-accent">
          {a.source}
        </a>
        .
      </p>
    </section>
  )
}

/** A few numbers about the game, when not analysing. */
function GameNumbers({ sans, whiteName, blackName }: { sans: string[]; whiteName: string; blackName: string }) {
  const v = useI18n().t.viewer
  const stats = useMemo(() => moveStats(sans), [sans])
  const castled = (c: 'short' | 'long' | null) => (c === 'short' ? v.castledShort : c === 'long' ? v.castledLong : v.castledNone)
  const rows: [string, string | number, string | number][] = [
    [v.captures, stats.white.captures, stats.black.captures],
    [v.checks, stats.white.checks, stats.black.checks],
    [v.castled, castled(stats.white.castled), castled(stats.black.castled)],
  ]
  if (stats.white.promotions + stats.black.promotions > 0) rows.push([v.promotions, stats.white.promotions, stats.black.promotions])
  return (
    <table aria-label={v.statsTitle} className="w-full border-t border-line text-sm">
      <caption className="mt-3 mb-2 text-left text-[0.8125rem] text-muted">
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
        {rows.map(([name, w, b]) => (
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
  )
}
