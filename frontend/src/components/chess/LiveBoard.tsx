// A read-only board showing where a live game stands, with the last move marked. Loaded lazily
// (chess.js and the board aren't needed on most pages).
import { useMemo } from 'react'
import { Chessboard } from 'react-chessboard'
import { moveSquares, parseMoves, positionAfter } from '../../chess/moves'

const highlight = { backgroundColor: 'rgb(226 177 74 / 0.5)' }

export default function LiveBoard({ moves, orientation = 'white', id }: { moves: string | null; orientation?: 'white' | 'black'; id: string }) {
  const sans = useMemo(() => {
    const parsed = parseMoves(moves ?? '')
    return parsed.sans
  }, [moves])
  const last = sans.length > 0 ? moveSquares(sans)[sans.length - 1] : null
  return (
    <Chessboard
      options={{
        id,
        position: positionAfter(sans, sans.length),
        boardOrientation: orientation,
        allowDragging: false,
        allowDrawingArrows: false,
        animationDurationInMs: 250,
        lightSquareStyle: { backgroundColor: '#f3e6d0' },
        darkSquareStyle: { backgroundColor: '#b86a58' },
        squareStyles: last ? { [last.from]: highlight, [last.to]: highlight } : {},
      }}
    />
  )
}
