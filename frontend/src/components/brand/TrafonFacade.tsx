import { cn } from '../../lib/cn'

/**
 * A simplified drawing of the Trafon building: hipped slate roof, red facade with cream trim,
 * a central bay with a curved gable and arched windows. Purely decorative.
 *
 * - `color`: full colour (red walls, cream trim, slate roof)
 * - `line`: single-colour outline in `currentColor`, for use on top of red surfaces
 */
export function TrafonFacade({ variant = 'color', className }: { variant?: 'color' | 'line'; className?: string }) {
  const line = variant === 'line'
  const wall = line ? 'none' : 'var(--c-brand)'
  const trim = line ? 'none' : 'var(--c-trim)'
  const roof = line ? 'none' : 'var(--c-roof)'
  const glass = line ? 'none' : 'var(--c-glass)'
  const stroke = line ? { stroke: 'currentColor', strokeWidth: 1.4, strokeLinejoin: 'round' as const } : {}

  // Arched window: frame (trim), glass inset, and a cross of glazing bars.
  const win = (cx: number, top: number, w: number, h: number, door = false) => {
    const x = cx - w / 2
    const r = w / 2
    const frame = `M${x} ${top + h}V${top + r}A${r} ${r} 0 0 1 ${x + w} ${top + r}V${top + h}Z`
    const i = 2.6
    const gx = x + i
    const gw = w - 2 * i
    const gr = gw / 2
    const pane = `M${gx} ${top + h}V${top + r}A${gr} ${gr} 0 0 1 ${gx + gw} ${top + r}V${top + h}Z`
    return (
      <g key={`${cx}-${top}`}>
        <path d={frame} fill={trim} {...stroke} />
        <path d={pane} fill={door ? roof : glass} {...(line ? { ...stroke, strokeWidth: 1 } : {})} />
        {!door && !line && (
          <path d={`M${cx} ${top + i + 1}V${top + h}M${gx} ${top + r + 4}H${gx + gw}`} stroke="var(--c-trim)" strokeWidth="1.2" />
        )}
      </g>
    )
  }

  const sideXs = [60, 92, 228, 260]
  return (
    <svg viewBox="0 0 320 212" aria-hidden className={cn('block', className)} fill="none">
      {/* ground */}
      {!line && <rect x="0" y="202" width="320" height="10" fill="var(--c-line)" />}
      {line && <path d="M8 202.5H312" stroke="currentColor" strokeWidth="1.4" />}
      {/* hipped roof */}
      <path d="M20 82 58 42H262L300 82Z" fill={roof} {...stroke} />
      {/* main walls */}
      <rect x="30" y="80" width="260" height="122" fill={wall} {...stroke} />
      {/* central bay with curved gable */}
      <path d="M116 202V66H204V202Z" fill={wall} {...stroke} />
      <path d="M112 68Q160 10 208 68Z" fill={wall} {...stroke} />
      <path d="M112 68Q160 10 208 68" stroke={line ? 'currentColor' : 'var(--c-trim)'} strokeWidth={line ? 1.4 : 3} />
      {/* cornices and floor band */}
      {!line && (
        <>
          <rect x="26" y="78" width="268" height="5" fill="var(--c-trim)" />
          <rect x="112" y="64" width="96" height="5" fill="var(--c-trim)" />
          <rect x="30" y="138" width="260" height="3" fill="var(--c-trim)" />
          {/* corner and bay pilasters */}
          {[30, 116, 199, 285].map((x) => (
            <rect key={x} x={x} y="83" width="5" height="113" fill="var(--c-trim)" />
          ))}
          {/* plinth */}
          <rect x="28" y="194" width="264" height="8" fill="var(--c-brand-hover)" />
        </>
      )}
      {line && <path d="M30 139.5H290" stroke="currentColor" strokeWidth="1" />}
      {/* gable oculus */}
      <circle cx="160" cy="48" r="7" fill={trim} {...stroke} />
      {!line && <circle cx="160" cy="48" r="4.6" fill="var(--c-glass)" />}
      {/* windows */}
      {sideXs.map((x) => win(x, 92, 20, 38))}
      {sideXs.map((x) => win(x, 150, 20, 40))}
      {win(160, 86, 34, 44)}
      {win(160, 150, 30, 44, true)}
      {/* steps */}
      {!line && <rect x="138" y="198" width="44" height="4" fill="var(--c-trim)" />}
    </svg>
  )
}
