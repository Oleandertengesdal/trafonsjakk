/** A small rating line for a story card: start, then the rating at the end of each month played. */
export function Sparkline({ start, points, className }: { start: number; points: number[]; className?: string }) {
  const values = [start, ...points]
  if (values.length < 2) return null
  const min = Math.min(...values)
  const max = Math.max(...values)
  const span = Math.max(max - min, 20)
  const w = 240
  const h = 64
  const xy = values.map((v, i) => [(i / (values.length - 1)) * w, h - 6 - ((v - min) / span) * (h - 12)] as const)
  const d = xy.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`).join(' ')
  const [lx, ly] = xy[xy.length - 1]
  return (
    <svg viewBox={`-4 0 ${w + 8} ${h}`} aria-hidden className={className}>
      <path d={d} fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" opacity="0.9" />
      <circle cx={lx} cy={ly} r="4" fill="currentColor" />
    </svg>
  )
}
