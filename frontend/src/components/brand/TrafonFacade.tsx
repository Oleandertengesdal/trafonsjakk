import { useId } from 'react'
import { cn } from '../../lib/cn'

/**
 * The Trafon building, drawn from photos of its front: a red two-storey block under a round gable
 * (cream trim, an oval window with painted rays, darker brick panels either side), three windows
 * upstairs, sunburst ornaments over two arched black doors, a small lunette between them, a banded
 * ground floor, a grey plinth and stone steps. Purely decorative.
 *
 * - `variant="color"`: full colour; `variant="line"`: outline in `currentColor`, for red surfaces.
 * - `lights`: the windows and door lamps are lit (`on`) or dark (`off`, the default). Trafon was a
 *   transformer station, so the error pages use the lights: on for "nothing here", off for "the fuse blew".
 * - `plate`: a short text on an enamel house-number plate beside the left door (the error pages put
 *   their status code there, e.g. 404).
 */
export function TrafonFacade({
  variant = 'color',
  lights = 'off',
  plate,
  className,
}: {
  variant?: 'color' | 'line'
  lights?: 'on' | 'off'
  plate?: string
  className?: string
}) {
  const clip = useId()
  return (
    <svg viewBox="0 0 320 212" aria-hidden className={cn('block', className)} fill="none">
      {variant === 'line' ? <Outline /> : <Colour lit={lights === 'on'} clipId={clip} />}
      {plate && (
        <g>
          <rect x="123" y="149" width="24" height="12.5" rx="2.2" fill="#244a7c" stroke="var(--c-trim)" strokeWidth="1" />
          <text x="135" y="158.6" textAnchor="middle" fill="#fdfaf4" fontSize="8.4" fontWeight="700" fontFamily="var(--font-display)">
            {plate}
          </text>
        </g>
      )}
    </svg>
  )
}

// Geometry shared by both versions (viewBox 320 × 212; ground at y = 204)
const DOORS = [110, 210]
const WINDOWS = [110, 160, 210]
const PILASTERS = [56.5, 89.5, 95, 127, 133, 187, 193, 225, 230.5, 263.5]
const BANDS = [150, 156, 162, 168, 174]
const gable = (rx: number, ry: number) => `M${160 - rx} 57A${rx} ${ry} 0 0 1 ${160 + rx} 57Z`
const doorArch = (cx: number, w: number, top: number, bottom: number) =>
  `M${cx - w / 2} ${bottom}V${top + w / 2}A${w / 2} ${w / 2} 0 0 1 ${cx + w / 2} ${top + w / 2}V${bottom}Z`
/** Stone steps in front of each door: [width, top, height], widest at the bottom. */
const STEPS = [
  [30, 189, 4.6],
  [42, 193.6, 5.2],
  [56, 198.8, 5.2],
] as const

function Colour({ lit, clipId }: { lit: boolean; clipId: string }) {
  const glass = lit ? 'var(--c-lit)' : 'var(--c-glass)'
  const frame = 'var(--c-roof)'
  const rays = Array.from({ length: 13 }, (_, i) => (Math.PI * (i + 1)) / 14)
  return (
    <>
      <defs>
        <clipPath id={clipId}>
          <path d={gable(84, 46)} />
        </clipPath>
      </defs>

      {/* ground */}
      <rect x="0" y="204" width="320" height="8" fill="var(--c-line)" />

      {/* roof stubs behind the gable's ends */}
      <path d="M44 57 51 50.5H76V57ZM276 57 269 50.5H244V57Z" fill="var(--c-roof)" />

      {/* round gable: metal edge, cream trim, red field with darker brick panels and painted rays */}
      <path d={gable(91, 53)} fill="var(--c-roof)" />
      <path d={gable(89, 51)} fill="var(--c-trim)" />
      <path d={gable(84, 46)} fill="var(--c-brand)" />
      <g clipPath={`url(#${clipId})`}>
        <path d="M90 57A70.4 29.4 0 0 1 127 31V57ZM230 57A70.4 29.4 0 0 0 193 31V57Z" fill="var(--c-brick)" />
        <g stroke="var(--c-brick)" strokeWidth="0.7">
          {rays.map((a) => (
            <path key={a} d={`M${160 - Math.cos(a) * 14} ${36 - Math.sin(a) * 9.5}L${160 - Math.cos(a) * 60} ${36 - Math.sin(a) * 40}`} />
          ))}
        </g>
      </g>
      {/* oval window */}
      <ellipse cx="160" cy="36" rx="12.5" ry="8.5" fill="var(--c-trim)" />
      <ellipse cx="160" cy="36" rx="10.6" ry="6.8" fill={glass} />
      <path d="M160 29.2V42.8" stroke="var(--c-trim)" strokeWidth="1.1" />

      {/* walls, cornice and pilasters */}
      <rect x="50" y="57" width="220" height="125" fill="var(--c-brand)" />
      <rect x="44" y="56" width="232" height="6" fill="var(--c-trim)" />
      <g stroke="var(--c-brick)" strokeWidth="1">
        {PILASTERS.map((x) => (
          <path key={x} d={`M${x} 62V146`} />
        ))}
      </g>

      {/* upstairs windows: dark frames, six panes */}
      {WINDOWS.map((cx) => (
        <g key={cx}>
          <rect x={cx - 11} y="75" width="22" height="19" fill={frame} />
          <rect x={cx - 9.6} y="76.4" width="19.2" height="16.2" fill={glass} />
          <path d={`M${cx} 76.4V92.6M${cx - 9.6} 81.8H${cx + 9.6}M${cx - 9.6} 87.2H${cx + 9.6}`} stroke={frame} strokeWidth="1" />
        </g>
      ))}

      {/* ground floor: string course and horizontal bands */}
      <rect x="50" y="145.5" width="220" height="1.6" fill="var(--c-brick)" />
      <g stroke="var(--c-brick)" strokeWidth="0.8">
        {BANDS.map((y) => (
          <path key={y} d={`M50 ${y}H270`} />
        ))}
      </g>

      {/* sunburst ornaments over the doors */}
      {DOORS.map((cx) => (
        <g key={cx}>
          <path d={`M${cx - 15} 109H${cx + 15}V124.5Q${cx + 9} 124.5 ${cx + 9} 131H${cx - 9}Q${cx - 9} 124.5 ${cx - 15} 124.5Z`} fill="var(--c-brick)" />
          <g stroke="var(--c-trim)" strokeWidth="0.5" opacity="0.8">
            {[-1, -0.6, -0.25, 0.25, 0.6, 1].map((k) => (
              <path key={k} d={`M${cx + k * 5} 124L${cx + k * 13.5} 114`} />
            ))}
          </g>
          <path d={`M${cx - 15} 109H${cx + 15}V124.5Q${cx + 9} 124.5 ${cx + 9} 131M${cx - 15} 109V124.5Q${cx - 9} 124.5 ${cx - 9} 131`} stroke="var(--c-trim)" strokeWidth="1.5" />
          <path d={`M${cx - 6.5} 128A6.5 6.5 0 0 1 ${cx + 6.5} 128`} stroke="var(--c-trim)" strokeWidth="1.4" />
          <path d={`M${cx - 14} 111.2H${cx + 14}`} stroke="var(--c-trim)" strokeWidth="2.6" strokeDasharray="2.4 1.1" />
          {[
            [cx - 15, 109],
            [cx + 15, 109],
            [cx - 15, 124.5],
            [cx + 15, 124.5],
          ].map(([x, y]) => (
            <circle key={`${x}-${y}`} cx={x} cy={y} r="1.7" fill="var(--c-trim)" />
          ))}
        </g>
      ))}

      {/* arched doors with fanlights and lamps */}
      {DOORS.map((cx) => (
        <g key={cx}>
          <path d={doorArch(cx, 24, 132, 189)} fill="var(--c-brick)" />
          <path d={doorArch(cx, 20, 135, 189)} fill={frame} />
          <path d={`M${cx - 8.6} 145A8.6 8.6 0 0 1 ${cx + 8.6} 145Z`} fill={glass} />
          <path d={`M${cx} 136.4V145M${cx - 6} 139.4 ${cx - 3} 145M${cx + 6} 139.4 ${cx + 3} 145M${cx - 10} 146H${cx + 10}`} stroke={frame} strokeWidth="0.9" />
          <circle cx={cx} cy="131.2" r="2.1" fill={lit ? 'var(--c-lit)' : 'var(--c-trim)'} />
          {lit && <circle cx={cx} cy="131.2" r="5" fill="var(--c-lit)" opacity="0.25" />}
        </g>
      ))}
      {/* lunette between the doors */}
      <path d="M148.5 147A11.5 11.5 0 0 1 171.5 147Z" fill={frame} />
      <path d="M150.2 146.2A9.8 9.8 0 0 1 169.8 146.2Z" fill={glass} />
      <path d="M160 136.4V146.2M153.3 139.3 157 146.2M166.7 139.3 163 146.2" stroke={frame} strokeWidth="0.9" />

      {/* plinth, vent and steps */}
      <rect x="50" y="182" width="220" height="22" fill="var(--c-plinth)" />
      <rect x="151" y="187" width="18" height="11" fill="var(--c-roof)" />
      <path d="M154 187V198M157 187V198M160 187V198M163 187V198M166 187V198" stroke="var(--c-plinth)" strokeWidth="0.9" />
      {DOORS.map((cx) =>
        STEPS.map(([w, y, h]) => <rect key={`${cx}-${y}`} x={cx - w / 2} y={y} width={w} height={h} fill="var(--c-step)" stroke="var(--c-plinth)" strokeWidth="0.5" />),
      )}
    </>
  )
}

function Outline() {
  const s = { stroke: 'currentColor', strokeWidth: 1.4, strokeLinejoin: 'round' as const }
  const thin = { stroke: 'currentColor', strokeWidth: 0.9 }
  return (
    <>
      <path d="M8 204.5H312" {...s} />
      <path d="M44 57 51 50.5H70M276 57 269 50.5H250" {...s} />
      <path d={gable(91, 53)} {...s} />
      <path d={`M${160 - 84} 57A84 46 0 0 1 ${160 + 84} 57`} {...thin} />
      <ellipse cx="160" cy="36" rx="12.5" ry="8.5" {...s} />
      <path d="M160 27.5V44.5" {...thin} />
      <path d="M44 56H276V62H44Z" {...s} />
      <path d="M50 62V204H270V62" {...s} />
      <path d="M50 182H270" {...thin} />
      <path d="M50 146.3H270" {...thin} />
      {WINDOWS.map((cx) => (
        <g key={cx}>
          <rect x={cx - 11} y="75" width="22" height="19" {...s} />
          <path d={`M${cx} 75V94M${cx - 11} 81.8H${cx + 11}M${cx - 11} 87.2H${cx + 11}`} {...thin} />
        </g>
      ))}
      {DOORS.map((cx) => (
        <g key={cx}>
          <path d={`M${cx - 15} 109H${cx + 15}V124.5Q${cx + 9} 124.5 ${cx + 9} 131M${cx - 15} 109V124.5Q${cx - 9} 124.5 ${cx - 9} 131`} {...thin} />
          <path d={`M${cx - 6.5} 128A6.5 6.5 0 0 1 ${cx + 6.5} 128`} {...thin} />
          <path d={doorArch(cx, 20, 135, 189)} {...s} />
          <path d={`M${cx - 10} 146H${cx + 10}`} {...thin} />
          {STEPS.map(([w, y, h]) => (
            <rect key={y} x={cx - w / 2} y={y} width={w} height={h} {...thin} />
          ))}
        </g>
      ))}
      <path d="M148.5 147A11.5 11.5 0 0 1 171.5 147Z" {...s} />
    </>
  )
}
