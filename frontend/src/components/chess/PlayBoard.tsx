// The board for playing an online game: tap or drag your pieces, with the legal squares marked and a
// choice of piece on promotion. The server checks the move again; this only stops obvious mistakes.
// Loaded lazily: it pulls in chess.js and the board, which most pages don't need.
import { Chess, type Square } from 'chess.js'
import { memo, useMemo, useState } from 'react'
import { Chessboard } from 'react-chessboard'
import type { Colour } from '../../api/types'
import { useI18n } from '../../i18n/I18nProvider'
import { Button } from '../ui/Button'

type Promotion = 'q' | 'r' | 'b' | 'n'

export interface PlayBoardProps {
  fen: string
  orientation: Colour
  /** The colour you may move, when it's your turn; null to only watch. */
  movable: Colour | null
  /** "e2e4": highlighted. */
  lastMove: string | null
  /** A legal move was made on the board: "e2e4" / "e7e8q", and the position after it. */
  onMove: (uci: string, fenAfter: string) => void
}

const highlight = { backgroundColor: 'rgb(226 177 74 / 0.5)' }
const selectedStyle = { boxShadow: 'inset 0 0 0 3px rgb(31 41 51 / 0.55)' }

function PlayBoard({ fen, orientation, movable, lastMove, onMove }: PlayBoardProps) {
  const m = useI18n().t.moves
  const chess = useMemo(() => new Chess(fen), [fen])
  const turn: Colour = chess.turn() === 'w' ? 'WHITE' : 'BLACK'
  const canMove = movable !== null && movable === turn && !chess.isGameOver()
  const [selected, setSelected] = useState<{ fen: string; square: Square } | null>(null)
  const [promotion, setPromotion] = useState<{ fen: string; from: Square; to: Square } | null>(null)
  // A selection belongs to the position it was made in
  const sel = selected?.fen === fen ? selected.square : null
  const pendingPromotion = promotion?.fen === fen ? promotion : null

  const targets = useMemo(
    () => (sel ? new Set<string>(chess.moves({ square: sel, verbose: true }).map((mv) => mv.to)) : new Set<string>()),
    [chess, sel],
  )

  function tryMove(from: Square, to: Square, piece?: Promotion): boolean {
    if (!canMove) return false
    const candidates = chess.moves({ square: from, verbose: true }).filter((mv) => mv.to === to)
    if (candidates.length === 0) return false
    if (!piece && candidates.some((mv) => mv.promotion)) {
      setPromotion({ fen, from, to })
      setSelected(null)
      return false
    }
    const move = candidates.find((mv) => !mv.promotion || mv.promotion === piece)
    if (!move) return false
    const after = new Chess(fen)
    after.move({ from, to, promotion: move.promotion })
    setSelected(null)
    setPromotion(null)
    onMove(`${from}${to}${move.promotion ?? ''}`, after.fen())
    return true
  }

  function onSquareClick(square: Square) {
    if (!canMove) return
    setPromotion(null)
    if (sel && targets.has(square)) {
      tryMove(sel, square)
      return
    }
    const piece = chess.get(square)
    setSelected(piece && piece.color === chess.turn() && square !== sel ? { fen, square } : null)
  }

  const squareStyles: Record<string, React.CSSProperties> = {}
  if (lastMove) {
    squareStyles[lastMove.slice(0, 2)] = highlight
    squareStyles[lastMove.slice(2, 4)] = highlight
  }
  if (sel) squareStyles[sel] = { ...squareStyles[sel], ...selectedStyle }

  return (
    <div className="relative">
      <div className="overflow-hidden rounded-control border border-line">
        <Chessboard
          options={{
            id: 'play-board',
            position: fen,
            boardOrientation: orientation === 'WHITE' ? 'white' : 'black',
            allowDragging: canMove,
            allowDrawingArrows: true,
            animationDurationInMs: 150,
            lightSquareStyle: { backgroundColor: '#f3e6d0' },
            darkSquareStyle: { backgroundColor: '#b86a58' },
            canDragPiece: ({ piece }) => canMove && piece.pieceType[0] === chess.turn(),
            onSquareClick: ({ square }) => onSquareClick(square as Square),
            onPieceDrop: ({ sourceSquare, targetSquare }) =>
              targetSquare ? tryMove(sourceSquare as Square, targetSquare as Square) : false,
            // With a squareRenderer the board ignores squareStyles, so the highlights go on here
            squareRenderer: ({ square, children }) => (
              <div
                className="relative size-full"
                style={squareStyles[square]}
                data-last-move={lastMove?.includes(square) || undefined}
                data-legal-target={targets.has(square) || undefined}
              >
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
  )
}

export default memo(PlayBoard)
