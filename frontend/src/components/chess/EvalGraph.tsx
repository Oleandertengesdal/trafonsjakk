import type { Judgement } from '../../chess/analysis'

const W = 320
const H = 84
const markColour: Record<Judgement, string> = { blunder: '#c0392b', mistake: '#d4a017', inaccuracy: '#7b8794' }

/**
 * White's winning chance through the game: cream (White) below the curve, slate (Black) above it, so the
 * colour that fills the picture is the side that's ahead. Mistakes are dots; tap anywhere to go to that move.
 */
export function EvalGraph({
  wins,
  marks,
  ply,
  label,
  onSelect,
}: {
  /** White's win % (0–100) for the position after each ply, starting with the initial position. */
  wins: number[]
  marks: { ply: number; judgement: Judgement }[]
  ply: number
  label: string
  onSelect: (ply: number) => void
}) {
  const n = Math.max(1, wins.length - 1)
  const x = (i: number) => (i / n) * W
  const y = (w: number) => H - (w / 100) * H
  const line = wins.map((w, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)} ${y(w).toFixed(1)}`).join(' ')
  const area = `${line} L${W} ${H} L0 ${H} Z`

  return (
    <svg
      viewBox={`0 0 ${W} ${H}`}
      role="img"
      aria-label={label}
      className="block h-auto w-full cursor-pointer touch-manipulation overflow-visible rounded-control"
      onClick={(e) => {
        const r = e.currentTarget.getBoundingClientRect()
        onSelect(Math.round(((e.clientX - r.left) / r.width) * n))
      }}
    >
      <rect width={W} height={H} rx="6" fill="#2e333a" />
      <path d={area} fill="#f3e6d0" />
      <line x1="0" x2={W} y1={H / 2} y2={H / 2} stroke="#b86a58" strokeWidth="1" strokeDasharray="3 3" opacity="0.7" />
      <line x1={x(ply)} x2={x(ply)} y1="0" y2={H} stroke="#c23a2e" strokeWidth="2" />
      {marks.map((m) => (
        <circle
          key={m.ply}
          cx={x(m.ply)}
          cy={y(wins[m.ply])}
          r={m.judgement === 'blunder' ? 4.5 : 3.5}
          fill={markColour[m.judgement]}
          stroke="#fbf6ec"
          strokeWidth="1.5"
        />
      ))}
    </svg>
  )
}

/** A vertical bar beside the board: the cream part is White's share of the winning chances. */
export function EvalBar({ white, whiteAtBottom, label }: { white: number; whiteAtBottom: boolean; label: string }) {
  return (
    <div role="img" aria-label={label} className="relative w-3 shrink-0 overflow-hidden rounded-full bg-[#2e333a]">
      <div
        className="absolute inset-x-0 bg-[#f3e6d0] transition-[height] duration-300"
        style={{ height: `${white}%`, [whiteAtBottom ? 'bottom' : 'top']: 0 }}
      />
    </div>
  )
}
