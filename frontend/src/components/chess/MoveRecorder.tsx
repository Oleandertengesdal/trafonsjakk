// Records the moves of a game: tap (or drag) on a board, or type/paste them. Every move is checked
// for legality. Loaded lazily: it pulls in chess.js and the board, which most pages don't need.
import { Chess, type Square } from 'chess.js'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Chessboard } from 'react-chessboard'
import { FlipVertical2, RotateCcw, Trash2 } from 'lucide-react'
import type { GameResult } from '../../api/types'
import { finalState, formatMoves, parseMoves } from '../../chess/moves'
import { useI18n } from '../../i18n/I18nProvider'
import { Button } from '../ui/Button'
import { Segmented } from '../ui/Segmented'

type Promotion = 'q' | 'r' | 'b' | 'n'

export interface MoveRecorderProps {
  /** Moves as stored ("1. e4 e5 …"), or null. Free text that isn't moves opens in the text box. */
  value: string | null
  onChange: (moves: string | null) => void
  orientation: 'white' | 'black'
  /** When given, a warning shows if the moves end in mate/stalemate that contradicts it. */
  result?: GameResult
}

function replay(sans: string[]) {
  const chess = new Chess()
  for (const san of sans) chess.move(san)
  return chess
}

export default function MoveRecorder({ value, onChange, orientation, result }: MoveRecorderProps) {
  const { t } = useI18n()
  const m = t.moves
  const initial = useMemo(() => parseMoves(value ?? ''), []) // eslint-disable-line react-hooks/exhaustive-deps
  const [sans, setSans] = useState<string[]>(initial.sans)
  const [mode, setMode] = useState<'board' | 'text'>(initial.ok ? 'board' : 'text')
  const [text, setText] = useState(initial.ok ? formatMoves(initial.sans) : (value ?? ''))
  const [textError, setTextError] = useState<{ move: string; token: string; valid: string[] } | null>(null)
  const [unreadable] = useState(!initial.ok)
  const [selected, setSelected] = useState<Square | null>(null)
  const [pendingPromotion, setPendingPromotion] = useState<{ from: Square; to: Square } | null>(null)
  const [flipped, setFlipped] = useState(false)
  const listRef = useRef<HTMLOListElement>(null)

  // Keep the newest move in view as the game gets long
  useEffect(() => {
    const list = listRef.current
    if (list) list.scrollTop = list.scrollHeight
  }, [sans.length, mode])

  const chess = useMemo(() => replay(sans), [sans])
  const state = useMemo(() => finalState(sans), [sans])
  const targets = useMemo(
    () => (selected ? new Set(chess.moves({ square: selected, verbose: true }).map((mv) => mv.to)) : new Set<string>()),
    [chess, selected],
  )

  function commit(next: string[]) {
    setSans(next)
    setSelected(null)
    setPendingPromotion(null)
    const pgn = formatMoves(next)
    setText(pgn)
    onChange(pgn === '' ? null : pgn)
  }

  /** Tries a move; asks for the piece first if it's a promotion. Returns whether the board changed. */
  function tryMove(from: Square, to: Square, promotion?: Promotion): boolean {
    const candidates = chess.moves({ square: from, verbose: true }).filter((mv) => mv.to === to)
    if (candidates.length === 0) return false
    if (!promotion && candidates.some((mv) => mv.promotion)) {
      setPendingPromotion({ from, to })
      setSelected(null)
      return false
    }
    const move = candidates.find((mv) => !mv.promotion || mv.promotion === promotion)
    if (!move) return false
    commit([...sans, move.san])
    return true
  }

  function onSquareClick(square: Square) {
    if (state.kind !== 'ongoing') return
    setPendingPromotion(null)
    if (selected && targets.has(square)) {
      tryMove(selected, square)
      return
    }
    const piece = chess.get(square)
    setSelected(piece && piece.color === chess.turn() && square !== selected ? square : null)
  }

  function applyText() {
    const parsed = parseMoves(text)
    if (parsed.ok) {
      setTextError(null)
      commit(parsed.sans)
    } else {
      const moveNo = Math.floor(parsed.ply / 2) + 1
      setTextError({ move: `${moveNo}${parsed.ply % 2 === 0 ? '.' : '...'}`, token: parsed.token, valid: parsed.sans })
    }
  }

  const whiteBottom = (orientation === 'white') !== flipped
  const status =
    state.kind === 'ongoing'
      ? state.turn === 'w'
        ? m.whiteToMove
        : m.blackToMove
      : state.kind === 'checkmate'
        ? m.checkmate(state.result === 'WHITE_WIN' ? t.common.white : t.common.black)
        : state.kind === 'stalemate'
          ? m.stalemate
          : m.drawn
  const mismatch = result && state.kind !== 'ongoing' && state.result !== result

  const rows: { n: number; white: string; black: string | null }[] = []
  for (let i = 0; i < sans.length; i += 2) rows.push({ n: i / 2 + 1, white: sans[i], black: sans[i + 1] ?? null })

  return (
    <div className="flex flex-col gap-4">
      <Segmented
        label={m.title}
        hideLabel
        value={mode}
        onChange={(v) => {
          setMode(v)
          setTextError(null)
        }}
        options={[
          { value: 'board', label: m.board },
          { value: 'text', label: m.text },
        ]}
      />

      {mode === 'board' ? (
        <div className="grid gap-4 sm:grid-cols-[minmax(0,22rem)_1fr]">
          <div className="relative">
            <div className="overflow-hidden rounded-control border border-line">
              <Chessboard
                options={{
                  id: 'move-recorder',
                  position: chess.fen(),
                  boardOrientation: whiteBottom ? 'white' : 'black',
                  allowDragging: state.kind === 'ongoing',
                  animationDurationInMs: 120,
                  lightSquareStyle: { backgroundColor: '#f3e6d0' },
                  darkSquareStyle: { backgroundColor: '#b86a58' },
                  squareStyles: selected ? { [selected]: { boxShadow: 'inset 0 0 0 3px rgb(31 41 51 / 0.55)' } } : {},
                  canDragPiece: ({ piece }) => piece.pieceType[0] === chess.turn(),
                  onSquareClick: ({ square }) => onSquareClick(square as Square),
                  onPieceDrop: ({ sourceSquare, targetSquare }) =>
                    targetSquare ? tryMove(sourceSquare as Square, targetSquare as Square) : false,
                  squareRenderer: ({ square, children }) => (
                    <div className="relative size-full" data-legal-target={targets.has(square) || undefined}>
                      {children}
                      {targets.has(square) && (
                        <span aria-hidden className="pointer-events-none absolute top-1/2 left-1/2 size-[28%] -translate-1/2 rounded-full bg-[rgb(31_41_51/0.35)]" />
                      )}
                    </div>
                  ),
                }}
              />
            </div>
            {pendingPromotion && (
              <div
                role="group"
                aria-label={m.promoteTo}
                className="absolute inset-x-3 top-1/2 flex -translate-y-1/2 flex-col gap-2 rounded-control border border-line bg-surface p-3 shadow-lg"
              >
                <p className="text-sm font-medium">{m.promoteTo}</p>
                <div className="grid grid-cols-2 gap-2">
                  {(
                    [
                      ['q', m.queen],
                      ['r', m.rook],
                      ['b', m.bishop],
                      ['n', m.knight],
                    ] as const
                  ).map(([p, label]) => (
                    <Button key={p} variant="secondary" size="sm" onClick={() => tryMove(pendingPromotion.from, pendingPromotion.to, p)}>
                      {label}
                    </Button>
                  ))}
                </div>
              </div>
            )}
          </div>

          <div className="flex min-w-0 flex-col gap-3">
            <p className="text-sm font-medium" aria-live="polite">
              {status}
            </p>
            {sans.length === 0 ? (
              <p className="text-sm text-muted">{m.none}</p>
            ) : (
              <ol ref={listRef} aria-label={m.list} className="grid max-h-64 grid-cols-[auto_1fr_1fr] content-start gap-x-3 gap-y-0.5 overflow-y-auto font-mono text-sm tabular-nums">
                {rows.map((r) => (
                  <li key={r.n} className="col-span-3 grid grid-cols-subgrid">
                    <span className="text-faint">{r.n}.</span>
                    <span>{r.white}</span>
                    <span>{r.black ?? ''}</span>
                  </li>
                ))}
              </ol>
            )}
            <div className="mt-auto flex flex-wrap gap-2">
              <Button variant="secondary" size="sm" onClick={() => commit(sans.slice(0, -1))} disabled={sans.length === 0}>
                <RotateCcw aria-hidden className="size-4" />
                {m.undo}
              </Button>
              <Button variant="secondary" size="sm" onClick={() => setFlipped((f) => !f)}>
                <FlipVertical2 aria-hidden className="size-4" />
                {m.flip}
              </Button>
              <Button variant="ghost" size="sm" onClick={() => commit([])} disabled={sans.length === 0}>
                <Trash2 aria-hidden className="size-4" />
                {m.clear}
              </Button>
            </div>
          </div>
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          {unreadable && <p className="rounded-control bg-accent-soft px-3 py-2 text-sm">{m.unreadable}</p>}
          <label htmlFor="moves-text" className="text-sm font-medium">
            {m.textLabel}
          </label>
          <textarea
            id="moves-text"
            value={text}
            onChange={(e) => {
              setText(e.target.value)
              setTextError(null)
            }}
            rows={5}
            spellCheck={false}
            autoCapitalize="off"
            autoCorrect="off"
            className="rounded-control border border-line bg-surface px-3 py-2 font-mono text-sm"
          />
          <p className="text-sm text-muted">{m.textHint}</p>
          {textError && (
            <div role="alert" className="flex flex-col items-start gap-2 rounded-control border border-accent/40 bg-accent-soft px-3 py-2 text-sm">
              <span>{m.illegal(textError.move, textError.token)}</span>
              {textError.valid.length > 0 && (
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => {
                    setTextError(null)
                    commit(textError.valid)
                  }}
                >
                  {m.keepValid(textError.valid.length)}
                </Button>
              )}
            </div>
          )}
          <Button className="self-start" onClick={applyText}>
            {m.apply}
          </Button>
        </div>
      )}

      {mismatch && (
        <p className="rounded-control bg-accent-soft px-3 py-2 text-sm">
          {m.resultMismatch(status.toLowerCase().replace(/\.$/, ''), t.result[result].toLowerCase())}
        </p>
      )}
    </div>
  )
}
