import { cn } from '../../lib/cn'

// Arched-window avatars in the facade palette.
// Fixed, theme-independent tones so cream initials always have enough contrast.
const tones = [
  'bg-brand text-on-accent',
  'bg-[#2e333a] text-[#fbf6ec] ring-1 ring-inset ring-white/10',
  'bg-[#2b6a4b] text-[#fbf6ec]',
  'bg-[#7d5a17] text-[#fbf6ec]',
  'bg-raised text-ink ring-1 ring-inset ring-line',
]

function hash(s: string) {
  let h = 0
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0
  return Math.abs(h)
}

function initials(name: string) {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  const first = parts[0][0] ?? ''
  const last = parts.length > 1 ? (parts[parts.length - 1][0] ?? '') : ''
  return (first + last).toUpperCase()
}

/** Initials in an arched window. */
export function Avatar({ name, seed, size = 'md' }: { name: string; seed: string; size?: 'sm' | 'md' | 'lg' }) {
  return (
    <span
      aria-hidden
      className={cn(
        'inline-flex shrink-0 items-end justify-center rounded-t-full rounded-b-[5px] font-display font-semibold leading-none',
        tones[hash(seed) % tones.length],
        size === 'sm' && 'h-8 w-7 pb-1.5 text-[0.6875rem]',
        size === 'md' && 'h-10 w-9 pb-2 text-[0.8125rem]',
        size === 'lg' && 'h-[4.75rem] w-16 rounded-b-[8px] pb-3.5 text-xl',
      )}
    >
      {initials(name)}
    </span>
  )
}
