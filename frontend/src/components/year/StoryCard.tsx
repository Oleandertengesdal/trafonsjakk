import { useEffect, useState, type ReactNode } from 'react'
import { cn } from '../../lib/cn'
import { Avatar } from '../chess/Avatar'

/**
 * Card colours. Fixed values (not theme tokens), so a card looks the same in light and dark mode and in the
 * shared image. They're the facade's own colours: brick, slate roof, window-trim cream, brass and the green door.
 */
export type Tone = 'brick' | 'slate' | 'paper' | 'brass' | 'green'

const tones: Record<Tone, string> = {
  brick: 'brick text-[#fbf6ec]',
  slate: 'bg-[#2e333a] text-[#fbf6ec]',
  paper: 'bg-[#fbf6ec] text-[#23252a]',
  brass: 'bg-[#8a6416] text-[#fbf6ec]',
  green: 'bg-[#2b6a4b] text-[#fbf6ec]',
}

const trim: Record<Tone, string> = {
  brick: 'border-[#fbf6ec]/30',
  slate: 'border-[#fbf6ec]/20',
  paper: 'border-[#23252a]/15',
  brass: 'border-[#fbf6ec]/30',
  green: 'border-[#fbf6ec]/25',
}

/** A semicircular top on a 9:16 card: the arched windows of the Trafon building. */
const ARCH = '50% 50% 18px 18px / 28.2% 28.2% 18px 18px'

/** One story card, shaped like an arched window. Content is laid out top to bottom inside the trim. */
export function StoryCard({ tone, kicker, footer, children, className }: { tone: Tone; kicker: ReactNode; footer: ReactNode; children: ReactNode; className?: string }) {
  return (
    <div
      className={cn('relative flex aspect-[9/16] w-full flex-col overflow-hidden [container-type:inline-size] shadow-[0_24px_48px_-28px_rgb(35_37_42/0.55)]', tone === 'paper' && 'ring-1 ring-[#23252a]/10', tones[tone], className)}
      style={{ borderRadius: ARCH }}
    >
      <div aria-hidden className={cn('pointer-events-none absolute inset-2.5 border', trim[tone])} style={{ borderRadius: ARCH }} />
      <div className="relative flex flex-1 flex-col px-[9%] pt-[22%] pb-[7%]">
        <p className="text-center font-display text-[1.0625rem] font-medium opacity-80">{kicker}</p>
        <div className="flex flex-1 flex-col justify-center">{children}</div>
        <div className="flex items-center justify-center gap-2 text-[0.8125rem] opacity-75">
          <svg viewBox="0 0 32 32" aria-hidden className="size-4 shrink-0">
            <path d="M5 30V14a11 11 0 0 1 22 0v16Z" fill="none" stroke="currentColor" strokeWidth="2.2" />
            <circle cx="16" cy="12.5" r="3" fill="currentColor" />
            <path d="M13.4 16.4h5.2l-.8 2c1.3 1.6 2 3.4 2.1 5.4h-7.8c.1-2 .8-3.8 2.1-5.4Z M10.8 24.6h10.4v2.4H10.8Z" fill="currentColor" />
          </svg>
          {footer}
        </div>
      </div>
    </div>
  )
}

/** An avatar in a light window frame, so it stands out on every card colour. */
export function CardAvatar({ player, size = 'lg' }: { player: { realName: string; username: string }; size?: 'md' | 'lg' }) {
  return (
    <span className="inline-flex rounded-t-full rounded-b-[10px] bg-[#fbf6ec] p-1 shadow-[0_0_0_1px_rgb(35_37_42/0.12)]">
      <Avatar name={player.realName} seed={player.username} size={size} />
    </span>
  )
}

/**
 * The big number or word on a card. Sized in container units, so it scales with the card, and smaller for
 * longer words so "september" or "Wednesday" never breaks in the middle.
 */
export function Big({ children, className, sentence }: { children: ReactNode; className?: string; sentence?: boolean }) {
  const len = typeof children === 'string' ? Math.max(...children.split(' ').map((w) => w.length)) : 3
  const size = sentence ? 'text-[9cqw]' : len <= 4 ? 'text-[22cqw]' : len <= 7 ? 'text-[15cqw]' : len <= 10 ? 'text-[12cqw]' : 'text-[10cqw]'
  return <p className={cn('text-center font-display leading-[0.95] font-bold tracking-[-0.04em] text-balance', size, className)}>{children}</p>
}

export function Line({ children, className }: { children: ReactNode; className?: string }) {
  return <p className={cn('mt-4 text-center text-[1.0625rem] leading-snug text-balance', className)}>{children}</p>
}

/** Counts up to `value` once (skipped with reduced motion). The final value is always what's read out. */
export function CountUp({ value, format = String }: { value: number; format?: (n: number) => string }) {
  const [shown, setShown] = useState(() => (animates() ? 0 : value))
  useEffect(() => {
    if (!animates()) return
    let frame = 0
    const start = performance.now()
    const tick = (now: number) => {
      const p = Math.min(1, (now - start) / 700)
      setShown(Math.round(value * (1 - Math.pow(1 - p, 4))))
      if (p < 1) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [value])
  return (
    <span aria-label={format(value)}>
      <span aria-hidden>{format(shown)}</span>
    </span>
  )
}

function animates() {
  return typeof window !== 'undefined' && typeof window.matchMedia === 'function' && !window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/** Wins, draws and losses as one bar in the card's own colour. */
export function RecordBar({ w, d, l }: { w: number; d: number; l: number }) {
  const total = w + d + l
  if (!total) return null
  return (
    <div aria-hidden className="mx-auto mt-5 flex h-2.5 w-4/5 gap-0.5 overflow-hidden rounded-full">
      {w > 0 && <span className="bg-current" style={{ flexGrow: w }} />}
      {d > 0 && <span className="bg-current opacity-45" style={{ flexGrow: d }} />}
      {l > 0 && <span className="bg-current opacity-20" style={{ flexGrow: l }} />}
    </div>
  )
}

/** Runners-up under the winner: "2. Ola Hansen  28". */
export function Podium({ rows }: { rows: [string, string][] }) {
  if (rows.length === 0) return null
  return (
    <ol className="mx-auto mt-6 w-4/5 space-y-1.5 text-[1rem]">
      {rows.map(([name, value], i) => (
        <li key={name} className="flex items-baseline justify-between gap-3 border-t border-current/20 pt-1.5">
          <span className="truncate">
            <span className="mr-2 opacity-70">{i + 2}.</span>
            {name}
          </span>
          <span className="font-display font-semibold tabular-nums">{value}</span>
        </li>
      ))}
    </ol>
  )
}
