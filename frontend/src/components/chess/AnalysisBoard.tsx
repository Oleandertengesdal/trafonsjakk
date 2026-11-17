// The board for going through a game, live or finished: the evaluation bar beside it when analysing, the
// last move marked, the engine's move as an arrow. The board itself only redraws when what it shows
// changes, not every time the engine has a new thought.
import { memo, useMemo } from 'react'
import { Chessboard } from 'react-chessboard'
import { scoreText, winPercent, type Score } from '../../chess/analysis'
import { useI18n } from '../../i18n/I18nProvider'
import { EvalBar } from './EvalGraph'

const LIGHT = { backgroundColor: '#f3e6d0' }
const DARK = { backgroundColor: '#b86a58' }
const LAST = { backgroundColor: 'rgb(226 177 74 / 0.5)' }
const PICKED = { boxShadow: 'inset 0 0 0 3px rgb(31 41 51 / 0.55)' }
const ARROW = 'rgb(43 106 75 / 0.85)'

export interface AnalysisBoardProps {
  id: string
  fen: string
  orientation: 'white' | 'black'
  /** The move that led here, as UCI ("e2e4"). */
  lastMove: string | null
  /** The engine's move in this position, as UCI, drawn as an arrow. */
  best: string | null
  /** The evaluation for the bar; no bar without it. */
  score: Score | null
  /** Pieces can be moved (trying your own moves). */
  interactive?: boolean
  selected?: string | null
  onMove?: (from: string, to: string) => boolean
  onSquare?: (square: string) => void
}

export function AnalysisBoard({ score, ...board }: AnalysisBoardProps) {
  const { t, f } = useI18n()
  return (
    <div className="flex gap-2">
      {score && (
        <EvalBar
          white={winPercent(score)}
          whiteAtBottom={board.orientation === 'white'}
          label={t.analysis.evalBar(scoreText(score, f.decimal1))}
        />
      )}
      <div className="min-w-0 flex-1 overflow-hidden rounded-control border border-line">
        <Board {...board} />
      </div>
    </div>
  )
}

const Board = memo(function Board({ id, fen, orientation, lastMove, best, interactive = false, selected = null, onMove, onSquare }: Omit<AnalysisBoardProps, 'score'>) {
  const arrows = useMemo(() => (best ? [{ startSquare: best.slice(0, 2), endSquare: best.slice(2, 4), color: ARROW }] : []), [best])
  const squareStyles = useMemo(
    () => ({
      ...(lastMove ? { [lastMove.slice(0, 2)]: LAST, [lastMove.slice(2, 4)]: LAST } : {}),
      ...(selected ? { [selected]: PICKED } : {}),
    }),
    [lastMove, selected],
  )
  return (
    <Chessboard
      options={{
        id,
        position: fen,
        boardOrientation: orientation,
        allowDragging: interactive,
        allowDrawingArrows: false,
        onPieceDrop: ({ sourceSquare, targetSquare }) => (interactive && targetSquare && onMove ? onMove(sourceSquare, targetSquare) : false),
        onSquareClick: ({ square }) => interactive && onSquare?.(square),
        arrows,
        animationDurationInMs: 150,
        lightSquareStyle: LIGHT,
        darkSquareStyle: DARK,
        squareStyles,
      }}
    />
  )
})
