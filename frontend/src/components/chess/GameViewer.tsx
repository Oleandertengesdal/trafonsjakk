// Replays a game on a board: step through with buttons, arrow keys or by tapping a move, plus a few
// numbers about the game. Loaded lazily (chess.js and the board), like the move recorder.
import { useMemo, useRef, useState, useEffect, type KeyboardEvent } from 'react'
import { Chessboard } from 'react-chessboard'
import { ChevronFirst, ChevronLast, ChevronLeft, ChevronRight, FlipVertical2 } from 'lucide-react'
import { moveSquares, moveStats, parseMoves, positionAfter } from '../../chess/moves'
import { useI18n } from '../../i18n/I18nProvider'
import { cn } from '../../lib/cn'
import { Button } from '../ui/Button'

export interface GameViewerProps {
  moves: string
  orientation: 'white' | 'black'
  whiteName: string
  blackName: string
}

/** "12. Nf3" for White's move, "12... Nf6" for Black's. */
function label(sans: string[], ply: number) {
  const n = Math.floor((ply - 1) / 2) + 1
  return ply % 2 === 1 ? `${n}. ${sans[ply - 1]}` : `${n}... ${sans[ply - 1]}`
}

export default function GameViewer({ moves, orientation, whiteName, blackName }: GameViewerProps) {
  const { t } = useI18n()
  const v = t.viewer
  const parsed = useMemo(() => parseMoves(moves), [moves])
  const sans = parsed.sans
  const squares = useMemo(() => moveSquares(sans), [sans])
  const stats = useMemo(() => moveStats(sans), [sans])
  const [ply, setPly] = useState(sans.length) // opens at the final position
  const [flipped, setFlipped] = useState(false)
  const listRef = useRef<HTMLOListElement>(null)

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

  return (
    <div
      role="group"
      aria-label={v.title}
      tabIndex={0}
      onKeyDown={onKeyDown}
      className="grid gap-5 rounded-control focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-accent sm:grid-cols-[minmax(0,24rem)_1fr]"
    >
      <div className="flex flex-col gap-3">
        <div className="overflow-hidden rounded-control border border-line">
          <Chessboard
            options={{
              id: 'game-viewer',
              position: positionAfter(sans, ply),
              boardOrientation: (orientation === 'white') !== flipped ? 'white' : 'black',
              allowDragging: false,
              animationDurationInMs: 150,
              lightSquareStyle: { backgroundColor: '#f3e6d0' },
              darkSquareStyle: { backgroundColor: '#b86a58' },
              squareStyles: last ? { [last.from]: highlight, [last.to]: highlight } : {},
            }}
          />
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
      </div>

      <div className="flex min-w-0 flex-col gap-4">
        <div>
          <p data-testid="viewer-status" className="font-medium" aria-live="polite">
            {ply === 0 ? v.startPosition : v.afterMove(label(sans, ply))}
          </p>
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
      </div>
    </div>
  )
}
